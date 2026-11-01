package com.example.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.data.model.AccountType
import com.example.data.model.TransactionType

@Entity(
    tableName = "users",
    indices = [Index(value = ["email"], unique = true)]
)
data class UserEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 1L,
    val name: String = "Alex",
    val email: String = "alex@arthika.local",
    val currency: String = "USD",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "accounts",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["name", "userId"])
    ]
)
data class AccountEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: Long = 1L,
    val name: String,
    val type: AccountType,
    val currentBalance: Double = 0.0,
    val initialBalance: Double = 0.0,
    val currency: String = "USD",
    val accountNumberLast4: String = "0000",
    val institutionName: String = "Bank",
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "categories",
    indices = [
        Index(value = ["name", "type"], unique = true)
    ]
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val type: TransactionType,
    val iconName: String = "category",
    val colorHex: String = "#56846B",
    val monthlyBudgetLimit: Double? = null,
    val isCustom: Boolean = false
)

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["accountId"]),
        Index(value = ["dateMillis"]),
        Index(value = ["type"]),
        Index(value = ["fingerprintHash"]),
        Index(value = ["originalTransactionId"]),
        Index(value = ["type", "dateMillis"]),
        Index(value = ["accountId", "dateMillis"])
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: Long = 1L,
    val accountId: Long = 1L,
    val toAccountId: Long? = null,
    val amount: Double,
    val type: TransactionType,
    val category: String,
    val dateMillis: Long,
    val timeString: String,
    val paymentMethod: String,
    val notes: String = "",
    val merchant: String = "",
    val receiptUri: String? = null,
    val itemsSummary: String? = null,
    val referenceNumber: String? = null,
    val status: String = "COMPLETED", // COMPLETED, PENDING, RECONCILED, VOIDED, REVERSED
    val originalTransactionId: Long? = null,
    val isReconciled: Boolean = false,
    val transferFee: Double = 0.0,
    val fingerprintHash: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "transfers",
    indices = [
        Index(value = ["fromAccountId"]),
        Index(value = ["toAccountId"]),
        Index(value = ["sourceTransactionId"]),
        Index(value = ["destinationTransactionId"])
    ]
)
data class TransferEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fromAccountId: Long,
    val toAccountId: Long,
    val sourceTransactionId: Long = 0L,
    val destinationTransactionId: Long = 0L,
    val amount: Double,
    val transferFee: Double = 0.0,
    val exchangeRate: Double = 1.0,
    val dateMillis: Long,
    val notes: String = "",
    val status: String = "COMPLETED", // COMPLETED, REVERSED
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "journal_entries",
    indices = [
        Index(value = ["transactionId"]),
        Index(value = ["entryDateMillis"]),
        Index(value = ["debitAccountId"]),
        Index(value = ["creditAccountId"])
    ]
)
data class JournalEntryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val transactionId: Long = 0L,
    val entryDateMillis: Long,
    val description: String,
    val debitAccountId: Long,
    val creditAccountId: Long,
    val debitAmount: Double,
    val creditAmount: Double,
    val ledgerCategory: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "imported_statements",
    indices = [
        Index(value = ["accountId"]),
        Index(value = ["statementPeriodStart", "statementPeriodEnd"])
    ]
)
data class ImportedStatementEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val accountId: Long,
    val fileName: String,
    val fileFormat: String, // CSV, OFX, JSON, TEXT
    val statementPeriodStart: Long,
    val statementPeriodEnd: Long,
    val openingBalance: Double,
    val closingBalance: Double,
    val totalCredits: Double,
    val totalDebits: Double,
    val transactionCount: Int,
    val rawContent: String = "",
    val status: String = "IMPORTED", // IMPORTED, RECONCILED, PARTIALLY_RECONCILED
    val importedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "reconciliation_records",
    indices = [
        Index(value = ["accountId"]),
        Index(value = ["statementId"])
    ]
)
data class ReconciliationRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val accountId: Long,
    val statementId: Long,
    val reconciledAt: Long = System.currentTimeMillis(),
    val statementEndingBalance: Double,
    val calculatedEndingBalance: Double,
    val discrepancy: Double,
    val status: String, // BALANCED, DISCREPANCY, PENDING
    val matchedCount: Int,
    val unmatchedCount: Int,
    val notes: String = ""
)

@Entity(
    tableName = "reconciliation_items",
    indices = [
        Index(value = ["reconciliationId"]),
        Index(value = ["transactionId"])
    ]
)
data class ReconciliationItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val reconciliationId: Long,
    val transactionId: Long? = null,
    val statementDateMillis: Long,
    val statementDescription: String,
    val statementAmount: Double,
    val isCredit: Boolean,
    val matchType: String, // EXACT, FUZZY, MANUAL, UNMATCHED
    val confidence: Float,
    val discrepancyAmount: Double = 0.0,
    val notes: String = ""
)

@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey
    val id: Int = 1,
    val userId: Long = 1L,
    val monthlyLimit: Double = 2500.0,
    val currencySymbol: String = "$",
    val isPinEnabled: Boolean = false,
    val pinCode: String = "",
    val userName: String = "Alex",
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "audit_logs",
    indices = [
        Index(value = ["entityType", "entityId"]),
        Index(value = ["timestamp"])
    ]
)
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: Long = 1L,
    val entityType: String, // TRANSACTION, ACCOUNT, TRANSFER, RECONCILIATION, BUDGET
    val entityId: Long,
    val action: String, // CREATE, UPDATE, DELETE, REVERSE, RECONCILE, TRANSFER
    val previousStateJson: String? = null,
    val newStateJson: String? = null,
    val details: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sender: String, // "user" or "ai"
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isPdfReportOffer: Boolean = false,
    val statsMetadata: String? = null
)
