package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.TransactionEntity
import com.example.data.model.TransactionType
import com.example.ui.FinanceViewModel
import com.example.ui.components.formatCurrency
import com.example.ui.theme.ArthikaBackground
import com.example.ui.theme.ArthikaBorder
import com.example.ui.theme.ArthikaCard
import com.example.ui.theme.ArthikaDarkGreen
import com.example.ui.theme.ArthikaExpense
import com.example.ui.theme.ArthikaIncome
import com.example.ui.theme.ArthikaPrimary
import com.example.ui.theme.ArthikaSoftGreen
import com.example.ui.theme.ArthikaSurfaceSubtle
import com.example.ui.theme.ArthikaTextPrimary
import com.example.ui.theme.ArthikaTextSecondary
import java.util.Calendar

@Composable
fun ReportsScreen(
    viewModel: FinanceViewModel,
    modifier: Modifier = Modifier
) {
    val allTransactions by viewModel.allTransactions.collectAsState()
    val pdfStatus by viewModel.pdfFeedbackMessage.collectAsState()

    var selectedPeriod by remember { mutableStateOf("Monthly") }

    val filteredTxs = remember(allTransactions, selectedPeriod) {
        when (selectedPeriod) {
            "Weekly" -> {
                val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -7) }
                val weekStart = cal.timeInMillis
                allTransactions.filter { it.dateMillis >= weekStart }
            }
            "All Time" -> allTransactions
            else -> {
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
                allTransactions.filter { it.dateMillis in startOfMonth until endOfMonth }
            }
        }
    }

    val totalIncome = filteredTxs.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
    val totalExpenses = filteredTxs.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
    val netSavings = totalIncome - totalExpenses
    val savingsRate = if (totalIncome > 0) ((netSavings / totalIncome) * 100).toInt().coerceAtLeast(0) else 0

    val categoryBreakdown = remember(filteredTxs) {
        filteredTxs.filter { it.type == TransactionType.EXPENSE }
            .groupBy { it.category }
            .mapValues { entry -> entry.value.sumOf { it.amount } }
            .toList()
            .sortedByDescending { it.second }
    }

    val categoryPalette = listOf(
        Color(0xFF56846B), // ARTHIKA Primary Sage
        Color(0xFF355C4B), // Dark Green
        Color(0xFF7CAE96), // Mint Sage
        Color(0xFFD4924A), // Muted Amber
        Color(0xFFD98282), // Soft Coral
        Color(0xFF6B8B9E), // Slate Blue
        Color(0xFFA1AEA6)  // Muted Gray-Green
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(ArthikaBackground)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Reports & Analytics",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 24.sp,
                            color = ArthikaTextPrimary
                        )
                    )
                    Text(
                        text = "Financial performance statement",
                        style = MaterialTheme.typography.bodySmall.copy(color = ArthikaTextSecondary)
                    )
                }

                Surface(
                    onClick = {
                        val scope = when (selectedPeriod) {
                            "Weekly" -> "Weekly Statement"
                            "All Time" -> "All Time Ledger"
                            else -> "Monthly Statement"
                        }
                        viewModel.generatePdfReport(scope)
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = ArthikaPrimary,
                    modifier = Modifier.testTag("export_pdf_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = "PDF",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Export PDF",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                        )
                    }
                }
            }
        }

        // Period Selector Tabs
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Monthly", "Weekly", "All Time").forEach { period ->
                    FilterChip(
                        selected = selectedPeriod == period,
                        onClick = { selectedPeriod = period },
                        label = { Text(period) },
                        shape = RoundedCornerShape(12.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ArthikaPrimary,
                            selectedLabelColor = Color.White,
                            containerColor = ArthikaCard,
                            labelColor = ArthikaTextPrimary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = ArthikaBorder,
                            selectedBorderColor = ArthikaPrimary,
                            enabled = true,
                            selected = selectedPeriod == period
                        )
                    )
                }
            }
        }

        // PDF feedback banner
        if (pdfStatus != null) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = ArthikaSoftGreen,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = null,
                            tint = ArthikaDarkGreen,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = pdfStatus ?: "",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = ArthikaDarkGreen,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }
            }
        }

        // Financial Summary KPI Card
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = ArthikaCard,
                border = androidx.compose.foundation.BorderStroke(1.dp, ArthikaBorder)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "$selectedPeriod Performance",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp,
                            color = ArthikaTextPrimary
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Total Income",
                                style = MaterialTheme.typography.bodySmall.copy(color = ArthikaTextSecondary)
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = formatCurrency(totalIncome),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = ArthikaIncome,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 17.sp
                                )
                            )
                        }

                        Column {
                            Text(
                                text = "Total Expenses",
                                style = MaterialTheme.typography.bodySmall.copy(color = ArthikaTextSecondary)
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = formatCurrency(totalExpenses),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = ArthikaExpense,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 17.sp
                                )
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Net Savings",
                                style = MaterialTheme.typography.bodySmall.copy(color = ArthikaTextSecondary)
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = formatCurrency(netSavings),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = ArthikaDarkGreen,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 17.sp
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Savings rate
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Savings Ratio",
                            style = MaterialTheme.typography.bodySmall.copy(color = ArthikaTextSecondary)
                        )
                        Text(
                            text = "$savingsRate% of income retained",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = ArthikaIncome,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    LinearProgressIndicator(
                        progress = { (savingsRate / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = ArthikaIncome,
                        trackColor = ArthikaSurfaceSubtle
                    )
                }
            }
        }

        // Category Breakdown Card with Donut Chart
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = ArthikaCard,
                border = androidx.compose.foundation.BorderStroke(1.dp, ArthikaBorder)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Spending by Category",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp,
                            color = ArthikaTextPrimary
                        )
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    if (categoryBreakdown.isEmpty()) {
                        Text(
                            text = "No expenses recorded in this period.",
                            style = MaterialTheme.typography.bodySmall.copy(color = ArthikaTextSecondary)
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Donut Chart
                            Box(
                                modifier = Modifier.size(105.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Canvas(modifier = Modifier.size(86.dp)) {
                                    var startAngle = -90f
                                    val strokeWidth = 16f
                                    val total = totalExpenses.toFloat().coerceAtLeast(1f)

                                    categoryBreakdown.forEachIndexed { index, (_, amount) ->
                                        val sweep = (amount.toFloat() / total) * 360f
                                        val color = categoryPalette[index % categoryPalette.size]

                                        drawArc(
                                            color = color,
                                            startAngle = startAngle,
                                            sweepAngle = sweep,
                                            useCenter = false,
                                            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                                        )
                                        startAngle += sweep
                                    }
                                }

                                Text(
                                    text = "${categoryBreakdown.size}\nAreas",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = ArthikaTextSecondary,
                                        fontWeight = FontWeight.Medium,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        fontSize = 10.sp
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.width(18.dp))

                            // Legend
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                categoryBreakdown.take(4).forEachIndexed { index, (cat, amount) ->
                                    val color = categoryPalette[index % categoryPalette.size]
                                    val pct = if (totalExpenses > 0) (amount / totalExpenses * 100).toInt() else 0

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(7.dp)
                                                    .clip(CircleShape)
                                                    .background(color)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = cat,
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    color = ArthikaTextPrimary,
                                                    fontSize = 12.sp
                                                )
                                            )
                                        }

                                        Text(
                                            text = "$pct%",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = ArthikaTextSecondary,
                                                fontWeight = FontWeight.Medium
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Category Linear Bars
                        categoryBreakdown.forEachIndexed { index, (cat, amount) ->
                            val color = categoryPalette[index % categoryPalette.size]
                            val pct = if (totalExpenses > 0) (amount / totalExpenses * 100).toInt() else 0

                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = cat,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = ArthikaTextPrimary,
                                            fontSize = 12.5.sp
                                        )
                                    )
                                    Text(
                                        text = "${formatCurrency(amount)} ($pct%)",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = ArthikaTextSecondary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(5.dp)
                                        .clip(RoundedCornerShape(2.5.dp))
                                        .background(ArthikaSurfaceSubtle)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth((pct / 100f).coerceIn(0.02f, 1f))
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(2.5.dp))
                                            .background(color)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // PDF Report Action Banner
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = ArthikaCard,
                border = androidx.compose.foundation.BorderStroke(1.dp, ArthikaBorder)
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(ArthikaSoftGreen),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = "PDF Report",
                            tint = ArthikaDarkGreen,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Professional PDF Export",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Medium,
                                fontSize = 15.sp,
                                color = ArthikaTextPrimary
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Detailed ledger with charts & executive summary",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = ArthikaTextSecondary,
                                fontSize = 11.5.sp
                            )
                        )
                    }

                    Button(
                        onClick = {
                            val scope = when (selectedPeriod) {
                                "Weekly" -> "Weekly Statement"
                                "All Time" -> "All Time Ledger"
                                else -> "Monthly Statement"
                            }
                            viewModel.generatePdfReport(scope)
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ArthikaPrimary),
                        modifier = Modifier.testTag("download_pdf_report_button")
                    ) {
                        Text("Export", color = Color.White)
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(90.dp))
        }
    }
}
