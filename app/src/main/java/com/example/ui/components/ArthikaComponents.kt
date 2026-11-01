package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalAtm
import androidx.compose.material.icons.filled.LocalMall
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Work
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.entity.TransactionEntity
import com.example.data.model.TransactionType
import com.example.ui.theme.ArthikaBackground
import com.example.ui.theme.ArthikaBorder
import com.example.ui.theme.ArthikaCard
import com.example.ui.theme.ArthikaDarkGreen
import com.example.ui.theme.ArthikaDivider
import com.example.ui.theme.ArthikaExpense
import com.example.ui.theme.ArthikaExpenseSoft
import com.example.ui.theme.ArthikaIncome
import com.example.ui.theme.ArthikaIncomeSoft
import com.example.ui.theme.ArthikaPrimary
import com.example.ui.theme.ArthikaSoftGreen
import com.example.ui.theme.ArthikaSurfaceSubtle
import com.example.ui.theme.ArthikaTextPrimary
import com.example.ui.theme.ArthikaTextSecondary
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun getCategoryIcon(category: String): ImageVector {
    return when (category.lowercase(Locale.ROOT)) {
        "food" -> Icons.Default.Fastfood
        "transport" -> Icons.Default.DirectionsCar
        "shopping" -> Icons.Default.LocalMall
        "education" -> Icons.Default.School
        "entertainment" -> Icons.Default.Movie
        "bills" -> Icons.Default.Receipt
        "health" -> Icons.Default.FitnessCenter
        "salary" -> Icons.Default.AccountBalance
        "freelance" -> Icons.Default.Work
        "gift" -> Icons.Default.CardGiftcard
        "pocket money" -> Icons.Default.LocalAtm
        else -> Icons.Outlined.Category
    }
}

object FastFormatters {
    private val currencyFormatThreadLocal = ThreadLocal.withInitial {
        NumberFormat.getNumberInstance(Locale.US).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
    }

    private val dateFormatThreadLocal = ThreadLocal.withInitial {
        SimpleDateFormat("MMM dd", Locale.US)
    }

    fun formatCurrency(amount: Double, symbol: String = "$"): String {
        return "$symbol${currencyFormatThreadLocal.get()!!.format(amount)}"
    }

    fun formatDate(millis: Long): String {
        return dateFormatThreadLocal.get()!!.format(Date(millis))
    }
}

fun formatCurrency(amount: Double, symbol: String = "$"): String {
    return FastFormatters.formatCurrency(amount, symbol)
}

fun formatTransactionDate(millis: Long): String {
    return FastFormatters.formatDate(millis)
}

/**
 * Top App Bar with the official ARTHIKA logo asset, brand wordmark, personalized greeting,
 * and profile/settings button.
 */
@Composable
fun ArthikaTopBar(
    userName: String,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Logo & Brand Wordmark
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .border(1.dp, ArthikaBorder, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_arthika_logo),
                    contentDescription = "ARTHIKA Logo",
                    modifier = Modifier.size(34.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = "ARTHIKA",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp,
                        letterSpacing = 0.5.sp,
                        color = ArthikaDarkGreen
                    )
                )
                Text(
                    text = "Hello, $userName",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = ArthikaTextSecondary,
                        fontSize = 12.sp
                    )
                )
            }
        }

        // Profile / Settings Button
        Surface(
            onClick = onOpenSettings,
            shape = CircleShape,
            color = ArthikaCard,
            border = androidx.compose.foundation.BorderStroke(1.dp, ArthikaBorder),
            modifier = Modifier
                .size(40.dp)
                .testTag("settings_button")
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = ArthikaTextSecondary,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
    }
}

/**
 * Main Balance Section: Apple Wallet / Linear style.
 * Crisp, confident typography with subtle monthly context and trend pill.
 */
