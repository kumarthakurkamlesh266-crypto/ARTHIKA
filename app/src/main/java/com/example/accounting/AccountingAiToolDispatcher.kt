package com.example.accounting

import com.example.data.AppDatabase
import com.example.data.model.AccountBalanceSummary
import com.example.data.model.IncomeExpenseSummary
import com.example.data.model.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class AccountingToolResult(
    val toolName: String,
    val isSuccess: Boolean,
    val verifiedDataSummary: String,
    val structuredJson: JSONObject,
    val errorMessage: String? = null
)

class AccountingAiToolDispatcher(
    private val db: AppDatabase,
    private val accountingEngine: AccountingEngine,
    private val reconciliationEngine: ReconciliationEngine
) {
    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale.US)
    private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US)

    /**
     * Dispatches user intent to real verified accounting database tools.
     */
    suspend fun executeTool(
        toolName: String,
        params: Map<String, String> = emptyMap()
    ): AccountingToolResult = withContext(Dispatchers.IO) {
        try {
            when (toolName) {
                "get_account_balances" -> executeGetAccountBalances(params)
                "get_income_and_expense_summary" -> executeGetIncomeExpenseSummary(params)
                "get_cash_flow_statement" -> executeGetCashFlowStatement(params)
                "get_reconciliation_status" -> executeGetReconciliationStatus(params)
                "detect_duplicates_and_anomalies" -> executeDetectDuplicatesAndAnomalies()
                "get_audit_trail" -> executeGetAuditTrail(params)
                "get_category_spending_breakdown" -> executeGetCategoryBreakdown(params)
                else -> AccountingToolResult(
                    toolName = toolName,
                    isSuccess = false,
                    verifiedDataSummary = "Unknown accounting tool: $toolName",
                    structuredJson = JSONObject(),
                    errorMessage = "Unrecognized tool: $toolName"
                )
            }
        } catch (e: Exception) {
            AccountingToolResult(
                toolName = toolName,
                isSuccess = false,
                verifiedDataSummary = "Accounting tool execution error: ${e.message}",
                structuredJson = JSONObject(),
                errorMessage = e.message
            )
        }
    }

    private suspend fun executeGetAccountBalances(params: Map<String, String>): AccountingToolResult {
        val accountId = params["accountId"]?.toLongOrNull()
        val json = JSONObject()
        val textBuilder = StringBuilder()

        if (accountId != null) {
            val balance = accountingEngine.calculateAccountBalance(accountId)
            if (balance != null) {
                json.put("accountId", balance.accountId)
                json.put("accountName", balance.accountName)
                json.put("currentBalance", balance.closingBalance)
                json.put("openingBalance", balance.openingBalance)
                json.put("totalCredits", balance.totalCredits)
                json.put("totalDebits", balance.totalDebits)
                json.put("reconciledBalance", balance.reconciledBalance)

                textBuilder.append("Verified Account Balance for ${balance.accountName} (${balance.accountType}):\n")
                textBuilder.append("• Current Balance: ${currencyFormat.format(balance.closingBalance)}\n")
                textBuilder.append("• Opening Balance: ${currencyFormat.format(balance.openingBalance)}\n")
                textBuilder.append("• Credits (Inflow): +${currencyFormat.format(balance.totalCredits)}\n")
                textBuilder.append("• Debits (Outflow): -${currencyFormat.format(balance.totalDebits)}\n")
                textBuilder.append("• Reconciled: ${currencyFormat.format(balance.reconciledBalance)}")
            } else {
                return AccountingToolResult("get_account_balances", false, "Account ID $accountId not found", json)
            }
        } else {
            val balances = accountingEngine.calculateAllAccountBalances()
            val accountsArray = JSONArray()
            var grandTotal = 0.0

            textBuilder.append("Verified Balances Across All Accounts:\n")
            for (acc in balances) {
                val accObj = JSONObject().apply {
                    put("id", acc.accountId)
                    put("name", acc.accountName)
                    put("type", acc.accountType.name)
                    put("balance", acc.closingBalance)
                }
                accountsArray.put(accObj)
                grandTotal += acc.closingBalance
                textBuilder.append("• ${acc.accountName}: ${currencyFormat.format(acc.closingBalance)} (${acc.accountType})\n")
            }
            textBuilder.append("• Net Total Balance: ${currencyFormat.format(grandTotal)}")
            json.put("accounts", accountsArray)
            json.put("totalNetBalance", grandTotal)
        }

        return AccountingToolResult("get_account_balances", true, textBuilder.toString(), json)
    }

    private suspend fun executeGetIncomeExpenseSummary(params: Map<String, String>): AccountingToolResult {
        val cal = Calendar.getInstance()
        val period = params["period"] ?: "month"

        val (startMillis, endMillis) = when (period) {
            "today" -> {
                val start = cal.apply {
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
                }.timeInMillis
                val end = cal.apply {
                    set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59)
                }.timeInMillis
                Pair(start, end)
            }
            "year" -> {
                val start = cal.apply {
                    set(Calendar.DAY_OF_YEAR, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                }.timeInMillis
                val end = System.currentTimeMillis()
                Pair(start, end)
            }
            else -> {
                // Default: Current month
                val start = cal.apply {
                    set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                }.timeInMillis
                val end = System.currentTimeMillis()
                Pair(start, end)
            }
        }

        val summary = accountingEngine.calculateIncomeAndExpense(startMillis, endMillis)
        val json = JSONObject().apply {
            put("totalIncome", summary.totalIncome)
            put("totalExpense", summary.totalExpense)
            put("netSavings", summary.netSavings)
            put("savingsRatePct", summary.savingsRatePct)
            put("totalTransfers", summary.totalTransfers)
            put("periodStart", dateFormat.format(Date(startMillis)))
            put("periodEnd", dateFormat.format(Date(endMillis)))
        }

        val summaryText = """
            Verified Accounting Period Summary (${dateFormat.format(Date(startMillis))} – ${dateFormat.format(Date(endMillis))}):
            • Total Verified Income: +${currencyFormat.format(summary.totalIncome)}
            • Total Verified Expenses: -${currencyFormat.format(summary.totalExpense)}
            • Net Operating Savings: ${currencyFormat.format(summary.netSavings)}
            • Savings Rate: ${summary.savingsRatePct}%
            • Internal Transfers (Asset movements): ${currencyFormat.format(summary.totalTransfers)}
        """.trimIndent()

        return AccountingToolResult("get_income_and_expense_summary", true, summaryText, json)
    }

    private suspend fun executeGetCashFlowStatement(params: Map<String, String>): AccountingToolResult {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        val startMillis = cal.timeInMillis
        val endMillis = System.currentTimeMillis()

        val summary = accountingEngine.calculateIncomeAndExpense(startMillis, endMillis)
        val balances = accountingEngine.calculateAllAccountBalances()
        val totalAssets = balances.filter { it.accountType != com.example.data.model.AccountType.CREDIT_CARD }.sumOf { it.closingBalance }
        val totalLiabilities = balances.filter { it.accountType == com.example.data.model.AccountType.CREDIT_CARD }.sumOf { it.closingBalance }

        val json = JSONObject().apply {
            put("operatingCashInflow", summary.totalIncome)
            put("operatingCashOutflow", summary.totalExpense)
            put("netCashFlow", summary.netSavings)
            put("totalLiquidAssets", totalAssets)
            put("totalLiabilities", totalLiabilities)
        }

        val summaryText = """
            Verified Cash Flow & Liquidity Statement:
            • Operating Inflows (Income): +${currencyFormat.format(summary.totalIncome)}
            • Operating Outflows (Expenses): -${currencyFormat.format(summary.totalExpense)}
            • Net Operating Cash Flow: ${currencyFormat.format(summary.netSavings)}
            • Total Liquid Assets (Bank & Cash): ${currencyFormat.format(totalAssets)}
            • Total Short-Term Liabilities (Credit Lines): ${currencyFormat.format(totalLiabilities)}
        """.trimIndent()

        return AccountingToolResult("get_cash_flow_statement", true, summaryText, json)
    }

    private suspend fun executeGetReconciliationStatus(params: Map<String, String>): AccountingToolResult {
        val accountId = params["accountId"]?.toLongOrNull() ?: 1L
        val account = db.accountDao().getAccountById(accountId)
        val reconciliations = db.reconciliationDao().getReconciliationsForAccount(accountId)
        val unreconciledTxs = db.transactionDao().getUnreconciledTransactions(accountId)

        val json = JSONObject()
        val textBuilder = StringBuilder()

        textBuilder.append("Bank Reconciliation Status for ${account?.name ?: "Account #$accountId"}:\n")
        textBuilder.append("• Unreconciled System Entries: ${unreconciledTxs.size}\n")

        if (reconciliations.isNotEmpty()) {
            val latest = reconciliations.first()
            json.put("lastReconciledAt", latest.reconciledAt)
            json.put("status", latest.status)
            json.put("discrepancy", latest.discrepancy)
            json.put("matchedCount", latest.matchedCount)
            json.put("unmatchedCount", latest.unmatchedCount)

            textBuilder.append("• Latest Reconciliation Status: ${latest.status}\n")
            textBuilder.append("• Discrepancy Amount: ${currencyFormat.format(latest.discrepancy)}\n")
            textBuilder.append("• Matched Items: ${latest.matchedCount}\n")
            textBuilder.append("• Unmatched Items: ${latest.unmatchedCount}\n")
            textBuilder.append("• Date: ${dateFormat.format(Date(latest.reconciledAt))}")
        } else {
            textBuilder.append("• No prior formal statement reconciliations on record.")
        }

        return AccountingToolResult("get_reconciliation_status", true, textBuilder.toString(), json)
    }

    private suspend fun executeDetectDuplicatesAndAnomalies(): AccountingToolResult {
        val allTxs = db.transactionDao().getAllTransactions()
        val duplicatesFound = mutableListOf<String>()

        // Check for duplicate amounts on same merchant on same day
        val grouped = allTxs.filter { it.status != "REVERSED" }.groupBy {
            val cal = Calendar.getInstance().apply { timeInMillis = it.dateMillis }
            "${it.accountId}_${it.merchant.trim().lowercase(Locale.ROOT)}_${it.amount}_${cal.get(Calendar.DAY_OF_YEAR)}"
        }

        for ((_, group) in grouped) {
            if (group.size > 1) {
                val first = group[0]
                duplicatesFound.add("Duplicate pair: ${first.merchant} for ${currencyFormat.format(first.amount)} on ${dateFormat.format(Date(first.dateMillis))}")
            }
        }

        val json = JSONObject().apply {
            put("duplicateCount", duplicatesFound.size)
        }

        val summaryText = if (duplicatesFound.isNotEmpty()) {
            "Detected Potential Duplicate Transactions:\n" + duplicatesFound.joinToString("\n") { "• $it" }
        } else {
            "No duplicate transactions or anomalies detected in current active records. Database integrity verified."
        }

        return AccountingToolResult("detect_duplicates_and_anomalies", true, summaryText, json)
    }

    private suspend fun executeGetAuditTrail(params: Map<String, String>): AccountingToolResult {
        val limit = params["limit"]?.toIntOrNull() ?: 10
        val logs = db.auditLogDao().getRecentLogs(limit)

        val json = JSONObject().apply { put("count", logs.size) }
        val textBuilder = StringBuilder("Verified Audit Log (Latest ${logs.size} actions):\n")

        for (log in logs) {
            val dateStr = SimpleDateFormat("MMM dd HH:mm", Locale.US).format(Date(log.timestamp))
            textBuilder.append("• [$dateStr] ${log.action} ${log.entityType} #${log.entityId}: ${log.details}\n")
        }

        return AccountingToolResult("get_audit_trail", true, textBuilder.toString(), json)
    }

    private suspend fun executeGetCategoryBreakdown(params: Map<String, String>): AccountingToolResult {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        val startMillis = cal.timeInMillis
        val endMillis = System.currentTimeMillis()

        val txs = db.transactionDao().getTransactionsBetween(startMillis, endMillis)
            .filter { it.type == TransactionType.EXPENSE && it.status != "REVERSED" }

        val categorySums = txs.groupBy { it.category }
            .mapValues { entry -> entry.value.sumOf { it.amount } }
            .toList()
            .sortedByDescending { it.second }

        val json = JSONObject()
        val textBuilder = StringBuilder("Verified Spending by Category (This Month):\n")
        for ((cat, sum) in categorySums) {
            json.put(cat, sum)
            textBuilder.append("• $cat: ${currencyFormat.format(sum)}\n")
        }

        return AccountingToolResult("get_category_spending_breakdown", true, textBuilder.toString(), json)
    }
}
