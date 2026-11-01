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
    version = 3,
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
                                    if (database.accountDao().getAllAccounts().isEmpty() || database.transactionDao().getCount() == 0) {
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
            // 1. Initial User
            val user = UserEntity(
                id = 1L,
                name = "Alex",
                email = "alex@arthika.local",
                currency = "USD"
            )
            db.userDao().insertUser(user)

            // 2. Initial Accounts
            val checkingAccount = AccountEntity(
                id = 1L,
                userId = 1L,
                name = "Main Checking",
                type = AccountType.CHECKING,
                initialBalance = 3200.0,
                currentBalance = 3200.0,
                currency = "USD",
                accountNumberLast4 = "7821",
                institutionName = "Chase Bank"
            )
            val savingsAccount = AccountEntity(
                id = 2L,
                userId = 1L,
                name = "High-Yield Savings",
                type = AccountType.SAVINGS,
                initialBalance = 12500.0,
                currentBalance = 12500.0,
                currency = "USD",
                accountNumberLast4 = "9042",
                institutionName = "Marcus by Goldman"
            )
            val cashAccount = AccountEntity(
                id = 3L,
                userId = 1L,
                name = "Daily Cash & Wallet",
                type = AccountType.CASH,
                initialBalance = 240.0,
                currentBalance = 240.0,
                currency = "USD",
                accountNumberLast4 = "CASH",
                institutionName = "Physical Cash"
            )
            val creditAccount = AccountEntity(
                id = 4L,
                userId = 1L,
                name = "Sapphire Credit Card",
                type = AccountType.CREDIT_CARD,
                initialBalance = -450.0,
                currentBalance = -450.0,
                currency = "USD",
                accountNumberLast4 = "3318",
                institutionName = "Chase Credit"
            )
            db.accountDao().insertAll(listOf(checkingAccount, savingsAccount, cashAccount, creditAccount))

            // 3. Initial Categories
            val categories = mutableListOf<CategoryEntity>()
            CategoryConstants.expenseCategories.forEach { catName ->
                categories.add(CategoryEntity(name = catName, type = TransactionType.EXPENSE))
            }
            CategoryConstants.incomeCategories.forEach { catName ->
                categories.add(CategoryEntity(name = catName, type = TransactionType.INCOME))
            }
            db.categoryDao().insertAll(categories)

            // 4. Initial Budget
            db.budgetDao().saveBudget(
                BudgetEntity(
                    id = 1,
                    userId = 1L,
                    monthlyLimit = 2800.0,
                    currencySymbol = "$",
                    isPinEnabled = false,
                    pinCode = "",
                    userName = "Alex"
                )
            )

            // Timestamps
            val now = Calendar.getInstance()
            val todayMillis = now.timeInMillis

            val calYesterday = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -1)
                set(Calendar.HOUR_OF_DAY, 13)
                set(Calendar.MINUTE, 30)
            }
            val yesterdayMillis = calYesterday.timeInMillis

            val cal3DaysAgo = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -3)
                set(Calendar.HOUR_OF_DAY, 18)
                set(Calendar.MINUTE, 45)
            }

            val cal5DaysAgo = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -5)
                set(Calendar.HOUR_OF_DAY, 11)
                set(Calendar.MINUTE, 15)
            }

            val cal8DaysAgo = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -8)
                set(Calendar.HOUR_OF_DAY, 9)
                set(Calendar.MINUTE, 0)
            }

            val calMidMonth = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -12)
                set(Calendar.HOUR_OF_DAY, 14)
                set(Calendar.MINUTE, 20)
            }

            val calLastMonth = Calendar.getInstance().apply {
                add(Calendar.MONTH, -1)
                set(Calendar.DAY_OF_MONTH, 15)
                set(Calendar.HOUR_OF_DAY, 16)
                set(Calendar.MINUTE, 0)
            }

            val calLastMonthSalary = Calendar.getInstance().apply {
                add(Calendar.MONTH, -1)
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 9)
                set(Calendar.MINUTE, 0)
            }

            val seedTransactions = listOf(
                TransactionEntity(
                    accountId = 1L,
                    amount = 4500.0,
                    type = TransactionType.INCOME,
                    category = "Salary",
                    dateMillis = cal8DaysAgo.timeInMillis,
                    timeString = "09:00",
                    paymentMethod = "Bank Transfer",
                    notes = "Monthly tech consulting salary",
                    merchant = "Acme Corp Tech",
                    referenceNumber = "DEP-9812",
                    isReconciled = true
                ),
                TransactionEntity(
                    accountId = 1L,
                    amount = 650.0,
                    type = TransactionType.INCOME,
                    category = "Freelance",
                    dateMillis = cal3DaysAgo.timeInMillis,
                    timeString = "17:30",
                    paymentMethod = "Bank Transfer",
                    notes = "UI/UX design project milestone",
                    merchant = "DesignStudio Global",
                    referenceNumber = "DEP-9844",
                    isReconciled = true
                ),
                TransactionEntity(
                    accountId = 1L,
                    amount = 38.50,
                    type = TransactionType.EXPENSE,
                    category = "Food",
                    dateMillis = todayMillis,
                    timeString = "12:45",
                    paymentMethod = "Credit Card",
                    notes = "Artisan lunch bowl & cold brew",
                    merchant = "Green Kitchen Cafe",
                    referenceNumber = "POS-1002"
                ),
                TransactionEntity(
                    accountId = 1L,
                    amount = 124.80,
                    type = TransactionType.EXPENSE,
                    category = "Food",
                    dateMillis = yesterdayMillis,
                    timeString = "13:30",
                    paymentMethod = "Credit Card",
                    notes = "Weekly organic groceries & produce",
                    merchant = "Whole Foods Market",
                    itemsSummary = "Avocados, Almond Milk, Salmon, Greens, Olive Oil",
                    referenceNumber = "POS-0994"
                ),
                TransactionEntity(
                    accountId = 1L,
                    amount = 26.00,
                    type = TransactionType.EXPENSE,
                    category = "Transport",
                    dateMillis = yesterdayMillis,
                    timeString = "16:15",
                    paymentMethod = "UPI / Debit",
                    notes = "Ride to client meeting",
                    merchant = "Uber",
                    referenceNumber = "UBR-4412"
                ),
                TransactionEntity(
                    accountId = 4L,
                    amount = 89.99,
                    type = TransactionType.EXPENSE,
                    category = "Shopping",
                    dateMillis = cal3DaysAgo.timeInMillis,
                    timeString = "18:45",
                    paymentMethod = "Credit Card",
                    notes = "Ergonomic desk light",
                    merchant = "Amazon Essentials",
                    referenceNumber = "AMZ-8821"
                ),
                TransactionEntity(
                    accountId = 1L,
                    amount = 210.00,
                    type = TransactionType.EXPENSE,
                    category = "Bills",
                    dateMillis = cal5DaysAgo.timeInMillis,
                    timeString = "11:15",
                    paymentMethod = "Bank Transfer",
                    notes = "Fiber internet and energy utilities",
                    merchant = "Metropolitan Power",
                    referenceNumber = "ACH-7721",
                    isReconciled = true
                ),
                TransactionEntity(
                    accountId = 4L,
                    amount = 45.00,
                    type = TransactionType.EXPENSE,
                    category = "Entertainment",
                    dateMillis = calMidMonth.timeInMillis,
                    timeString = "14:20",
                    paymentMethod = "Credit Card",
                    notes = "Cinema tickets & theater snack",
                    merchant = "Cineplex Premier",
                    referenceNumber = "POS-0774"
                ),
                TransactionEntity(
                    accountId = 1L,
                    amount = 60.00,
                    type = TransactionType.EXPENSE,
                    category = "Health",
                    dateMillis = calMidMonth.timeInMillis,
                    timeString = "15:45",
                    paymentMethod = "Credit Card",
                    notes = "Monthly wellness & yoga studio pass",
                    merchant = "Equinox Studio",
                    referenceNumber = "ACH-0551"
                ),
                TransactionEntity(
                    accountId = 1L,
                    amount = 4500.0,
                    type = TransactionType.INCOME,
                    category = "Salary",
                    dateMillis = calLastMonthSalary.timeInMillis,
                    timeString = "09:00",
                    paymentMethod = "Bank Transfer",
                    notes = "Previous month salary",
                    merchant = "Acme Corp Tech",
                    referenceNumber = "DEP-8101",
                    isReconciled = true
                ),
                TransactionEntity(
                    accountId = 4L,
                    amount = 750.0,
                    type = TransactionType.EXPENSE,
                    category = "Shopping",
                    dateMillis = calLastMonth.timeInMillis,
                    timeString = "16:00",
                    paymentMethod = "Credit Card",
                    notes = "Annual wardrobe refresh",
                    merchant = "Nordstrom",
                    referenceNumber = "POS-8105"
                ),
                TransactionEntity(
                    accountId = 1L,
                    amount = 420.0,
                    type = TransactionType.EXPENSE,
                    category = "Food",
                    dateMillis = calLastMonth.timeInMillis,
                    timeString = "19:00",
                    paymentMethod = "Credit Card",
                    notes = "Dining and groceries previous month",
                    merchant = "Trader Joe's",
                    referenceNumber = "POS-8112"
                )
            )

            db.transactionDao().insertAll(seedTransactions)

            // 5. Seed double-entry journal entries for key transactions
            val journalEntries = listOf(
                JournalEntryEntity(
                    transactionId = 1L,
                    entryDateMillis = cal8DaysAgo.timeInMillis,
                    description = "Monthly tech consulting salary",
                    debitAccountId = 1L, // Debit Checking (Asset up)
                    creditAccountId = 0L, // Credit Salary (Income)
                    debitAmount = 4500.0,
                    creditAmount = 4500.0,
                    ledgerCategory = "Salary"
                ),
                JournalEntryEntity(
                    transactionId = 3L,
                    entryDateMillis = todayMillis,
                    description = "Artisan lunch bowl & cold brew",
                    debitAccountId = 0L, // Debit Food (Expense)
                    creditAccountId = 1L, // Credit Checking (Asset down)
                    debitAmount = 38.50,
                    creditAmount = 38.50,
                    ledgerCategory = "Food"
                ),
                JournalEntryEntity(
                    transactionId = 7L,
                    entryDateMillis = cal5DaysAgo.timeInMillis,
                    description = "Fiber internet and energy utilities",
                    debitAccountId = 0L, // Debit Bills (Expense)
                    creditAccountId = 1L, // Credit Checking (Asset down)
                    debitAmount = 210.00,
                    creditAmount = 210.00,
                    ledgerCategory = "Bills"
                )
            )
            db.journalEntryDao().insertAll(journalEntries)

            // 6. Recalculate and update account balances based on seed transactions
            val checkingCredits = seedTransactions.filter { it.accountId == 1L && it.type == TransactionType.INCOME }.sumOf { it.amount }
            val checkingDebits = seedTransactions.filter { it.accountId == 1L && it.type == TransactionType.EXPENSE }.sumOf { it.amount }
            db.accountDao().updateBalance(1L, checkingAccount.initialBalance + checkingCredits - checkingDebits)

            val creditDebits = seedTransactions.filter { it.accountId == 4L && it.type == TransactionType.EXPENSE }.sumOf { it.amount }
            db.accountDao().updateBalance(4L, creditAccount.initialBalance - creditDebits)

            // 7. Seed Audit Log entry
            db.auditLogDao().insertLog(
                AuditLogEntity(
                    userId = 1L,
                    entityType = "SYSTEM",
                    entityId = 1L,
                    action = "INITIALIZE",
                    details = "ARTHIKA Production Accounting Engine Initialized with Double-Entry Ledger"
                )
            )

            // 8. Welcome message
            db.chatDao().insertMessage(
                ChatMessageEntity(
                    sender = "ai",
                    text = "Hello Alex! I am ARTHIKA, your verified accounting AI assistant. I have live access to your multi-account ledger, reconciliation records, and audit logs. Ask me anything like:\n\n• \"What are my account balances?\"\n• \"Show my verified income and expenses\"\n• \"Are there any reconciliation discrepancies?\"\n• \"Show me my audit log of reversed transactions\"\n• \"Generate a monthly financial summary\"",
                    isPdfReportOffer = false
                )
            )
        }
    }
}