@Composable
fun ArthikaBalanceCard(
    totalBalance: Double,
    monthlyIncome: Double,
    monthlyExpenses: Double,
    modifier: Modifier = Modifier
) {
    val netMonthly = monthlyIncome - monthlyExpenses
    val isNetPositive = netMonthly >= 0

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("total_balance_card"),
        shape = RoundedCornerShape(22.dp),
        color = ArthikaCard,
        border = androidx.compose.foundation.BorderStroke(1.dp, ArthikaBorder),
        shadowElevation = 0.5.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 22.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TOTAL BALANCE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.2.sp,
                        color = ArthikaTextSecondary
                    )
                )

                // Subtle Trend Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isNetPositive) ArthikaIncomeSoft else ArthikaExpenseSoft)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isNetPositive) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                            contentDescription = null,
                            tint = if (isNetPositive) ArthikaIncome else ArthikaExpense,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "${if (isNetPositive) "+" else "-"}${formatCurrency(Math.abs(netMonthly))} net",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (isNetPositive) ArthikaIncome else ArthikaExpense,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Large, readable financial figure
            Text(
                text = formatCurrency(totalBalance),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 36.sp,
                    letterSpacing = (-0.5).sp,
                    color = ArthikaTextPrimary
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Context line
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(ArthikaPrimary)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "All accounts consolidated • Real-time database",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = ArthikaTextSecondary,
                        fontSize = 12.sp
                    )
                )
            }
        }
    }
}

/**
 * Quick Action Row:
 * "Use simple icons and soft backgrounds instead of large outlined boxes."
 */
@Composable
fun ArthikaQuickActions(
    onAddExpense: () -> Unit,
    onAddIncome: () -> Unit,
    onScanReceipt: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Quick Action 1: Add Expense
        QuickActionButton(
            label = "Expense",
            icon = Icons.Default.Remove,
            iconTint = ArthikaExpense,
            background = ArthikaExpenseSoft,
            onClick = onAddExpense,
            modifier = Modifier.weight(1f).testTag("quick_add_expense")
        )

        // Quick Action 2: Add Income
        QuickActionButton(
            label = "Income",
            icon = Icons.Default.Add,
            iconTint = ArthikaIncome,
            background = ArthikaIncomeSoft,
            onClick = onAddIncome,
            modifier = Modifier.weight(1f).testTag("quick_add_income")
        )

        // Quick Action 3: Scan Receipt
        QuickActionButton(
            label = "Scan Bill",
            icon = Icons.Default.DocumentScanner,
            iconTint = ArthikaPrimary,
            background = ArthikaSoftGreen,
            onClick = onScanReceipt,
            modifier = Modifier.weight(1.1f).testTag("quick_scan_receipt")
        )
    }
}

@Composable
private fun QuickActionButton(
    label: String,
    icon: ImageVector,
    iconTint: Color,
    background: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = ArthikaCard,
        border = androidx.compose.foundation.BorderStroke(1.dp, ArthikaBorder),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(background),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = iconTint,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    color = ArthikaTextPrimary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp
                )
            )
        }
    }
}

/**
 * Financial Overview:
 * Monthly Income, Monthly Expenses, Remaining Budget in a clean, unified card.
 */
@Composable
fun ArthikaFinancialOverview(
    monthlyIncome: Double,
    monthlyExpenses: Double,
    remainingBudget: Double,
    monthlyLimit: Double,
    utilizationPct: Int,
    isApproachingLimit: Boolean,
    isExceeded: Boolean,
    onAdjustBudget: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = ArthikaCard,
        border = androidx.compose.foundation.BorderStroke(1.dp, ArthikaBorder)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Financial Overview",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        color = ArthikaTextPrimary
                    )
                )

                Text(
                    text = "Adjust Limit",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = ArthikaPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    ),
                    modifier = Modifier
                        .clickable(onClick = onAdjustBudget)
                        .padding(4.dp)
                        .testTag("adjust_budget_button")
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3-Column Financial Metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Income
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Income",
                        style = MaterialTheme.typography.bodySmall.copy(color = ArthikaTextSecondary)
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = formatCurrency(monthlyIncome),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = ArthikaIncome,
                            fontSize = 16.sp
                        )
                    )
                }

                // Expenses
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Expenses",
                        style = MaterialTheme.typography.bodySmall.copy(color = ArthikaTextSecondary)
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = formatCurrency(monthlyExpenses),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = ArthikaExpense,
                            fontSize = 16.sp
                        )
                    )
                }

                // Remaining Budget
                Column(modifier = Modifier.weight(1.1f), horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Budget Left",
                        style = MaterialTheme.typography.bodySmall.copy(color = ArthikaTextSecondary)
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = formatCurrency(remainingBudget),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = if (isExceeded) ArthikaExpense else ArthikaPrimary,
                            fontSize = 16.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Budget Utilization Progress Line
            val progressFraction = (monthlyExpenses / monthlyLimit.coerceAtLeast(1.0)).toFloat().coerceIn(0f, 1f)
            val barColor = when {
                isExceeded -> ArthikaExpense
                isApproachingLimit -> Color(0xFFD4924A)
                else -> ArthikaPrimary
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(ArthikaSurfaceSubtle)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progressFraction)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(barColor)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "$utilizationPct% of ${formatCurrency(monthlyLimit)} limit",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.5.sp,
                        color = if (isApproachingLimit || isExceeded) barColor else ArthikaTextSecondary,
                        fontWeight = if (isApproachingLimit || isExceeded) FontWeight.Medium else FontWeight.Normal
                    )
                )

                if (isApproachingLimit || isExceeded) {
                    Text(
                        text = if (isExceeded) "Exceeded" else "Near Limit",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = barColor,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp
                        )
                    )
                }
            }
        }
    }
}

