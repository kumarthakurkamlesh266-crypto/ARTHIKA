package com.example.data.model

enum class TransactionType {
    EXPENSE,
    INCOME,
    TRANSFER,
    REFUND,
    REVERSAL
}

enum class AccountType {
    CHECKING,
    SAVINGS,
    CREDIT_CARD,
    CASH,
    INVESTMENT,
    WALLET
}

enum class TransactionStatus {
    COMPLETED,
    PENDING,
    RECONCILED,
    VOIDED,
    REVERSED
}

enum class MatchType {
    EXACT,
    FUZZY,
    MANUAL,
    UNMATCHED
}

enum class ReconciliationStatus {
    BALANCED,
    DISCREPANCY,
    PENDING
}

data class IncomeExpenseSummary(
    val totalIncome: Double,
    val totalExpense: Double,
    val netSavings: Double,
    val savingsRatePct: Double,
    val totalTransfers: Double = 0.0,
    val totalFees: Double = 0.0,
    val totalRefundsReceived: Double = 0.0,
    val periodStartMillis: Long,
    val periodEndMillis: Long
)

data class AccountBalanceSummary(
    val accountId: Long,
    val accountName: String,
    val accountType: AccountType,
    val openingBalance: Double,
    val totalCredits: Double,
    val totalDebits: Double,
    val closingBalance: Double,
    val reconciledBalance: Double = 0.0,
    val transactionCount: Int = 0
)

data class DuplicateCheckResult(
    val isDuplicate: Boolean,
    val matchConfidence: Float,
    val existingTransactionId: Long? = null,
    val matchReason: String? = null
)

object CategoryConstants {
    val expenseCategories = listOf(
        "Food",
        "Transport",
        "Shopping",
        "Education",
        "Entertainment",
        "Bills",
        "Health",
        "Personal",
        "Other"
    )

    val incomeCategories = listOf(
        "Salary",
        "Pocket Money",
        "Freelance",
        "Refund",
        "Gift",
        "Other"
    )

    val paymentMethods = listOf(
        "Credit Card",
        "Cash",
        "Bank Transfer",
        "UPI / Debit"
    )
}

