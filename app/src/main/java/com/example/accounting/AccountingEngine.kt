package com.example.accounting

import com.example.data.AppDatabase
import com.example.data.entity.AccountEntity
import com.example.data.entity.AuditLogEntity
import com.example.data.entity.JournalEntryEntity
import com.example.data.entity.TransactionEntity
import com.example.data.entity.TransferEntity
import com.example.data.model.AccountBalanceSummary
import com.example.data.model.AccountType
import com.example.data.model.DuplicateCheckResult
import com.example.data.model.IncomeExpenseSummary
import com.example.data.model.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

data class BalancePeriodReport(
    val accountId: Long,
    val accountName: String,
    val periodStartMillis: Long,
    val periodEndMillis: Long,
    val openingBalance: Double,
    val totalCredits: Double,
    val totalDebits: Double,
    val closingBalance: Double,
    val transactionCount: Int
)

sealed class TransferExecutionResult {
    data class Success(val transfer: TransferEntity, val sourceTxId: Long, val destinationTxId: Long) : TransferExecutionResult()
    data class Error(val message: String) : TransferExecutionResult()
}

sealed class ReversalExecutionResult {
    data class Success(val reversedTransaction: TransactionEntity, val restoredBalance: Double) : ReversalExecutionResult()
    data class Error(val message: String) : ReversalExecutionResult()
}

sealed class RefundExecutionResult {
    data class Success(val refundTransaction: TransactionEntity, val newBalance: Double) : RefundExecutionResult()
    data class Error(val message: String) : RefundExecutionResult()
}

class AccountingEngine(private val db: AppDatabase) {

    private val transactionDao = db.transactionDao()
    private val accountDao = db.accountDao()
    private val transferDao = db.transferDao()
    private val journalEntryDao = db.journalEntryDao()
    private val auditLogDao = db.auditLogDao()

    /**
     * Accurate income and expense calculations for a given period.
     * Crucially: Internal transfers and reversals are excluded from revenue/expenses,
     * maintaining strict accounting integrity.
     */
    suspend fun calculateIncomeAndExpense(
        startMillis: Long,
        endMillis: Long,
        accountId: Long? = null
    ): IncomeExpenseSummary = withContext(Dispatchers.IO) {
        val txs = if (accountId != null) {
            transactionDao.getTransactionsForAccountBetween(accountId, startMillis, endMillis)
        } else {
            transactionDao.getTransactionsBetween(startMillis, endMillis)
        }

        var totalIncome = 0.0
        var totalExpense = 0.0
        var totalTransfers = 0.0
        var totalFees = 0.0
        var totalRefunds = 0.0

        for (tx in txs) {
            // Ignore reversed or voided transactions in net calculations
            if (tx.status == "REVERSED" || tx.status == "VOIDED") {
                continue
            }

            when (tx.type) {
                TransactionType.INCOME -> {
                    totalIncome += tx.amount
                }
                TransactionType.EXPENSE -> {
                    totalExpense += tx.amount
                }
                TransactionType.TRANSFER -> {
                    totalTransfers += tx.amount
                    totalFees += tx.transferFee
                    if (tx.transferFee > 0.0) {
                        totalExpense += tx.transferFee
                    }
                }
                TransactionType.REFUND -> {
                    totalRefunds += tx.amount
                    // A refund received offsets expenses
                    totalExpense = (totalExpense - tx.amount).coerceAtLeast(0.0)
                }
                TransactionType.REVERSAL -> {
                    // Handled via status
                }
            }
        }

        // Round to 2 decimals for financial precision
        totalIncome = (totalIncome * 100.0).roundToInt() / 100.0
        totalExpense = (totalExpense * 100.0).roundToInt() / 100.0
        val netSavings = ((totalIncome - totalExpense) * 100.0).roundToInt() / 100.0
        val savingsRate = if (totalIncome > 0.0) {
            ((netSavings / totalIncome) * 1000.0).roundToInt() / 10.0
        } else {
            0.0
        }

        IncomeExpenseSummary(
            totalIncome = totalIncome,
            totalExpense = totalExpense,
            netSavings = netSavings,
            savingsRatePct = savingsRate,
            totalTransfers = (totalTransfers * 100.0).roundToInt() / 100.0,
            totalFees = (totalFees * 100.0).roundToInt() / 100.0,
            totalRefundsReceived = (totalRefunds * 100.0).roundToInt() / 100.0,
            periodStartMillis = startMillis,
            periodEndMillis = endMillis
        )
    }