/**
 * Spending Overview: Minimal, readable weekly spending chart with consistent palette.
 */
@Composable
fun ArthikaSpendingChart(
    dailyData: List<Pair<String, Double>>,
    modifier: Modifier = Modifier
) {
    val maxSpent = dailyData.maxOfOrNull { it.second }?.coerceAtLeast(1.0) ?: 1.0
    val totalWeek = dailyData.sumOf { it.second }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = ArthikaCard,
        border = androidx.compose.foundation.BorderStroke(1.dp, ArthikaBorder)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Weekly Spending",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp,
                            color = ArthikaTextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Last 7 days pattern",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = ArthikaTextSecondary,
                            fontSize = 12.sp
                        )
                    )
                }

                Text(
                    text = formatCurrency(totalWeek),
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = ArthikaDarkGreen,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Bars Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                dailyData.forEachIndexed { index, (dayName, spent) ->
                    val isToday = index == dailyData.size - 1
                    val fraction = (spent / maxSpent).toFloat().coerceIn(0.06f, 1f)

                    val animatedHeight by animateFloatAsState(
                        targetValue = fraction,
                        animationSpec = tween(500),
                        label = "barHeight"
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(10.dp)
                                    .fillMaxHeight(animatedHeight)
                                    .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp))
                                    .background(
                                        when {
                                            isToday && spent > 0 -> ArthikaDarkGreen
                                            spent > 0 -> ArthikaPrimary
                                            else -> ArthikaSurfaceSubtle
                                        }
                                    )
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = dayName,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                color = if (isToday) ArthikaDarkGreen else ArthikaTextSecondary,
                                fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal
                            )
                        )
                    }
                }
            }
        }
    }
}

/**
 * Recent Transaction Item:
 * Modern Linear-style row with merchant name, category, date/time, and amount.
 */
@Composable
fun ArthikaTransactionRow(
    transaction: TransactionEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isIncome = transaction.type == TransactionType.INCOME
    val amountColor = if (isIncome) ArthikaIncome else ArthikaTextPrimary
    val dateFormatted = formatTransactionDate(transaction.dateMillis)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("transaction_item_${transaction.id}"),
        color = ArthikaCard,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, ArthikaBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (isIncome) ArthikaIncomeSoft else ArthikaSurfaceSubtle),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getCategoryIcon(transaction.category),
                    contentDescription = transaction.category,
                    tint = if (isIncome) ArthikaIncome else ArthikaDarkGreen,
                    modifier = Modifier.size(19.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Merchant / Notes / Category
            Column(modifier = Modifier.weight(1f)) {
                val title = transaction.merchant.ifBlank {
                    transaction.notes.ifBlank { transaction.category }
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.5.sp,
                        color = ArthikaTextPrimary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = transaction.category,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = ArthikaTextSecondary,
                            fontSize = 12.sp
                        )
                    )
                    Text(
                        text = " • ",
                        style = MaterialTheme.typography.bodySmall.copy(color = ArthikaBorder)
                    )
                    Text(
                        text = "$dateFormatted, ${transaction.timeString}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = ArthikaTextSecondary,
                            fontSize = 12.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Amount
            Column(horizontalAlignment = Alignment.End) {
                val prefix = if (isIncome) "+" else "-"
                Text(
                    text = "$prefix${formatCurrency(transaction.amount)}",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = amountColor,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = transaction.paymentMethod,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = ArthikaTextSecondary,
                        fontSize = 10.5.sp
                    )
                )
            }
        }
    }
}
