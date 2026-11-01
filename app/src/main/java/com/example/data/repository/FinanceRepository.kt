package com.example.data.repository

import com.example.accounting.AccountingEngine
import com.example.accounting.FullReconciliationSummary
import com.example.accounting.ParsedStatement
import com.example.accounting.ReconciliationEngine
import com.example.accounting.RefundExecutionResult
import com.example.accounting.ReversalExecutionResult
import com.example.accounting.TransferExecutionResult
import com.example.data.AppDatabase
import com.example.data.entity.AccountEntity
import com.example.data.entity.AuditLogEntity
import com.example.data.entity.BudgetEntity
import com.example.data.entity.ChatMessageEntity
import com.example.data.entity.JournalEntryEntity
import com.example.data.entity.ReconciliationRecordEntity
import com.example.data.entity.TransactionEntity
import com.example.data.entity.TransferEntity
import com.example.data.model.AccountBalanceSummary
import com.example.data.model.IncomeExpenseSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar

class FinanceRepository(private val database: AppDatabase) {
    private val transactionDao = database.transactionDao()
    private val accountDao = database.accountDao()
    private val transferDao = database.transferDao()
    private val journalEntryDao = database.journalEntryDao()
    private val reconciliationDao = database.reconciliationDao()
    private val auditLogDao = database.auditLogDao()
    private val budgetDao = database.budgetDao()
    private val chatDao = database.chatDao()

    val accountingEngine = AccountingEngine(database)
    val reconciliationEngine = ReconciliationEngine(database)

    val allTransactions: Flow<List<TransactionEntity>> = transactionDao.getAllTransactionsFlow()
    val recentTransactions: Flow<List<TransactionEntity>> = transactionDao.getRecentTransactions(8)
    val allAccounts: Flow<List<AccountEntity>> = accountDao.getAllAccountsFlow()
    val allTransfers: Flow<List<TransferEntity>> = transferDao.getAllTransfersFlow()
    val allReconciliations: Flow<List<ReconciliationRecordEntity>> = reconciliationDao.getAllReconciliationsFlow()
    val auditLogs: Flow<List<AuditLogEntity>> = auditLogDao.getAuditLogsFlow(100)
    val journalEntries: Flow<List<JournalEntryEntity>> = journalEntryDao.getAllJournalEntriesFlow()
    val budget: Flow<BudgetEntity> = budgetDao.getBudgetFlow().map { it ?: BudgetEntity() }
    val chatMessages: Flow<List<ChatMessageEntity>> = chatDao.getAllMessagesFlow()

    suspend fun getTransactionsList(): List<TransactionEntity> = transactionDao.getAllTransactions()
    suspend fun getPagedTransactions(limit: Int, offset: Int): List<TransactionEntity> = transactionDao.getPagedTransactions(limit, offset)

    suspend fun addTransaction(transaction: TransactionEntity): Long {
        // Also update account balance via accounting engine
        val id = transactionDao.insertTransaction(transaction)
        accountingEngine.calculateAccountBalance(transaction.accountId)
        return id
    }

    suspend fun updateTransaction(transaction: TransactionEntity) {
        transactionDao.updateTransaction(transaction)
        accountingEngine.calculateAccountBalance(transaction.accountId)
    }

    suspend fun deleteTransaction(transaction: TransactionEntity) {
        transactionDao.deleteTransaction(transaction)
        accountingEngine.calculateAccountBalance(transaction.accountId)
    }

    suspend fun executeTransfer(
        fromAccountId: Long,
        toAccountId: Long,
        amount: Double,
        fee: Double = 0.0,
        dateMillis: Long = System.currentTimeMillis(),
        notes: String = ""
    ): TransferExecutionResult {
        return accountingEngine.executeTransfer(fromAccountId, toAccountId, amount, fee, dateMillis, notes)
    }

    suspend fun executeReversal(transactionId: Long, reason: String): ReversalExecutionResult {
        return accountingEngine.executeReversal(transactionId, reason)
    }

    suspend fun executeRefund(
        originalTransactionId: Long,
        amount: Double,
        reason: String,
        dateMillis: Long = System.currentTimeMillis()
    ): RefundExecutionResult {
        return accountingEngine.executeRefund(originalTransactionId, amount, reason, dateMillis)
    }

    suspend fun reconcileAccount(accountId: Long, statement: ParsedStatement): FullReconciliationSummary {
        return reconciliationEngine.reconcileAccount(accountId, statement)
    }

    suspend fun getAccountBalances(): List<AccountBalanceSummary> {
        return accountingEngine.calculateAllAccountBalances()
    }

    suspend fun getIncomeExpenseSummary(startMillis: Long, endMillis: Long, accountId: Long? = null): IncomeExpenseSummary {
        return accountingEngine.calculateIncomeAndExpense(startMillis, endMillis, accountId)
    }

    suspend fun getBudgetSnapshot(): BudgetEntity {
        return budgetDao.getBudget() ?: BudgetEntity()
    }

    suspend fun updateBudget(budget: BudgetEntity) {
        budgetDao.saveBudget(budget)
    }

    suspend fun saveChatMessage(
        sender: String,
        text: String,
        isPdfOffer: Boolean = false,
        statsMetadata: String? = null
    ): Long {
        return chatDao.insertMessage(
            ChatMessageEntity(
                sender = sender,
                text = text,
                isPdfReportOffer = isPdfOffer,
                statsMetadata = statsMetadata
            )
        )
    }

    suspend fun clearChatHistory() {
        chatDao.clearHistory()
    }

    suspend fun checkDuplicate(merchant: String, amount: Double, dateMillis: Long, accountId: Long = 1L): TransactionEntity? {
        val dupResult = accountingEngine.checkForDuplicate(accountId, amount, merchant, dateMillis)
        if (dupResult.isDuplicate && dupResult.existingTransactionId != null) {
            return transactionDao.getTransactionById(dupResult.existingTransactionId)
        }
        val cal = Calendar.getInstance().apply {
            timeInMillis = dateMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = cal.timeInMillis
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        val endOfDay = cal.timeInMillis
        return transactionDao.findDuplicate(merchant.trim(), amount, startOfDay, endOfDay)
    }

    fun searchTransactions(query: String): Flow<List<TransactionEntity>> {
        return transactionDao.searchTransactions(query)
    }

    suspend fun getTransactionsInDateRange(startMillis: Long, endMillis: Long): List<TransactionEntity> {
        return transactionDao.getTransactionsBetween(startMillis, endMillis)
    }
}
