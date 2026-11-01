package com.example.accounting

import com.example.data.AppDatabase
import com.example.data.entity.AuditLogEntity
import com.example.data.entity.ImportedStatementEntity
import com.example.data.entity.ReconciliationItemEntity
import com.example.data.entity.ReconciliationRecordEntity
import com.example.data.entity.TransactionEntity
import com.example.data.model.AccountType
import com.example.data.model.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

data class ParsedStatementItem(
    val dateMillis: Long,
    val description: String,
    val amount: Double,
    val isCredit: Boolean,
    val referenceNumber: String? = null,
    val runningBalance: Double? = null
)

data class ParsedStatement(
    val fileName: String,
    val fileFormat: String,
    val openingBalance: Double,
    val closingBalance: Double,
    val periodStartMillis: Long,
    val periodEndMillis: Long,
    val items: List<ParsedStatementItem>
)

data class ReconciliationMatchResult(
    val statementItem: ParsedStatementItem,
    val matchedTransaction: TransactionEntity?,
    val matchType: String, // EXACT, FUZZY, UNMATCHED
    val confidence: Float,
    val discrepancyAmount: Double = 0.0,
    val matchNotes: String = ""
)

data class FullReconciliationSummary(
    val reconciliationId: Long,
    val accountId: Long,
    val statementFileName: String,
    val statementOpeningBalance: Double,
    val statementClosingBalance: Double,
    val calculatedEndingBalance: Double,
    val systemCurrentBalance: Double,
    val balanceDiscrepancy: Double,
    val isBalanced: Boolean,
    val totalCredits: Double,
    val totalDebits: Double,
    val matchedCount: Int,
    val unmatchedStatementCount: Int,
    val unmatchedSystemCount: Int,
    val matches: List<ReconciliationMatchResult>,
    val unmatchedSystemTransactions: List<TransactionEntity>
)

class ReconciliationEngine(private val db: AppDatabase) {

    private val transactionDao = db.transactionDao()
    private val accountDao = db.accountDao()
    private val statementDao = db.importedStatementDao()
    private val reconciliationDao = db.reconciliationDao()
    private val auditLogDao = db.auditLogDao()

    /**
     * Parses bank statement raw content (CSV or standard lines).
     * Format example CSV:
     * Date,Description,Amount,Type[,Balance]
     * 2026-10-25,Whole Foods,-124.80,DEBIT,3075.20
     */
    fun parseStatementText(
        rawText: String,
        fileName: String = "statement.csv",
        defaultOpeningBalance: Double = 0.0,
        defaultClosingBalance: Double? = null
    ): ParsedStatement {
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotBlank() }
        val items = mutableListOf<ParsedStatementItem>()

        val dateFormats = listOf(
            SimpleDateFormat("yyyy-MM-dd", Locale.ROOT),
            SimpleDateFormat("MM/dd/yyyy", Locale.ROOT),
            SimpleDateFormat("dd/MM/yyyy", Locale.ROOT),
            SimpleDateFormat("yyyy/MM/dd", Locale.ROOT)
        )

        var earliestDate = Long.MAX_VALUE
        var latestDate = Long.MIN_VALUE
        var runningBal: Double? = null

        var detectedOpeningBal = defaultOpeningBalance
        var detectedClosingBal = defaultClosingBalance

