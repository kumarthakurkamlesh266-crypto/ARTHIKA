package com.example.ai

import android.content.Context
import com.example.BuildConfig
import com.example.data.entity.BudgetEntity
import com.example.data.entity.TransactionEntity
import com.example.data.model.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class AiResponse(
    val replyText: String,
    val matchedTransactions: List<TransactionEntity> = emptyList(),
    val isPdfRequested: Boolean = false,
    val statsInsight: String? = null,
    val categoryBreakdown: Map<String, Double> = emptyMap()
)

class FinanceAiEngine(private val context: Context) {

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .build()
    }

    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale.US)
    private val db by lazy { com.example.data.AppDatabase.getInstance(context) }
    private val accountingEngine by lazy { com.example.accounting.AccountingEngine(db) }
    private val reconciliationEngine by lazy { com.example.accounting.ReconciliationEngine(db) }
    private val toolDispatcher by lazy { com.example.accounting.AccountingAiToolDispatcher(db, accountingEngine, reconciliationEngine) }

    suspend fun processQuery(
        query: String,
        allTransactions: List<TransactionEntity>,
        budget: BudgetEntity
    ): AiResponse = withContext(Dispatchers.Default) {
        val cleanQuery = query.trim().lowercase(Locale.ROOT)

        // 1. Check for PDF report generation request
        if (cleanQuery.contains("pdf") || cleanQuery.contains("export report") || cleanQuery.contains("generate report")) {
            return@withContext AiResponse(
                replyText = "I have prepared your financial report for export. Tap the button below to generate and share the official PDF summary with executive charts and transaction ledger.",
                isPdfRequested = true,
                statsInsight = "PDF Report Ready"
            )
        }

        // 2. Verified Accounting Engine Backend Tools
        val verifiedToolAnalysis = runVerifiedAccountingTool(cleanQuery)
        val analysis = verifiedToolAnalysis ?: analyzeDatabase(query, cleanQuery, allTransactions, budget)

        // 3. Try enriching with Gemini AI if API key is provided, strictly grounded in verified facts
        val geminiApiKey = getApiKey()
        if (geminiApiKey.isNotBlank() && !geminiApiKey.contains("MY_GEMINI_API_KEY")) {
            val enriched = callGeminiGrounded(query, analysis, geminiApiKey)
            if (enriched != null) {
                return@withContext analysis.copy(replyText = enriched)
            }
        }

        // Return deterministic verified answer
        return@withContext analysis
    }

    private suspend fun runVerifiedAccountingTool(q: String): AiResponse? {
        return when {
            q.contains("account balance") || q.contains("balances") || q.contains("how much in my checking") || q.contains("how much money") || q.contains("net worth") || q.contains("savings balance") || q.contains("checking balance") -> {
                val result = toolDispatcher.executeTool("get_account_balances")
                AiResponse(replyText = result.verifiedDataSummary, statsInsight = "Account Balances Verified")
            }
            q.contains("reconcil") || q.contains("discrepanc") || q.contains("bank statement") -> {
                val result = toolDispatcher.executeTool("get_reconciliation_status")
                AiResponse(replyText = result.verifiedDataSummary, statsInsight = "Reconciliation Audited")
            }
            q.contains("duplicate") || q.contains("double charge") || q.contains("anomal") -> {
                val result = toolDispatcher.executeTool("detect_duplicates_and_anomalies")
                AiResponse(replyText = result.verifiedDataSummary, statsInsight = "Duplicate Detection Run")
            }
            q.contains("cash flow") || q.contains("liquidity") || q.contains("liquid assets") -> {
                val result = toolDispatcher.executeTool("get_cash_flow_statement")
                AiResponse(replyText = result.verifiedDataSummary, statsInsight = "Cash Flow Statement Verified")
            }
            q.contains("audit") || q.contains("reversal") || q.contains("reversed") -> {
                val result = toolDispatcher.executeTool("get_audit_trail")
                AiResponse(replyText = result.verifiedDataSummary, statsInsight = "Audit Trail Verified")
            }
            q.contains("transfer") -> {
                val result = toolDispatcher.executeTool("get_income_and_expense_summary")
                AiResponse(replyText = result.verifiedDataSummary, statsInsight = "Transfers & Income Verified")
            }
            else -> null
        }
    }

    private val apiKeyManager by lazy { com.example.data.ApiKeyManager(context) }

    private fun getApiKey(): String {
        return apiKeyManager.getApiKey()
    }

    private fun analyzeDatabase(
        originalQuery: String,
        q: String,
        transactions: List<TransactionEntity>,
        budget: BudgetEntity
    ): AiResponse {
        val now = Calendar.getInstance()

        // Query: "Who did I pay the most money to?" / "top merchant" / "who got the most money"
        if (q.contains("who did i pay") || q.contains("pay the most") || q.contains("most money to") || q.contains("top recipient") || q.contains("top merchant")) {
            val expenses = transactions.filter { it.type == TransactionType.EXPENSE && it.merchant.isNotBlank() }
            if (expenses.isEmpty()) {
                return AiResponse("You don't have any recorded merchant expenses yet.")
            }
            val merchantTotals = expenses.groupBy { it.merchant.trim() }
                .mapValues { entry -> entry.value.sumOf { it.amount } }
            val topMerchant = merchantTotals.maxByOrNull { it.value }
            if (topMerchant != null) {
                val formatted = currencyFormat.format(topMerchant.value)
                val merchantTxs = expenses.filter { it.merchant.trim().equals(topMerchant.key, ignoreCase = true) }
                return AiResponse(
                    replyText = "You paid the most money to **${topMerchant.key}**, totaling **$formatted** across ${merchantTxs.size} transaction(s).",
                    matchedTransactions = merchantTxs,
                    statsInsight = "Top Merchant: ${topMerchant.key} ($formatted)"
                )
            }
        }

        // Query: "What is my biggest expense category?" / "highest category"
        if (q.contains("biggest expense category") || q.contains("largest expense") || q.contains("top category") || q.contains("biggest category")) {
            val expenses = transactions.filter { it.type == TransactionType.EXPENSE }
            if (expenses.isEmpty()) {
                return AiResponse("No expenses recorded yet.")
            }
            val categoryTotals = expenses.groupBy { it.category }
                .mapValues { entry -> entry.value.sumOf { it.amount } }
            val topCat = categoryTotals.maxByOrNull { it.value }
            if (topCat != null) {
                val totalExpenses = expenses.sumOf { it.amount }
                val pct = if (totalExpenses > 0) (topCat.value / totalExpenses * 100).toInt() else 0
                val formatted = currencyFormat.format(topCat.value)
                val catTxs = expenses.filter { it.category.equals(topCat.key, ignoreCase = true) }
                return AiResponse(
                    replyText = "Your biggest expense category is **${topCat.key}** at **$formatted** ($pct% of all spending).",
                    matchedTransactions = catTxs.take(5),
                    categoryBreakdown = categoryTotals,
                    statsInsight = "Top Category: ${topCat.key} ($pct%)"
                )
            }
        }

        // Query: "Compare my September and October expenses" / "compare months"
        if (q.contains("compare") && (q.contains("september") || q.contains("october") || q.contains("month"))) {
            return compareMonths(transactions)
        }

        // Query: "Spending patterns" / "unusual spending"
        if (q.contains("unusual") || q.contains("pattern") || q.contains("anomaly") || q.contains("spike")) {
            val expenses = transactions.filter { it.type == TransactionType.EXPENSE }
            if (expenses.size < 3) {
                return AiResponse("Spending pattern analysis requires at least 3 transactions. Your current spending is normal.")
            }
            val avg = expenses.map { it.amount }.average()
            val outliers = expenses.filter { it.amount > avg * 2.0 }
            val avgFormatted = currencyFormat.format(avg)
            return if (outliers.isNotEmpty()) {
                val outlierSummary = outliers.take(3).joinToString(", ") {
                    "${it.merchant.ifBlank { it.category }} (${currencyFormat.format(it.amount)})"
                }
                AiResponse(
                    replyText = "Your average transaction is **$avgFormatted**. I identified unusual high spending on: $outlierSummary, which exceeded twice your normal transaction size.",
                    matchedTransactions = outliers,
                    statsInsight = "Average: $avgFormatted | ${outliers.size} High Spikes"
                )
            } else {
                AiResponse(
                    replyText = "All your spending patterns look consistent. Your average transaction is **$avgFormatted** with no unusual spikes.",
                    statsInsight = "Spending is Stable (Avg: $avgFormatted)"
                )
            }
        }

        // Query: "Monthly financial summary" / "summary" / "how am i doing"
        if (q.contains("summary") || q.contains("overview") || q.contains("how am i doing") || q.contains("financial status")) {
            return generateMonthlySummary(transactions, budget)
        }

        // Query with Date Filters
        // "yesterday"
        if (q.contains("yesterday")) {
            val cal = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -1)
            }
            return filterByCalendarDay(cal, transactions, "yesterday", q)
        }

        // "today"
        if (q.contains("today")) {
            val cal = Calendar.getInstance()
            return filterByCalendarDay(cal, transactions, "today", q)
        }

        // "last sunday" / "sunday"
        if (q.contains("sunday")) {
            val cal = Calendar.getInstance()
            while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) {
                cal.add(Calendar.DAY_OF_YEAR, -1)
            }
            return filterByCalendarDay(cal, transactions, "last Sunday", q)
        }

        // Specific date: "15 october" or "october 15"
        val specificDateRegex = Regex("(?:on\\s+)?(\\d{1,2})(?:st|nd|rd|th)?\\s+(january|february|march|april|may|june|july|august|september|october|november|december)", RegexOption.IGNORE_CASE)
        val match = specificDateRegex.find(q)
        if (match != null) {
            val day = match.groupValues[1].toIntOrNull() ?: 15
            val monthName = match.groupValues[2].lowercase(Locale.ROOT)
            val monthIdx = getMonthIndex(monthName)
            val cal = Calendar.getInstance().apply {
                set(Calendar.MONTH, monthIdx)
                set(Calendar.DAY_OF_MONTH, day)
            }

            // Check if time range is specified: "between 10 am and 6 pm"
            val timeRegex = Regex("between\\s+(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?\\s+and\\s+(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?", RegexOption.IGNORE_CASE)
            val timeMatch = timeRegex.find(q)
            var startHour = 0
            var endHour = 24
            if (timeMatch != null) {
                var h1 = timeMatch.groupValues[1].toInt()
                val ampm1 = timeMatch.groupValues[3].lowercase(Locale.ROOT)
                if (ampm1 == "pm" && h1 < 12) h1 += 12
                if (ampm1 == "am" && h1 == 12) h1 = 0

                var h2 = timeMatch.groupValues[4].toInt()
                val ampm2 = timeMatch.groupValues[6].lowercase(Locale.ROOT)
                if (ampm2 == "pm" && h2 < 12) h2 += 12
                if (ampm2 == "am" && h2 == 12) h2 = 0

                startHour = h1
                endHour = h2
            }

            return filterByDateAndTime(cal, startHour, endHour, transactions, "$day ${monthName.replaceFirstChar { it.uppercase() }}", timeMatch != null)
        }

        // "last week" / "past week"
        if (q.contains("last week") || q.contains("past week")) {
            val calEnd = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
            }
            val calStart = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -7)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
            }
            val filtered = transactions.filter { it.dateMillis in calStart.timeInMillis..calEnd.timeInMillis }
            val expenses = filtered.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
            val income = filtered.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
            val expFormatted = currencyFormat.format(expenses)
            val incFormatted = currencyFormat.format(income)

            return if (filtered.isEmpty()) {
                AiResponse("You don't have any transactions recorded from last week.")
            } else {
                AiResponse(
                    replyText = "In the past week, you had **${filtered.size} transaction(s)**. You spent **$expFormatted** and received **$incFormatted**.",
                    matchedTransactions = filtered,
                    statsInsight = "Last 7 Days: Spent $expFormatted | Received $incFormatted"
                )
            }
        }

        // Category query: "how much did I spend on food this month?"
        val categories = listOf("food", "transport", "shopping", "education", "entertainment", "bills", "health", "personal", "salary", "freelance")
        val matchedCategory = categories.firstOrNull { q.contains(it) }
        if (matchedCategory != null) {
            val catTitle = matchedCategory.replaceFirstChar { it.uppercase() }
            val catTxs = transactions.filter { it.category.equals(catTitle, ignoreCase = true) }
            val total = catTxs.sumOf { it.amount }
            val formatted = currencyFormat.format(total)
            return if (catTxs.isEmpty()) {
                AiResponse("You have no recorded transactions under **$catTitle**.")
            } else {
                AiResponse(
                    replyText = "You have spent a total of **$formatted** across ${catTxs.size} transaction(s) under **$catTitle**.",
                    matchedTransactions = catTxs,
                    statsInsight = "$catTitle Total: $formatted"
                )
            }
        }

        // General fallback query: Search matching transactions
        val searchResults = transactions.filter {
            it.merchant.contains(q, ignoreCase = true) ||
            it.notes.contains(q, ignoreCase = true) ||
            it.category.contains(q, ignoreCase = true)
        }

        if (searchResults.isNotEmpty()) {
            val total = searchResults.sumOf { it.amount }
            val formatted = currencyFormat.format(total)
            return AiResponse(
                replyText = "I found **${searchResults.size} record(s)** matching \"$originalQuery\", totaling **$formatted**.",
                matchedTransactions = searchResults,
                statsInsight = "Found ${searchResults.size} matching records"
            )
        }

        return AiResponse(
            replyText = "I searched your transaction database for \"$originalQuery\", but found no matching records. Try asking:\n\n• \"How much did I spend yesterday?\"\n• \"What is my biggest expense category?\"\n• \"Who did I pay the most money to?\"\n• \"Generate a monthly financial summary\"",
            statsInsight = "No records found"
        )
    }

    private fun filterByCalendarDay(
        targetCal: Calendar,
        transactions: List<TransactionEntity>,
        label: String,
        query: String
    ): AiResponse {
        val y = targetCal.get(Calendar.YEAR)
        val d = targetCal.get(Calendar.DAY_OF_YEAR)

        val dayTransactions = transactions.filter {
            val c = Calendar.getInstance().apply { timeInMillis = it.dateMillis }
            c.get(Calendar.YEAR) == y && c.get(Calendar.DAY_OF_YEAR) == d
        }

        val isIncomeQuery = query.contains("receive") || query.contains("income") || query.contains("got")
        val isExpenseQuery = query.contains("spend") || query.contains("spent") || query.contains("paid")

        val relevantTxs = when {
            isIncomeQuery -> dayTransactions.filter { it.type == TransactionType.INCOME }
            isExpenseQuery -> dayTransactions.filter { it.type == TransactionType.EXPENSE }
            else -> dayTransactions
        }

        if (relevantTxs.isEmpty()) {
            return AiResponse("No ${if (isIncomeQuery) "income" else if (isExpenseQuery) "expenses" else "transactions"} recorded for $label.")
        }

        val total = relevantTxs.sumOf { it.amount }
        val formatted = currencyFormat.format(total)
        val actionWord = if (isIncomeQuery) "received" else if (isExpenseQuery) "spent" else "transacted"

        return AiResponse(
            replyText = "On $label, you $actionWord **$formatted** across ${relevantTxs.size} transaction(s).",
            matchedTransactions = relevantTxs,
            statsInsight = "$label: $formatted"
        )
    }

    private fun filterByDateAndTime(
        cal: Calendar,
        startHour: Int,
        endHour: Int,
        transactions: List<TransactionEntity>,
        dateLabel: String,
        hasTimeRange: Boolean
    ): AiResponse {
        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH)
        val d = cal.get(Calendar.DAY_OF_MONTH)

        val matching = transactions.filter {
            val c = Calendar.getInstance().apply { timeInMillis = it.dateMillis }
            val sameDay = c.get(Calendar.YEAR) == y && c.get(Calendar.MONTH) == m && c.get(Calendar.DAY_OF_MONTH) == d
            if (!sameDay) return@filter false

            if (hasTimeRange) {
                val hour = parseHour(it.timeString)
                hour in startHour..endHour
            } else {
                true
            }
        }

        if (matching.isEmpty()) {
            val timeSuffix = if (hasTimeRange) " between $startHour:00 and $endHour:00" else ""
            return AiResponse("No transactions were found on $dateLabel$timeSuffix.")
        }

        val total = matching.sumOf { it.amount }
        val formatted = currencyFormat.format(total)
        val timeDesc = if (hasTimeRange) " between $startHour:00 and $endHour:00" else ""

        return AiResponse(
            replyText = "On $dateLabel$timeDesc, you had **${matching.size} transaction(s)** totaling **$formatted**.",
            matchedTransactions = matching,
            statsInsight = "$dateLabel: $formatted (${matching.size} txs)"
        )
    }

    private fun compareMonths(transactions: List<TransactionEntity>): AiResponse {
        val now = Calendar.getInstance()
        val thisMonth = now.get(Calendar.MONTH)
        val prevMonth = if (thisMonth == 0) 11 else thisMonth - 1

        val currentMonthTxs = transactions.filter {
            val c = Calendar.getInstance().apply { timeInMillis = it.dateMillis }
            c.get(Calendar.MONTH) == thisMonth && it.type == TransactionType.EXPENSE
        }
        val prevMonthTxs = transactions.filter {
            val c = Calendar.getInstance().apply { timeInMillis = it.dateMillis }
            c.get(Calendar.MONTH) == prevMonth && it.type == TransactionType.EXPENSE
        }

        val currentTotal = currentMonthTxs.sumOf { it.amount }
        val prevTotal = prevMonthTxs.sumOf { it.amount }

        val diff = currentTotal - prevTotal
        val diffFormatted = currencyFormat.format(Math.abs(diff))
        val currentFormatted = currencyFormat.format(currentTotal)
        val prevFormatted = currencyFormat.format(prevTotal)

        val monthNameCurrent = SimpleDateFormat("MMMM", Locale.US).format(now.time)
        val calPrev = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }
        val monthNamePrev = SimpleDateFormat("MMMM", Locale.US).format(calPrev.time)

        val comparisonVerdict = if (diff < 0) {
            "You spent **$diffFormatted less** in $monthNameCurrent compared to $monthNamePrev (great savings!)."
        } else if (diff > 0) {
            "You spent **$diffFormatted more** in $monthNameCurrent compared to $monthNamePrev."
        } else {
            "Your spending was identical in both months."
        }

        return AiResponse(
            replyText = "Month-over-Month Spending Comparison:\n\n• **$monthNamePrev**: $prevFormatted (${prevMonthTxs.size} expenses)\n• **$monthNameCurrent**: $currentFormatted (${currentMonthTxs.size} expenses)\n\n$comparisonVerdict",
            statsInsight = "$monthNamePrev: $prevFormatted vs $monthNameCurrent: $currentFormatted"
        )
    }

    private fun generateMonthlySummary(
        transactions: List<TransactionEntity>,
        budget: BudgetEntity
    ): AiResponse {
        val now = Calendar.getInstance()
        val currentMonth = now.get(Calendar.MONTH)

        val monthTxs = transactions.filter {
            val c = Calendar.getInstance().apply { timeInMillis = it.dateMillis }
            c.get(Calendar.MONTH) == currentMonth
        }

        val totalIncome = monthTxs.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
        val totalExpenses = monthTxs.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
        val netSavings = totalIncome - totalExpenses
        val savingsRate = if (totalIncome > 0) ((netSavings / totalIncome) * 100).toInt() else 0

        val categoryBreakdown = monthTxs.filter { it.type == TransactionType.EXPENSE }
            .groupBy { it.category }
            .mapValues { entry -> entry.value.sumOf { it.amount } }

        val topCategory = categoryBreakdown.maxByOrNull { it.value }

        val budgetLimit = budget.monthlyLimit
        val budgetUsedPct = if (budgetLimit > 0) ((totalExpenses / budgetLimit) * 100).toInt() else 0
        val remainingBudget = budgetLimit - totalExpenses

        val monthName = SimpleDateFormat("MMMM yyyy", Locale.US).format(now.time)
        val incFormatted = currencyFormat.format(totalIncome)
        val expFormatted = currencyFormat.format(totalExpenses)
        val netFormatted = currencyFormat.format(netSavings)
        val budgetFormatted = currencyFormat.format(budgetLimit)
        val remFormatted = currencyFormat.format(remainingBudget)

        val reply = buildString {
            append("### Financial Summary for $monthName\n\n")
            append("• **Total Income:** $incFormatted\n")
            append("• **Total Expenses:** $expFormatted\n")
            append("• **Net Savings:** $netFormatted (Savings Rate: $savingsRate%)\n")
            append("• **Budget Utilization:** $budgetUsedPct% of $budgetFormatted limit (Remaining: $remFormatted)\n\n")
            if (topCategory != null) {
                append("Your highest spending area is **${topCategory.key}** at ${currencyFormat.format(topCategory.value)}.\n\n")
            }
            if (budgetUsedPct > 90) {
                append("⚠️ Alert: You have utilized over 90% of your monthly budget limit.")
            } else {
                append("✅ Your monthly budget is well within healthy parameters.")
            }
        }

        return AiResponse(
            replyText = reply,
            categoryBreakdown = categoryBreakdown,
            statsInsight = "Net Savings: $netFormatted | Budget: $budgetUsedPct% Used"
        )
    }

    private fun parseHour(timeString: String): Int {
        return try {
            val parts = timeString.split(":")
            parts[0].trim().toInt()
        } catch (e: Exception) {
            12
        }
    }

    private fun getMonthIndex(name: String): Int {
        return when (name.lowercase(Locale.ROOT).take(3)) {
            "jan" -> 0
            "feb" -> 1
            "mar" -> 2
            "apr" -> 3
            "may" -> 4
            "jun" -> 5
            "jul" -> 6
            "aug" -> 7
            "sep" -> 8
            "oct" -> 9
            "nov" -> 10
            "dec" -> 11
            else -> 9
        }
    }

    private suspend fun callGeminiGrounded(
        query: String,
        analysis: AiResponse,
        apiKey: String
    ): String? = withContext(Dispatchers.IO) {
        try {
            val prompt = """
                You are ARTHIKA AI, a premium personal finance assistant.
                The user asked: "$query"
                
                The user's actual database was queried and produced the following verified mathematical facts:
                ${analysis.replyText}
                
                Format this information into an elegant, concise, conversational reply for a modern fintech app.
                CRITICAL RULES:
                1. Strictly preserve all exact numbers, merchant names, dates, and amounts. Never alter or hallucinate figures.
                2. Keep the tone minimal, calm, intelligent, and warm.
                3. Do not use conversational fluff or preamble like "Sure thing!" or "I'd be happy to help".
                4. Max 3-4 concise sentences or bullet points.
            """.trimIndent()

            val jsonBody = JSONObject().apply {
                val contents = JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", prompt)
                            })
                        })
                    })
                }
                put("contents", contents)
            }

            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey")
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val bodyStr = response.body?.string() ?: return@withContext null
                val rootJson = JSONObject(bodyStr)
                val candidates = rootJson.optJSONArray("candidates") ?: return@withContext null
                if (candidates.length() > 0) {
                    val candidate = candidates.getJSONObject(0)
                    val contentObj = candidate.optJSONObject("content")
                    val parts = contentObj?.optJSONArray("parts")
                    if (parts != null && parts.length() > 0) {
                        return@withContext parts.getJSONObject(0).optString("text")
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }
}
