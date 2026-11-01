package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.TransactionEntity
import com.example.data.model.CategoryConstants
import com.example.data.model.TransactionType
import com.example.ui.DateRangeFilter
import com.example.ui.FinanceViewModel
import com.example.ui.SortOrder
import com.example.ui.components.ArthikaTransactionRow
import com.example.ui.components.formatCurrency
import com.example.ui.theme.ArthikaBackground
import com.example.ui.theme.ArthikaBorder
import com.example.ui.theme.ArthikaCard
import com.example.ui.theme.ArthikaDarkGreen
import com.example.ui.theme.ArthikaExpense
import com.example.ui.theme.ArthikaExpenseSoft
import com.example.ui.theme.ArthikaIncome
import com.example.ui.theme.ArthikaIncomeSoft
import com.example.ui.theme.ArthikaPrimary
import com.example.ui.theme.ArthikaSoftGreen
import com.example.ui.theme.ArthikaTextPrimary
import com.example.ui.theme.ArthikaTextSecondary

@Composable
fun TransactionsScreen(
    viewModel: FinanceViewModel,
    onOpenReceiptScanner: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val transactions by viewModel.filteredTransactions.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val typeFilter by viewModel.typeFilter.collectAsState()
    val categoryFilter by viewModel.categoryFilter.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val dateRangeFilter by viewModel.dateRangeFilter.collectAsState()

    var showSortMenu by remember { mutableStateOf(false) }
    var showCategoryMenu by remember { mutableStateOf(false) }

    val allCategories = remember {
        (CategoryConstants.expenseCategories + CategoryConstants.incomeCategories).distinct()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ArthikaBackground)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // Title and Sort action
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Transactions",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 24.sp,
                        color = ArthikaTextPrimary
                    )
                )
                Text(
                    text = "Search, filter & review records",
                    style = MaterialTheme.typography.bodySmall.copy(color = ArthikaTextSecondary)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    onClick = onOpenReceiptScanner,
                    shape = RoundedCornerShape(12.dp),
                    color = ArthikaSoftGreen,
                    modifier = Modifier.testTag("scan_bill_transactions_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.DocumentScanner,
                            contentDescription = "Scan Bill",
                            tint = ArthikaDarkGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Scan",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = ArthikaDarkGreen,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.5.sp
                            )
                        )
                    }
                }

                Box {
                    Surface(
                        onClick = { showSortMenu = true },
                        shape = RoundedCornerShape(12.dp),
                        color = ArthikaCard,
                        border = androidx.compose.foundation.BorderStroke(1.dp, ArthikaBorder),
                        modifier = Modifier.testTag("sort_menu_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = "Sort",
                            tint = ArthikaPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when (sortOrder) {
                                SortOrder.NEWEST -> "Newest"
                                SortOrder.OLDEST -> "Oldest"
                                SortOrder.HIGHEST_AMOUNT -> "Highest"
                                SortOrder.LOWEST_AMOUNT -> "Lowest"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = ArthikaDarkGreen,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                        )
                    }
                }

                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Newest First") },
                        onClick = { viewModel.setSortOrder(SortOrder.NEWEST); showSortMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Oldest First") },
                        onClick = { viewModel.setSortOrder(SortOrder.OLDEST); showSortMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Highest Amount") },
                        onClick = { viewModel.setSortOrder(SortOrder.HIGHEST_AMOUNT); showSortMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Lowest Amount") },
                        onClick = { viewModel.setSortOrder(SortOrder.LOWEST_AMOUNT); showSortMenu = false }
                    )
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(14.dp))

        // Search Input with minimal aesthetic
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.setSearchQuery(it) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("transaction_search_input"),
            placeholder = { Text("Search merchant, note, or category...", color = ArthikaTextSecondary) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = ArthikaTextSecondary,
                    modifier = Modifier.size(19.dp)
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear",
                            tint = ArthikaTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = ArthikaCard,
                unfocusedContainerColor = ArthikaCard,
                focusedBorderColor = ArthikaPrimary,
                unfocusedBorderColor = ArthikaBorder
            )
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Filters Horizontal Scroll Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Type: All
            FilterChip(
                selected = typeFilter == null,
                onClick = { viewModel.setTypeFilter(null) },
                label = { Text("All") },
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
                    selected = typeFilter == null
                )
            )

            // Type: Expenses
            FilterChip(
                selected = typeFilter == TransactionType.EXPENSE,
                onClick = {
                    viewModel.setTypeFilter(
                        if (typeFilter == TransactionType.EXPENSE) null else TransactionType.EXPENSE
                    )
                },
                label = { Text("Expenses") },
                shape = RoundedCornerShape(12.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = ArthikaExpenseSoft,
                    selectedLabelColor = ArthikaExpense,
                    containerColor = ArthikaCard,
                    labelColor = ArthikaTextPrimary
                ),
                border = FilterChipDefaults.filterChipBorder(
                    borderColor = ArthikaBorder,
                    selectedBorderColor = ArthikaExpense,
                    enabled = true,
                    selected = typeFilter == TransactionType.EXPENSE
                )
            )

            // Type: Income
            FilterChip(
                selected = typeFilter == TransactionType.INCOME,
                onClick = {
                    viewModel.setTypeFilter(
                        if (typeFilter == TransactionType.INCOME) null else TransactionType.INCOME
                    )
                },
                label = { Text("Income") },
                shape = RoundedCornerShape(12.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = ArthikaIncomeSoft,
                    selectedLabelColor = ArthikaIncome,
                    containerColor = ArthikaCard,
                    labelColor = ArthikaTextPrimary
                ),
                border = FilterChipDefaults.filterChipBorder(
                    borderColor = ArthikaBorder,
                    selectedBorderColor = ArthikaIncome,
                    enabled = true,
                    selected = typeFilter == TransactionType.INCOME
                )
            )

            // Date Range: Today
            FilterChip(
                selected = dateRangeFilter == DateRangeFilter.TODAY,
                onClick = {
                    viewModel.setDateRangeFilter(
                        if (dateRangeFilter == DateRangeFilter.TODAY) DateRangeFilter.ALL else DateRangeFilter.TODAY
                    )
                },
                label = { Text("Today") },
                shape = RoundedCornerShape(12.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = ArthikaSoftGreen,
                    selectedLabelColor = ArthikaDarkGreen,
                    containerColor = ArthikaCard,
                    labelColor = ArthikaTextPrimary
                ),
                border = FilterChipDefaults.filterChipBorder(
                    borderColor = ArthikaBorder,
                    selectedBorderColor = ArthikaPrimary,
                    enabled = true,
                    selected = dateRangeFilter == DateRangeFilter.TODAY
                )
            )

            // Date Range: This Week
            FilterChip(
                selected = dateRangeFilter == DateRangeFilter.THIS_WEEK,
                onClick = {
                    viewModel.setDateRangeFilter(
                        if (dateRangeFilter == DateRangeFilter.THIS_WEEK) DateRangeFilter.ALL else DateRangeFilter.THIS_WEEK
                    )
                },
                label = { Text("This Week") },
                shape = RoundedCornerShape(12.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = ArthikaSoftGreen,
                    selectedLabelColor = ArthikaDarkGreen,
                    containerColor = ArthikaCard,
                    labelColor = ArthikaTextPrimary
                ),
                border = FilterChipDefaults.filterChipBorder(
                    borderColor = ArthikaBorder,
                    selectedBorderColor = ArthikaPrimary,
                    enabled = true,
                    selected = dateRangeFilter == DateRangeFilter.THIS_WEEK
                )
            )

            // Date Range: This Month
            FilterChip(
                selected = dateRangeFilter == DateRangeFilter.THIS_MONTH,
                onClick = {
                    viewModel.setDateRangeFilter(
                        if (dateRangeFilter == DateRangeFilter.THIS_MONTH) DateRangeFilter.ALL else DateRangeFilter.THIS_MONTH
                    )
                },
                label = { Text("This Month") },
                shape = RoundedCornerShape(12.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = ArthikaSoftGreen,
                    selectedLabelColor = ArthikaDarkGreen,
                    containerColor = ArthikaCard,
                    labelColor = ArthikaTextPrimary
                ),
                border = FilterChipDefaults.filterChipBorder(
                    borderColor = ArthikaBorder,
                    selectedBorderColor = ArthikaPrimary,
                    enabled = true,
                    selected = dateRangeFilter == DateRangeFilter.THIS_MONTH
                )
            )

            // Category Filter Dropdown
            Box {
                FilterChip(
                    selected = categoryFilter != null,
                    onClick = { showCategoryMenu = true },
                    label = { Text(categoryFilter ?: "Category") },
                    shape = RoundedCornerShape(12.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ArthikaSoftGreen,
                        selectedLabelColor = ArthikaDarkGreen,
                        containerColor = ArthikaCard,
                        labelColor = ArthikaTextPrimary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = ArthikaBorder,
                        selectedBorderColor = ArthikaPrimary,
                        enabled = true,
                        selected = categoryFilter != null
                    )
                )

                DropdownMenu(
                    expanded = showCategoryMenu,
                    onDismissRequest = { showCategoryMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("All Categories") },
                        onClick = {
                            viewModel.setCategoryFilter(null)
                            showCategoryMenu = false
                        }
                    )
                    allCategories.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(cat) },
                            onClick = {
                                viewModel.setCategoryFilter(cat)
                                showCategoryMenu = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Transaction Summary Count
        val totalCount = transactions.size
        val totalSum = remember(transactions) {
            transactions.sumOf { if (it.type == TransactionType.INCOME) it.amount else -it.amount }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$totalCount transaction${if (totalCount != 1) "s" else ""}",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = ArthikaTextSecondary,
                    fontSize = 12.sp
                )
            )
            Text(
                text = "Net: ${formatCurrency(totalSum)}",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = if (totalSum >= 0) ArthikaIncome else ArthikaExpense,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp
                )
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Transaction List
        if (transactions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 80.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = ArthikaCard,
                    border = androidx.compose.foundation.BorderStroke(1.dp, ArthikaBorder),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No matching records",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Medium,
                                color = ArthikaTextPrimary
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Clear search or select different filters to view data",
                            style = MaterialTheme.typography.bodySmall.copy(color = ArthikaTextSecondary)
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    items = transactions,
                    key = { it.id },
                    contentType = { it.type }
                ) { tx ->
                    ArthikaTransactionRow(
                        transaction = tx,
                        onClick = { viewModel.openEditTransaction(tx) }
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(90.dp))
                }
            }
        }
    }
}
