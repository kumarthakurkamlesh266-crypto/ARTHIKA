package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.AccountDao
import com.example.data.dao.AuditLogDao
import com.example.data.dao.BudgetDao
import com.example.data.dao.CategoryDao
import com.example.data.dao.ChatDao
import com.example.data.dao.ImportedStatementDao
import com.example.data.dao.JournalEntryDao
import com.example.data.dao.ReconciliationDao
import com.example.data.dao.TransactionDao
import com.example.data.dao.TransferDao
import com.example.data.dao.UserDao
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
import com.example.data.model.AccountType
import com.example.data.model.CategoryConstants
import com.example.data.model.TransactionType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

class Converters {
    @TypeConverter
    fun fromTransactionType(value: TransactionType): String = value.name

    @TypeConverter
    fun toTransactionType(value: String): TransactionType = try {
        TransactionType.valueOf(value)
    } catch (e: Exception) {
        TransactionType.EXPENSE
    }

    @TypeConverter
    fun fromAccountType(value: AccountType): String = value.name

    @TypeConverter
    fun toAccountType(value: String): AccountType = try {
        AccountType.valueOf(value)
    } catch (e: Exception) {
        AccountType.CHECKING
    }
}

@Database(
    entities = [
        UserEntity::class,
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        TransferEntity::class,
        JournalEntryEntity::class,
        ImportedStatementEntity::class,
        ReconciliationRecordEntity::class,
        ReconciliationItemEntity::class,
        BudgetEntity::class,
        AuditLogEntity::class,
        ChatMessageEntity::class
    ],
    version = 4,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun accountDao(): AccountDao
    abstract fun transferDao(): TransferDao
    abstract fun journalEntryDao(): JournalEntryDao
    abstract fun categoryDao(): CategoryDao
    abstract fun importedStatementDao(): ImportedStatementDao
    abstract fun reconciliationDao(): ReconciliationDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun userDao(): UserDao
    abstract fun budgetDao(): BudgetDao
    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "lumina_finance.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            CoroutineScope(Dispatchers.IO).launch {
                                try {
                                    populateInitialData(getInstance(context))
                                } catch (e: Exception) {
                                    android.util.Log.e("AppDatabase", "Error populating initial data on create", e)
                                }
                            }
                        }

                        override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
                            super.onDestructiveMigration(db)
                            CoroutineScope(Dispatchers.IO).launch {
                                try {
                                    populateInitialData(getInstance(context))
                                } catch (e: Exception) {
                                    android.util.Log.e("AppDatabase", "Error populating initial data on destructive migration", e)
                                }
                            }
                        }

                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            CoroutineScope(Dispatchers.IO).launch {
                                try {
                                    val database = getInstance(context)
                                    if (database.accountDao().getAllAccounts().isEmpty()) {
                                        populateInitialData(database)
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("AppDatabase", "Error checking initial data on open", e)
                                }
                            }
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }

        suspend fun populateInitialData(db: AppDatabase) {
            // 1. Clean User
            val user = UserEntity(
                id = 1L,
                name = "User",
                email = "",
                currency = "INR"
            )
            db.userDao().insertUser(user)

            // 2. Clean Base Accounts with zero starting balance
            val primaryAccount = AccountEntity(
                id = 1L,
                userId = 1L,
                name = "Main Account",
                type = AccountType.CHECKING,
                initialBalance = 0.0,
                currentBalance = 0.0,
                currency = "INR",
                accountNumberLast4 = "",
                institutionName = "Bank"
            )
            val cashAccount = AccountEntity(
                id = 2L,
                userId = 1L,
                name = "Cash & Wallet",
                type = AccountType.CASH,
                initialBalance = 0.0,
                currentBalance = 0.0,
                currency = "INR",
                accountNumberLast4 = "",
                institutionName = "Cash"
            )
            val savingsAccount = AccountEntity(
                id = 3L,
                userId = 1L,
                name = "Savings",
                type = AccountType.SAVINGS,
                initialBalance = 0.0,
                currentBalance = 0.0,
                currency = "INR",
                accountNumberLast4 = "",
                institutionName = "Savings"
            )
            db.accountDao().insertAll(listOf(primaryAccount, cashAccount, savingsAccount))

            // 3. System Category Definitions (User picks from these when logging)
            val categories = mutableListOf<CategoryEntity>()
            CategoryConstants.expenseCategories.forEach { catName ->
                categories.add(CategoryEntity(name = catName, type = TransactionType.EXPENSE))
            }
            CategoryConstants.incomeCategories.forEach { catName ->
                categories.add(CategoryEntity(name = catName, type = TransactionType.INCOME))
            }
            db.categoryDao().insertAll(categories)

            // 4. Clean Budget (default INR ₹50,000 limit, customizable in settings)
            db.budgetDao().saveBudget(
                BudgetEntity(
                    id = 1,
                    userId = 1L,
                    monthlyLimit = 50000.0,
                    currencySymbol = "₹",
                    isPinEnabled = false,
                    pinCode = "",
                    userName = "User"
                )
            )

            // 5. System Initialization Log
            db.auditLogDao().insertLog(
                AuditLogEntity(
                    userId = 1L,
                    entityType = "SYSTEM",
                    entityId = 1L,
                    action = "INITIALIZE",
                    details = "ARTHIKA Clean Accounting Ledger Initialized"
                )
            )

            // 6. Welcome Message
            db.chatDao().insertMessage(
                ChatMessageEntity(
                    sender = "ai",
                    text = "Hello! I am ARTHIKA, your financial intelligence assistant. Your accounts are ready. Tap '+ Quick Add' on the Home screen to add your first transaction or scan a receipt!",
                    isPdfReportOffer = false
                )
            )
        }
    }
}
