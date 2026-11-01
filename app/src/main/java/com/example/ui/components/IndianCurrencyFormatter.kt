package com.example.ui.components

import java.util.Locale

/**
 * High-performance currency formatter adhering to the Indian Numbering System
 * (Lakhs and Crores: e.g. ₹1,23,456.78, ₹10,00,000.00).
 */
object IndianCurrencyFormatter {

    /**
     * Formats an amount using Indian numbering grouping:
     * - Last 3 digits grouped together
     * - Preceding digits grouped in pairs of 2
     * - 2 decimal places
     */
    fun format(amount: Double, symbol: String = "₹"): String {
        val isNegative = amount < 0
        val absAmount = Math.abs(amount)

        // Precision handling: round to 2 decimal places to avoid IEEE 754 precision artifacts
        val totalPaise = Math.round(absAmount * 100.0)
        val rupees = totalPaise / 100
        val paise = totalPaise % 100
        val paiseStr = String.format(Locale.US, "%02d", paise)

        val rupeesStr = rupees.toString()
        val formattedRupees = if (rupeesStr.length <= 3) {
            rupeesStr
        } else {
            val lastThree = rupeesStr.substring(rupeesStr.length - 3)
            val remaining = rupeesStr.substring(0, rupeesStr.length - 3)
            val builder = StringBuilder()
            var count = 0
            for (i in remaining.length - 1 downTo 0) {
                builder.append(remaining[i])
                count++
                if (count == 2 && i > 0) {
                    builder.append(',')
                    count = 0
                }
            }
            builder.reverse().append(',').append(lastThree).toString()
        }

        val result = "$symbol$formattedRupees.$paiseStr"
        return if (isNegative) "-$result" else result
    }

    /**
     * Formats without decimals if zero, or with standard decimals.
     */
    fun formatCompact(amount: Double, symbol: String = "₹"): String {
        val abs = Math.abs(amount)
        return when {
            abs >= 10_000_000 -> {
                val crores = amount / 10_000_000.0
                String.format(Locale.US, "%s%.2f Cr", symbol, crores)
            }
            abs >= 100_000 -> {
                val lakhs = amount / 100_000.0
                String.format(Locale.US, "%s%.2f L", symbol, lakhs)
            }
            else -> format(amount, symbol)
        }
    }
}
