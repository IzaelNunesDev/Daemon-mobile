package com.example.daemonmobile.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.daemonmobile.ui.components.*
import com.example.daemonmobile.ui.theme.*
import com.example.daemonmobile.ui.viewmodels.ChatMessage
import com.example.daemonmobile.ui.viewmodels.ChatViewModel
import com.example.daemonmobile.ui.viewmodels.ChatViewModelFactory
import com.example.daemonmobile.ui.viewmodels.PlanStatus

@Composable
fun ChatScreen(
    onNavigateBack: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: ChatViewModel = viewModel(factory = ChatViewModelFactory(context))
    val uiState by viewModel.uiState.collectAsState()

    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        viewModel.connect()
    }

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg0)
            .statusBarsPadding()
    ) {
        // ═══════════════════════════════════════════════════════
        // App Header — matching JSX AppHeader
        // ═══════════════════════════════════════════════════════
        AppHeader(
            isConnected = uiState.isConnected,
            connectionStatus = uiState.connectionStatus,
            currentMode = uiState.currentMode,
            currentModel = uiState.currentModel,
            onModeToggle = { mode ->
                viewModel.setMode(mode)
            },
            onSettingsClick = onNavigateToSettings
        )

        // ═══════════════════════════════════════════════════════
        // Quick Actions Bar
        // ═══════════════════════════════════════════════════════
        QuickActionsBar(
            onAction = { command -> viewModel.sendQuickAction(command) }
        )

        // ═══════════════════════════════════════════════════════
        // Chat Messages
        // ═══════════════════════════════════════════════════════
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(vertical = 10.dp)
        ) {
            // Empty state
            if (uiState.messages.isEmpty() && !uiState.isThinking) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.alpha(0.35f)
                        ) {
                            Text(
                                text = "⬡",
                                color = Indigo,
                                fontSize = 38.sp
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "Daemon ativo",
                                color = T3,
                                fontSize = 10.sp,
                                fontFamily = MonoFamily,
                                lineHeight = 18.sp
                            )
                            Text(
                                text = "Envie uma mensagem",
                                color = T3,
                                fontSize = 10.sp,
                                fontFamily = MonoFamily,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }

            items(uiState.messages) { msg ->
                when (msg) {
                    is ChatMessage.UserMessage -> UserMessageBubble(msg.text)
                    is ChatMessage.SystemMessage -> SystemMessageBubble(msg.text)
                    is ChatMessage.AssistantMessage -> AssistantChatBubble(
                        text = msg.text,
                        toolName = msg.toolName,
                        toolCmd = msg.toolCmd,
                        isSuccess = msg.isSuccess,
                        timestamp = msg.timestamp
                    )
                    is ChatMessage.StreamChunkMsg -> AssistantChatBubble(
                        text = msg.accumulated
                    )
                    is ChatMessage.ToolUseMsg -> ToolCard(
                        toolName = msg.toolName,
                        toolId = msg.toolId,
                        parameters = msg.parameters,
                        resultStatus = msg.resultStatus,
                        resultOutput = msg.resultOutput,
                        resultError = msg.resultError
                    )
                    is ChatMessage.StreamOutputMsg -> StreamBubble(msg.lines)
                    is ChatMessage.PromptInputMsg -> {
                        if (msg.isAnswered) {
                            // Show answered state
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.Start
                            ) {
                                Text(
                                    text = if (msg.promptType == "password") "🔐 Senha enviada" else "⚡ Confirmado: ${msg.answeredText}",
                                    color = T4,
                                    fontSize = 9.sp,
                                    fontFamily = MonoFamily
                                )
                            }
                        } else {
                            ConfirmationCard(
                                promptType = msg.promptType,
                                label = msg.label,
                                options = msg.options,
                                hint = msg.hint,
                                onSubmit = { response ->
                                    viewModel.sendPromptResponse(response)
                                }
                            )
                        }
                    }
                    is ChatMessage.ThinkingMessage -> ThinkingDots(stage = msg.stage)
                    is ChatMessage.StreamErrorMsg -> ErrorBubble(text = msg.message)
                    is ChatMessage.SessionInfoMsg -> {
                        SystemMessageBubble("📡 Sessão: ${msg.model}")
                    }
                    is ChatMessage.PlanCardMsg -> PlanCard(
                        plan = msg.plan,
                        status = msg.status,
                        onApprove = { viewModel.approvePlan(msg.plan) },
                        onReject = { viewModel.rejectPlan(msg.plan.id) }
                    )
                    is ChatMessage.StepProgress -> StepProgressItem(
                        stepIndex = msg.stepIndex,
                        description = msg.description,
                        output = msg.output,
                        error = msg.error,
                        isCompleted = msg.isCompleted
                    )
                }
            }
        }

        // ═══════════════════════════════════════════════════════
        // Input Bar — matching JSX InputBar
        // ═══════════════════════════════════════════════════════
        InputBar(
            text = inputText,
            onTextChange = { inputText = it },
            isDisabled = uiState.isWaitingInput,
            isConnected = uiState.isConnected,
            currentMode = uiState.currentMode,
            onSend = {
                val text = inputText.trim()
                if (text.isNotBlank()) {
                    viewModel.sendMessage(text)
                    inputText = ""
                } else if (!uiState.isConnected) {
                    viewModel.retryConnection()
                }
            }
        )
    }
}

