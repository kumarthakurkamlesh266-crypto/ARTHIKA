package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.data.entity.AccountEntity
import com.example.data.entity.AuditLogEntity
import com.example.data.entity.BudgetEntity
import com.example.data.entity.CategoryEntity
import com.example.data.entity.ChatMessageEntity
import com.example.data.entity.ImportedStatementEntity
import com.example.data.entity.JournalEntryEntity
import com.example.data.entity.ReconciliationItemEntity
import com.example.data.entity.ReconciliationRecordEntity
import com.example.data.entity.TransactionEntity
import com.example.data.entity.TransferEntity
import com.example.data.entity.UserEntity
import com.example.data.model.TransactionType
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transactions: List<TransactionEntity>)

    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)

    @Delete
    suspend fun deleteTransaction(transaction: TransactionEntity)

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun getTransactionById(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions ORDER BY dateMillis DESC, id DESC")
    fun getAllTransactionsFlow(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY dateMillis DESC, id DESC")
    suspend fun getAllTransactions(): List<TransactionEntity>

    @Query("SELECT * FROM transactions ORDER BY dateMillis DESC, id DESC LIMIT :limit")
    fun getRecentTransactions(limit: Int): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY dateMillis DESC, id DESC LIMIT :limit OFFSET :offset")
    suspend fun getPagedTransactions(limit: Int, offset: Int): List<TransactionEntity>

    @Query("SELECT * FROM transactions ORDER BY dateMillis DESC, id DESC LIMIT :limit OFFSET :offset")
    fun getPagedTransactionsFlow(limit: Int, offset: Int): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE dateMillis >= :startMillis AND dateMillis <= :endMillis ORDER BY dateMillis DESC")
    suspend fun getTransactionsBetween(startMillis: Long, endMillis: Long): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE accountId = :accountId AND dateMillis >= :startMillis AND dateMillis <= :endMillis ORDER BY dateMillis DESC")
    suspend fun getTransactionsForAccountBetween(accountId: Long, startMillis: Long, endMillis: Long): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE accountId = :accountId ORDER BY dateMillis DESC, id DESC")
    fun getTransactionsForAccountFlow(accountId: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE accountId = :accountId ORDER BY dateMillis DESC, id DESC")
    suspend fun getTransactionsForAccount(accountId: Long): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE type = :type ORDER BY dateMillis DESC")
    suspend fun getTransactionsByType(type: TransactionType): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE LOWER(merchant) LIKE '%' || LOWER(:query) || '%' OR LOWER(notes) LIKE '%' || LOWER(:query) || '%' OR LOWER(category) LIKE '%' || LOWER(:query) || '%' ORDER BY dateMillis DESC")
    fun searchTransactions(query: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE LOWER(merchant) = LOWER(:merchant) AND amount = :amount AND dateMillis >= :startOfDay AND dateMillis <= :endOfDay LIMIT 1")
    suspend fun findDuplicate(merchant: String, amount: Double, startOfDay: Long, endOfDay: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE fingerprintHash = :fingerprint LIMIT 1")
    suspend fun findByFingerprint(fingerprint: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE accountId = :accountId AND isReconciled = 0 ORDER BY dateMillis ASC")
    suspend fun getUnreconciledTransactions(accountId: Long): List<TransactionEntity>

    @Query("UPDATE transactions SET isReconciled = 1, status = 'RECONCILED' WHERE id IN (:transactionIds)")
    suspend fun markAsReconciled(transactionIds: List<Long>)

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun getCount(): Int
}

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts WHERE isActive = 1 ORDER BY id ASC")
    fun getAllAccountsFlow(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE isActive = 1 ORDER BY id ASC")
    suspend fun getAllAccounts(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :id LIMIT 1")
    suspend fun getAccountById(id: Long): AccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: AccountEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(accounts: List<AccountEntity>)

    @Update
    suspend fun updateAccount(account: AccountEntity)

    @Query("UPDATE accounts SET currentBalance = :balance, updatedAt = :timestamp WHERE id = :accountId")
    suspend fun updateBalance(accountId: Long, balance: Double, timestamp: Long = System.currentTimeMillis())

    @Delete
    suspend fun deleteAccount(account: AccountEntity)
}

@Dao
interface TransferDao {
    @Query("SELECT * FROM transfers ORDER BY dateMillis DESC, id DESC")
    fun getAllTransfersFlow(): Flow<List<TransferEntity>>

    @Query("SELECT * FROM transfers ORDER BY dateMillis DESC, id DESC")
    suspend fun getAllTransfers(): List<TransferEntity>

    @Query("SELECT * FROM transfers WHERE id = :id LIMIT 1")
    suspend fun getTransferById(id: Long): TransferEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransfer(transfer: TransferEntity): Long

    @Update
    suspend fun updateTransfer(transfer: TransferEntity)
}

@Dao
interface JournalEntryDao {
    @Query("SELECT * FROM journal_entries ORDER BY entryDateMillis DESC, id DESC")
    fun getAllJournalEntriesFlow(): Flow<List<JournalEntryEntity>>

    @Query("SELECT * FROM journal_entries WHERE transactionId = :transactionId ORDER BY id ASC")
    suspend fun getEntriesForTransaction(transactionId: Long): List<JournalEntryEntity>

    @Query("SELECT * FROM journal_entries WHERE debitAccountId = :accountId OR creditAccountId = :accountId ORDER BY entryDateMillis DESC")
    suspend fun getLedgerForAccount(accountId: Long): List<JournalEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: JournalEntryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<JournalEntryEntity>)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY type ASC, name ASC")
    fun getAllCategoriesFlow(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY type ASC, name ASC")
    suspend fun getAllCategories(): List<CategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: CategoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<CategoryEntity>)
}

@Dao
interface ImportedStatementDao {
    @Query("SELECT * FROM imported_statements ORDER BY importedAt DESC")
    fun getAllStatementsFlow(): Flow<List<ImportedStatementEntity>>

    @Query("SELECT * FROM imported_statements WHERE accountId = :accountId ORDER BY importedAt DESC")
    suspend fun getStatementsForAccount(accountId: Long): List<ImportedStatementEntity>

    @Query("SELECT * FROM imported_statements WHERE id = :id LIMIT 1")
    suspend fun getStatementById(id: Long): ImportedStatementEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStatement(statement: ImportedStatementEntity): Long

    @Update
    suspend fun updateStatement(statement: ImportedStatementEntity)
}

@Dao
interface ReconciliationDao {
    @Query("SELECT * FROM reconciliation_records ORDER BY reconciledAt DESC")
    fun getAllReconciliationsFlow(): Flow<List<ReconciliationRecordEntity>>

    @Query("SELECT * FROM reconciliation_records WHERE accountId = :accountId ORDER BY reconciledAt DESC")
    suspend fun getReconciliationsForAccount(accountId: Long): List<ReconciliationRecordEntity>

    @Query("SELECT * FROM reconciliation_records WHERE id = :id LIMIT 1")
    suspend fun getReconciliationById(id: Long): ReconciliationRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: ReconciliationRecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<ReconciliationItemEntity>)

    @Query("SELECT * FROM reconciliation_items WHERE reconciliationId = :reconciliationId ORDER BY id ASC")
    suspend fun getItemsForReconciliation(reconciliationId: Long): List<ReconciliationItemEntity>

    @Update
    suspend fun updateRecord(record: ReconciliationRecordEntity)
}

@Dao
interface AuditLogDao {
    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getAuditLogsFlow(limit: Int = 100): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_logs WHERE entityType = :entityType AND entityId = :entityId ORDER BY timestamp DESC")
    suspend fun getLogsForEntity(entityType: String, entityId: Long): List<AuditLogEntity>

    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentLogs(limit: Int = 50): List<AuditLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: AuditLogEntity): Long
}

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    suspend fun getUser(id: Long = 1L): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity): Long

    @Update
    suspend fun updateUser(user: UserEntity)
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets WHERE id = 1 LIMIT 1")
    fun getBudgetFlow(): Flow<BudgetEntity?>

    @Query("SELECT * FROM budgets WHERE id = 1 LIMIT 1")
    suspend fun getBudget(): BudgetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveBudget(budget: BudgetEntity)
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun getAllMessagesFlow(): Flow<List<ChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity): Long

    @Query("DELETE FROM chat_messages")
    suspend fun clearHistory()
}
