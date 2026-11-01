package com.example.reports

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.example.data.entity.TransactionEntity
import com.example.data.model.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PdfReportGenerator(private val context: Context) {

    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale.US)
    private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US)

    suspend fun generatePdfReport(
        title: String,
        dateRangeLabel: String,
        transactions: List<TransactionEntity>
    ): File = withContext(Dispatchers.IO) {
        val pdfDoc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 standard 72dpi
        val page = pdfDoc.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val paint = Paint().apply { isAntiAlias = true }

        // Metrics calculations
        val totalIncome = transactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
        val totalExpenses = transactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
        val netSavings = totalIncome - totalExpenses

        val categoryMap = transactions.filter { it.type == TransactionType.EXPENSE }
            .groupBy { it.category }
            .mapValues { it.value.sumOf { tx -> tx.amount } }
            .toList()
            .sortedByDescending { it.second }

        // --- DRAWING THE ARTHIKA REPORT ---

        // 1. Top Decorative Brand Bar (ARTHIKA Primary Green #56846B)
        paint.color = Color.parseColor("#56846B")
        canvas.drawRect(0f, 0f, 595f, 6f, paint)

        // 2. Report Brand and Title
        paint.color = Color.parseColor("#355C4B")
        paint.textSize = 20f
        paint.isFakeBoldText = true
        canvas.drawText("ARTHIKA", 40f, 48f, paint)

        paint.textSize = 9.5f
        paint.isFakeBoldText = false
        paint.color = Color.parseColor("#858D87")
        canvas.drawText("PREMIUM FINANCIAL STATEMENT", 40f, 62f, paint)

        // Metadata on right
        val genDateStr = "Generated: ${dateFormat.format(Date())}"
        paint.textAlign = Paint.Align.RIGHT
        paint.textSize = 9f
        paint.color = Color.parseColor("#858D87")
        canvas.drawText(genDateStr, 555f, 48f, paint)
        canvas.drawText("Scope: $dateRangeLabel", 555f, 62f, paint)
        paint.textAlign = Paint.Align.LEFT

        // Divider
        paint.color = Color.parseColor("#E9EDE8")
        paint.strokeWidth = 1f
        canvas.drawLine(40f, 76f, 555f, 76f, paint)

        // 3. Document Title
        paint.color = Color.parseColor("#252B27")
        paint.textSize = 15f
        paint.isFakeBoldText = true
        canvas.drawText(title, 40f, 102f, paint)

        // 4. Executive Summary KPI Cards (3 Cards)
        val cardWidth = 162f
        val cardHeight = 62f
        val cardY = 118f

        // Card 1: Total Income
        drawSummaryCard(
            canvas, paint,
            x = 40f, y = cardY, width = cardWidth, height = cardHeight,
            label = "TOTAL INCOME",
            amount = currencyFormat.format(totalIncome),
            accentColor = Color.parseColor("#5B9A78")
        )

        // Card 2: Total Expenses
        drawSummaryCard(
            canvas, paint,
            x = 216f, y = cardY, width = cardWidth, height = cardHeight,
            label = "TOTAL EXPENSES",
            amount = currencyFormat.format(totalExpenses),
            accentColor = Color.parseColor("#D98282")
        )

        // Card 3: Net Balance
        drawSummaryCard(
            canvas, paint,
            x = 393f, y = cardY, width = cardWidth, height = cardHeight,
            label = "NET SAVINGS",
            amount = currencyFormat.format(netSavings),
            accentColor = Color.parseColor("#355C4B")
        )

        // 5. Category Breakdown Section
        var currentY = 208f
        paint.color = Color.parseColor("#252B27")
        paint.textSize = 12f
        paint.isFakeBoldText = true
        canvas.drawText("Expense Category Distribution", 40f, currentY, paint)

        currentY += 16f
        val displayCats = categoryMap.take(4)
        for ((cat, catTotal) in displayCats) {
            val pct = if (totalExpenses > 0) (catTotal / totalExpenses * 100).toInt() else 0
            paint.isFakeBoldText = false
            paint.textSize = 9.5f
            paint.color = Color.parseColor("#252B27")
            canvas.drawText(cat, 40f, currentY, paint)

            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText("${currencyFormat.format(catTotal)} ($pct%)", 555f, currentY, paint)
            paint.textAlign = Paint.Align.LEFT

            // Progress bar
            currentY += 6f
            paint.color = Color.parseColor("#E9EDE8")
            canvas.drawRoundRect(RectF(40f, currentY, 555f, currentY + 4f), 2f, 2f, paint)

            paint.color = Color.parseColor("#56846B")
            val barW = 515f * (pct / 100f)
            canvas.drawRoundRect(RectF(40f, currentY, 40f + barW, currentY + 4f), 2f, 2f, paint)

            currentY += 14f
        }

        // 6. Detailed Transaction Ledger
        currentY += 10f
        paint.color = Color.parseColor("#252B27")
        paint.textSize = 12f
        paint.isFakeBoldText = true
        canvas.drawText("Transaction Ledger (${transactions.size} records)", 40f, currentY, paint)

        currentY += 16f
        // Table Header
        paint.color = Color.parseColor("#F7F8F5")
        canvas.drawRect(40f, currentY - 12f, 555f, currentY + 8f, paint)

        paint.color = Color.parseColor("#858D87")
        paint.textSize = 8.5f
        paint.isFakeBoldText = true
        canvas.drawText("DATE & TIME", 46f, currentY, paint)
        canvas.drawText("MERCHANT / DETAILS", 140f, currentY, paint)
        canvas.drawText("CATEGORY", 320f, currentY, paint)
        canvas.drawText("METHOD", 420f, currentY, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("AMOUNT", 548f, currentY, paint)
        paint.textAlign = Paint.Align.LEFT

        currentY += 14f

        val rowsToDraw = transactions.take(18)
        paint.isFakeBoldText = false
        paint.textSize = 8.5f

        for ((index, tx) in rowsToDraw.withIndex()) {
            if (index % 2 == 1) {
                paint.color = Color.parseColor("#FAFBF9")
                canvas.drawRect(40f, currentY - 10f, 555f, currentY + 8f, paint)
            }

            val dateStr = "${dateFormat.format(Date(tx.dateMillis))} ${tx.timeString}"
            paint.color = Color.parseColor("#858D87")
            canvas.drawText(dateStr, 46f, currentY, paint)

            val desc = tx.merchant.ifBlank { tx.notes.ifBlank { tx.category } }
            paint.color = Color.parseColor("#252B27")
            val trimmedDesc = if (desc.length > 28) desc.take(26) + "..." else desc
            canvas.drawText(trimmedDesc, 140f, currentY, paint)

            paint.color = Color.parseColor("#858D87")
            canvas.drawText(tx.category, 320f, currentY, paint)
            canvas.drawText(tx.paymentMethod, 420f, currentY, paint)

            val isIncome = tx.type == TransactionType.INCOME
            paint.color = if (isIncome) Color.parseColor("#5B9A78") else Color.parseColor("#D98282")
            paint.isFakeBoldText = true
            paint.textAlign = Paint.Align.RIGHT
            val sign = if (isIncome) "+" else "-"
            canvas.drawText("$sign${currencyFormat.format(tx.amount)}", 548f, currentY, paint)
            paint.textAlign = Paint.Align.LEFT
            paint.isFakeBoldText = false

            currentY += 18f
        }

        // 7. Footer
        paint.color = Color.parseColor("#E9EDE8")
        canvas.drawLine(40f, 800f, 555f, 800f, paint)

        paint.color = Color.parseColor("#858D87")
        paint.textSize = 8f
        canvas.drawText("ARTHIKA Personal Finance • Confidential Financial Statement", 40f, 816f, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("Page 1 of 1", 555f, 816f, paint)

        pdfDoc.finishPage(page)

        // Save PDF
        val reportsDir = File(context.cacheDir, "reports").apply { mkdirs() }
        val fileName = "Arthika_Statement_${System.currentTimeMillis()}.pdf"
        val pdfFile = File(reportsDir, fileName)
        val fos = FileOutputStream(pdfFile)
        pdfDoc.writeTo(fos)
        fos.flush()
        fos.close()
        pdfDoc.close()

        pdfFile
    }

    private fun drawSummaryCard(
        canvas: Canvas,
        paint: Paint,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        label: String,
        amount: String,
        accentColor: Int
    ) {
        val rect = RectF(x, y, x + width, y + height)

        // Background
        paint.color = Color.parseColor("#FFFFFF")
        canvas.drawRoundRect(rect, 8f, 8f, paint)

        // Border
        paint.color = Color.parseColor("#E9EDE8")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRoundRect(rect, 8f, 8f, paint)
        paint.style = Paint.Style.FILL

        // Left accent indicator line
        paint.color = accentColor
        canvas.drawRoundRect(RectF(x, y + 8f, x + 3.5f, y + height - 8f), 2f, 2f, paint)

        // Label
        paint.color = Color.parseColor("#858D87")
        paint.textSize = 8f
        paint.isFakeBoldText = true
        canvas.drawText(label, x + 12f, y + 22f, paint)

        // Amount
        paint.color = Color.parseColor("#252B27")
        paint.textSize = 14f
        paint.isFakeBoldText = true
        canvas.drawText(amount, x + 12f, y + 46f, paint)
    }

    fun sharePdf(file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "ARTHIKA Financial Statement")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Share Financial Statement").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