// ═══════════════════════════════════════════════════════════════
// App Header — SLED logo, status, mode toggle
// ═══════════════════════════════════════════════════════════════
@Composable
private fun AppHeader(
    isConnected: Boolean,
    connectionStatus: String,
    currentMode: String,
    currentModel: String,
    onModeToggle: (String) -> Unit,
    onSettingsClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(listOf(Bg2, Bg1))
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // SLED logo icon
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(
                    Brush.linearGradient(listOf(Indigo.copy(alpha = 0.8f), Violet.copy(alpha = 0.8f))),
                    RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text("⬡", fontSize = 16.sp, color = Color.White)
        }

        // Name + status
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "SLED",
                color = T1,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = MonoFamily,
                letterSpacing = 1.sp
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 2.dp)
            ) {
                if (isConnected) {
                    // Pulsing green dot
                    val infiniteTransition = rememberInfiniteTransition(label = "conn")
                    val pulseAlpha by infiniteTransition.animateFloat(
                        initialValue = 0.5f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(2000, easing = EaseInOut),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "pulse"
                    )
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .alpha(pulseAlpha)
                            .clip(CircleShape)
                            .background(Neon)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = connectionStatus,
                        color = Neon,
                        fontSize = 9.sp,
                        fontFamily = MonoFamily
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(Red)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = connectionStatus,
                        color = T3,
                        fontSize = 9.sp,
                        fontFamily = MonoFamily
                    )
                }
            }
        }

        // Mode toggle pill
        Row(
            modifier = Modifier
                .background(Bg0, RoundedCornerShape(20.dp))
                .padding(2.dp)
        ) {
            listOf("Padrão", "Avançado").forEach { mode ->
                val isActive = currentMode == mode
                val bgBrush = if (isActive) {
                    if (mode == "Avançado") Brush.linearGradient(listOf(Indigo, Violet))
                    else Brush.linearGradient(listOf(Bg2, Bg2))
                } else {
                    Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                }

                Box(
                    modifier = Modifier
                        .background(bgBrush, RoundedCornerShape(18.dp))
                        .clickable { onModeToggle(mode) }
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = mode,
                        color = if (isActive) T1 else T3,
                        fontSize = 9.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = MonoFamily
                    )
                }
            }
        }

        // Settings icon
        Box(
            modifier = Modifier
                .size(28.dp)
                .clickable { onSettingsClick() },
            contentAlignment = Alignment.Center
        ) {
            Text("⚙", color = T3, fontSize = 14.sp)
        }
    }

    // Bottom border
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(B1)
    )
}

// ═══════════════════════════════════════════════════════════════
// Input Bar — matching JSX InputBar
// ═══════════════════════════════════════════════════════════════
@Composable
private fun InputBar(
    text: String,
    onTextChange: (String) -> Unit,
    isDisabled: Boolean,
    isConnected: Boolean,
    currentMode: String,
    onSend: () -> Unit
) {
    // Top border
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(B1)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Bg1)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .navigationBarsPadding()
            .imePadding(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Text field
        OutlinedTextField(
            value = text,
            onValueChange = { if (!isDisabled) onTextChange(it) },
            modifier = Modifier.weight(1f),
            enabled = !isDisabled,
            placeholder = {
                Text(
                    text = if (isDisabled) "Aguardando resposta..." else "Mensagem ao SLED...",
                    color = T4,
                    fontSize = 11.sp,
                    fontFamily = MonoFamily
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Bg0,
                unfocusedContainerColor = Bg0,
                disabledContainerColor = Bg0,
                focusedBorderColor = B1,
                unfocusedBorderColor = B1,
                disabledBorderColor = B1,
                focusedTextColor = T1,
                unfocusedTextColor = T1,
                disabledTextColor = T3,
                cursorColor = Indigo
            ),
            textStyle = androidx.compose.ui.text.TextStyle(
                fontFamily = MonoFamily,
                fontSize = 12.sp
            ),
            shape = RoundedCornerShape(14.dp),
            singleLine = false,
            maxLines = 3,
            trailingIcon = {
                Text(
                    text = "⊕",
                    color = T4,
                    fontSize = 12.sp
                )
            }
        )

        // Send button
        val canSend = text.isNotBlank() || !isConnected
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(
                    if (canSend && !isDisabled) Brush.linearGradient(listOf(Indigo, Violet))
                    else Brush.linearGradient(listOf(Bg2, Bg2)),
                    RoundedCornerShape(10.dp)
                )
                .clickable {
                    if (!isDisabled) onSend()
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isConnected) "▶" else "↻",
                color = if (canSend && !isDisabled) Color.White else T3,
                fontSize = 12.sp
            )
        }
    }
}
