package com.example.ui.dialogs

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.TransactionEntity
import com.example.data.model.CategoryConstants
import com.example.data.model.TransactionType
import com.example.ui.components.getCategoryIcon
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditTransactionSheet(
    isOpen: Boolean,
    defaultType: TransactionType,
    editingTransaction: TransactionEntity?,
    onDismiss: () -> Unit,
    onSave: (
        id: Long,
        amount: Double,
        type: TransactionType,
        category: String,
        dateMillis: Long,
        timeString: String,
        paymentMethod: String,
        notes: String,
        merchant: String
    ) -> Unit,
    onDelete: ((TransactionEntity) -> Unit)? = null,
    onScanReceipt: (() -> Unit)? = null
) {
    if (!isOpen) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    var selectedType by remember { mutableStateOf(editingTransaction?.type ?: defaultType) }
    var amountText by remember {
        mutableStateOf(editingTransaction?.amount?.let { if (it > 0) it.toString() else "" } ?: "")
    }
    var selectedCategory by remember {
        mutableStateOf(
            editingTransaction?.category ?: if (selectedType == TransactionType.EXPENSE) "Food" else "Salary"
        )
    }
    var selectedDateMillis by remember {
        mutableLongStateOf(editingTransaction?.dateMillis ?: System.currentTimeMillis())
    }
    var selectedTimeString by remember {
        mutableStateOf(
            editingTransaction?.timeString ?: SimpleDateFormat("HH:mm", Locale.US).format(Date())
        )
    }
    var selectedPaymentMethod by remember {
        mutableStateOf(editingTransaction?.paymentMethod ?: "Credit Card")
    }
    var merchantText by remember { mutableStateOf(editingTransaction?.merchant ?: "") }
    var notesText by remember { mutableStateOf(editingTransaction?.notes ?: "") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val categories = if (selectedType == TransactionType.EXPENSE) {
        CategoryConstants.expenseCategories
    } else {
        CategoryConstants.incomeCategories
    }

    LaunchedEffect(selectedType) {
        if (!categories.contains(selectedCategory)) {
            selectedCategory = categories.first()
        }
    }

    val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ArthikaCard,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (editingTransaction == null) "Add Transaction" else "Edit Transaction",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 19.sp,
                        color = ArthikaTextPrimary
                    )
                )

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = ArthikaTextSecondary
                    )
                }
            }

            if (editingTransaction == null && onScanReceipt != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    onClick = {
                        onDismiss()
                        onScanReceipt()
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = ArthikaSoftGreen,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("scan_bill_autofill_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DocumentScanner,
                            contentDescription = null,
                            tint = ArthikaDarkGreen,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Auto-fill from Receipt or Bill Photo",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = ArthikaDarkGreen,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Income / Expense Segmented Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(ArthikaSurfaceSubtle)
                    .padding(4.dp)
            ) {
                // Expense Button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selectedType == TransactionType.EXPENSE) ArthikaExpense else Color.Transparent)
                        .clickable { selectedType = TransactionType.EXPENSE }
                        .padding(vertical = 10.dp)
                        .testTag("type_toggle_expense"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Expense",
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = if (selectedType == TransactionType.EXPENSE) Color.White else ArthikaTextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }

                // Income Button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selectedType == TransactionType.INCOME) ArthikaIncome else Color.Transparent)
                        .clickable { selectedType = TransactionType.INCOME }
                        .padding(vertical = 10.dp)
                        .testTag("type_toggle_income"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Income",
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = if (selectedType == TransactionType.INCOME) Color.White else ArthikaTextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Large Amount Input
            Text(
                text = "Amount",
                style = MaterialTheme.typography.labelMedium.copy(color = ArthikaTextSecondary)
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = amountText,
                onValueChange = {
                    amountText = it.filter { char -> char.isDigit() || char == '.' }
                    errorMessage = null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("transaction_amount_input"),
                placeholder = { Text("0.00", style = MaterialTheme.typography.headlineMedium.copy(color = ArthikaBorder)) },
                prefix = {
                    Text(
                        text = "$",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Medium,
                            color = if (selectedType == TransactionType.INCOME) ArthikaIncome else ArthikaExpense
                        )
                    )
                },
                textStyle = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Medium,
                    color = ArthikaTextPrimary
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ArthikaPrimary,
                    unfocusedBorderColor = ArthikaBorder
                )
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Category Selector
            Text(
                text = "Category",
                style = MaterialTheme.typography.labelMedium.copy(color = ArthikaTextSecondary)
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories) { cat ->
                    val isSelected = selectedCategory.equals(cat, ignoreCase = true)
                    val activeBg = if (selectedType == TransactionType.INCOME) ArthikaIncome else ArthikaPrimary
                    Surface(
                        onClick = { selectedCategory = cat },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) activeBg else ArthikaSurfaceSubtle,
                        modifier = Modifier.testTag("category_chip_$cat")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = getCategoryIcon(cat),
                                contentDescription = cat,
                                tint = if (isSelected) Color.White else ArthikaTextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = cat,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = if (isSelected) Color.White else ArthikaTextPrimary,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Date & Time pickers
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Date Picker Button
                Surface(
                    onClick = {
                        val cal = Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
                        DatePickerDialog(
                            context,
                            { _, y, m, d ->
                                val updated = Calendar.getInstance().apply {
                                    set(Calendar.YEAR, y)
                                    set(Calendar.MONTH, m)
                                    set(Calendar.DAY_OF_MONTH, d)
                                }
                                selectedDateMillis = updated.timeInMillis
                            },
                            cal.get(Calendar.YEAR),
                            cal.get(Calendar.MONTH),
                            cal.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    },
                    shape = RoundedCornerShape(14.dp),
                    color = ArthikaCard,
                    border = androidx.compose.foundation.BorderStroke(1.dp, ArthikaBorder),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("select_date_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = "Date",
                            tint = ArthikaPrimary,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = dateFormat.format(Date(selectedDateMillis)),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp,
                                color = ArthikaTextPrimary
                            )
                        )
                    }
                }

                // Time Picker Button
                Surface(
                    onClick = {
                        val parts = selectedTimeString.split(":")
                        val curH = parts.getOrNull(0)?.toIntOrNull() ?: 12
                        val curM = parts.getOrNull(1)?.toIntOrNull() ?: 0
                        TimePickerDialog(
                            context,
                            { _, h, m ->
                                selectedTimeString = String.format(Locale.US, "%02d:%02d", h, m)
                            },
                            curH,
                            curM,
                            true
                        ).show()
                    },
                    shape = RoundedCornerShape(14.dp),
                    color = ArthikaCard,
                    border = androidx.compose.foundation.BorderStroke(1.dp, ArthikaBorder),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("select_time_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = "Time",
                            tint = ArthikaPrimary,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = selectedTimeString,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp,
                                color = ArthikaTextPrimary
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Merchant / Payee input
            Text(
                text = "Merchant or Recipient",
                style = MaterialTheme.typography.labelMedium.copy(color = ArthikaTextSecondary)
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = merchantText,
                onValueChange = { merchantText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("merchant_input"),
                placeholder = { Text("e.g. Trader Joe's, Coffee Roasters", color = ArthikaTextSecondary) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ArthikaPrimary,
                    unfocusedBorderColor = ArthikaBorder
                )
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Payment Method Selector
            Text(
                text = "Payment Method",
                style = MaterialTheme.typography.labelMedium.copy(color = ArthikaTextSecondary)
            )
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(CategoryConstants.paymentMethods) { method ->
                    val isSelected = selectedPaymentMethod == method
                    Surface(
                        onClick = { selectedPaymentMethod = method },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) ArthikaPrimary else ArthikaSurfaceSubtle
                    ) {
                        Text(
                            text = method,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = if (isSelected) Color.White else ArthikaTextPrimary,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            ),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Notes
            Text(
                text = "Notes (Optional)",
                style = MaterialTheme.typography.labelMedium.copy(color = ArthikaTextSecondary)
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = notesText,
                onValueChange = { notesText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("notes_input"),
                placeholder = { Text("Details or item notes...", color = ArthikaTextSecondary) },
                maxLines = 2,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ArthikaPrimary,
                    unfocusedBorderColor = ArthikaBorder
                )
            )

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorMessage ?: "",
                    style = MaterialTheme.typography.bodySmall.copy(color = ArthikaExpense)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (editingTransaction != null && onDelete != null) {
                    OutlinedButton(
                        onClick = { onDelete(editingTransaction) },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ArthikaExpense),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ArthikaExpense),
                        modifier = Modifier
                            .weight(0.4f)
                            .height(50.dp)
                            .testTag("delete_transaction_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Button(
                    onClick = {
                        val amount = amountText.toDoubleOrNull()
                        if (amount == null || amount <= 0.0) {
                            errorMessage = "Please enter an amount greater than 0."
                            return@Button
                        }
                        onSave(
                            editingTransaction?.id ?: 0L,
                            amount,
                            selectedType,
                            selectedCategory,
                            selectedDateMillis,
                            selectedTimeString,
                            selectedPaymentMethod,
                            notesText.trim(),
                            merchantText.trim()
                        )
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedType == TransactionType.INCOME) ArthikaIncome else ArthikaPrimary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("save_transaction_button")
                ) {
                    Text(
                        text = if (editingTransaction == null) "Save Transaction" else "Update Record",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