    /**
     * Calculates account-wise balance with credits, debits, and reconciled balance.
     */
    suspend fun calculateAccountBalance(accountId: Long): AccountBalanceSummary? = withContext(Dispatchers.IO) {
        val account = accountDao.getAccountById(accountId) ?: return@withContext null
        val txs = transactionDao.getTransactionsForAccount(accountId)

        var totalCredits = 0.0
        var totalDebits = 0.0
        var reconciledBalance = account.initialBalance

        for (tx in txs) {
            if (tx.status == "REVERSED" || tx.status == "VOIDED") continue

            val isCredit = when (tx.type) {
                TransactionType.INCOME, TransactionType.REFUND -> true
                TransactionType.EXPENSE -> false
                TransactionType.TRANSFER -> tx.toAccountId == null // received transfer is credit
                TransactionType.REVERSAL -> false
            }

            if (isCredit) {
                totalCredits += tx.amount
                if (tx.isReconciled) reconciledBalance += tx.amount
            } else {
                val effectiveDebit = tx.amount + tx.transferFee
                totalDebits += effectiveDebit
                if (tx.isReconciled) reconciledBalance -= effectiveDebit
            }
        }

        val closingBalance = if (account.type == AccountType.CREDIT_CARD) {
            account.initialBalance - totalDebits + totalCredits
        } else {
            account.initialBalance + totalCredits - totalDebits
        }

        // Update database with calculated current balance
        accountDao.updateBalance(accountId, (closingBalance * 100.0).roundToInt() / 100.0)

        AccountBalanceSummary(
            accountId = account.id,
            accountName = account.name,
            accountType = account.type,
            openingBalance = (account.initialBalance * 100.0).roundToInt() / 100.0,
            totalCredits = (totalCredits * 100.0).roundToInt() / 100.0,
            totalDebits = (totalDebits * 100.0).roundToInt() / 100.0,
            closingBalance = (closingBalance * 100.0).roundToInt() / 100.0,
            reconciledBalance = (reconciledBalance * 100.0).roundToInt() / 100.0,
            transactionCount = txs.size
        )
    }

    /**
     * Calculates balances for all active accounts.
     */
    suspend fun calculateAllAccountBalances(): List<AccountBalanceSummary> = withContext(Dispatchers.IO) {
        val accounts = accountDao.getAllAccounts()
        accounts.mapNotNull { calculateAccountBalance(it.id) }
    }

    /**
     * Opening and Closing Balances for a given period.
     */
    suspend fun getOpeningAndClosingBalances(
        accountId: Long,
        startMillis: Long,
        endMillis: Long
    ): BalancePeriodReport? = withContext(Dispatchers.IO) {
        val account = accountDao.getAccountById(accountId) ?: return@withContext null
        val allAccountTxs = transactionDao.getTransactionsForAccount(accountId)

        // Opening balance = initial balance + (credits - debits) prior to startMillis
        var openingCredits = 0.0
        var openingDebits = 0.0
        var periodCredits = 0.0
        var periodDebits = 0.0
        var periodCount = 0

        for (tx in allAccountTxs) {
            if (tx.status == "REVERSED" || tx.status == "VOIDED") continue

            val isCredit = when (tx.type) {
                TransactionType.INCOME, TransactionType.REFUND -> true
                TransactionType.EXPENSE -> false
                TransactionType.TRANSFER -> tx.toAccountId == null
                TransactionType.REVERSAL -> false
            }

            val amount = tx.amount + (if (!isCredit) tx.transferFee else 0.0)

            if (tx.dateMillis < startMillis) {
                if (isCredit) openingCredits += amount else openingDebits += amount
            } else if (tx.dateMillis <= endMillis) {
                periodCount++
                if (isCredit) periodCredits += amount else periodDebits += amount
            }
        }

        val openingBalance = account.initialBalance + openingCredits - openingDebits
        val closingBalance = openingBalance + periodCredits - periodDebits

        BalancePeriodReport(
            accountId = account.id,
            accountName = account.name,
            periodStartMillis = startMillis,
            periodEndMillis = endMillis,
            openingBalance = (openingBalance * 100.0).roundToInt() / 100.0,
            totalCredits = (periodCredits * 100.0).roundToInt() / 100.0,
            totalDebits = (periodDebits * 100.0).roundToInt() / 100.0,
            closingBalance = (closingBalance * 100.0).roundToInt() / 100.0,
            transactionCount = periodCount
        )
    }

