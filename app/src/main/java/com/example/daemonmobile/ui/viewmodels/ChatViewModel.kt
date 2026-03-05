package com.example.daemonmobile.ui.viewmodels

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.daemonmobile.data.local.SledPreferences
import com.example.daemonmobile.data.models.*
import com.example.daemonmobile.data.websocket.SledWebSocketClient
import com.example.daemonmobile.services.SledForegroundService
import com.google.gson.JsonElement
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ChatViewModel(private val context: Context) : ViewModel() {
    companion object {
        private val SUPPORTED_QUICK_COMMANDS = setOf(
            "get_status",
            "get_logs",
            "get_model_info",
            "get_settings"
        )
    }

    private val prefs = SledPreferences(context)
    private val wsClient = SledWebSocketClient()

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState

    // StreamChunk accumulator
    private val streamBuffer = StringBuilder()

    // Reconnect control
    private var reconnectJob: Job? = null
    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 5
    private val baseDelayMs = 5000L
    private val maxDelayMs = 60000L
    private var wasEverConnected = false
    private var suppressAutoReconnect = false

    init {
        loadHistory()
        wsClient.onConnectionStateChanged = { isConnected ->
            if (isConnected) {
                reconnectAttempts = 0
                wasEverConnected = true
                reconnectJob?.cancel()
                _uiState.value = _uiState.value.copy(
                    isConnected = true,
                    connectionStatus = "Conectado"
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isConnected = false,
                    connectionStatus = "Desconectado"
                )
                if (!suppressAutoReconnect) {
                    scheduleReconnect()
                }
            }
        }

        wsClient.onAuthResult = { result ->
            if (result.success) {
                addMessage(ChatMessage.SystemMessage("🟢 ${result.message ?: "Conectado ao SLED"}"))
                // Store the device name from connection
                _uiState.value = _uiState.value.copy(
                    deviceName = prefs.host ?: ""
                )
            } else {
                addMessage(ChatMessage.SystemMessage("❌ Autenticação falhou: ${result.message}"))
            }
        }

        wsClient.onThinkingReceived = { thinking ->
            handleThinking(thinking)
        }

        wsClient.onSessionInit = { session ->
            _uiState.value = _uiState.value.copy(
                sessionId = session.sessionId,
                currentModel = session.model
            )
        }

        wsClient.onStreamChunk = { chunk ->
            handleStreamChunk(chunk)
        }

        wsClient.onToolUse = { toolUse ->
            removeThinking()
            addMessage(ChatMessage.ToolUseMsg(
                toolName = toolUse.toolName,
                toolId = toolUse.toolId,
                parameters = toolUse.parameters?.let { formatToolParameters(it) }
            ))
        }

        wsClient.onToolResult = { result ->
            // Find matching ToolUseMsg and update it
            val msgs = _uiState.value.messages.toMutableList()
            val idx = msgs.indexOfLast {
                it is ChatMessage.ToolUseMsg && it.toolId == result.toolId
            }
            if (idx >= 0) {
                val existing = msgs[idx] as ChatMessage.ToolUseMsg
                msgs[idx] = existing.copy(
                    resultStatus = result.status,
                    resultOutput = result.output,
                    resultError = result.error
                )
                _uiState.value = _uiState.value.copy(messages = msgs)
            }

            // If there's multiline output, also add as StreamOutputMsg
            val output = result.output
            if (output != null && output.contains("\n")) {
                addMessage(ChatMessage.StreamOutputMsg(output.split("\n")))
            }
        }

        wsClient.onPromptInput = { prompt ->
            removeThinking()
            val cId = prompt.correlationId ?: "unknown_${System.currentTimeMillis()}"
            val newPending = _uiState.value.pendingActions + cId
            _uiState.value = _uiState.value.copy(pendingActions = newPending)
            addMessage(ChatMessage.PromptInputMsg(
                promptType = prompt.promptType,
                label = prompt.label,
                options = prompt.options,
                hint = prompt.hint,
                correlationId = prompt.correlationId
            ))
        }

        wsClient.onStreamError = { error ->
            removeThinking()
            _uiState.value = _uiState.value.copy(
                isThinking = false,
                thinkingStage = "",
                pendingActions = emptySet()
            )
            addMessage(ChatMessage.StreamErrorMsg(
                severity = error.severity,
                message = error.message
            ))
        }

        wsClient.onToolApprovalRequest = { req ->
            removeThinking()
            val cId = req.correlationId ?: "unknown_${System.currentTimeMillis()}"
            val newPending = _uiState.value.pendingActions + cId
            _uiState.value = _uiState.value.copy(pendingActions = newPending)
            addMessage(ChatMessage.ToolApprovalRequestMsg(
                toolId = req.toolId,
                toolName = req.toolName,
                command = req.command,
                riskLevel = req.riskLevel,
                correlationId = req.correlationId
            ))
        }

        wsClient.onAskUser = { req ->
            removeThinking()
            val cId = req.correlationId ?: "unknown_${System.currentTimeMillis()}"
            val newPending = _uiState.value.pendingActions + cId
            _uiState.value = _uiState.value.copy(pendingActions = newPending)
            addMessage(ChatMessage.AskUserMsg(
                title = req.title,
                questions = req.questions,
                correlationId = req.correlationId
            ))
        }

        wsClient.onBrowserAuth = { req ->
            removeThinking()
            val cId = req.correlationId ?: "unknown_${System.currentTimeMillis()}"
            val newPending = _uiState.value.pendingActions + cId
            _uiState.value = _uiState.value.copy(pendingActions = newPending)
            addMessage(ChatMessage.BrowserAuthMsg(
                url = req.url,
                code = req.code,
                instruction = req.instruction,
                correlationId = req.correlationId
            ))
        }

        wsClient.onChatMessageReceived = { chatMsg ->
            removeThinking()
            streamBuffer.clear()
            _uiState.value = _uiState.value.copy(pendingActions = emptySet())

            when (chatMsg.messageType) {
                "plan_summary" -> {
                    val metadata = chatMsg.metadata
                    if (metadata != null) {
                        try {
                            val plan = parsePlanFromMetadata(metadata)
                            addMessage(ChatMessage.PlanCardMsg(plan))
                            if (prefs.autoApproveLowRisk && plan.riskLevel.lowercase() == "low") {
                                viewModelScope.launch {
                                    delay(500)
                                    approvePlan(plan)
                                    addMessage(ChatMessage.SystemMessage("⚡ Plano auto-aprovado (risco baixo)"))
                                }
                            }
                        } catch (e: Exception) {
                            addMessage(ChatMessage.AssistantMessage(text = chatMsg.text))
                        }
                    } else {
                        addMessage(ChatMessage.AssistantMessage(text = chatMsg.text))
                    }
                }
                else -> {
                    addMessage(ChatMessage.AssistantMessage(
                        text = chatMsg.text,
                        timestamp = chatMsg.timestamp
                    ))
                }
            }
        }

        wsClient.onResponseReceived = { response ->
            handleCommandResponse(response)
        }

        wsClient.onModelInfo = { info ->
            _uiState.value = _uiState.value.copy(currentModel = info.currentModel)
        }
    }

    // ── StreamChunk accumulation ──────────────────────────────
    private fun handleStreamChunk(chunk: StreamChunkPayload) {
        if (chunk.done) {
            // Daemon does NOT emit ChatMessage after stream ends (§13 ARCHITECTURE.md)
            // So we must convert the accumulated text to AssistantMessage ourselves
            if (streamBuffer.isNotEmpty()) {
                val finalText = streamBuffer.toString()
                val msgs = _uiState.value.messages.filterNot { it is ChatMessage.StreamChunkMsg }
                _uiState.value = _uiState.value.copy(messages = msgs)
                addMessage(ChatMessage.AssistantMessage(text = finalText))
                streamBuffer.clear()
            }
            removeThinking()
            // ── isThinking = false when stream completes ──
            _uiState.value = _uiState.value.copy(
                isThinking = false,
                thinkingStage = "",
                pendingActions = emptySet()
            )
            return
        }

        streamBuffer.append(chunk.content)
        
        // Apenas remover a animação de "pensando", mas MANTER isThinking = true
        // para que o botão "Interromper" continue visível durante execução
        val filteredMsgs = _uiState.value.messages.filterNot { it is ChatMessage.ThinkingMessage }
        _uiState.value = _uiState.value.copy(messages = filteredMsgs)

        // Update or create StreamChunkMsg
        val msgs = _uiState.value.messages.toMutableList()
        val existingIdx = msgs.indexOfLast { it is ChatMessage.StreamChunkMsg }
        val newMsg = ChatMessage.StreamChunkMsg(streamBuffer.toString())
        if (existingIdx >= 0) {
            msgs[existingIdx] = newMsg
        } else {
            msgs.add(newMsg)
        }
        _uiState.value = _uiState.value.copy(messages = msgs)
    }

    private fun handleThinking(thinking: ThinkingPayload) {
        val msgs = _uiState.value.messages.toMutableList()
        val existingIdx = msgs.indexOfLast { it is ChatMessage.ThinkingMessage }
        if (existingIdx >= 0) {
            msgs[existingIdx] = ChatMessage.ThinkingMessage(thinking.stage)
        } else {
            msgs.add(ChatMessage.ThinkingMessage(thinking.stage))
        }
        _uiState.value = _uiState.value.copy(
            messages = msgs,
            isThinking = true,
            thinkingStage = thinking.stage
        )
    }

    private fun removeThinking() {
        val msgs = _uiState.value.messages.filterNot { it is ChatMessage.ThinkingMessage }
        _uiState.value = _uiState.value.copy(
            messages = msgs,
            isThinking = false,
            thinkingStage = ""
        )
    }

    // ── Public API ────────────────────────────────────────────
    fun connect() {
        suppressAutoReconnect = false
        val host = prefs.host
        val port = prefs.port
        val secret = prefs.secret
        val deviceId = prefs.deviceId

        if (host == null || secret == null) {
            addMessage(ChatMessage.SystemMessage("⚠️ Dados de conexão não encontrados. Faça o pareamento novamente."))
            return
        }
        _uiState.value = _uiState.value.copy(connectionStatus = "Conectando...")
        try {
            SledForegroundService.start(context)
        } catch (e: Exception) {
            Log.e("ChatVM", "Failed to start foreground service", e)
        }
        wsClient.connect(host, port, secret, deviceId)
    }

    fun sendMessage(text: String) {
        addMessage(ChatMessage.UserMessage(text))
        if (!_uiState.value.isConnected) {
            addMessage(ChatMessage.SystemMessage("⚠️ Sem conexão. Reconectando..."))
            retryConnection()
            return
        }
        _uiState.value = _uiState.value.copy(isThinking = true, thinkingStage = "Processando...")
        streamBuffer.clear()
        wsClient.sendMessage(text)
    }

    fun sendPromptResponse(response: String) {
        // Mark prompt as answered and extract correlationId
        var correlationId: String? = null
        val msgs = _uiState.value.messages.toMutableList()
        val idx = msgs.indexOfLast { it is ChatMessage.PromptInputMsg && !(it as ChatMessage.PromptInputMsg).isAnswered }
        if (idx >= 0) {
            val prompt = msgs[idx] as ChatMessage.PromptInputMsg
            correlationId = prompt.correlationId
            val newPending = _uiState.value.pendingActions - (correlationId ?: "")
            msgs[idx] = prompt.copy(isAnswered = true, answeredText = response)
            _uiState.value = _uiState.value.copy(messages = msgs, pendingActions = newPending)
        }
        // Send response via canonical StdinResponse with correlationId when available
        if (correlationId != null) {
            wsClient.sendStdinResponse(correlationId, confirmed = true, answers = mapOf("text" to response))
        } else {
            wsClient.sendSimpleStdinResponse(response)
        }
        _uiState.value = _uiState.value.copy(isThinking = true, thinkingStage = "Processando...")
    }

    fun sendToolApproval(toolId: String, choice: String) {
        var correlationId: String? = null
        val msgs = _uiState.value.messages.toMutableList()
        val idx = msgs.indexOfLast { it is ChatMessage.ToolApprovalRequestMsg && !(it as ChatMessage.ToolApprovalRequestMsg).isAnswered }
        if (idx >= 0) {
            val msg = msgs[idx] as ChatMessage.ToolApprovalRequestMsg
            if (msg.toolId == toolId) {
                correlationId = msg.correlationId
                val newPending = _uiState.value.pendingActions - (correlationId ?: "")
                msgs[idx] = msg.copy(isAnswered = true, choice = choice)
                _uiState.value = _uiState.value.copy(messages = msgs, pendingActions = newPending)
            }
        }
        // UI emits: once | session | deny. Keep legacy aliases for compatibility.
        val normalizedChoice = choice.trim().lowercase()
        val outcome = when (normalizedChoice) {
            "session" -> "proceed_always"
            "deny", "no", "n", "nao", "não" -> "cancel"
            else -> "proceed_once" // "once", "yes", "approve", etc.
        }
        val approved = outcome != "cancel"
        if (correlationId != null) {
            wsClient.sendToolApprovalResponse(correlationId, approved, outcome)
        } else {
            Log.w("ChatVM", "No correlationId for tool approval — daemon may not route this correctly")
            wsClient.sendToolApprovalResponse(toolId, approved, outcome)  // fallback with toolId
        }
        _uiState.value = _uiState.value.copy(isThinking = true, thinkingStage = "Processando...")
    }

    fun sendAskUserResponse(answers: Map<String, String>) {
        var correlationId: String? = null
        val msgs = _uiState.value.messages.toMutableList()
        val idx = msgs.indexOfLast { it is ChatMessage.AskUserMsg && !(it as ChatMessage.AskUserMsg).isAnswered }
        if (idx >= 0) {
            val msg = msgs[idx] as ChatMessage.AskUserMsg
            correlationId = msg.correlationId
            val newPending = _uiState.value.pendingActions - (correlationId ?: "")
            msgs[idx] = msg.copy(isAnswered = true)
            _uiState.value = _uiState.value.copy(messages = msgs, pendingActions = newPending)
        }
        if (correlationId != null) {
            wsClient.sendAskUserResponse(correlationId, answers)
        } else {
            Log.w("ChatVM", "No correlationId for AskUser response")
            wsClient.sendSimpleStdinResponse(answers.values.firstOrNull() ?: "")
        }
        _uiState.value = _uiState.value.copy(isThinking = true, thinkingStage = "Processando...")
    }

    /** Open browser on the PC via daemon command, not on the phone */
    fun openBrowserOnPc(url: String) {
        wsClient.sendCommand("open_browser", mapOf("url" to url))
    }

    fun sendBrowserAuthResponse() {
        var correlationId: String? = null
        val msgs = _uiState.value.messages.toMutableList()
        val idx = msgs.indexOfLast { it is ChatMessage.BrowserAuthMsg && !(it as ChatMessage.BrowserAuthMsg).isAnswered }
        if (idx >= 0) {
            val msg = msgs[idx] as ChatMessage.BrowserAuthMsg
            correlationId = msg.correlationId
            val newPending = _uiState.value.pendingActions - (correlationId ?: "")
            msgs[idx] = msg.copy(isAnswered = true)
            _uiState.value = _uiState.value.copy(messages = msgs, pendingActions = newPending)
        }
        // Send confirmation back via canonical StdinResponse with correlationId
        if (correlationId != null) {
            wsClient.sendStdinResponse(correlationId, confirmed = true)
        } else {
            Log.w("ChatVM", "No correlationId for BrowserAuth response")
            wsClient.sendSimpleStdinResponse("\n")
        }
        _uiState.value = _uiState.value.copy(isThinking = true, thinkingStage = "Aguardando autenticação...")
    }

    fun sendQuickAction(command: String) {
        if (!_uiState.value.isConnected) {
            addMessage(ChatMessage.SystemMessage("⚠️ Sem conexão com o SLED."))
            return
        }

        if (command !in SUPPORTED_QUICK_COMMANDS && command != "abort") {
            addMessage(ChatMessage.SystemMessage("⚠️ Comando não suportado no daemon atual: $command"))
            return
        }

        wsClient.sendCommand(command, emptyMap())
        addMessage(ChatMessage.SystemMessage("⚡ $command"))
    }

    fun startNewChat() {
        archiveCurrentChatSession(prefs, _uiState.value.messages)
        clearChatHistory(prefs)

        val current = _uiState.value
        _uiState.value = current.copy(
            messages = emptyList(),
            isThinking = false,
            thinkingStage = "",
            pendingActions = emptySet()
        )
        streamBuffer.clear()

        reconnectJob?.cancel()
        reconnectAttempts = maxReconnectAttempts
        suppressAutoReconnect = true
        wsClient.disconnect()
        reconnectAttempts = 0
        suppressAutoReconnect = false

        addMessage(ChatMessage.SystemMessage("🆕 Novo chat iniciado"))
        connect()
    }

    fun setMode(mode: String) {
        _uiState.value = _uiState.value.copy(currentMode = mode)
        if (_uiState.value.isConnected) {
            wsClient.sendCommand("set_mode", mapOf("mode" to if (mode == "Avançado") "advanced" else "standard"))
        }
    }

    fun approvePlan(plan: Plan) {
        updatePlanStatus(plan.id, PlanStatus.APPROVED)
        val planMap = mapOf(
            "id" to plan.id,
            "goal" to plan.goal,
            "risk_level" to plan.riskLevel,
            "steps" to plan.steps.map { step ->
                mutableMapOf<String, Any?>(
                    "action" to step.action,
                    "description" to step.description
                ).also { map -> step.parameters?.let { map["parameters"] = it } }
                    .filterValues { it != null }
            }
        )
        wsClient.sendCommand("approve_plan", mapOf("plan" to planMap))
    }

    fun rejectPlan(planId: String) {
        updatePlanStatus(planId, PlanStatus.REJECTED)
        wsClient.sendCommand("reject_plan", mapOf("plan_id" to planId))
        addMessage(ChatMessage.SystemMessage("❌ Plano rejeitado."))
    }

    fun retryConnection() {
        suppressAutoReconnect = false
        reconnectAttempts = 0
        reconnectJob?.cancel()
        connect()
    }

    fun disconnect() {
        suppressAutoReconnect = true
        reconnectJob?.cancel()
        reconnectAttempts = maxReconnectAttempts
        try {
            SledForegroundService.stop(context)
        } catch (e: Exception) {
            Log.e("ChatVM", "Failed to stop foreground service", e)
        }
        wsClient.disconnect()
    }

    // ── Internals ─────────────────────────────────────────────
    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        if (reconnectAttempts >= maxReconnectAttempts) {
            addMessage(ChatMessage.SystemMessage("⚠️ Não foi possível conectar após $maxReconnectAttempts tentativas."))
            _uiState.value = _uiState.value.copy(connectionStatus = "Falha na conexão")
            return
        }
        reconnectAttempts++
        val delayMs = minOf(baseDelayMs * (1L shl (reconnectAttempts - 1)), maxDelayMs)
        val delaySec = delayMs / 1000
        _uiState.value = _uiState.value.copy(
            connectionStatus = "Reconectando em ${delaySec}s... ($reconnectAttempts/$maxReconnectAttempts)"
        )
        if (reconnectAttempts == 1) {
            val msg = if (wasEverConnected) "🔴 Conexão perdida. Reconectando..."
                else "🔴 Não foi possível conectar. Tentando novamente..."
            addMessage(ChatMessage.SystemMessage(msg))
        }
        reconnectJob = viewModelScope.launch {
            delay(delayMs)
            connect()
        }
    }

    private fun addMessage(msg: ChatMessage) {
        val currentList = _uiState.value.messages.toMutableList()
        currentList.add(msg)
        _uiState.value = _uiState.value.copy(messages = currentList)
        saveHistoryAsync()
    }

    private fun updatePlanStatus(planId: String, status: PlanStatus) {
        val msgs = _uiState.value.messages.map {
            if (it is ChatMessage.PlanCardMsg && it.plan.id == planId) it.copy(status = status) else it
        }
        _uiState.value = _uiState.value.copy(messages = msgs)
        saveHistoryAsync()
    }

    private fun saveHistoryAsync() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                saveChatHistory(prefs, _uiState.value.messages)
            } catch (e: Exception) {
                Log.e("ChatVM", "Error saving history", e)
            }
        }
    }

    private fun loadHistory() {
        try {
            val historyMessages = loadChatHistory(prefs)
            if (historyMessages.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(messages = historyMessages)
            }
        } catch (e: Exception) {
            Log.e("ChatVM", "Error loading history", e)
        }
    }

    override fun onCleared() {
        super.onCleared()
        suppressAutoReconnect = true
        reconnectJob?.cancel()
        wsClient.disconnect()
    }

    private fun handleCommandResponse(response: ResponsePayload) {
        if (!response.success) {
            addMessage(
                ChatMessage.SystemMessage(
                    "❌ ${response.command}: ${response.error ?: "falhou"}"
                )
            )
            Log.w("ChatVM", "Response error: command=${response.command} error=${response.error}")
            return
        }

        if (response.command == "set_mode") {
            val mode = response.data?.get("mode")?.asString
            if (mode != null) {
                _uiState.value = _uiState.value.copy(currentMode = mode)
            }
        }

        if (response.command == "set_model") {
            val model = response.data?.get("model")?.asString
            if (model != null) {
                _uiState.value = _uiState.value.copy(currentModel = model)
                addMessage(ChatMessage.SystemMessage("🔄 Modelo alterado para $model"))
            }
        }

        if (response.command in SUPPORTED_QUICK_COMMANDS) {
            val details = formatResponseData(response.data)
            if (details.isNullOrBlank()) {
                addMessage(ChatMessage.SystemMessage("✅ ${response.command} concluído"))
            } else if (details.contains("\n")) {
                addMessage(ChatMessage.StreamOutputMsg(details.lines()))
            } else {
                addMessage(ChatMessage.SystemMessage("✅ $details"))
            }
        }

        Log.d("ChatVM", "Response: command=${response.command} success=${response.success}")
    }

    private fun formatResponseData(data: com.google.gson.JsonObject?): String? {
        if (data == null || data.entrySet().isEmpty()) return null
        return data.entrySet().joinToString("\n") { entry ->
            "${entry.key}: ${jsonToText(entry.value)}"
        }
    }

    private fun jsonToText(value: JsonElement): String {
        return when {
            value.isJsonNull -> "null"
            value.isJsonPrimitive -> value.asJsonPrimitive.toString().trim('"')
            else -> value.toString()
        }
    }

    private fun formatToolParameters(params: com.google.gson.JsonObject): String {
        if (params.entrySet().isEmpty()) return ""
        return params.entrySet().joinToString("\n") { entry ->
            "${entry.key}: ${jsonToText(entry.value)}"
        }
    }
}
