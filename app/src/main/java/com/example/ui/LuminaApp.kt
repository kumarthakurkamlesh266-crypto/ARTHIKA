package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.TransactionType
import com.example.ui.dialogs.AddEditTransactionSheet
import com.example.ui.dialogs.BudgetSecuritySettingsDialog
import com.example.ui.dialogs.PinLockOverlay
import com.example.ui.dialogs.ReceiptScanReviewSheet
import com.example.ui.screens.AiAssistantScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ReportsScreen
import com.example.ui.screens.TransactionsScreen
import com.example.ui.theme.ArthikaBackground
import com.example.ui.theme.ArthikaBorder
import com.example.ui.theme.ArthikaCard
import com.example.ui.theme.ArthikaDarkGreen
import com.example.ui.theme.ArthikaPrimary
import com.example.ui.theme.ArthikaSoftGreen
import com.example.ui.theme.ArthikaTextSecondary
import kotlinx.coroutines.launch

enum class ArthikaTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("Home", Icons.Filled.Home, Icons.Outlined.Home),
    TRANSACTIONS("Transactions", Icons.AutoMirrored.Filled.ReceiptLong, Icons.AutoMirrored.Outlined.ReceiptLong),
    AI_ASSISTANT("AI Assistant", Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome),
    REPORTS("Reports", Icons.Filled.BarChart, Icons.Outlined.BarChart)
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun LuminaApp(viewModel: FinanceViewModel = viewModel()) {
    var currentTab by remember { mutableStateOf(ArthikaTab.HOME) }
    var isSettingsOpen by remember { mutableStateOf(false) }

    val isLocked by viewModel.isAppLocked.collectAsState()
    val isAddSheetOpen by viewModel.isAddSheetOpen.collectAsState()
    val editingTransaction by viewModel.editingTransaction.collectAsState()
    val defaultAddType by viewModel.newTransactionDefaultType.collectAsState()
    val receiptReviewState by viewModel.receiptReviewState.collectAsState()
    val budget by viewModel.budget.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Back handling: return to Home if on secondary tab
    BackHandler(enabled = currentTab != ArthikaTab.HOME && !isLocked) {
        currentTab = ArthikaTab.HOME
    }

    if (isLocked) {
        PinLockOverlay(
            onUnlockWithPin = { pin -> viewModel.unlockAppWithPin(pin) }
        )
        return
    }

    val isImeVisible = WindowInsets.isImeVisible

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars),
        containerColor = ArthikaBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (currentTab != ArthikaTab.AI_ASSISTANT || !isImeVisible) {
                NavigationBar(
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .testTag("arthika_bottom_nav"),
                    containerColor = ArthikaCard,
                    tonalElevation = 0.dp
                ) {
                    ArthikaTab.entries.forEach { tab ->
                        val isSelected = currentTab == tab
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { currentTab = tab },
                            icon = {
                                Icon(
                                    imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.title,
                                    modifier = Modifier.size(23.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = tab.title,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        fontSize = 11.sp
                                    )
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = ArthikaDarkGreen,
                                selectedTextColor = ArthikaDarkGreen,
                                indicatorColor = ArthikaSoftGreen,
                                unselectedIconColor = ArthikaTextSecondary,
                                unselectedTextColor = ArthikaTextSecondary
                            ),
                            modifier = Modifier.testTag("nav_item_${tab.name.lowercase()}")
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (currentTab == ArthikaTab.HOME || currentTab == ArthikaTab.TRANSACTIONS) {
                FloatingActionButton(
                    onClick = { viewModel.openAddTransactionSheet(TransactionType.EXPENSE) },
                    shape = CircleShape,
                    containerColor = ArthikaPrimary,
                    contentColor = Color.White,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp),
                    modifier = Modifier
                        .size(54.dp)
                        .testTag("fab_add_transaction")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Quick Add Transaction",
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Crossfade(
                targetState = currentTab,
                animationSpec = androidx.compose.animation.core.tween(durationMillis = 180),
                label = "screen_transition"
            ) { tab ->
                when (tab) {
                    ArthikaTab.HOME -> HomeScreen(
                        viewModel = viewModel,
                        onNavigateToTransactions = { currentTab = ArthikaTab.TRANSACTIONS },
                        onOpenReceiptScanner = { viewModel.openReceiptScanner() },
                        onOpenSettings = { isSettingsOpen = true }
                    )
                    ArthikaTab.TRANSACTIONS -> TransactionsScreen(
                        viewModel = viewModel,
                        onOpenReceiptScanner = { viewModel.openReceiptScanner() }
                    )
                    ArthikaTab.AI_ASSISTANT -> AiAssistantScreen(
                        viewModel = viewModel
                    )
                    ArthikaTab.REPORTS -> ReportsScreen(
                        viewModel = viewModel
                    )
                }
            }
        }
    }

    // Modal: Add / Edit Transaction
    AddEditTransactionSheet(
        isOpen = isAddSheetOpen,
        defaultType = defaultAddType,
        editingTransaction = editingTransaction,
        onDismiss = { viewModel.closeAddSheet() },
        onScanReceipt = {
            viewModel.closeAddSheet()
            viewModel.openReceiptScanner()
        },
        onSave = { id, amount, type, category, dateMillis, timeString, paymentMethod, notes, merchant ->
            viewModel.saveTransaction(
                id = id,
                amount = amount,
                type = type,
                category = category,
                dateMillis = dateMillis,
                timeString = timeString,
                paymentMethod = paymentMethod,
                notes = notes,
                merchant = merchant
            )
            scope.launch {
                snackbarHostState.showSnackbar("Transaction recorded")
            }
        },
        onDelete = { tx ->
            viewModel.deleteTransaction(tx)
            scope.launch {
                snackbarHostState.showSnackbar("Transaction deleted")
            }
        }
    )

    // Modal: Receipt OCR Review
    ReceiptScanReviewSheet(
        reviewState = receiptReviewState,
        samplePresets = viewModel.getSampleReceiptPresets(),
        hasApiKeyConfigured = viewModel.hasApiKeyConfigured(),
        onSelectImageUri = { uri -> viewModel.scanReceiptUri(uri) },
        onSelectPreset = { preset -> viewModel.applySampleReceipt(preset) },
        onOpenSettings = {
            viewModel.closeReceiptReview()
            isSettingsOpen = true
        },
        onDismiss = { viewModel.closeReceiptReview() },
        onConfirmSave = { merchant, amount, dateString, timeString, category, paymentMethod, notes, receiptUri ->
            viewModel.confirmReceiptTransaction(
                merchant = merchant,
                amount = amount,
                dateString = dateString,
                timeString = timeString,
                category = category,
                paymentMethod = paymentMethod,
                notes = notes,
                receiptUri = receiptUri
            )
            scope.launch {
                snackbarHostState.showSnackbar("Scanned receipt saved")
            }
        }
    )

    // Modal: Budget & Security Settings
    BudgetSecuritySettingsDialog(
        isOpen = isSettingsOpen,
        currentBudget = budget,
        currentApiKey = viewModel.getSavedApiKey(),
        onDismiss = { isSettingsOpen = false },
        onTestApiKey = { key -> viewModel.testApiKey(key) },
        onSaveSettings = { limit, currency, pin, isPinEnabled, name, apiKey ->
            viewModel.updateBudgetSettings(limit, currency, pin, isPinEnabled, name, apiKey)
            isSettingsOpen = false
            scope.launch {
                snackbarHostState.showSnackbar("Preferences & API key saved")
            }
        }
    )
}