    /**
     * Internal Transfer with Double-Entry Bookkeeping.
     * Debits destination account, credits source account.
     */
    suspend fun executeTransfer(
        fromAccountId: Long,
        toAccountId: Long,
        amount: Double,
        fee: Double = 0.0,
        dateMillis: Long = System.currentTimeMillis(),
        notes: String = ""
    ): TransferExecutionResult = withContext(Dispatchers.IO) {
        if (amount <= 0.0) {
            return@withContext TransferExecutionResult.Error("Transfer amount must be strictly greater than zero.")
        }
        if (fromAccountId == toAccountId) {
            return@withContext TransferExecutionResult.Error("Cannot transfer funds to the same account.")
        }

        val sourceAccount = accountDao.getAccountById(fromAccountId)
            ?: return@withContext TransferExecutionResult.Error("Source account not found.")
        val destAccount = accountDao.getAccountById(toAccountId)
            ?: return@withContext TransferExecutionResult.Error("Destination account not found.")

        val timeString = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(dateMillis))

        // 1. Source Transaction (OUT)
        val sourceTx = TransactionEntity(
            userId = sourceAccount.userId,
            accountId = fromAccountId,
            toAccountId = toAccountId,
            amount = amount,
            type = TransactionType.TRANSFER,
            category = "Transfer",
            dateMillis = dateMillis,
            timeString = timeString,
            paymentMethod = "Bank Transfer",
            notes = "Transfer to ${destAccount.name}. $notes".trim(),
            merchant = destAccount.name,
            referenceNumber = "TRF-OUT-${System.currentTimeMillis() % 100000}",
            transferFee = fee
        )
        val sourceTxId = transactionDao.insertTransaction(sourceTx)

        // 2. Destination Transaction (IN)
        val destTx = TransactionEntity(
            userId = destAccount.userId,
            accountId = toAccountId,
            toAccountId = null, // Inbound transfer
            amount = amount,
            type = TransactionType.TRANSFER,
            category = "Transfer",
            dateMillis = dateMillis,
            timeString = timeString,
            paymentMethod = "Bank Transfer",
            notes = "Transfer from ${sourceAccount.name}. $notes".trim(),
            merchant = sourceAccount.name,
            referenceNumber = "TRF-IN-${System.currentTimeMillis() % 100000}",
            transferFee = 0.0
        )
        val destTxId = transactionDao.insertTransaction(destTx)

        // 3. Record Transfer Entity
        val transfer = TransferEntity(
            fromAccountId = fromAccountId,
            toAccountId = toAccountId,
            sourceTransactionId = sourceTxId,
            destinationTransactionId = destTxId,
            amount = amount,
            transferFee = fee,
            dateMillis = dateMillis,
            notes = notes,
            status = "COMPLETED"
        )
        val transferId = transferDao.insertTransfer(transfer)

        // 4. Double-Entry Journal Entry: Debit Destination, Credit Source
        journalEntryDao.insertEntry(
            JournalEntryEntity(
                transactionId = sourceTxId,
                entryDateMillis = dateMillis,
                description = "Internal transfer from ${sourceAccount.name} to ${destAccount.name}",
                debitAccountId = toAccountId,
                creditAccountId = fromAccountId,
                debitAmount = amount,
                creditAmount = amount,
                ledgerCategory = "Internal Transfer"
            )
        )

