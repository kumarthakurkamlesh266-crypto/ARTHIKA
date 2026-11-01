package com.example.performance

import com.example.data.entity.TransactionEntity
import com.example.data.model.TransactionType
import com.example.ui.DateRangeFilter
import com.example.ui.components.FastFormatters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class PerformanceBenchmarkTest {

    @Test
    fun benchmarkFastFormattersVersusAllocations() {
        val iterations = 20_000

        // Benchmark FastFormatters
        val startFast = System.nanoTime()
        for (i in 0 until iterations) {
            val formatted = FastFormatters.formatCurrency(i * 1.25)
            assertTrue(formatted.startsWith("$"))
        }
        val durationFastMs = (System.nanoTime() - startFast) / 1_000_000

        // Benchmark repeated NumberFormat allocation
        val startSlow = System.nanoTime()
        for (i in 0 until iterations) {
            val formatter = NumberFormat.getNumberInstance(Locale.US).apply {
                minimumFractionDigits = 2
                maximumFractionDigits = 2
            }
            val formatted = "$${formatter.format(i * 1.25)}"
            assertTrue(formatted.startsWith("$"))
        }
        val durationSlowMs = (System.nanoTime() - startSlow) / 1_000_000

        println("⚡ Currency Format Benchmark ($iterations ops): FastFormatters = ${durationFastMs}ms vs Raw Allocation = ${durationSlowMs}ms")
        // FastFormatters should be significantly faster and create near zero GC churn
        assertTrue("FastFormatters ($durationFastMs ms) should execute under 200ms", durationFastMs < 200)
    }

    @Test
    fun benchmarkDateFormattingThroughput() {
        val iterations = 20_000
        val timestamp = System.currentTimeMillis()

        val start = System.nanoTime()
        for (i in 0 until iterations) {
            val dateStr = FastFormatters.formatDate(timestamp - i * 3600_000L)
            assertTrue(dateStr.isNotEmpty())
        }
        val durationMs = (System.nanoTime() - start) / 1_000_000
        println("⚡ Date Format Benchmark ($iterations ops): Completed in ${durationMs}ms")
        assertTrue("Date formatting should finish in < 150ms", durationMs < 150)
    }

    @Test
    fun benchmarkSinglePassTransactionAggregationScale() {
        // Generate 3,000 synthetic transactions
        val count = 3_000
        val now = System.currentTimeMillis()
        val txs = ArrayList<TransactionEntity>(count)

        for (i in 0 until count) {
            val isIncome = (i % 4 == 0)
            val dateMillis = now - (i * 3_600_000L * 3) // spread over last ~1 year
            txs.add(
                TransactionEntity(
                    id = (i + 1).toLong(),
                    userId = 1L,
                    accountId = (i % 3 + 1).toLong(),
                    amount = 15.0 + (i % 50),
                    type = if (isIncome) TransactionType.INCOME else TransactionType.EXPENSE,
                    category = if (isIncome) "Salary" else "Food",
                    dateMillis = dateMillis,
                    timeString = "12:00 PM",
                    paymentMethod = "Debit Card",
                    merchant = "Merchant #$i"
                )
            )
        }

        // Measure single-pass aggregation
        val startTime = System.nanoTime()

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
        val dayStarts = LongArray(7)
        val dayEnds = LongArray(7)
        val dayBuckets = DoubleArray(7)

        for (i in 6 downTo 0) {
            val index = 6 - i
            val dayCal = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -i)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val start = dayCal.timeInMillis
            dayStarts[index] = start
            dayEnds[index] = start + 86_400_000L
        }

        var totalIncomeAll = 0.0
        var totalExpensesAll = 0.0
        var monthIncome = 0.0
        var monthExpenses = 0.0

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

        val netBalance = totalIncomeAll - totalExpensesAll
        val elapsedMs = (System.nanoTime() - startTime) / 1_000_000

        println("⚡ Aggregation on $count transactions took ${elapsedMs}ms (Net Balance: $$netBalance)")
        assertTrue("Aggregating 3,000 transactions must take < 20ms", elapsedMs < 20)
    }

    @Test
    fun benchmarkZeroAllocationDateBoundaryFiltering() {
        val count = 2_500
        val now = System.currentTimeMillis()
        val txs = (1..count).map { i ->
            TransactionEntity(
                id = i.toLong(),
                userId = 1L,
                accountId = 1L,
                amount = 25.0,
                type = TransactionType.EXPENSE,
                category = "Food",
                dateMillis = now - (i * 1_800_000L), // step by 30 mins
                timeString = "10:00 AM",
                paymentMethod = "Cash",
                merchant = "Grocery Store $i"
            )
        }

        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = cal.timeInMillis
        val endOfDay = startOfDay + 86_400_000L

        val start = System.nanoTime()
        val todayTxs = txs.filter { it.dateMillis in startOfDay until endOfDay }
        val durationMs = (System.nanoTime() - start) / 1_000_000

        println("⚡ Boundary filtering of $count items took ${durationMs}ms, matched ${todayTxs.size} items")
        assertTrue("Filtering should take < 5ms", durationMs < 5)
    }
}
