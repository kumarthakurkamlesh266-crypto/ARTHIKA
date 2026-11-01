package com.example.ui.dialogs

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import com.example.data.ApiKeyStatus
import com.example.data.entity.BudgetEntity
import com.example.ui.theme.ArthikaBackground
import com.example.ui.theme.ArthikaBorder
import com.example.ui.theme.ArthikaCard
import com.example.ui.theme.ArthikaDarkGreen
import com.example.ui.theme.ArthikaPrimary
import com.example.ui.theme.ArthikaSoftGreen
import com.example.ui.theme.ArthikaTextPrimary
import com.example.ui.theme.ArthikaTextSecondary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetSecuritySettingsDialog(
    isOpen: Boolean,
    currentBudget: BudgetEntity,
    currentApiKey: String,
    onDismiss: () -> Unit,
    onTestApiKey: suspend (String) -> ApiKeyStatus,
    onSaveSettings: (
        monthlyLimit: Double,
        currencySymbol: String,
        pinCode: String,
        isPinEnabled: Boolean,
        userName: String,
        geminiApiKey: String
    ) -> Unit
) {
    if (!isOpen) return

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var monthlyLimitText by remember { mutableStateOf(currentBudget.monthlyLimit.toString()) }
    var userNameText by remember { mutableStateOf(currentBudget.userName) }
    var currencySymbolText by remember { mutableStateOf(currentBudget.currencySymbol) }
    var isPinEnabled by remember { mutableStateOf(currentBudget.isPinEnabled) }
    var pinCodeText by remember { mutableStateOf(currentBudget.pinCode) }

    // API Key states
    var apiKeyText by remember { mutableStateOf(currentApiKey) }
    var isApiKeyVisible by remember { mutableStateOf(false) }
    var apiKeyStatus by remember { mutableStateOf<ApiKeyStatus>(ApiKeyStatus.Idle) }

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
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(ArthikaSoftGreen),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Savings,
                            contentDescription = "Budget",
                            tint = ArthikaDarkGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Settings & AI Config",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 19.sp,
                            color = ArthikaTextPrimary
                        )
                    )
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

            // User Profile Name
            Text(
                text = "Your Name",
                style = MaterialTheme.typography.labelMedium.copy(color = ArthikaTextSecondary)
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = userNameText,
                onValueChange = { userNameText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_username_input"),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ArthikaPrimary,
                    unfocusedBorderColor = ArthikaBorder
                )
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Monthly Spending Limit
            Text(
                text = "Monthly Spending Budget ($)",
                style = MaterialTheme.typography.labelMedium.copy(color = ArthikaTextSecondary)
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = monthlyLimitText,
                onValueChange = { monthlyLimitText = it.filter { c -> c.isDigit() || c == '.' } },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_budget_limit_input"),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ArthikaPrimary,
                    unfocusedBorderColor = ArthikaBorder
                )
            )

            Spacer(modifier = Modifier.height(20.dp))

            // --- Google Gemini AI & OCR API Key Section ---
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
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(ArthikaSoftGreen),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "AI Key",
                                    tint = ArthikaDarkGreen,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Google Gemini API Key",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp,
                                        color = ArthikaTextPrimary
                                    )
                                )
                                Text(
                                    text = "Powers deep OCR bill scanning & AI analytics",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = ArthikaTextSecondary,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = apiKeyText,
                        onValueChange = {
                            apiKeyText = it
                            apiKeyStatus = ApiKeyStatus.Idle
                        },
                        label = { Text("Enter Gemini API Key") },
                        placeholder = { Text("AIzaSy...") },
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

                    // Test API Key button and status indicator
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
                                text = "Get Free API Key",
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
                                    apiKeyStatus = onTestApiKey(apiKeyText)
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
                                    text = "Testing...",
                                    style = MaterialTheme.typography.bodySmall.copy(color = ArthikaDarkGreen)
                                )
                            } else {
                                Text(
                                    text = "Test Connection",
                                    style = MaterialTheme.typography.bodySmall.copy(color = ArthikaDarkGreen)
                                )
                            }
                        }
                    }

                    // Status Banner
                    AnimatedVisibility(visible = apiKeyStatus !is ApiKeyStatus.Idle) {
                        Spacer(modifier = Modifier.height(8.dp))
                        when (val status = apiKeyStatus) {
                            is ApiKeyStatus.Valid -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(ArthikaSoftGreen)
                                        .padding(8.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = ArthikaDarkGreen,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = status.message,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = ArthikaDarkGreen,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        )
                                    }
                                }
                            }
                            is ApiKeyStatus.Invalid -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFFEE2E2))
                                        .padding(8.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.ErrorOutline,
                                            contentDescription = null,
                                            tint = Color(0xFFDC2626),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = status.error,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = Color(0xFF991B1B),
                                                fontSize = 11.sp
                                            )
                                        )
                                    }
                                }
                            }
                            else -> {}
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Security PIN Section
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = ArthikaSoftGreen
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
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 15.sp,
                                        color = ArthikaDarkGreen
                                    )
                                )
                                Text(
                                    text = "Protect personal financial logs",
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
                                }
                            },
                            label = { Text("Set 4-Digit Passcode") },
                            placeholder = { Text("e.g. 1234") },
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

            // Save Settings
            Button(
                onClick = {
                    val limit = monthlyLimitText.toDoubleOrNull() ?: 2800.0
                    onSaveSettings(
                        limit,
                        currencySymbolText.trim().ifBlank { "$" },
                        pinCodeText.trim(),
                        isPinEnabled,
                        userNameText.trim().ifBlank { "Alex" },
                        apiKeyText.trim()
                    )
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ArthikaPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("save_settings_button")
            ) {
                Text(
                    text = "Save Preferences & API Key",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
