package com.example.ui.dialogs

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.data.model.CategoryConstants
import com.example.ocr.ExtractedReceipt
import com.example.ui.ReceiptReviewState
import com.example.ui.components.formatCurrency
import com.example.ui.theme.ArthikaBackground
import com.example.ui.theme.ArthikaBorder
import com.example.ui.theme.ArthikaCard
import com.example.ui.theme.ArthikaDarkGreen
import com.example.ui.theme.ArthikaPrimary
import com.example.ui.theme.ArthikaSoftGreen
import com.example.ui.theme.ArthikaSurfaceSubtle
import com.example.ui.theme.ArthikaTextPrimary
import com.example.ui.theme.ArthikaTextSecondary
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptScanReviewSheet(
    reviewState: ReceiptReviewState,
    samplePresets: List<ExtractedReceipt>,
    hasApiKeyConfigured: Boolean,
    onSelectImageUri: (Uri) -> Unit,
    onSelectPreset: (ExtractedReceipt) -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
    onConfirmSave: (
        merchant: String,
        amount: Double,
        dateString: String,
        timeString: String,
        category: String,
        paymentMethod: String,
        notes: String,
        receiptUri: String?
    ) -> Unit
) {
    if (!reviewState.isOpen) return

    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Camera capture Uri state
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }

    // Photo picker launcher (Gallery)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            onSelectImageUri(uri)
        }
    }

    // Camera photo capture launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success && tempCameraUri != null) {
            onSelectImageUri(tempCameraUri!!)
        }
    }

    fun launchCamera() {
        try {
            val receiptsDir = File(context.cacheDir, "receipts").apply { mkdirs() }
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val tempFile = File(receiptsDir, "RECEIPT_${timeStamp}.jpg")
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                tempFile
            )
            tempCameraUri = uri
            cameraLauncher.launch(uri)
        } catch (e: Exception) {
            // Fallback to gallery picker if camera URI fails
            photoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
    }

    val extracted = reviewState.receipt

    var merchantInput by remember(extracted) { mutableStateOf(extracted?.merchant ?: "") }
    var amountInput by remember(extracted) {
        val amt = extracted?.amount ?: 0.0
        mutableStateOf(if (amt > 0) amt.toString() else "")
    }
    var dateInput by remember(extracted) { mutableStateOf(extracted?.dateString ?: "") }
    var timeInput by remember(extracted) { mutableStateOf(extracted?.timeString ?: "") }
    var categoryInput by remember(extracted) { mutableStateOf(extracted?.category ?: "Food") }
    var paymentMethodInput by remember(extracted) {
        mutableStateOf(extracted?.detectedPaymentMethod ?: "Credit Card")
    }
    var notesInput by remember(extracted) {
        val itemsSummary = extracted?.items?.joinToString(", ") ?: ""
        mutableStateOf(if (itemsSummary.isNotBlank()) "Items: $itemsSummary" else "")
    }

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
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(ArthikaSoftGreen),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DocumentScanner,
                            contentDescription = "Scan",
                            tint = ArthikaDarkGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Receipt & Bill OCR",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 19.sp,
                                color = ArthikaTextPrimary
                            )
                        )
                        Text(
                            text = "Automatic expense entry via image recognition",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = ArthikaTextSecondary,
                                fontSize = 11.5.sp
                            )
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = ArthikaTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- Photo Upload & Camera Action Buttons ---
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, ArthikaBorder, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                color = ArthikaBackground
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Upload or Photograph Bill",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = ArthikaTextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Take a picture of any invoice, restaurant slip, or grocery receipt",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = ArthikaTextSecondary,
                            fontSize = 12.sp
                        )
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Option 1: Take Photo with Camera
                        Button(
                            onClick = { launchCamera() },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ArthikaDarkGreen),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("camera_snap_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Camera",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }

                        // Option 2: Upload from Gallery / Files
                        Button(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ArthikaPrimary),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("upload_bill_photo_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddPhotoAlternate,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Upload Photo",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // API Key Advisory banner if user hasn't set custom key
            if (!hasApiKeyConfigured) {
                Surface(
                    onClick = onOpenSettings,
                    shape = RoundedCornerShape(12.dp),
                    color = ArthikaSoftGreen,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = ArthikaDarkGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Tip: Add your Gemini API Key in Settings for accurate OCR",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = ArthikaDarkGreen,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = ArthikaDarkGreen,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Quick Preset Samples (useful for testing or fallback)
            Text(
                text = "Or test verified sample receipts:",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = ArthikaTextSecondary,
                    fontSize = 11.5.sp
                )
            )
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(samplePresets) { preset ->
                    Surface(
                        onClick = { onSelectPreset(preset) },
                        shape = RoundedCornerShape(10.dp),
                        color = ArthikaCard,
                        border = androidx.compose.foundation.BorderStroke(1.dp, ArthikaBorder),
                        modifier = Modifier.testTag("preset_receipt_${preset.merchant.take(8)}")
                    ) {
                        Text(
                            text = "${preset.merchant} (${formatCurrency(preset.amount)})",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.5.sp,
                                color = ArthikaTextPrimary
                            ),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Processing Indicator
            if (reviewState.isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = ArthikaPrimary,
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Analyzing receipt with Gemini Vision OCR...",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = ArthikaTextPrimary,
                                fontWeight = FontWeight.Medium
                            )
                        )
                        Text(
                            text = "Extracting vendor, totals, items, and tax...",
                            style = MaterialTheme.typography.bodySmall.copy(color = ArthikaTextSecondary)
                        )
                    }
                }
            } else if (extracted != null) {
                // Image preview thumbnail (if Uri present)
                if (!extracted.receiptImageUri.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(ArthikaSurfaceSubtle),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = extracted.receiptImageUri,
                            contentDescription = "Uploaded Receipt",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp)),
                            contentScale = ContentScale.Crop
                        )

                        // Subtle overlay chip
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black.copy(alpha = 0.65f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (extracted.isAiPowered) "✨ AI OCR Parsed" else "📷 Photo Attached",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color.White,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // Error / Advisory message if any
                if (!extracted.errorMessage.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFFEF3C7))
                            .border(1.dp, Color(0xFFFDE68A), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = Color(0xFFB45309),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = extracted.errorMessage,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color(0xFF92400E),
                                    fontSize = 11.5.sp
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Potential Duplicate Warning Alert
                if (reviewState.isPotentialDuplicate) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFFFF7ED))
                            .border(1.dp, Color(0xFFFFD8A8), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.WarningAmber,
                                contentDescription = "Duplicate Warning",
                                tint = Color(0xFFD97706),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Potential Duplicate Entry",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        color = Color(0xFF92400E),
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                                Text(
                                    text = "A transaction with the same merchant, amount, and date already exists.",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Color(0xFFB45309),
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // Extracted Review Form Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Verify & Edit Extracted Details",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = ArthikaTextPrimary
                        )
                    )
                    if (extracted.confidence > 0f) {
                        Text(
                            text = "${(extracted.confidence * 100).toInt()}% Match",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = ArthikaDarkGreen,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Merchant
                OutlinedTextField(
                    value = merchantInput,
                    onValueChange = { merchantInput = it },
                    label = { Text("Merchant / Store Name") },
                    placeholder = { Text("e.g. Starbucks, Walmart") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ocr_merchant_input"),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ArthikaPrimary,
                        unfocusedBorderColor = ArthikaBorder
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Amount
                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { amountInput = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Total Amount") },
                    placeholder = { Text("0.00") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ocr_amount_input"),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ArthikaPrimary,
                        unfocusedBorderColor = ArthikaBorder
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = dateInput,
                        onValueChange = { dateInput = it },
                        label = { Text("Date (YYYY-MM-DD)") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ArthikaPrimary,
                            unfocusedBorderColor = ArthikaBorder
                        )
                    )

                    OutlinedTextField(
                        value = timeInput,
                        onValueChange = { timeInput = it },
                        label = { Text("Time (HH:MM)") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ArthikaPrimary,
                            unfocusedBorderColor = ArthikaBorder
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Category selector
                Text(
                    text = "Category",
                    style = MaterialTheme.typography.bodySmall.copy(color = ArthikaTextSecondary)
                )
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(CategoryConstants.expenseCategories) { cat ->
                        val isSelected = categoryInput.equals(cat, ignoreCase = true)
                        Surface(
                            onClick = { categoryInput = cat },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) ArthikaPrimary else ArthikaSurfaceSubtle
                        ) {
                            Text(
                                text = cat,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = if (isSelected) Color.White else ArthikaTextPrimary,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    fontSize = 12.sp
                                ),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                // Extracted line items
                if (extracted.items.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Line Items Detected:",
                        style = MaterialTheme.typography.bodySmall.copy(color = ArthikaTextSecondary)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(ArthikaSurfaceSubtle)
                            .padding(10.dp)
                    ) {
                        extracted.items.forEach { item ->
                            Text(
                                text = "• $item",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = ArthikaTextPrimary,
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Notes
                OutlinedTextField(
                    value = notesInput,
                    onValueChange = { notesInput = it },
                    label = { Text("Notes / Item Summary") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ArthikaPrimary,
                        unfocusedBorderColor = ArthikaBorder
                    )
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Confirm and Save Button
                Button(
                    onClick = {
                        val amt = amountInput.toDoubleOrNull() ?: 0.0
                        if (amt > 0) {
                            onConfirmSave(
                                merchantInput.trim().ifBlank { "Receipt Merchant" },
                                amt,
                                dateInput.trim(),
                                timeInput.trim(),
                                categoryInput,
                                paymentMethodInput,
                                notesInput.trim(),
                                extracted.receiptImageUri
                            )
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ArthikaPrimary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("confirm_receipt_save_button")
                ) {
                    Text(
                        text = "Confirm & Save Expense",
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
