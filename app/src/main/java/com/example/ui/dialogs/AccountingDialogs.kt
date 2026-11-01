package com.example.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.accounting.FullReconciliationSummary
import com.example.data.entity.AccountEntity
import com.example.data.entity.TransactionEntity
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
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountingHubSheet(
    isOpen: Boolean,
    accounts: List<AccountEntity>,
    onDismiss: () -> Unit,
    onExecuteTransfer: (fromId: Long, toId: Long, amount: Double, fee: Double, notes: String, callback: (Boolean, String) -> Unit) -> Unit,
    onReconcileStatement: (accountId: Long, content: String, fileName: String, callback: (FullReconciliationSummary?) -> Unit) -> Unit
) {
    if (!isOpen) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTab by remember { mutableStateOf(0) } // 0: Transfer, 1: Statement Reconciliation, 2: Account Balances

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ArthikaBackground,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Accounting Operations",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ArthikaTextPrimary
                    )
                    Text(
                        text = "Double-entry transfers, accounts & bank reconciliation",
                        fontSize = 13.sp,
                        color = ArthikaTextSecondary
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(ArthikaBorder.copy(alpha = 0.5f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = ArthikaTextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Tabs
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = ArthikaSurfaceSubtle,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp)),
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = ArthikaPrimary,
                        height = 3.dp
                    )
                },
                divider = {}
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Transfer", fontSize = 13.sp, fontWeight = FontWeight.Medium) },
                    selectedContentColor = ArthikaPrimary,
                    unselectedContentColor = ArthikaTextSecondary
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Reconcile", fontSize = 13.sp, fontWeight = FontWeight.Medium) },
                    selectedContentColor = ArthikaPrimary,
                    unselectedContentColor = ArthikaTextSecondary
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Accounts", fontSize = 13.sp, fontWeight = FontWeight.Medium) },
                    selectedContentColor = ArthikaPrimary,
                    unselectedContentColor = ArthikaTextSecondary
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            when (selectedTab) {
                0 -> TransferTabContent(
                    accounts = accounts,
                    onExecuteTransfer = onExecuteTransfer
                )
                1 -> ReconciliationTabContent(
                    accounts = accounts,
                    onReconcileStatement = onReconcileStatement
                )
                2 -> AccountsOverviewContent(accounts = accounts)
            }
        }
    }
}

