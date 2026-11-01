package com.example.ui.dialogs

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.GeminiConfig
import com.example.data.ApiKeyStatus
import com.example.data.entity.BudgetEntity
import com.example.ui.components.IndianCurrencyFormatter
import com.example.ui.theme.ArthikaBackground
import com.example.ui.theme.ArthikaBorder
import com.example.ui.theme.ArthikaCard
import com.example.ui.theme.ArthikaDarkGreen
import com.example.ui.theme.ArthikaPrimary
import com.example.ui.theme.ArthikaSoftGreen
import com.example.ui.theme.ArthikaTextPrimary
import com.example.ui.theme.ArthikaTextSecondary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BudgetSecuritySettingsDialog(
    isOpen: Boolean,
    currentBudget: BudgetEntity,
    currentApiKey: String,
    currentModel: String = GeminiConfig.DEFAULT_MODEL,
    onDismiss: () -> Unit,
    onTestApiKey: suspend (key: String, model: String) -> ApiKeyStatus,
    onSaveSettings: (
        monthlyLimit: Double,
        currencySymbol: String,
        pinCode: String,
        isPinEnabled: Boolean,
        userName: String,
        geminiApiKey: String,
        selectedModel: String
    ) -> Unit
) {
    if (!isOpen) return

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var monthlyLimitText by remember {
        mutableStateOf(
            if (currentBudget.monthlyLimit > 0) {
                currentBudget.monthlyLimit.toLong().toString()
            } else {
                "50000"
            }
        )
    }
    var userNameText by remember { mutableStateOf(currentBudget.userName) }
    var currencySymbolText by remember { mutableStateOf(currentBudget.currencySymbol.ifBlank { "₹" }) }
    var isPinEnabled by remember { mutableStateOf(currentBudget.isPinEnabled) }
    var pinCodeText by remember { mutableStateOf(currentBudget.pinCode) }

    // Model selection
    var selectedModel by remember { mutableStateOf(currentModel.ifBlank { GeminiConfig.DEFAULT_MODEL }) }

    // API Key states
    var apiKeyText by remember { mutableStateOf(currentApiKey) }
    var isApiKeyVisible by remember { mutableStateOf(false) }
    var apiKeyStatus by remember { mutableStateOf<ApiKeyStatus>(ApiKeyStatus.Idle) }

    // Validation error states
    var userNameError by remember { mutableStateOf<String?>(null) }
    var monthlyLimitError by remember { mutableStateOf<String?>(null) }
    var pinCodeError by remember { mutableStateOf<String?>(null) }

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
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(ArthikaSoftGreen),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Savings,
                            contentDescription = "Budget",
                            tint = ArthikaDarkGreen,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Settings & AI Config",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 19.sp,
                                color = ArthikaTextPrimary
                            )
                        )
                        Text(
                            text = "Model: $selectedModel • Currency: INR (₹)",
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

            Spacer(modifier = Modifier.height(18.dp))

            // ==========================================
            // Section 1: Profile & Budget Limits (INR)
            // ==========================================
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, ArthikaBorder, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                color = ArthikaBackground
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = ArthikaDarkGreen,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Profile & Spending Limit",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                color = ArthikaTextPrimary
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Profile Name
                    Text(
                        text = "Your Profile Name",
                        style = MaterialTheme.typography.labelMedium.copy(color = ArthikaTextSecondary)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = userNameText,
                        onValueChange = {
                            userNameText = it
                            if (it.trim().isNotBlank()) userNameError = null
                        },
                        isError = userNameError != null,
                        supportingText = {
                            if (userNameError != null) {
                                Text(
                                    text = userNameError ?: "",
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 11.sp
                                )
                            }
                        },
                        placeholder = { Text("e.g. Kamlesh Kumar") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_username_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = ArthikaCard,
                            unfocusedContainerColor = ArthikaCard,
                            focusedBorderColor = ArthikaPrimary,
                            unfocusedBorderColor = ArthikaBorder
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Monthly Spending Budget in INR
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Monthly Spending Budget (₹)",
                            style = MaterialTheme.typography.labelMedium.copy(color = ArthikaTextSecondary)
                        )
                        val previewAmount = monthlyLimitText.toDoubleOrNull() ?: 0.0
                        Text(
                            text = "Preview: ${IndianCurrencyFormatter.format(previewAmount, "₹")}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = ArthikaDarkGreen,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = monthlyLimitText,
                        onValueChange = { input ->
                            monthlyLimitText = input.filter { c -> c.isDigit() || c == '.' }
                            if (monthlyLimitText.toDoubleOrNull() != null) monthlyLimitError = null
                        },
                        prefix = {
                            Text(
                                text = "₹",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = ArthikaDarkGreen
                                )
                            )
                        },
                        isError = monthlyLimitError != null,
                        supportingText = {
                            if (monthlyLimitError != null) {
                                Text(
                                    text = monthlyLimitError ?: "",
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 11.sp
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_budget_limit_input"),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = ArthikaCard,
                            unfocusedContainerColor = ArthikaCard,
                            focusedBorderColor = ArthikaPrimary,
                            unfocusedBorderColor = ArthikaBorder
                        )
                    )

                    // Quick Preset Chips for Indian Rupees
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("25000" to "₹25K", "50000" to "₹50K", "100000" to "₹1 Lakh").forEach { (amount, label) ->
                            val isSelected = monthlyLimitText == amount
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) ArthikaDarkGreen else ArthikaCard)
                                    .border(
                                        1.dp,
                                        if (isSelected) ArthikaDarkGreen else ArthikaBorder,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        monthlyLimitText = amount
                                        monthlyLimitError = null
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = if (isSelected) Color.White else ArthikaTextPrimary,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Default Currency Display
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CurrencyRupee,
                                contentDescription = null,
                                tint = ArthikaDarkGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Currency Standard",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = ArthikaTextSecondary,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(ArthikaSoftGreen)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "INR (₹) • Indian Numbering",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = ArthikaDarkGreen,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // ==========================================
            // Section 2: Gemini AI & Model Configuration
            // ==========================================
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, ArthikaBorder, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                color = ArthikaBackground
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(ArthikaSoftGreen),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "AI Model",
                                    tint = ArthikaDarkGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Google Gemini AI Engine",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp,
                                        color = ArthikaTextPrimary
                                    )
                                )
                                Text(
                                    text = "Powers deep OCR bill extraction & intelligence",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = ArthikaTextSecondary,
                                        fontSize = 11.5.sp
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Model Selector
                    Text(
                        text = "Active AI Model",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = ArthikaTextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        GeminiConfig.AVAILABLE_MODELS.forEach { modelId ->
                            val isSelected = selectedModel == modelId
                            val isRecommended = modelId == GeminiConfig.DEFAULT_MODEL
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedModel = modelId
                                    apiKeyStatus = ApiKeyStatus.Idle
                                },
                                label = {
                                    Text(
                                        text = if (isRecommended) "$modelId (Recommended)" else modelId,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                },
                                leadingIcon = if (isSelected) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ArthikaSoftGreen,
                                    selectedLabelColor = ArthikaDarkGreen,
                                    selectedLeadingIconColor = ArthikaDarkGreen
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // API Key Field
                    Text(
                        text = "Gemini API Key",
                        style = MaterialTheme.typography.labelMedium.copy(color = ArthikaTextSecondary)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = apiKeyText,
                        onValueChange = {
                            apiKeyText = it
                            apiKeyStatus = ApiKeyStatus.Idle
                        },
                        placeholder = { Text("Paste Google AI Studio API Key (AIzaSy...)") },
                        visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { isApiKeyVisible = !isApiKeyVisible }) {
                                Icon(
                                    imageVector = if (isApiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (isApiKeyVisible) "Hide key" else "Show key",
                                    tint = ArthikaTextSecondary
                                )
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_api_key_input"),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = ArthikaCard,
                            unfocusedContainerColor = ArthikaCard,
                            focusedBorderColor = ArthikaPrimary,
                            unfocusedBorderColor = ArthikaBorder
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Test API Key button and get key link
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/app/apikey"))
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    // Ignore if no browser
                                }
                            }
                        ) {
                            Text(
                                text = "Get Free Gemini Key",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = ArthikaDarkGreen,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = ArthikaDarkGreen
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                coroutineScope.launch {
                                    apiKeyStatus = ApiKeyStatus.Checking
                                    apiKeyStatus = onTestApiKey(apiKeyText.trim(), selectedModel)
                                }
                            },
                            enabled = apiKeyText.isNotBlank() && apiKeyStatus !is ApiKeyStatus.Checking,
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, ArthikaDarkGreen),
                            modifier = Modifier.testTag("test_api_key_button")
                        ) {
                            if (apiKeyStatus is ApiKeyStatus.Checking) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = ArthikaDarkGreen
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Testing $selectedModel...",
                                    style = MaterialTheme.typography.bodySmall.copy(color = ArthikaDarkGreen)
                                )
                            } else {
                                Text(
                                    text = "Test Connection",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = ArthikaDarkGreen,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }
                        }
                    }

                    // Status Banners (Separated by Failure Category & Success)
                    AnimatedVisibility(
                        visible = apiKeyStatus !is ApiKeyStatus.Idle,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Spacer(modifier = Modifier.height(10.dp))
                        when (val status = apiKeyStatus) {
                            is ApiKeyStatus.Valid -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(ArthikaSoftGreen)
                                        .border(1.dp, ArthikaDarkGreen.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                        .padding(12.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.Top) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = ArthikaDarkGreen,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = "Connection Verified (HTTP 200)",
                                                style = MaterialTheme.typography.titleSmall.copy(
                                                    color = ArthikaDarkGreen,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                )
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "${status.message} • Latency: ${status.latencyMs}ms",
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    color = ArthikaDarkGreen,
                                                    fontSize = 11.5.sp
                                                )
                                            )
                                        }
                                    }
                                }
                            }

                            is ApiKeyStatus.Invalid.BadKey -> {
                                StatusAlertCard(
                                    icon = Icons.Default.ErrorOutline,
                                    title = "Invalid API Key",
                                    error = status.error,
                                    tip = status.actionableTip,
                                    bgColor = Color(0xFFFEE2E2),
                                    borderColor = Color(0xFFFCA5A5),
                                    accentColor = Color(0xFFDC2626),
                                    textColor = Color(0xFF991B1B)
                                )
                            }

                            is ApiKeyStatus.Invalid.QuotaExceeded -> {
                                StatusAlertCard(
                                    icon = Icons.Default.Warning,
                                    title = "Quota Limit Reached",
                                    error = status.error,
                                    tip = status.actionableTip,
                                    bgColor = Color(0xFFFEF3C7),
                                    borderColor = Color(0xFFFCD34D),
                                    accentColor = Color(0xFFD97706),
                                    textColor = Color(0xFF92400E)
                                )
                            }

                            is ApiKeyStatus.Invalid.ModelUnsupported -> {
                                StatusAlertCard(
                                    icon = Icons.Default.Info,
                                    title = "Model Unavailable",
                                    error = status.error,
                                    tip = status.actionableTip,
                                    bgColor = Color(0xFFEDE9FE),
                                    borderColor = Color(0xFFDDD6FE),
                                    accentColor = Color(0xFF7C3AED),
                                    textColor = Color(0xFF5B21B6)
                                )
                            }

                            is ApiKeyStatus.Invalid.NetworkError -> {
                                StatusAlertCard(
                                    icon = Icons.Default.WifiOff,
                                    title = "Network Connection Error",
                                    error = status.error,
                                    tip = status.actionableTip,
                                    bgColor = Color(0xFFEFF6FF),
                                    borderColor = Color(0xFFBFDBFE),
                                    accentColor = Color(0xFF2563EB),
                                    textColor = Color(0xFF1E40AF)
                                )
                            }

                            is ApiKeyStatus.Invalid.GenericError -> {
                                StatusAlertCard(
                                    icon = Icons.Default.ErrorOutline,
                                    title = "Connection Failed",
                                    error = status.error,
                                    tip = status.actionableTip,
                                    bgColor = Color(0xFFFEE2E2),
                                    borderColor = Color(0xFFFCA5A5),
                                    accentColor = Color(0xFFDC2626),
                                    textColor = Color(0xFF991B1B)
                                )
                            }

                            else -> {}
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // ==========================================
            // Section 3: Security Passcode Lock
            // ==========================================
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, ArthikaBorder, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                color = ArthikaBackground
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = "Security",
                                tint = ArthikaDarkGreen,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "4-Digit Passcode Lock",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp,
                                        color = ArthikaTextPrimary
                                    )
                                )
                                Text(
                                    text = "Lock app on backgrounding to secure records",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = ArthikaTextSecondary,
                                        fontSize = 11.5.sp
                                    )
                                )
                            }
                        }

                        Switch(
                            checked = isPinEnabled,
                            onCheckedChange = { isPinEnabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = ArthikaPrimary
                            ),
                            modifier = Modifier.testTag("security_pin_toggle")
                        )
                    }

                    if (isPinEnabled) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = pinCodeText,
                            onValueChange = {
                                if (it.length <= 4 && it.all { c -> c.isDigit() }) {
                                    pinCodeText = it
                                    if (it.length == 4) pinCodeError = null
                                }
                            },
                            label = { Text("Set 4-Digit Passcode") },
                            placeholder = { Text("e.g. 1234") },
                            isError = pinCodeError != null,
                            supportingText = {
                                if (pinCodeError != null) {
                                    Text(
                                        text = pinCodeError ?: "",
                                        color = MaterialTheme.colorScheme.error,
                                        fontSize = 11.sp
                                    )
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("pin_code_input"),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = ArthikaCard,
                                unfocusedContainerColor = ArthikaCard,
                                focusedBorderColor = ArthikaPrimary,
                                unfocusedBorderColor = ArthikaBorder
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ==========================================
            // Section 4: Save Preferences Button
            // ==========================================
            Button(
                onClick = {
                    var hasError = false

                    val cleanName = userNameText.trim()
                    if (cleanName.isBlank()) {
                        userNameError = "Profile name cannot be blank."
                        hasError = true
                    }

                    val limit = monthlyLimitText.toDoubleOrNull()
                    if (limit == null || limit < 0) {
                        monthlyLimitError = "Enter a valid budget amount (>= 0)."
                        hasError = true
                    }

                    if (isPinEnabled && pinCodeText.length != 4) {
                        pinCodeError = "Please enter a 4-digit passcode."
                        hasError = true
                    }

                    if (hasError) return@Button

                    onSaveSettings(
                        limit ?: 50000.0,
                        currencySymbolText.trim().ifBlank { "₹" },
                        pinCodeText.trim(),
                        isPinEnabled,
                        cleanName,
                        apiKeyText.trim(),
                        selectedModel
                    )
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ArthikaPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_settings_button")
            ) {
                Text(
                    text = "Save Preferences & AI Config",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StatusAlertCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    error: String,
    tip: String,
    bgColor: Color,
    borderColor: Color,
    accentColor: Color,
    textColor: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        color = textColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = textColor,
                        fontSize = 11.5.sp
                    )
                )
                if (tip.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tip: $tip",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = textColor.copy(alpha = 0.85f),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }
        }
    }
}