        for ((index, line) in lines.withIndex()) {
            // Skip header if line contains words like date, description, amount
            val lower = line.lowercase(Locale.ROOT)
            if (index == 0 && (lower.contains("date") || lower.contains("description") || lower.contains("amount"))) {
                continue
            }

            // Check for balance header metadata
            if (lower.startsWith("opening balance:") || lower.startsWith("starting balance:")) {
                val num = line.substringAfter(":").trim().replace("$", "").toDoubleOrNull()
                if (num != null) detectedOpeningBal = num
                continue
            }
            if (lower.startsWith("closing balance:") || lower.startsWith("ending balance:")) {
                val num = line.substringAfter(":").trim().replace("$", "").toDoubleOrNull()
                if (num != null) detectedClosingBal = num
                continue
            }

            // Parse CSV / TSV or comma separated tokens
            val tokens = if (line.contains(",")) {
                line.split(",").map { it.trim().trim('"', '\'') }
            } else {
                line.split(Regex("\\s{2,}|\t")).map { it.trim() }
            }

            if (tokens.size < 3) continue

            // 1. Date
            var parsedDateMillis: Long? = null
            for (df in dateFormats) {
                try {
                    val d = df.parse(tokens[0])
                    if (d != null) {
                        parsedDateMillis = d.time
                        break
                    }
                } catch (_: Exception) {}
            }
            val dateMillis = parsedDateMillis ?: System.currentTimeMillis()

            // 2. Description
            val desc = tokens[1]

            // 3. Amount & Type
            val rawAmountStr = tokens[2].replace("$", "").replace(",", "")
            val parsedAmount = rawAmountStr.toDoubleOrNull() ?: 0.0

            var isCredit = false
            var finalAmount = abs(parsedAmount)

            if (tokens.size >= 4) {
                val typeToken = tokens[3].uppercase(Locale.ROOT)
                if (typeToken.contains("CREDIT") || typeToken.contains("CR") || typeToken.contains("DEP") || typeToken.contains("INCOME")) {
                    isCredit = true
                } else if (typeToken.contains("DEBIT") || typeToken.contains("DR") || typeToken.contains("WITHDRAWAL")) {
                    isCredit = false
                } else {
                    isCredit = parsedAmount > 0
                }
            } else {
                isCredit = parsedAmount > 0 && !line.contains("-")
            }

            if (tokens.size >= 5) {
                val bal = tokens[4].replace("$", "").replace(",", "").toDoubleOrNull()
                if (bal != null) runningBal = bal
            }

            if (dateMillis < earliestDate) earliestDate = dateMillis
            if (dateMillis > latestDate) latestDate = dateMillis

            items.add(
                ParsedStatementItem(
                    dateMillis = dateMillis,
                    description = desc,
                    amount = finalAmount,
                    isCredit = isCredit,
                    runningBalance = runningBal
                )
            )
        }

        if (earliestDate == Long.MAX_VALUE) earliestDate = System.currentTimeMillis() - (30L * 86400000L)
        if (latestDate == Long.MIN_VALUE) latestDate = System.currentTimeMillis()

        val creditsSum = items.filter { it.isCredit }.sumOf { it.amount }
        val debitsSum = items.filter { !it.isCredit }.sumOf { it.amount }

        val closingBal = detectedClosingBal ?: runningBal ?: (detectedOpeningBal + creditsSum - debitsSum)