@Composable
fun TransferTabContent(
    accounts: List<AccountEntity>,
    onExecuteTransfer: (fromId: Long, toId: Long, amount: Double, fee: Double, notes: String, callback: (Boolean, String) -> Unit) -> Unit
) {
    var fromAccountId by remember { mutableLongStateOf(accounts.firstOrNull()?.id ?: 1L) }
    var toAccountId by remember { mutableLongStateOf(accounts.getOrNull(1)?.id ?: 2L) }
    var amountText by remember { mutableStateOf("") }
    var feeText by remember { mutableStateOf("") }
    var notesText by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isSuccess by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Source Account (Transfer Out)", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = ArthikaTextSecondary)
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            accounts.forEach { acc ->
                val selected = acc.id == fromAccountId
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (selected) ArthikaSoftGreen else ArthikaCard,
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) ArthikaPrimary else ArthikaBorder),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { fromAccountId = acc.id }
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(acc.name, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1, color = ArthikaTextPrimary)
                        Text("$${"%.2f".format(acc.currentBalance)}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = ArthikaDarkGreen)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text("Destination Account (Transfer In)", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = ArthikaTextSecondary)
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            accounts.forEach { acc ->
                val selected = acc.id == toAccountId
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (selected) ArthikaSoftGreen else ArthikaCard,
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) ArthikaPrimary else ArthikaBorder),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { toAccountId = acc.id }
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(acc.name, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1, color = ArthikaTextPrimary)
                        Text("$${"%.2f".format(acc.currentBalance)}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = ArthikaDarkGreen)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                label = { Text("Transfer Amount ($)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier
                    .weight(1.5f)
                    .testTag("transfer_amount_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ArthikaPrimary,
                    unfocusedBorderColor = ArthikaBorder,
                    focusedContainerColor = ArthikaCard,
                    unfocusedContainerColor = ArthikaCard
                )
            )
            OutlinedTextField(
                value = feeText,
                onValueChange = { feeText = it },
                label = { Text("Fee ($)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ArthikaPrimary,
                    unfocusedBorderColor = ArthikaBorder,
                    focusedContainerColor = ArthikaCard,
                    unfocusedContainerColor = ArthikaCard
                )
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = notesText,
            onValueChange = { notesText = it },
            label = { Text("Notes / Memo") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ArthikaPrimary,
                unfocusedBorderColor = ArthikaBorder,
                focusedContainerColor = ArthikaCard,
                unfocusedContainerColor = ArthikaCard
            )
        )

        if (statusMessage != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = statusMessage!!,
                color = if (isSuccess) ArthikaIncome else ArthikaExpense,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                val amt = amountText.toDoubleOrNull() ?: 0.0
                val fee = feeText.toDoubleOrNull() ?: 0.0
                if (amt <= 0.0) {
                    statusMessage = "Please enter a valid transfer amount"
                    isSuccess = false
                    return@Button
                }
                if (fromAccountId == toAccountId) {
                    statusMessage = "Source and destination accounts must be different"
                    isSuccess = false
                    return@Button
                }
                onExecuteTransfer(fromAccountId, toAccountId, amt, fee, notesText) { success, msg ->
                    statusMessage = msg
                    isSuccess = success
                    if (success) {
                        amountText = ""
                        feeText = ""
                        notesText = ""
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("execute_transfer_button"),
            colors = ButtonDefaults.buttonColors(containerColor = ArthikaPrimary),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.CompareArrows, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Execute Verified Transfer", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun ReconciliationTabContent(
    accounts: List<AccountEntity>,
    onReconcileStatement: (accountId: Long, content: String, fileName: String, callback: (FullReconciliationSummary?) -> Unit) -> Unit
) {
    var selectedAccountId by remember { mutableLongStateOf(accounts.firstOrNull()?.id ?: 1L) }
    var statementText by remember { mutableStateOf("") }
    var summaryResult by remember { mutableStateOf<FullReconciliationSummary?>(null) }
    var isReconciling by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Account to Reconcile", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = ArthikaTextSecondary)
        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            accounts.take(3).forEach { acc ->
                val selected = acc.id == selectedAccountId
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (selected) ArthikaSoftGreen else ArthikaCard,
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) ArthikaPrimary else ArthikaBorder),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selectedAccountId = acc.id }
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(acc.name, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1, color = ArthikaTextPrimary)
                        Text("$${"%.2f".format(acc.currentBalance)}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = ArthikaDarkGreen)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Bank Statement Data (CSV / Text)", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = ArthikaTextSecondary)
            if (statementText.isNotBlank()) {
                Text(
                    "Clear",
                    fontSize = 12.sp,
                    color = ArthikaTextSecondary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable { statementText = "" }
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        OutlinedTextField(
            value = statementText,
            onValueChange = { statementText = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .testTag("statement_input_field"),
            placeholder = {
                Text(
                    "Paste CSV statement (Date,Description,Amount,Type)...",
                    fontSize = 11.sp,
                    color = ArthikaTextSecondary
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ArthikaPrimary,
                unfocusedBorderColor = ArthikaBorder,
                focusedContainerColor = ArthikaCard,
                unfocusedContainerColor = ArthikaCard
            ),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                isReconciling = true
                onReconcileStatement(selectedAccountId, statementText, "chase_october_statement.csv") { res ->
                    isReconciling = false
                    summaryResult = res
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("run_reconciliation_button"),
            colors = ButtonDefaults.buttonColors(containerColor = ArthikaDarkGreen),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (isReconciling) "Reconciling Ledger..." else "Run Verified Bank Reconciliation", fontWeight = FontWeight.SemiBold)
        }

        // Summary Results
        if (summaryResult != null) {
            val res = summaryResult!!
            Spacer(modifier = Modifier.height(14.dp))
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = ArthikaCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (res.isBalanced) ArthikaIncome else ArthikaExpense),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (res.isBalanced) "Reconciliation Balanced" else "Reconciliation Discrepancy Found",
                            fontWeight = FontWeight.Bold,
                            color = if (res.isBalanced) ArthikaIncome else ArthikaExpense,
                            fontSize = 14.sp
                        )
                        Icon(
                            imageVector = if (res.isBalanced) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = if (res.isBalanced) ArthikaIncome else ArthikaExpense,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("• Matched Transactions: ${res.matchedCount}", fontSize = 12.sp, color = ArthikaTextPrimary)
                    Text("• Unmatched Bank Entries: ${res.unmatchedStatementCount}", fontSize = 12.sp, color = ArthikaTextPrimary)
                    Text("• Unmatched App Entries: ${res.unmatchedSystemCount}", fontSize = 12.sp, color = ArthikaTextPrimary)
                    Text("• Balance Discrepancy: $${"%.2f".format(res.balanceDiscrepancy)}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (res.isBalanced) ArthikaIncome else ArthikaExpense)
                }
            }
        }
    }
}

@Composable
fun AccountsOverviewContent(accounts: List<AccountEntity>) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .height(350.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(accounts) { acc ->
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = ArthikaCard),
                border = androidx.compose.foundation.BorderStroke(1.dp, ArthikaBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(acc.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = ArthikaTextPrimary)
                        Text("${acc.institutionName} ••••• ${acc.accountNumberLast4} (${acc.type.name})", fontSize = 12.sp, color = ArthikaTextSecondary)
                    }
                    Text(
                        text = "$${"%.2f".format(acc.currentBalance)}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = if (acc.currentBalance >= 0) ArthikaDarkGreen else ArthikaExpense
                    )
                }
            }
        }
    }
}
