package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.TransactionType
import com.example.ui.FinanceViewModel
import com.example.ui.components.ArthikaBalanceCard
import com.example.ui.components.ArthikaFinancialOverview
import com.example.ui.components.ArthikaQuickActions
import com.example.ui.components.ArthikaSpendingChart
import com.example.ui.components.ArthikaTopBar
import com.example.ui.components.ArthikaTransactionRow
import com.example.ui.theme.ArthikaBackground
import com.example.ui.theme.ArthikaBorder
import com.example.ui.theme.ArthikaCard
import com.example.ui.theme.ArthikaDarkGreen
import com.example.ui.theme.ArthikaPrimary
import com.example.ui.theme.ArthikaTextPrimary
import com.example.ui.theme.ArthikaTextSecondary

@Composable
fun HomeScreen(
    viewModel: FinanceViewModel,
    onNavigateToTransactions: () -> Unit,
    onOpenReceiptScanner: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val metrics by viewModel.dashboardMetrics.collectAsState()
    val budget by viewModel.budget.collectAsState()
    val recentTransactions by viewModel.recentTransactions.collectAsState()
    val accounts by viewModel.allAccounts.collectAsState()
    var isAccountingHubOpen by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(ArthikaBackground)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Top Section: ARTHIKA Logo, Wordmark, Greeting & Settings
        item {
            ArthikaTopBar(
                userName = budget.userName,
                onOpenSettings = onOpenSettings
            )
        }

        // 2. Main Balance Section (Apple Wallet / Linear style)
        item {
            ArthikaBalanceCard(
                totalBalance = metrics.totalBalance,
                monthlyIncome = metrics.monthlyIncome,
                monthlyExpenses = metrics.monthlyExpenses
            )
        }

        // 3. Quick Actions: Expense, Income, Scan Receipt
        item {
            ArthikaQuickActions(
                onAddExpense = { viewModel.openAddTransactionSheet(TransactionType.EXPENSE) },
                onAddIncome = { viewModel.openAddTransactionSheet(TransactionType.INCOME) },
                onScanReceipt = onOpenReceiptScanner
            )
        }

        // 3b. Accounting Hub: Transfers, Accounts & Bank Reconciliation
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isAccountingHubOpen = true }
                    .testTag("open_accounting_hub"),
                shape = RoundedCornerShape(16.dp),
                color = com.example.ui.theme.ArthikaSoftGreen.copy(alpha = 0.6f),
                border = androidx.compose.foundation.BorderStroke(1.dp, ArthikaBorder)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(ArthikaPrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.material3.Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.AccountBalance,
                                contentDescription = null,
                                tint = androidx.compose.ui.graphics.Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Accounting Hub & Ledger",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = ArthikaTextPrimary
                            )
                            Text(
                                text = "${accounts.size} Accounts • Transfers • Bank Statement Reconcile",
                                fontSize = 11.sp,
                                color = ArthikaTextSecondary
                            )
                        }
                    }
                    Text(
                        text = "Manage →",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ArthikaPrimary
                    )
                }
            }
        }

        // 4. Financial Overview: Monthly Income, Monthly Expenses, Remaining Budget
        item {
            ArthikaFinancialOverview(
                monthlyIncome = metrics.monthlyIncome,
                monthlyExpenses = metrics.monthlyExpenses,
                remainingBudget = metrics.remainingBudget,
                monthlyLimit = budget.monthlyLimit,
                utilizationPct = metrics.budgetUtilizationPct,
                isApproachingLimit = metrics.isBudgetApproachingLimit,
                isExceeded = metrics.isBudgetExceeded,
                onAdjustBudget = onOpenSettings
            )
        }

        // 5. Spending Overview: Minimal weekly spending activity chart
        item {
            ArthikaSpendingChart(dailyData = metrics.dailyTrend)
        }

        // 6. Recent Transactions Section Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Transactions",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp,
                        color = ArthikaTextPrimary
                    )
                )

                Text(
                    text = "View All",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = ArthikaPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    ),
                    modifier = Modifier
                        .clickable(onClick = onNavigateToTransactions)
                        .padding(4.dp)
                        .testTag("view_all_transactions")
                )
            }
        }

        // Recent Transactions List
        if (recentTransactions.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = ArthikaCard,
                    border = androidx.compose.foundation.BorderStroke(1.dp, ArthikaBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No transactions yet",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Medium,
                                color = ArthikaTextPrimary
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Add an expense or scan a receipt to begin tracking",
                            style = MaterialTheme.typography.bodySmall.copy(color = ArthikaTextSecondary)
                        )
                    }
                }
            }
        } else {
            items(recentTransactions.take(5), key = { it.id }, contentType = { it.type }) { tx ->
                ArthikaTransactionRow(
                    transaction = tx,
                    onClick = { viewModel.openEditTransaction(tx) }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(84.dp))
        }
    }

    if (isAccountingHubOpen) {
        com.example.ui.dialogs.AccountingHubSheet(
            isOpen = true,
            accounts = accounts,
            onDismiss = { isAccountingHubOpen = false },
            onExecuteTransfer = { fromId, toId, amount, fee, notes, cb ->
                viewModel.transferFunds(fromId, toId, amount, fee, notes, cb)
            },
            onReconcileStatement = { accountId, content, fileName, cb ->
                viewModel.importAndReconcile(accountId, content, fileName, cb)
            }
        )
    }
}

