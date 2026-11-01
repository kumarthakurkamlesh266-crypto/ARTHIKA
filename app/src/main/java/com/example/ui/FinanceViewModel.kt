package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.FinanceAiEngine
import com.example.data.AppDatabase
import com.example.data.entity.BudgetEntity
import com.example.data.entity.ChatMessageEntity
import com.example.data.entity.TransactionEntity
import com.example.data.model.TransactionType
import com.example.data.repository.FinanceRepository
import com.example.ocr.ExtractedReceipt
import com.example.ocr.ReceiptScannerService
import com.example.reports.PdfReportGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class SortOrder {
    NEWEST,
    OLDEST,
    HIGHEST_AMOUNT,
    LOWEST_AMOUNT
}

enum class DateRangeFilter {
    ALL,
    TODAY,
    THIS_WEEK,
    THIS_MONTH
}

data class DashboardMetrics(
    val totalBalance: Double = 0.0,
    val monthlyIncome: Double = 0.0,
    val monthlyExpenses: Double = 0.0,
    val remainingBudget: Double = 0.0,
    val budgetUtilizationPct: Int = 0,
    val isBudgetApproachingLimit: Boolean = false,
    val isBudgetExceeded: Boolean = false,
    val dailyTrend: List<Pair<String, Double>> = emptyList()
)

data class ReceiptReviewState(
    val isOpen: Boolean = false,
    val receipt: ExtractedReceipt? = null,
    val isPotentialDuplicate: Boolean = false,
    val isProcessing: Boolean = false
)

data class TransactionFilterParams(
    val query: String,
    val type: TransactionType?,
    val category: String?,
    val sort: SortOrder,
    val range: DateRangeFilter
)

class FinanceViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val repository = FinanceRepository(db)
    private val aiEngine = FinanceAiEngine(application)
    private val receiptScanner = ReceiptScannerService(application)
    private val pdfGenerator = PdfReportGenerator(application)
    private val apiKeyManager = com.example.data.ApiKeyManager(application)

    // Raw Flows
    val allTransactions: StateFlow<List<TransactionEntity>> = repository.allTransactions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentTransactions: StateFlow<List<TransactionEntity>> = repository.recentTransactions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allAccounts: StateFlow<List<com.example.data.entity.AccountEntity>> = repository.allAccounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTransfers: StateFlow<List<com.example.data.entity.TransferEntity>> = repository.allTransfers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allReconciliations: StateFlow<List<com.example.data.entity.ReconciliationRecordEntity>> = repository.allReconciliations
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val auditLogs: StateFlow<List<com.example.data.entity.AuditLogEntity>> = repository.auditLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val budget: StateFlow<BudgetEntity> = repository.budget
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BudgetEntity())

    val chatMessages: StateFlow<List<ChatMessageEntity>> = repository.chatMessages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Search and Filters
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _typeFilter = MutableStateFlow<TransactionType?>(null)
    val typeFilter: StateFlow<TransactionType?> = _typeFilter.asStateFlow()

    private val _categoryFilter = MutableStateFlow<String?>(null)
    val categoryFilter: StateFlow<String?> = _categoryFilter.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.NEWEST)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    private val _dateRangeFilter = MutableStateFlow(DateRangeFilter.ALL)
    val dateRangeFilter: StateFlow<DateRangeFilter> = _dateRangeFilter.asStateFlow()

    private val filterParams = combine(
        combine(_searchQuery, _typeFilter, _categoryFilter) { query, type, cat ->
            Triple(query, type, cat)
        },
        combine(_sortOrder, _dateRangeFilter) { sort, range ->
            Pair(sort, range)
        }
    ) { (query, type, cat), (sort, range) ->
        TransactionFilterParams(query, type, cat, sort, range)
    }

    // Filtered Transactions
    val filteredTransactions: StateFlow<List<TransactionEntity>> = combine(
        allTransactions,
        filterParams
    ) { txs, params ->
        var list = txs
        val query = params.query
        val type = params.type
        val cat = params.category
        val sort = params.sort
        val range = params.range

        // Query Search
        if (query.isNotBlank()) {
            val q = query.trim().lowercase(Locale.ROOT)
            list = list.filter {
                it.merchant.lowercase(Locale.ROOT).contains(q) ||
                it.notes.lowercase(Locale.ROOT).contains(q) ||
                it.category.lowercase(Locale.ROOT).contains(q)
            }
        }

        // Type filter
        if (type != null) {
            list = list.filter { it.type == type }
        }

        // Category filter
        if (cat != null) {
            list = list.filter { it.category.equals(cat, ignoreCase = true) }
        }

        // Date range filter - High performance boundary calculations with zero object allocation during iteration
        when (range) {
            DateRangeFilter.TODAY -> {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val startOfDay = cal.timeInMillis
                val endOfDay = startOfDay + 86_400_000L
                list = list.filter { it.dateMillis in startOfDay until endOfDay }
            }
            DateRangeFilter.THIS_WEEK -> {
                val calWeekAgo = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -7) }
                val weekStart = calWeekAgo.timeInMillis
                list = list.filter { it.dateMillis >= weekStart }
            }
            DateRangeFilter.THIS_MONTH -> {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val startOfMonth = cal.timeInMillis
                cal.add(Calendar.MONTH, 1)
                val endOfMonth = cal.timeInMillis
                list = list.filter { it.dateMillis in startOfMonth until endOfMonth }
            }
            DateRangeFilter.ALL -> { /* no-op */ }
        }

        // Sort
        when (sort) {
            SortOrder.NEWEST -> list.sortedWith(compareByDescending<TransactionEntity> { it.dateMillis }.thenByDescending { it.id })
            SortOrder.OLDEST -> list.sortedWith(compareBy<TransactionEntity> { it.dateMillis }.thenBy { it.id })
            SortOrder.HIGHEST_AMOUNT -> list.sortedByDescending { it.amount }
            SortOrder.LOWEST_AMOUNT -> list.sortedBy { it.amount }
        }
    }
    .flowOn(Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Dashboard Metrics - Single-pass O(N) calculation with zero object allocation inside loop
    val dashboardMetrics: StateFlow<DashboardMetrics> = combine(allTransactions, budget) { txs, b ->
        val monthCal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfMonth = monthCal.timeInMillis
        monthCal.add(Calendar.MONTH, 1)
        val endOfMonth = monthCal.timeInMillis

        // Precompute 7-day windows
        val dayLabels = ArrayList<String>(7)
        val dayStarts = LongArray(7)
        val dayEnds = LongArray(7)
        val dayBuckets = DoubleArray(7)
        val dayFormat = SimpleDateFormat("EEE", Locale.US)

        for (i in 6 downTo 0) {
            val index = 6 - i
            val dayCal = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -i)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            dayLabels.add(dayFormat.format(dayCal.time))
            val start = dayCal.timeInMillis
            dayStarts[index] = start
            dayEnds[index] = start + 86_400_000L
        }

        var totalIncomeAll = 0.0
        var totalExpensesAll = 0.0
        var monthIncome = 0.0
        var monthExpenses = 0.0

        // Single pass over transactions
        for (tx in txs) {
            val amt = tx.amount
            val date = tx.dateMillis
            if (tx.type == TransactionType.INCOME) {
                totalIncomeAll += amt
                if (date in startOfMonth until endOfMonth) {
                    monthIncome += amt
                }
            } else if (tx.type == TransactionType.EXPENSE) {
                totalExpensesAll += amt
                if (date in startOfMonth until endOfMonth) {
                    monthExpenses += amt
                }
                for (j in 0 until 7) {
                    if (date >= dayStarts[j] && date < dayEnds[j]) {
                        dayBuckets[j] += amt
                        break
                    }
                }
            }
        }

        val balance = totalIncomeAll - totalExpensesAll
        val monthlyLimit = b.monthlyLimit
        val remBudget = (monthlyLimit - monthExpenses).coerceAtLeast(0.0)
        val utilPct = if (monthlyLimit > 0) ((monthExpenses / monthlyLimit) * 100).toInt() else 0

        val trend = ArrayList<Pair<String, Double>>(7)
        for (k in 0 until 7) {
            trend.add(Pair(dayLabels[k], dayBuckets[k]))
        }

        DashboardMetrics(
            totalBalance = balance,
            monthlyIncome = monthIncome,
            monthlyExpenses = monthExpenses,
            remainingBudget = remBudget,
            budgetUtilizationPct = utilPct,
            isBudgetApproachingLimit = utilPct in 80..99,
            isBudgetExceeded = utilPct >= 100,
            dailyTrend = trend
        )
    }
    .flowOn(Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardMetrics())

    // AI Chat state
    private val _isAiThinking = MutableStateFlow(false)
    val isAiThinking: StateFlow<Boolean> = _isAiThinking.asStateFlow()
    private val _pendingUserMessage = MutableStateFlow<String?>(null)
    val pendingUserMessage: StateFlow<String?> = _pendingUserMessage.asStateFlow()

    // OCR Review state
    private val _receiptReviewState = MutableStateFlow(ReceiptReviewState())
    val receiptReviewState: StateFlow<ReceiptReviewState> = _receiptReviewState.asStateFlow()

    // App Security Lock state
    private val _isAppLocked = MutableStateFlow(false)
    val isAppLocked: StateFlow<Boolean> = _isAppLocked.asStateFlow()

    // Transaction to Edit
    private val _editingTransaction = MutableStateFlow<TransactionEntity?>(null)
    val editingTransaction: StateFlow<TransactionEntity?> = _editingTransaction.asStateFlow()

    // Add / Edit Transaction Sheet visibility
    private val _isAddSheetOpen = MutableStateFlow(false)
    val isAddSheetOpen: StateFlow<Boolean> = _isAddSheetOpen.asStateFlow()

    // Default transaction type for the sheet
    private val _newTransactionDefaultType = MutableStateFlow(TransactionType.EXPENSE)
    val newTransactionDefaultType: StateFlow<TransactionType> = _newTransactionDefaultType.asStateFlow()

    // PDF generation feedback message
    private val _pdfFeedbackMessage = MutableStateFlow<String?>(null)
    val pdfFeedbackMessage: StateFlow<String?> = _pdfFeedbackMessage.asStateFlow()

    init {
        // Check if security lock is enabled
        viewModelScope.launch {
            val b = repository.getBudgetSnapshot()
            if (b.isPinEnabled && b.pinCode.isNotBlank()) {
                _isAppLocked.value = true
            }
        }
    }

    // --- Filter Mutations ---
    fun setSearchQuery(q: String) { _searchQuery.value = q }
    fun setTypeFilter(t: TransactionType?) { _typeFilter.value = t }
    fun setCategoryFilter(c: String?) { _categoryFilter.value = c }
    fun setSortOrder(s: SortOrder) { _sortOrder.value = s }
    fun setDateRangeFilter(d: DateRangeFilter) { _dateRangeFilter.value = d }

    // --- CRUD Actions ---
    fun openAddTransactionSheet(type: TransactionType = TransactionType.EXPENSE) {
        _editingTransaction.value = null
        _newTransactionDefaultType.value = type
        _isAddSheetOpen.value = true
    }

    fun openEditTransaction(tx: TransactionEntity) {
        _editingTransaction.value = tx
        _newTransactionDefaultType.value = tx.type
        _isAddSheetOpen.value = true
    }

    fun closeAddSheet() {
        _isAddSheetOpen.value = false
        _editingTransaction.value = null
    }

    fun saveTransaction(
        id: Long = 0,
        amount: Double,
        type: TransactionType,
        category: String,
        dateMillis: Long,
        timeString: String,
        paymentMethod: String,
        notes: String,
        merchant: String,
        receiptUri: String? = null,
        itemsSummary: String? = null
    ) {
        viewModelScope.launch {
            val tx = TransactionEntity(
                id = id,
                amount = amount,
                type = type,
                category = category,
                dateMillis = dateMillis,
                timeString = timeString.ifBlank { SimpleDateFormat("HH:mm", Locale.US).format(Date()) },
                paymentMethod = paymentMethod,
                notes = notes,
                merchant = merchant,
                receiptUri = receiptUri,
                itemsSummary = itemsSummary
            )
            if (id == 0L) {
                repository.addTransaction(tx)
            } else {
                repository.updateTransaction(tx)
            }
            closeAddSheet()
        }
    }

    fun deleteTransaction(tx: TransactionEntity) {
        viewModelScope.launch {
            repository.deleteTransaction(tx)
            if (_editingTransaction.value?.id == tx.id) {
                closeAddSheet()
            }
        }
    }

    // --- Accounting Operations ---
    fun transferFunds(
        fromAccountId: Long,
        toAccountId: Long,
        amount: Double,
        fee: Double = 0.0,
        notes: String = "",
        onComplete: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            val result = repository.executeTransfer(fromAccountId, toAccountId, amount, fee, System.currentTimeMillis(), notes)
            when (result) {
                is com.example.accounting.TransferExecutionResult.Success -> {
                    onComplete(true, "Successfully transferred $$amount between accounts.")
                }
                is com.example.accounting.TransferExecutionResult.Error -> {
                    onComplete(false, result.message)
                }
            }
        }
    }

    fun reverseTransaction(
        transactionId: Long,
        reason: String,
        onComplete: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            val result = repository.executeReversal(transactionId, reason)
            when (result) {
                is com.example.accounting.ReversalExecutionResult.Success -> {
                    onComplete(true, "Transaction reversed. Restored balance to $${result.restoredBalance}.")
                }
                is com.example.accounting.ReversalExecutionResult.Error -> {
                    onComplete(false, result.message)
                }
            }
        }
    }

    fun refundTransaction(
        transactionId: Long,
        amount: Double,
        reason: String,
        onComplete: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            val result = repository.executeRefund(transactionId, amount, reason)
            when (result) {
                is com.example.accounting.RefundExecutionResult.Success -> {
                    onComplete(true, "Refund of $$amount processed successfully.")
                }
                is com.example.accounting.RefundExecutionResult.Error -> {
                    onComplete(false, result.message)
                }
            }
        }
    }

    fun importAndReconcile(
        accountId: Long,
        statementContent: String,
        fileName: String,
        onComplete: (com.example.accounting.FullReconciliationSummary?) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val parsed = repository.reconciliationEngine.parseStatementText(statementContent, fileName)
                val summary = repository.reconcileAccount(accountId, parsed)
                onComplete(summary)
            } catch (e: Exception) {
                onComplete(null)
            }
        }
    }

    // --- AI Assistant ---
    fun sendAiMessage(promptText: String) {
        if (promptText.isBlank()) return
        val userQuery = promptText.trim()
        _pendingUserMessage.value = userQuery
        _isAiThinking.value = true
        viewModelScope.launch {
            try {
                // Save user message immediately to database
                repository.saveChatMessage(sender = "user", text = userQuery)
                _pendingUserMessage.value = null

                val allTxs = repository.getTransactionsList()
                val b = repository.getBudgetSnapshot()

                val aiResult = aiEngine.processQuery(userQuery, allTxs, b)

                repository.saveChatMessage(
                    sender = "ai",
                    text = aiResult.replyText,
                    isPdfOffer = aiResult.isPdfRequested,
                    statsMetadata = aiResult.statsInsight
                )
            } catch (e: Exception) {
                _pendingUserMessage.value = null
                repository.saveChatMessage(
                    sender = "ai",
                    text = "I encountered an error retrieving that verified financial data. Please try again."
                )
            } finally {
                _pendingUserMessage.value = null
                _isAiThinking.value = false
            }
        }
    }

    fun clearChatHistory() {
        viewModelScope.launch {
            repository.clearChatHistory()
            // Re-insert initial welcome message
            repository.saveChatMessage(
                sender = "ai",
                text = "Chat history cleared. How can I help you analyze your finances today?"
            )
        }
    }

    // --- Receipt Scanning & OCR ---
    fun openReceiptScanner() {
        _receiptReviewState.value = ReceiptReviewState(isOpen = true, receipt = null, isProcessing = false)
    }

    fun getSavedApiKey(): String = apiKeyManager.getUserSavedApiKey()

    fun hasApiKeyConfigured(): Boolean = apiKeyManager.hasKey()

    suspend fun testApiKey(apiKey: String): com.example.data.ApiKeyStatus = apiKeyManager.testApiKey(apiKey)

    fun scanReceiptUri(uri: Uri) {
        viewModelScope.launch {
            _receiptReviewState.value = ReceiptReviewState(isOpen = true, isProcessing = true)
            val extracted = receiptScanner.processReceiptUri(uri)

            // Check duplicate
            val dateMillis = parseDateStringToMillis(extracted.dateString)
            val duplicate = repository.checkDuplicate(extracted.merchant, extracted.amount, dateMillis)

            _receiptReviewState.value = ReceiptReviewState(
                isOpen = true,
                receipt = extracted,
                isPotentialDuplicate = duplicate != null,
                isProcessing = false
            )
        }
    }

    fun applySampleReceipt(preset: ExtractedReceipt) {
        viewModelScope.launch {
            _receiptReviewState.value = ReceiptReviewState(isOpen = true, isProcessing = true)
            val dateMillis = parseDateStringToMillis(preset.dateString)
            val duplicate = repository.checkDuplicate(preset.merchant, preset.amount, dateMillis)

            _receiptReviewState.value = ReceiptReviewState(
                isOpen = true,
                receipt = preset,
                isPotentialDuplicate = duplicate != null,
                isProcessing = false
            )
        }
    }

    fun closeReceiptReview() {
        _receiptReviewState.value = ReceiptReviewState(isOpen = false)
    }

    fun confirmReceiptTransaction(
        merchant: String,
        amount: Double,
        dateString: String,
        timeString: String,
        category: String,
        paymentMethod: String,
        notes: String,
        receiptUri: String?
    ) {
        viewModelScope.launch {
            val dateMillis = parseDateStringToMillis(dateString)
            val tx = TransactionEntity(
                amount = amount,
                type = TransactionType.EXPENSE,
                category = category,
                dateMillis = dateMillis,
                timeString = timeString.ifBlank { "12:00" },
                paymentMethod = paymentMethod,
                notes = notes,
                merchant = merchant,
                receiptUri = receiptUri
            )
            repository.addTransaction(tx)
            closeReceiptReview()
        }
    }

    fun getSampleReceiptPresets(): List<ExtractedReceipt> {
        return receiptScanner.getSampleReceiptPresets()
    }

    // --- PDF Export ---
    fun generatePdfReport(scope: String = "Monthly Statement") {
        viewModelScope.launch {
            _pdfFeedbackMessage.value = "Generating PDF report..."
            val allTxs = repository.getTransactionsList()
            val now = Calendar.getInstance()
            val currentMonth = now.get(Calendar.MONTH)
            val currentYear = now.get(Calendar.YEAR)

            val (reportTitle, dateRangeLabel, txsToInclude) = when (scope) {
                "Weekly Statement" -> {
                    val calWeek = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -7) }
                    Triple("Weekly Spending Statement", "Last 7 Days", allTxs.filter { it.dateMillis >= calWeek.timeInMillis })
                }
                "All Time Ledger" -> {
                    Triple("Complete Financial Ledger", "All Recorded Transactions", allTxs)
                }
                else -> {
                    val monthName = SimpleDateFormat("MMMM yyyy", Locale.US).format(now.time)
                    val monthTxs = allTxs.filter {
                        val c = Calendar.getInstance().apply { timeInMillis = it.dateMillis }
                        c.get(Calendar.MONTH) == currentMonth && c.get(Calendar.YEAR) == currentYear
                    }
                    Triple("Monthly Statement • $monthName", monthName, monthTxs)
                }
            }

            try {
                val file = pdfGenerator.generatePdfReport(reportTitle, dateRangeLabel, txsToInclude)
                _pdfFeedbackMessage.value = "PDF report generated successfully!"
                pdfGenerator.sharePdf(file)
            } catch (e: Exception) {
                _pdfFeedbackMessage.value = "Failed to generate PDF: ${e.localizedMessage}"
            }
        }
    }

    fun clearPdfFeedbackMessage() {
        _pdfFeedbackMessage.value = null
    }

    // --- Security & Settings ---
    fun updateBudgetSettings(
        monthlyLimit: Double,
        currencySymbol: String,
        pinCode: String,
        isPinEnabled: Boolean,
        userName: String,
        geminiApiKey: String = ""
    ) {
        apiKeyManager.saveApiKey(geminiApiKey)
        viewModelScope.launch {
            val updated = BudgetEntity(
                id = 1,
                monthlyLimit = monthlyLimit,
                currencySymbol = currencySymbol,
                pinCode = pinCode,
                isPinEnabled = isPinEnabled,
                userName = userName
            )
            repository.updateBudget(updated)
            if (isPinEnabled && pinCode.isNotBlank()) {
                _isAppLocked.value = false
            }
        }
    }

    fun unlockAppWithPin(enteredPin: String): Boolean {
        val currentPin = budget.value.pinCode
        if (enteredPin == currentPin || !budget.value.isPinEnabled) {
            _isAppLocked.value = false
            return true
        }
        return false
    }

    fun lockApp() {
        if (budget.value.isPinEnabled) {
            _isAppLocked.value = true
        }
    }

    private fun parseDateStringToMillis(dateStr: String): Long {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            sdf.parse(dateStr)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }
}
