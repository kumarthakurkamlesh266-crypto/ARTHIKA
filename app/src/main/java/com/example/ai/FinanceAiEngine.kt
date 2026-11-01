package com.example.ai

import android.content.Context
import android.util.Log
import com.example.data.ApiKeyManager
import com.example.data.entity.BudgetEntity
import com.example.data.entity.TransactionEntity
import com.example.data.model.TransactionType
import com.example.ui.components.IndianCurrencyFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
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
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    private val currencyFormat = object {
        fun format(amount: Double): String = IndianCurrencyFormatter.format(amount, "₹")
    }
    private val db by lazy { com.example.data.AppDatabase.getInstance(context) }
    private val accountingEngine by lazy { com.example.accounting.AccountingEngine(db) }
    private val reconciliationEngine by lazy { com.example.accounting.ReconciliationEngine(db) }
    private val toolDispatcher by lazy { com.example.accounting.AccountingAiToolDispatcher(db, accountingEngine, reconciliationEngine) }
    private val apiKeyManager by lazy { ApiKeyManager(context) }

    suspend fun processQuery(
        query: String,
        allTransactions: List<TransactionEntity>,
        budget: BudgetEntity
    ): AiResponse = withContext(Dispatchers.Default) {
        val cleanQuery = query.trim().lowercase(Locale.ROOT)

        // 1. Check for PDF report generation request
        if (cleanQuery.contains("pdf") || cleanQuery.contains("export report") || cleanQuery.contains("generate report") || cleanQuery.contains("report banao")) {
            return@withContext AiResponse(
                replyText = "I have prepared your verified financial statement for export. Tap the button below to generate and share the official PDF summary with transaction ledger and category analytics.",
                isPdfRequested = true,
                statsInsight = "PDF Report Ready"
            )
        }

        // 2. Verified Accounting Engine Backend Tools
        val verifiedToolAnalysis = runVerifiedAccountingTool(cleanQuery)
        val analysis = verifiedToolAnalysis ?: analyzeDatabase(query, cleanQuery, allTransactions, budget)

        // 3. Try enriching with Gemini AI if API key is provided
        val geminiApiKey = getApiKey()
        if (geminiApiKey.isNotBlank() && !geminiApiKey.contains("MY_GEMINI_API_KEY")) {
            val enriched = callGeminiGrounded(query, analysis, allTransactions, budget, geminiApiKey)
            if (enriched != null && enriched.isNotBlank()) {
                return@withContext analysis.copy(replyText = enriched)
            }
        }

        // Return deterministic verified answer
        return@withContext analysis
    }

    private suspend fun runVerifiedAccountingTool(q: String): AiResponse? {
        return when {
            // Balances & Net Worth (English + Hindi/Hinglish)
            q.contains("account balance") || q.contains("balances") || q.contains("how much in my checking") ||
            q.contains("how much money") || q.contains("net worth") || q.contains("savings balance") ||
            q.contains("checking balance") || q.contains("khata") || q.contains("kitna paisa") ||
            q.contains("kya balance") || q.contains("mera balance") -> {
                val result = toolDispatcher.executeTool("get_account_balances")
                AiResponse(replyText = result.verifiedDataSummary, statsInsight = "Account Balances Verified")
            }
            // Reconciliation & Statements
            q.contains("reconcil") || q.contains("discrepanc") || q.contains("bank statement") ||
            q.contains("mismatch") || q.contains("gadbad") -> {
                val result = toolDispatcher.executeTool("get_reconciliation_status")
                AiResponse(replyText = result.verifiedDataSummary, statsInsight = "Reconciliation Audited")
            }
            // Duplicate & Anomalies
            q.contains("duplicate") || q.contains("double charge") || q.contains("anomal") ||
            q.contains("dobara") || q.contains("ek jaisa") -> {
                val result = toolDispatcher.executeTool("detect_duplicates_and_anomalies")
                AiResponse(replyText = result.verifiedDataSummary, statsInsight = "Duplicate Detection Run")
            }
            // Cash Flow
            q.contains("cash flow") || q.contains("liquidity") || q.contains("liquid assets") ||
            q.contains("rokad") -> {
                val result = toolDispatcher.executeTool("get_cash_flow_statement")
                AiResponse(replyText = result.verifiedDataSummary, statsInsight = "Cash Flow Statement Verified")
            }
            // Audit Trail
            q.contains("audit") || q.contains("reversal") || q.contains("reversed") || q.contains("trail") -> {
                val result = toolDispatcher.executeTool("get_audit_trail")
                AiResponse(replyText = result.verifiedDataSummary, statsInsight = "Audit Trail Verified")
            }
            // Income, Expense & Transfers
            q.contains("transfer") || q.contains("kharcha") || q.contains("kamai") ||
            q.contains("kitna kharch") || q.contains("aamdani") -> {
                val result = toolDispatcher.executeTool("get_income_and_expense_summary")
                AiResponse(replyText = result.verifiedDataSummary, statsInsight = "Transfers & Income Verified")
            }
            else -> null
        }
    }

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

        // OCR / Bill Scanning Guidance
        if (q.contains("ocr") || q.contains("receipt") || q.contains("bill scan") || q.contains("photo scan")) {
            return AiResponse(
                replyText = "ARTHIKA includes an AI Vision OCR engine for automatic receipt and bill scanning:\n\n" +
                        "1. Tap the **+** (Add) button or open **Scan Receipt** from the Home screen.\n" +
                        "2. Take a photo or upload any grocery, restaurant, or utility receipt.\n" +
                        "3. Gemini Vision extracts the merchant, total amount (₹), date, category, and line items automatically.\n" +
                        "4. Ensure your Gemini API Key is configured in Settings (gear icon) for cloud OCR recognition.",
                statsInsight = "OCR Receipt Scanner Ready"
            )
        }

        // AI / Settings Guidance
        if (q.contains("gemini") || q.contains("api key") || q.contains("neural schema") || q.contains("model")) {
            val keyStatus = if (apiKeyManager.hasKey()) "Configured" else "Not Configured"
            val activeModel = GeminiConfig.getModel(context)
            return AiResponse(
                replyText = "ARTHIKA AI & Neural Schema Status:\n\n" +
                        "• **Active Model:** $activeModel\n" +
                        "• **API Key Status:** $keyStatus\n" +
                        "• **Neural Schema:** Structured JSON Schema enabled for OCR & Financial Analytics.\n" +
                        "• **Model Fallback:** Automatic failover across gemini-3.8-flash, gemini-2.5-flash, and gemini-1.5-flash.\n\n" +
                        "You can test your connection or change the model anytime in Settings (top right gear icon).",
                statsInsight = "Model: $activeModel | Key: $keyStatus"
            )
        }

        // Greetings & Introductions
        if (q == "hi" || q == "hello" || q == "hey" || q.contains("namaste") || q.contains("kaise ho") || q.contains("who are you")) {
            return AiResponse(
                replyText = "Namaste! I am ARTHIKA Intelligence, your personal financial assistant.\n\n" +
                        "I can help you with:\n" +
                        "• **Expense & Income Tracking** (\"How much did I spend this month?\")\n" +
                        "• **Account Balances** (\"What is my total balance across accounts?\")\n" +
                        "• **Top Merchants** (\"Who did I pay the most money to?\")\n" +
                        "• **Bill Scanning** (\"Scan receipts with AI OCR\")\n" +
                        "• **Audit & Reconciliation** (\"Check for duplicate charges\")\n\n" +
                        "How can I assist your finances today?",
                statsInsight = "ARTHIKA AI Ready"
            )
        }

        // Query: "Who did I pay the most money to?" / "top merchant" / "kisko diya"
        if (q.contains("who did i pay") || q.contains("pay the most") || q.contains("most money to") ||
            q.contains("top recipient") || q.contains("top merchant") || q.contains("kisko diya") || q.contains("sabse zyada")) {
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
        if (q.contains("biggest expense category") || q.contains("largest expense") || q.contains("top category") ||
            q.contains("biggest category") || q.contains("sabse bada kharcha")) {
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
                    replyText = "Your biggest expense category is **${topCat.key}** at **$formatted** ($pct% of total spending).",
                    matchedTransactions = catTxs.take(5),
                    categoryBreakdown = categoryTotals,
                    statsInsight = "Top Category: ${topCat.key} ($pct%)"
                )
            }
        }

        // Query: "Compare months"
        if (q.contains("compare") || q.contains("tulna")) {
            return compareMonths(transactions)
        }

        // "last week" / "past week" / "pichla hafta"
        if (q.contains("last week") || q.contains("past week") || q.contains("pichla hafta") || q.contains("pichle hafte")) {
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
                AiResponse("You don't have any transactions recorded from the past 7 days.")
            } else {
                AiResponse(
                    replyText = "In the past week, you recorded **${filtered.size} transaction(s)**. You spent **$expFormatted** and received **$incFormatted**.",
                    matchedTransactions = filtered,
                    statsInsight = "Past 7 Days: Spent $expFormatted | Received $incFormatted"
                )
            }
        }

        // Specific category query
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

        // Search matching transactions
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

        // Summary snapshot when query doesn't match specific records
        val totalExp = transactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
        val totalInc = transactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
        val expFmt = currencyFormat.format(totalExp)
        val incFmt = currencyFormat.format(totalInc)

        return AiResponse(
            replyText = "I reviewed your ledger. You currently have **${transactions.size} transaction(s)** recorded (Total Spent: **$expFmt**, Total Income: **$incFmt**).\n\n" +
                    "Try asking:\n" +
                    "• \"What is my biggest expense category?\"\n" +
                    "• \"Who did I pay the most money to?\"\n" +
                    "• \"How much did I spend this month?\"\n" +
                    "• \"Check for duplicate charges\"",
            statsInsight = "${transactions.size} records found"
        )
    }

    private fun compareMonths(transactions: List<TransactionEntity>): AiResponse {
        val now = Calendar.getInstance()
        val currentMonth = now.get(Calendar.MONTH)
        val currentYear = now.get(Calendar.YEAR)

        val lastMonthCal = Calendar.getInstance().apply {
            add(Calendar.MONTH, -1)
        }
        val lastMonth = lastMonthCal.get(Calendar.MONTH)
        val lastMonthYear = lastMonthCal.get(Calendar.YEAR)

        val currentMonthName = SimpleDateFormat("MMMM", Locale.US).format(now.time)
        val lastMonthName = SimpleDateFormat("MMMM", Locale.US).format(lastMonthCal.time)

        val currentTxs = transactions.filter {
            val c = Calendar.getInstance().apply { timeInMillis = it.dateMillis }
            c.get(Calendar.MONTH) == currentMonth && c.get(Calendar.YEAR) == currentYear && it.type == TransactionType.EXPENSE
        }
        val lastTxs = transactions.filter {
            val c = Calendar.getInstance().apply { timeInMillis = it.dateMillis }
            c.get(Calendar.MONTH) == lastMonth && c.get(Calendar.YEAR) == lastMonthYear && it.type == TransactionType.EXPENSE
        }

        val curTotal = currentTxs.sumOf { it.amount }
        val lastTotal = lastTxs.sumOf { it.amount }

        val curFmt = currencyFormat.format(curTotal)
        val lastFmt = currencyFormat.format(lastTotal)

        val diff = curTotal - lastTotal
        val diffFmt = currencyFormat.format(Math.abs(diff))

        val comparisonText = when {
            lastTotal == 0.0 && curTotal > 0.0 -> "You have spent $curFmt in $currentMonthName. (No expenses recorded in $lastMonthName)."
            diff > 0 -> "You spent **$curFmt** in $currentMonthName vs **$lastFmt** in $lastMonthName. Spending increased by **$diffFmt**."
            diff < 0 -> "You spent **$curFmt** in $currentMonthName vs **$lastFmt** in $lastMonthName. Great job! Spending decreased by **$diffFmt**."
            else -> "Spending remained identical between $currentMonthName and $lastMonthName at **$curFmt**."
        }

        return AiResponse(
            replyText = comparisonText,
            statsInsight = "$currentMonthName: $curFmt vs $lastMonthName: $lastFmt"
        )
    }

    /**
     * Calls Gemini AI with full financial grounding context, multi-model fallback,
     * and multi-lingual reasoning (English + Hindi/Hinglish).
     */
    private suspend fun callGeminiGrounded(
        query: String,
        analysis: AiResponse,
        transactions: List<TransactionEntity>,
        budget: BudgetEntity,
        apiKey: String
    ): String? = withContext(Dispatchers.IO) {
        val totalExpenses = transactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
        val totalIncome = transactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
        val expFmt = currencyFormat.format(totalExpenses)
        val incFmt = currencyFormat.format(totalIncome)
        val budgetFmt = currencyFormat.format(budget.monthlyLimit)

        val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val recentTxsSummary = transactions.take(8).joinToString("; ") {
            "${dateFormatter.format(Date(it.dateMillis))} ${it.merchant} (${it.type}): ₹${it.amount} [${it.category}]"
        }

        val prompt = """
            You are ARTHIKA AI, a premium personal finance and business accounting intelligence assistant.
            The user asked: "$query"
            
            Verified User Financial Snapshot (Real Database Facts):
            - Total Recorded Transactions: ${transactions.size}
            - Total Expenses Recorded: $expFmt
            - Total Income Recorded: $incFmt
            - Monthly Budget Limit: $budgetFmt
            - Verified Local Engine Analysis: "${analysis.replyText}"
            - Recent Transaction Ledger Samples: ${if (recentTxsSummary.isBlank()) "None recorded yet" else recentTxsSummary}
            
            CRITICAL INSTRUCTIONS:
            1. If the user asks about their financial numbers, balances, spending, or merchants, strictly use the verified figures above. Never invent fake transactions or amounts.
            2. If the user asks for financial guidance, budgeting advice, savings tips, explanations, or questions in Hindi/Hinglish (e.g. 'ocr kaam nhi kar raha', 'kya balance hai', 'paisa kaise bachaye'), reply helpfully and warmly in the same language.
            3. If the user asks about app capabilities (OCR receipt scanning, PDF export, Settings, Gemini API config), explain how to use them in ARTHIKA.
            4. Keep answers concise, elegant, and structured with clean markdown bullets where helpful. Avoid unnecessary preamble.
        """.trimIndent()

        val primaryModel = GeminiConfig.getModel(context)
        val candidateModels = listOf(
            primaryModel,
            "gemini-3.8-flash",
            "gemini-2.5-flash",
            "gemini-1.5-flash"
        ).distinct()

        for (model in candidateModels) {
            try {
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
                    put("generationConfig", JSONObject().apply {
                        put("temperature", 0.4)
                    })
                }

                val requestUrl = GeminiConfig.getGenerateContentUrl(apiKey, model)
                val request = Request.Builder()
                    .url(requestUrl)
                    .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val response = httpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string().orEmpty()
                    val rootJson = JSONObject(bodyStr)
                    val candidates = rootJson.optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val candidate = candidates.getJSONObject(0)
                        val contentObj = candidate.optJSONObject("content")
                        val parts = contentObj?.optJSONArray("parts")
                        if (parts != null && parts.length() > 0) {
                            val reply = parts.getJSONObject(0).optString("text").trim()
                            if (reply.isNotBlank()) {
                                return@withContext reply
                            }
                        }
                    }
                } else {
                    Log.w("FinanceAiEngine", "Gemini response error ($model): HTTP ${response.code}")
                }
            } catch (e: Exception) {
                Log.e("FinanceAiEngine", "Failed calling Gemini model $model: ${e.message}")
            }
        }
        null
    }
}
