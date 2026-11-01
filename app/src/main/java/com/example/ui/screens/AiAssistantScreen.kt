package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.entity.ChatMessageEntity
import com.example.ui.FinanceViewModel
import com.example.ui.theme.ArthikaBackground
import com.example.ui.theme.ArthikaBorder
import com.example.ui.theme.ArthikaCard
import com.example.ui.theme.ArthikaDarkGreen
import com.example.ui.theme.ArthikaIncome
import com.example.ui.theme.ArthikaPrimary
import com.example.ui.theme.ArthikaSoftGreen
import com.example.ui.theme.ArthikaTextPrimary
import com.example.ui.theme.ArthikaTextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun AiAssistantScreen(
    viewModel: FinanceViewModel,
    modifier: Modifier = Modifier
) {
    val messages by viewModel.chatMessages.collectAsState()
    val isThinking by viewModel.isAiThinking.collectAsState()
    val pendingMessage by viewModel.pendingUserMessage.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val isImeVisible = WindowInsets.isImeVisible

    // Smooth scroll to bottom whenever messages change, thinking state changes, user sends a message, or keyboard opens
    LaunchedEffect(messages.size, isThinking, pendingMessage, isImeVisible) {
        val totalCount = messages.size + (if (pendingMessage != null) 1 else 0) + (if (isThinking) 1 else 0) + 1
        if (totalCount > 1) {
            listState.animateScrollToItem(totalCount - 1)
        }
    }

    // Voice recognition launcher
    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                inputText = ""
                viewModel.sendAiMessage(spokenText)
            }
        }
    }

    val suggestedQuestions = listOf(
        "How much did I spend yesterday?",
        "What is my biggest expense category?",
        "Who did I pay the most money to?",
        "Show transactions from last week",
        "How much did I spend on food this month?",
        "Compare my September and October expenses",
        "Generate a monthly financial summary"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ArthikaBackground)
            .padding(horizontal = 18.dp)
            .imePadding()
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // Screen Top Bar with ARTHIKA logo
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White)
                        .border(1.dp, ArthikaBorder, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_arthika_logo),
                        contentDescription = "AI",
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "ARTHIKA Intelligence",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Medium,
                            fontSize = 16.sp,
                            color = ArthikaDarkGreen
                        )
                    )
                    Text(
                        text = "Connected to live transaction database",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = ArthikaIncome,
                            fontSize = 11.5.sp
                        )
                    )
                }
            }

            IconButton(
                onClick = { viewModel.clearChatHistory() },
                modifier = Modifier.testTag("clear_chat_button")
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteSweep,
                    contentDescription = "Clear Chat",
                    tint = ArthikaTextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Suggested Queries Horizontal Scroll
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            suggestedQuestions.forEach { prompt ->
                Surface(
                    onClick = { viewModel.sendAiMessage(prompt) },
                    shape = RoundedCornerShape(14.dp),
                    color = ArthikaCard,
                    border = androidx.compose.foundation.BorderStroke(1.dp, ArthikaBorder),
                    modifier = Modifier.testTag("suggested_prompt_${prompt.take(10)}")
                ) {
                    Text(
                        text = prompt,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            color = ArthikaTextPrimary,
                            fontWeight = FontWeight.Medium
                        ),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Messages List
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                val isUser = msg.sender == "user"
                ChatBubble(
                    message = msg,
                    isUser = isUser,
                    onGeneratePdf = { viewModel.generatePdfReport("Monthly Statement") }
                )
            }

            // Instant preview of user's typed message while saving or processing
            if (pendingMessage != null && messages.none { it.text == pendingMessage && it.sender == "user" }) {
                item(key = "pending_user_msg") {
                    ChatBubble(
                        message = ChatMessageEntity(
                            id = -1L,
                            sender = "user",
                            text = pendingMessage!!,
                            timestamp = System.currentTimeMillis()
                        ),
                        isUser = true,
                        onGeneratePdf = {}
                    )
                }
            }

            if (isThinking) {
                item(key = "ai_thinking") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(ArthikaSoftGreen)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = ArthikaPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Analyzing database...",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = ArthikaDarkGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // Message Input Row - docked neatly above keyboard or bottom nav
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier
                    .weight(1f)
                    .testTag("ai_chat_input"),
                placeholder = {
                    Text(
                        "Ask ARTHIKA about your spending...",
                        style = MaterialTheme.typography.bodyMedium.copy(color = ArthikaTextSecondary)
                    )
                },
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = ArthikaTextPrimary,
                    fontSize = 14.sp
                ),
                singleLine = false,
                maxLines = 4,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Send
                ),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (inputText.isNotBlank()) {
                            val text = inputText
                            inputText = ""
                            viewModel.sendAiMessage(text)
                        }
                    }
                ),
                shape = RoundedCornerShape(22.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = ArthikaTextPrimary,
                    unfocusedTextColor = ArthikaTextPrimary,
                    cursorColor = ArthikaPrimary,
                    focusedContainerColor = ArthikaCard,
                    unfocusedContainerColor = ArthikaCard,
                    focusedBorderColor = ArthikaPrimary,
                    unfocusedBorderColor = ArthikaBorder
                )
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Voice input button
            Surface(
                onClick = {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(
                            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                        )
                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Ask ARTHIKA...")
                    }
                    try {
                        voiceLauncher.launch(intent)
                    } catch (e: Exception) {
                        // voice recognition not supported
                    }
                },
                shape = CircleShape,
                color = ArthikaSoftGreen,
                modifier = Modifier
                    .size(44.dp)
                    .testTag("voice_input_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Voice Input",
                        tint = ArthikaDarkGreen,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Send button
            Surface(
                onClick = {
                    if (inputText.isNotBlank()) {
                        val text = inputText
                        inputText = ""
                        viewModel.sendAiMessage(text)
                    }
                },
                shape = CircleShape,
                color = if (inputText.isNotBlank()) ArthikaPrimary else ArthikaBorder,
                modifier = Modifier
                    .size(44.dp)
                    .testTag("send_ai_message_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(
    message: ChatMessageEntity,
    isUser: Boolean,
    onGeneratePdf: () -> Unit
) {
    val bubbleColor = if (isUser) ArthikaPrimary else ArthikaCard
    val textColor = if (isUser) Color.White else ArthikaTextPrimary
    val alignment = if (isUser) Alignment.End else Alignment.Start

    val timeFormatted = SimpleDateFormat("h:mm a", Locale.US).format(Date(message.timestamp))

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isUser) 18.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 18.dp
            ),
            color = bubbleColor,
            border = if (!isUser) androidx.compose.foundation.BorderStroke(1.dp, ArthikaBorder) else null,
            modifier = Modifier.widthIn(max = 310.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = textColor,
                        lineHeight = 21.sp,
                        fontSize = 14.sp
                    )
                )

                // Inline stats badge if available
                if (!message.statsMetadata.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isUser) Color(0x33FFFFFF) else ArthikaSoftGreen)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = message.statsMetadata,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (isUser) Color.White else ArthikaDarkGreen,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }

                // If message offers PDF generation
                if (message.isPdfReportOffer) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        onClick = onGeneratePdf,
                        shape = RoundedCornerShape(12.dp),
                        color = ArthikaDarkGreen,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("inline_generate_pdf_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PictureAsPdf,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Export Statement PDF",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = timeFormatted,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = if (isUser) Color(0xCCFFFFFF) else ArthikaTextSecondary,
                        fontSize = 10.sp
                    ),
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}