        // 5. Update Account Balances
        calculateAccountBalance(fromAccountId)
        calculateAccountBalance(toAccountId)

        // 6. Audit Log
        auditLogDao.insertLog(
            AuditLogEntity(
                userId = sourceAccount.userId,
                entityType = "TRANSFER",
                entityId = transferId,
                action = "TRANSFER",
                details = "Transferred $$amount from ${sourceAccount.name} to ${destAccount.name} (Fee: $$fee)"
            )
        )

        TransferExecutionResult.Success(transfer.copy(id = transferId), sourceTxId, destTxId)
    }

    /**
     * Refund & Reversal Handling:
     * Restores account balance and preserves audit trail.
     */
    suspend fun executeReversal(
        transactionId: Long,
        reason: String
    ): ReversalExecutionResult = withContext(Dispatchers.IO) {
        val tx = transactionDao.getTransactionById(transactionId)
            ?: return@withContext ReversalExecutionResult.Error("Transaction ID $transactionId not found.")

        if (tx.status == "REVERSED") {
            return@withContext ReversalExecutionResult.Error("Transaction has already been reversed.")
        }

        // Mark transaction as REVERSED
        val updatedTx = tx.copy(
            status = "REVERSED",
            notes = "${tx.notes} [REVERSED: $reason]".trim()
        )
        transactionDao.updateTransaction(updatedTx)

        // Post Reversing Journal Entry
        val originalEntries = journalEntryDao.getEntriesForTransaction(transactionId)
        for (orig in originalEntries) {
            journalEntryDao.insertEntry(
                JournalEntryEntity(
                    transactionId = transactionId,
                    entryDateMillis = System.currentTimeMillis(),
                    description = "Reversal of Entry #${orig.id}: $reason",
                    debitAccountId = orig.creditAccountId, // Swap debit & credit
                    creditAccountId = orig.debitAccountId,
                    debitAmount = orig.creditAmount,
                    creditAmount = orig.debitAmount,
                    ledgerCategory = "Reversal"
                )
            )
        }

        // Recalculate balance
        val summary = calculateAccountBalance(tx.accountId)
        val newBal = summary?.closingBalance ?: 0.0

        // Audit Log
        auditLogDao.insertLog(
            AuditLogEntity(
                userId = tx.userId,
                entityType = "TRANSACTION",
                entityId = transactionId,
                action = "REVERSE",
                previousStateJson = "amount=${tx.amount}, type=${tx.type}, status=${tx.status}",
                newStateJson = "status=REVERSED, reason=$reason",
                details = "Reversed transaction of $${tx.amount} (${tx.category}). Restored balance to $$newBal"
            )
        )

        ReversalExecutionResult.Success(updatedTx, newBal)
    }

    /**
     * Executes a verified partial or full refund for an original transaction.
     */
    suspend fun executeRefund(
        originalTransactionId: Long,
        refundAmount: Double,
        reason: String,
        dateMillis: Long = System.currentTimeMillis()
    ): RefundExecutionResult = withContext(Dispatchers.IO) {
        val origTx = transactionDao.getTransactionById(originalTransactionId)
            ?: return@withContext RefundExecutionResult.Error("Original transaction not found.")

        if (refundAmount <= 0.0 || refundAmount > origTx.amount) {
            return@withContext RefundExecutionResult.Error("Refund amount must be between 0.01 and original amount ($${origTx.amount}).")
        }

        val timeString = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(dateMillis))

        val refundTx = TransactionEntity(
            userId = origTx.userId,
            accountId = origTx.accountId,
            amount = refundAmount,
            type = TransactionType.REFUND,
            category = origTx.category,
            dateMillis = dateMillis,
            timeString = timeString,
            paymentMethod = origTx.paymentMethod,
            notes = "Refund for #${origTx.id} (${origTx.merchant}): $reason",
            merchant = origTx.merchant,
            referenceNumber = "REF-${System.currentTimeMillis() % 100000}",
            originalTransactionId = originalTransactionId
        )
        val refundId = transactionDao.insertTransaction(refundTx)

        // Double-entry: Debit Asset (Checking), Credit Expense category
        journalEntryDao.insertEntry(
            JournalEntryEntity(
                transactionId = refundId,
                entryDateMillis = dateMillis,
                description = "Refund for ${origTx.merchant}: $reason",
                debitAccountId = origTx.accountId,
                creditAccountId = 0L,
                debitAmount = refundAmount,
                creditAmount = refundAmount,
                ledgerCategory = "Refund - ${origTx.category}"
            )
        )

        // Update balance
        val summary = calculateAccountBalance(origTx.accountId)
        val newBal = summary?.closingBalance ?: 0.0

        // Audit Log
        auditLogDao.insertLog(
            AuditLogEntity(
                userId = origTx.userId,
                entityType = "TRANSACTION",
                entityId = refundId,
                action = "CREATE",
                details = "Processed refund of $$refundAmount for transaction #${origTx.id}. Reason: $reason"
            )
        )

        RefundExecutionResult.Success(refundTx.copy(id = refundId), newBal)
    }

    /**
     * Prevention of Duplicate Transactions:
     * Calculates deterministic fingerprint hash and detects duplicates within time window.
     */
    fun generateFingerprint(
        accountId: Long,
        amount: Double,
        merchant: String,
        dateMillis: Long
    ): String {
        val cal = Calendar.getInstance().apply { timeInMillis = dateMillis }
        val dayKey = "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.DAY_OF_YEAR)}"
        val cleanMerchant = merchant.trim().lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "")
        val roundedAmount = (amount * 100.0).roundToInt()

        val raw = "$accountId:$roundedAmount:$cleanMerchant:$dayKey"
        val bytes = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    suspend fun checkForDuplicate(
        accountId: Long,
        amount: Double,
        merchant: String,
        dateMillis: Long,
        windowHours: Int = 24
    ): DuplicateCheckResult = withContext(Dispatchers.IO) {
        val fingerprint = generateFingerprint(accountId, amount, merchant, dateMillis)
        val exactMatch = transactionDao.findByFingerprint(fingerprint)
        if (exactMatch != null && exactMatch.status != "REVERSED") {
            return@withContext DuplicateCheckResult(
                isDuplicate = true,
                matchConfidence = 1.0f,
                existingTransactionId = exactMatch.id,
                matchReason = "Exact duplicate fingerprint found on same day for merchant '$merchant' with amount $$amount"
            )
        }

        // Check window-based duplicate
        val windowMillis = windowHours * 3600 * 1000L
        val startWindow = dateMillis - windowMillis
        val endWindow = dateMillis + windowMillis
        val candidateTxs = transactionDao.getTransactionsForAccountBetween(accountId, startWindow, endWindow)

        val cleanMerchant = merchant.trim().lowercase(Locale.ROOT)
        for (cand in candidateTxs) {
            if (cand.status == "REVERSED") continue
            if (abs(cand.amount - amount) < 0.001) {
                val candMerchant = cand.merchant.trim().lowercase(Locale.ROOT)
                if (candMerchant == cleanMerchant || candMerchant.contains(cleanMerchant) || cleanMerchant.contains(candMerchant)) {
                    val timeDiffHours = abs(cand.dateMillis - dateMillis) / (3600 * 1000.0)
                    val confidence = (1.0 - (timeDiffHours / (windowHours * 2.0))).coerceIn(0.7, 0.95).toFloat()
                    return@withContext DuplicateCheckResult(
                        isDuplicate = true,
                        matchConfidence = confidence,
                        existingTransactionId = cand.id,
                        matchReason = "Potential duplicate: Transaction of $$amount for '${cand.merchant}' recorded within ${timeDiffHours.roundToInt()} hours."
                    )
                }
            }
        }

        DuplicateCheckResult(isDuplicate = false, matchConfidence = 0.0f)
    }
}
