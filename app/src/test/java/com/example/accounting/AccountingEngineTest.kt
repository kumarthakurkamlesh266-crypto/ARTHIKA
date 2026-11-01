package com.example.accounting

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.entity.AccountEntity
import com.example.data.entity.TransactionEntity
import com.example.data.model.AccountType
import com.example.data.model.TransactionType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AccountingEngineTest {

    private lateinit var db: AppDatabase
    private lateinit var accountingEngine: AccountingEngine
    private lateinit var reconciliationEngine: ReconciliationEngine
    private lateinit var toolDispatcher: AccountingAiToolDispatcher

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        accountingEngine = AccountingEngine(db)
        reconciliationEngine = ReconciliationEngine(db)
        toolDispatcher = AccountingAiToolDispatcher(db, accountingEngine, reconciliationEngine)

        runBlocking {
            AppDatabase.populateInitialData(db)
        }
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `test accurate income and expense calculations`() = runBlocking {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -5) }
        db.transactionDao().insertAll(
            listOf(
                TransactionEntity(accountId = 1L, amount = 1000.0, type = TransactionType.INCOME, category = "Salary", dateMillis = cal.timeInMillis, timeString = "09:00", paymentMethod = "Bank Transfer", merchant = "Test Employer"),
                TransactionEntity(accountId = 1L, amount = 200.0, type = TransactionType.EXPENSE, category = "Food", dateMillis = now, timeString = "13:00", paymentMethod = "Credit Card", merchant = "Test Restaurant")
            )
        )

        val summary = accountingEngine.calculateIncomeAndExpense(cal.timeInMillis - 1000, now + 1000)

        assertTrue("Income must be strictly positive", summary.totalIncome > 0.0)
        assertTrue("Expense must be strictly positive", summary.totalExpense > 0.0)
        assertEquals("Net savings must equal income minus expense",
            ((summary.totalIncome - summary.totalExpense) * 100).toInt(),
            (summary.netSavings * 100).toInt()
        )
        assertTrue("Savings rate must be calculated", summary.savingsRatePct >= 0.0)
    }

    @Test
    fun `test multiple account balances opening and closing integrity`() = runBlocking {
        val balances = accountingEngine.calculateAllAccountBalances()
        assertTrue("Must have active accounts configured", balances.isNotEmpty())

        val checking = balances.firstOrNull { it.accountId == 1L }
        assertNotNull("Checking account must exist", checking)
        assertEquals(AccountType.CHECKING, checking?.accountType)
        assertEquals("Opening balance is 0.0 for fresh clean account", 0.0, checking!!.openingBalance, 0.01)
    }

    @Test
    fun `test internal transfer updates both accounts and double-entry ledger`() = runBlocking {
        val fromAccount = db.accountDao().getAccountById(1L)!! // Checking
        val toAccount = db.accountDao().getAccountById(2L)!!   // Savings
        db.accountDao().updateAccount(fromAccount.copy(initialBalance = 1000.0, currentBalance = 1000.0))
        val initialFromBal = 1000.0
        val initialToBal = toAccount.currentBalance

        val transferAmount = 500.0
        val transferFee = 2.50

        val result = accountingEngine.executeTransfer(
            fromAccountId = 1L,
            toAccountId = 2L,
            amount = transferAmount,
            fee = transferFee,
            dateMillis = System.currentTimeMillis(),
            notes = "Monthly savings allocation"
        )

        assertTrue("Transfer must succeed", result is TransferExecutionResult.Success)
        val success = result as TransferExecutionResult.Success

        val updatedFrom = db.accountDao().getAccountById(1L)!!
        val updatedTo = db.accountDao().getAccountById(2L)!!

        assertEquals(
            "Source account must be debited by amount + fee",
            initialFromBal - (transferAmount + transferFee),
            updatedFrom.currentBalance,
            0.01
        )
        assertEquals(
            "Destination account must be credited by amount",
            initialToBal + transferAmount,
            updatedTo.currentBalance,
            0.01
        )

        // Verify Journal Entry
        val entries = db.journalEntryDao().getEntriesForTransaction(success.sourceTxId)
        assertFalse("Journal entry must be posted", entries.isEmpty())
        val entry = entries.first()
        assertEquals(1L, entry.creditAccountId)
        assertEquals(2L, entry.debitAccountId)
        assertEquals(transferAmount, entry.debitAmount, 0.01)
        assertEquals(transferAmount, entry.creditAmount, 0.01)
    }

    @Test
    fun `test transfer edge cases - zero, negative and self transfer`() = runBlocking {
        val resZero = accountingEngine.executeTransfer(1L, 2L, 0.0)
        assertTrue("Zero amount transfer must fail", resZero is TransferExecutionResult.Error)

        val resNegative = accountingEngine.executeTransfer(1L, 2L, -100.0)
        assertTrue("Negative amount transfer must fail", resNegative is TransferExecutionResult.Error)

        val resSelf = accountingEngine.executeTransfer(1L, 1L, 200.0)
        assertTrue("Self transfer must fail", resSelf is TransferExecutionResult.Error)

        val resNonexistent = accountingEngine.executeTransfer(999L, 1L, 100.0)
        assertTrue("Nonexistent account transfer must fail", resNonexistent is TransferExecutionResult.Error)
    }

    @Test
    fun `test refund and reversal restores balance and logs audit trail`() = runBlocking {
        // Create an expense
        val tx = TransactionEntity(
            accountId = 1L,
            amount = 150.0,
            type = TransactionType.EXPENSE,
            category = "Shopping",
            dateMillis = System.currentTimeMillis(),
            timeString = "14:00",
            paymentMethod = "Credit Card",
            merchant = "BestBuy Tech"
        )
        val txId = db.transactionDao().insertTransaction(tx)
        val balBefore = accountingEngine.calculateAccountBalance(1L)!!.closingBalance

        // 1. Partial Refund
        val refundRes = accountingEngine.executeRefund(txId, 50.0, "Returned defective adapter")
        assertTrue("Refund must succeed", refundRes is RefundExecutionResult.Success)
        val balAfterRefund = (refundRes as RefundExecutionResult.Success).newBalance
        assertEquals("Account balance must increase by refund amount", balBefore + 50.0, balAfterRefund, 0.01)

        // 2. Full Reversal
        val reversalRes = accountingEngine.executeReversal(txId, "Duplicate charge reversed")
        assertTrue("Reversal must succeed", reversalRes is ReversalExecutionResult.Success)
        val reversedTx = db.transactionDao().getTransactionById(txId)!!
        assertEquals("Status must be REVERSED", "REVERSED", reversedTx.status)

        // 3. Verify Audit Log was recorded
        val auditLogs = db.auditLogDao().getLogsForEntity("TRANSACTION", txId)
        assertFalse("Audit log must contain transaction action", auditLogs.isEmpty())
    }

    @Test
    fun `test duplicate transaction detection with deterministic fingerprint`() = runBlocking {
        val now = System.currentTimeMillis()
        val fp = accountingEngine.generateFingerprint(1L, 89.99, "Amazon", now)
        assertNotNull(fp)
        assertEquals("Fingerprint must be deterministic SHA-256", 64, fp.length)

        val tx = TransactionEntity(
            accountId = 1L,
            amount = 89.99,
            type = TransactionType.EXPENSE,
            category = "Shopping",
            dateMillis = now,
            timeString = "18:00",
            paymentMethod = "Credit Card",
            merchant = "Amazon",
            fingerprintHash = fp
        )
        db.transactionDao().insertTransaction(tx)

        val dupResult = accountingEngine.checkForDuplicate(1L, 89.99, "Amazon", now)
        assertTrue("Must detect duplicate transaction", dupResult.isDuplicate)
        assertEquals(1.0f, dupResult.matchConfidence, 0.01f)
    }

    @Test
    fun `test statement parser and bank reconciliation engine`() = runBlocking {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val date25 = sdf.parse("2026-10-25")?.time ?: 0L
        val date31 = sdf.parse("2026-10-31")?.time ?: 0L
        val date30 = sdf.parse("2026-10-30")?.time ?: 0L

        db.transactionDao().insertAll(
            listOf(
                TransactionEntity(accountId = 1L, amount = 4500.0, type = TransactionType.INCOME, category = "Salary", dateMillis = date25, timeString = "09:00", paymentMethod = "Bank Transfer", merchant = "Acme Corp Tech"),
                TransactionEntity(accountId = 1L, amount = 38.50, type = TransactionType.EXPENSE, category = "Food", dateMillis = date31, timeString = "12:00", paymentMethod = "Debit Card", merchant = "Green Kitchen Cafe"),
                TransactionEntity(accountId = 1L, amount = 124.80, type = TransactionType.EXPENSE, category = "Food", dateMillis = date30, timeString = "14:00", paymentMethod = "Debit Card", merchant = "Whole Foods Market")
            )
        )

        val csvStatement = """
            Date,Description,Amount,Type
            2026-10-25,Acme Corp Tech,4500.00,CREDIT
            2026-10-31,Green Kitchen Cafe,38.50,DEBIT
            2026-10-30,Whole Foods Market,124.80,DEBIT
            2026-10-29,Unknown Bank Fee,15.00,DEBIT
        """.trimIndent()

        val parsed = reconciliationEngine.parseStatementText(
            rawText = csvStatement,
            fileName = "bank_statement.csv",
            defaultOpeningBalance = 3200.0
        )

        assertEquals(4, parsed.items.size)
        assertEquals(4500.0, parsed.items[0].amount, 0.01)
        assertTrue(parsed.items[0].isCredit)
        assertFalse(parsed.items[1].isCredit)

        // Run reconciliation against Checking Account (id=1)
        val reconSummary = reconciliationEngine.reconcileAccount(1L, parsed)
        assertTrue("Must match existing system transactions", reconSummary.matchedCount >= 3)
        assertEquals("Must identify 1 unmatched statement entry (Bank Fee)", 1, reconSummary.unmatchedStatementCount)
        assertNotNull(reconSummary.reconciliationId)
    }

    @Test
    fun `test AI Tool Dispatcher returns verified mathematical calculations`() = runBlocking {
        val balanceResult = toolDispatcher.executeTool("get_account_balances")
        assertTrue("Account balance tool must succeed", balanceResult.isSuccess)
        assertTrue("Tool result must contain verified account names", balanceResult.verifiedDataSummary.contains("Main Account"))

        val cashFlowResult = toolDispatcher.executeTool("get_cash_flow_statement")
        assertTrue("Cash flow statement tool must succeed", cashFlowResult.isSuccess)
        assertTrue("Must report liquid assets", cashFlowResult.verifiedDataSummary.contains("Liquid Assets"))

        val dupResult = toolDispatcher.executeTool("detect_duplicates_and_anomalies")
        assertTrue("Duplicate anomaly tool must execute cleanly", dupResult.isSuccess)
    }
}