        return ParsedStatement(
            fileName = fileName,
            fileFormat = if (fileName.endsWith(".csv", true)) "CSV" else "TEXT",
            openingBalance = (detectedOpeningBal * 100.0).roundToInt() / 100.0,
            closingBalance = (closingBal * 100.0).roundToInt() / 100.0,
            periodStartMillis = earliestDate,
            periodEndMillis = latestDate,
            items = items
        )
    }

    /**
     * Executes real bank reconciliation for an account against a parsed bank statement.
     * Matches transactions, identifies discrepancies, detects duplicates and missing entries.
     */
    suspend fun reconcileAccount(
        accountId: Long,
        statement: ParsedStatement,
        autoMarkReconciled: Boolean = true
    ): FullReconciliationSummary = withContext(Dispatchers.IO) {
        val account = accountDao.getAccountById(accountId)
            ?: throw IllegalArgumentException("Account with ID $accountId not found")

        // 1. Fetch system transactions for this account in the window (expanded by ±3 days)
        val windowStart = statement.periodStartMillis - (3L * 86400000L)
        val windowEnd = statement.periodEndMillis + (3L * 86400000L)
        val systemTxs = transactionDao.getTransactionsForAccountBetween(accountId, windowStart, windowEnd)
            .filter { it.status != "REVERSED" && it.status != "VOIDED" }
            .toMutableList()

        val usedSystemTxIds = mutableSetOf<Long>()
        val matches = mutableListOf<ReconciliationMatchResult>()

        var totalCredits = 0.0
        var totalDebits = 0.0

        // 2. Match each statement item
        for (item in statement.items) {
            if (item.isCredit) totalCredits += item.amount else totalDebits += item.amount

            var bestMatch: TransactionEntity? = null
            var matchType = "UNMATCHED"
            var confidence = 0.0f
            var notes = ""

            // Pass 1: Exact Match (Amount identical, date ±2 days, type alignment)
            for (cand in systemTxs) {
                if (usedSystemTxIds.contains(cand.id)) continue

                val candIsCredit = cand.type == TransactionType.INCOME || cand.type == TransactionType.REFUND
                if (candIsCredit != item.isCredit) continue

                if (abs(cand.amount - item.amount) < 0.01) {
                    val dateDiffDays = abs(cand.dateMillis - item.dateMillis) / 86400000.0
                    if (dateDiffDays <= 2.0) {
                        bestMatch = cand
                        matchType = "EXACT"
                        confidence = 1.0f - (dateDiffDays * 0.05).toFloat()
                        notes = "Exact amount match within ${dateDiffDays.roundToInt()} day(s)"
                        break
                    }
                }
            }

            // Pass 2: Fuzzy Match (Amount identical, date ±5 days, merchant substring)
            if (bestMatch == null) {
                val cleanDesc = item.description.lowercase(Locale.ROOT)
                for (cand in systemTxs) {
                    if (usedSystemTxIds.contains(cand.id)) continue

                    val candIsCredit = cand.type == TransactionType.INCOME || cand.type == TransactionType.REFUND
                    if (candIsCredit != item.isCredit) continue

                    if (abs(cand.amount - item.amount) < 0.01) {
                        val dateDiffDays = abs(cand.dateMillis - item.dateMillis) / 86400000.0
                        if (dateDiffDays <= 5.0) {
                            val candDesc = (cand.merchant + " " + cand.notes).lowercase(Locale.ROOT)
                            val hasKeywordMatch = candDesc.split(" ").any { it.length > 3 && cleanDesc.contains(it) }
                            bestMatch = cand
                            matchType = "FUZZY"
                            confidence = if (hasKeywordMatch) 0.85f else 0.70f
                            notes = "Fuzzy match: Date offset of ${dateDiffDays.roundToInt()} days"
                            break
                        }
                    }
                }
            }

            if (bestMatch != null) {
                usedSystemTxIds.add(bestMatch.id)
                matches.add(
                    ReconciliationMatchResult(
                        statementItem = item,
                        matchedTransaction = bestMatch,
                        matchType = matchType,
                        confidence = confidence,
                        discrepancyAmount = 0.0,
                        matchNotes = notes
                    )
                )
            } else {
                matches.add(
                    ReconciliationMatchResult(
                        statementItem = item,
                        matchedTransaction = null,
                        matchType = "UNMATCHED",
                        confidence = 0.0f,
                        discrepancyAmount = item.amount,
                        matchNotes = "Missing in system records: Transaction appears on bank statement but not in ARTHIKA"
                    )
                )
            }
        }

        // 3. Identify unmatched system entries
        val unmatchedSystemTxs = systemTxs.filter { !usedSystemTxIds.contains(it.id) }

        // 4. Calculate Balance Discrepancy
        val calculatedEndingBalance = statement.openingBalance + totalCredits - totalDebits
        val balanceDiscrepancy = (statement.closingBalance - calculatedEndingBalance * 100.0).roundToInt() / 100.0
        val isBalanced = abs(balanceDiscrepancy) < 0.01 && matches.none { it.matchType == "UNMATCHED" }

        val matchedCount = matches.count { it.matchedTransaction != null }
        val unmatchedStatementCount = matches.count { it.matchedTransaction == null }
        val unmatchedSystemCount = unmatchedSystemTxs.size

        // 5. Persist Statement and Reconciliation Records in Room Database
        val statementRecord = ImportedStatementEntity(
            accountId = accountId,
            fileName = statement.fileName,
            fileFormat = statement.fileFormat,
            statementPeriodStart = statement.periodStartMillis,
            statementPeriodEnd = statement.periodEndMillis,
            openingBalance = statement.openingBalance,
            closingBalance = statement.closingBalance,
            totalCredits = (totalCredits * 100.0).roundToInt() / 100.0,
            totalDebits = (totalDebits * 100.0).roundToInt() / 100.0,
            transactionCount = statement.items.size,
            status = if (isBalanced) "RECONCILED" else "PARTIALLY_RECONCILED"
        )
        val statementId = statementDao.insertStatement(statementRecord)

        val reconciliationRecord = ReconciliationRecordEntity(
            accountId = accountId,
            statementId = statementId,
            statementEndingBalance = statement.closingBalance,
            calculatedEndingBalance = (calculatedEndingBalance * 100.0).roundToInt() / 100.0,
            discrepancy = balanceDiscrepancy,
            status = if (isBalanced) "BALANCED" else "DISCREPANCY",
            matchedCount = matchedCount,
            unmatchedCount = unmatchedStatementCount + unmatchedSystemCount,
            notes = "Reconciliation for ${account.name} against ${statement.fileName}"
        )
        val reconId = reconciliationDao.insertRecord(reconciliationRecord)

        val itemEntities = matches.map { match ->
            ReconciliationItemEntity(
                reconciliationId = reconId,
                transactionId = match.matchedTransaction?.id,
                statementDateMillis = match.statementItem.dateMillis,
                statementDescription = match.statementItem.description,
                statementAmount = match.statementItem.amount,
                isCredit = match.statementItem.isCredit,
                matchType = match.matchType,
                confidence = match.confidence,
                discrepancyAmount = match.discrepancyAmount,
                notes = match.matchNotes
            )
        }
        reconciliationDao.insertItems(itemEntities)

        // 6. Update system transactions status if requested
        if (autoMarkReconciled && matchedCount > 0) {
            val matchedIds = matches.mapNotNull { it.matchedTransaction?.id }
            transactionDao.markAsReconciled(matchedIds)
        }

        // 7. Audit log
        auditLogDao.insertLog(
            AuditLogEntity(
                userId = account.userId,
                entityType = "RECONCILIATION",
                entityId = reconId,
                action = "RECONCILE",
                details = "Reconciled ${account.name}: $matchedCount matched, $unmatchedStatementCount statement missing, $unmatchedSystemCount system missing. Discrepancy: $$balanceDiscrepancy"
            )
        )

        FullReconciliationSummary(
            reconciliationId = reconId,
            accountId = accountId,
            statementFileName = statement.fileName,
            statementOpeningBalance = statement.openingBalance,
            statementClosingBalance = statement.closingBalance,
            calculatedEndingBalance = (calculatedEndingBalance * 100.0).roundToInt() / 100.0,
            systemCurrentBalance = account.currentBalance,
            balanceDiscrepancy = balanceDiscrepancy,
            isBalanced = isBalanced,
            totalCredits = (totalCredits * 100.0).roundToInt() / 100.0,
            totalDebits = (totalDebits * 100.0).roundToInt() / 100.0,
            matchedCount = matchedCount,
            unmatchedStatementCount = unmatchedStatementCount,
            unmatchedSystemCount = unmatchedSystemCount,
            matches = matches,
            unmatchedSystemTransactions = unmatchedSystemTxs
        )
    }
}
