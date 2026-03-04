package com.example.daemonmobile.ui.viewmodels

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.daemonmobile.data.local.SledPreferences
import com.example.daemonmobile.data.models.*
import com.example.daemonmobile.data.websocket.SledWebSocketClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.example.daemonmobile.services.SledForegroundService

// ═══════════════════════════════════════════════════════════════
// Chat Messages — all possible items in the message list
// ═══════════════════════════════════════════════════════════════
sealed class ChatMessage {
    data class UserMessage(val text: String) : ChatMessage()
    data class SystemMessage(val text: String) : ChatMessage()
    data class AssistantMessage(
        val text: String,
        val toolName: String? = null,
        val toolCmd: String? = null,
        val isSuccess: Boolean = false,
        val timestamp: String? = null
    ) : ChatMessage()
    data class StreamChunkMsg(val accumulated: String) : ChatMessage()
    data class ToolUseMsg(
        val toolName: String,
        val toolId: String,
        val parameters: String? = null,
        val resultStatus: String? = null,
        val resultOutput: String? = null,
        val resultError: String? = null
    ) : ChatMessage()
    data class StreamOutputMsg(val lines: List<String>) : ChatMessage()
    data class PromptInputMsg(
        val promptType: String,
        val label: String,
        val options: List<String>?,
        val hint: String? = null,
        val correlationId: String? = null,
        val isAnswered: Boolean = false,
        val answeredText: String? = null
    ) : ChatMessage()
    data class ThinkingMessage(val stage: String) : ChatMessage()
    data class StreamErrorMsg(val severity: String, val message: String) : ChatMessage()
    data class SessionInfoMsg(val sessionId: String, val model: String) : ChatMessage()
    data class PlanCardMsg(
        val plan: Plan,
        val status: PlanStatus = PlanStatus.PENDING
    ) : ChatMessage()
    data class StepProgress(
        val stepIndex: Int,
        val description: String,
        val output: String? = null,
        val error: String? = null,
        val isCompleted: Boolean = false
    ) : ChatMessage()
    data class ToolApprovalRequestMsg(
        val toolId: String,
        val toolName: String,
        val command: String?,
        val riskLevel: String?,
        val correlationId: String? = null,
        val isAnswered: Boolean = false,
        val choice: String? = null
    ) : ChatMessage()
    data class AskUserMsg(
        val title: String,
        val questions: List<Question>,
        val correlationId: String? = null,
        val isAnswered: Boolean = false
    ) : ChatMessage()
    data class BrowserAuthMsg(
        val url: String,
        val code: String?,
        val instruction: String?,
        val correlationId: String? = null,
        val isAnswered: Boolean = false
    ) : ChatMessage()
}

enum class PlanStatus { PENDING, APPROVED, REJECTED }

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isConnected: Boolean = false,
    val isThinking: Boolean = false,
    val thinkingStage: String = "",
    val connectionStatus: String = "Desconectado",
    val currentMode: String = "Padrão",
    val currentModel: String = "",
    val sessionId: String = "",
    val isWaitingInput: Boolean = false,
    val deviceName: String = ""
)

class ChatViewModel(private val context: Context) : ViewModel() {
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
                scheduleReconnect()
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
                parameters = toolUse.parameters?.let { params ->
                    // Extract the first readable parameter for display
                    params.entrySet().firstOrNull()?.let { entry ->
                        "${entry.key}: ${entry.value.asString}"
                    }
                }
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
            _uiState.value = _uiState.value.copy(isWaitingInput = true)
            addMessage(ChatMessage.PromptInputMsg(
                promptType = prompt.promptType,
                label = prompt.label,
                options = prompt.options,
                hint = prompt.hint
            ))
        }

        wsClient.onStreamError = { error ->
            removeThinking()
            _uiState.value = _uiState.value.copy(
                isThinking = false,
                thinkingStage = ""
            )
            addMessage(ChatMessage.StreamErrorMsg(
                severity = error.severity,
                message = error.message
            ))
        }

        wsClient.onToolApprovalRequest = { req ->
            removeThinking()
            _uiState.value = _uiState.value.copy(isWaitingInput = true)
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
            _uiState.value = _uiState.value.copy(isWaitingInput = true)
            addMessage(ChatMessage.AskUserMsg(
                title = req.title,
                questions = req.questions,
                correlationId = req.correlationId
            ))
        }

        wsClient.onBrowserAuth = { req ->
            removeThinking()
            _uiState.value = _uiState.value.copy(isWaitingInput = true)
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
            if (response.success && response.command == "set_mode") {
                val mode = response.data?.get("mode")?.asString
                if (mode != null) {
                    _uiState.value = _uiState.value.copy(currentMode = mode)
                }
            }
            if (response.command == "set_model" && response.success) {
                val model = response.data?.get("model")?.asString
                if (model != null) {
                    _uiState.value = _uiState.value.copy(currentModel = model)
                    addMessage(ChatMessage.SystemMessage("🔄 Modelo alterado para $model"))
                }
            }
            Log.d("ChatVM", "Response: command=${response.command} success=${response.success}")
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
                thinkingStage = ""
            )
            return
        }

        streamBuffer.append(chunk.content)
        removeThinking()

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
            msgs[idx] = prompt.copy(isAnswered = true, answeredText = response)
            _uiState.value = _uiState.value.copy(messages = msgs, isWaitingInput = false)
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
                msgs[idx] = msg.copy(isAnswered = true, choice = choice)
                _uiState.value = _uiState.value.copy(messages = msgs, isWaitingInput = false)
            }
        }
        // Use canonical contract: correlationId + approved boolean
        val approved = choice.lowercase() in listOf("yes", "approve", "allow", "y", "sim")
        if (correlationId != null) {
            wsClient.sendToolApprovalResponse(correlationId, approved)
        } else {
            Log.w("ChatVM", "No correlationId for tool approval — daemon may not route this correctly")
            wsClient.sendToolApprovalResponse(toolId, approved)  // fallback with toolId
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
            msgs[idx] = msg.copy(isAnswered = true)
            _uiState.value = _uiState.value.copy(messages = msgs, isWaitingInput = false)
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
            msgs[idx] = msg.copy(isAnswered = true)
            _uiState.value = _uiState.value.copy(messages = msgs, isWaitingInput = false)
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
        wsClient.sendCommand(command, emptyMap())
        addMessage(ChatMessage.SystemMessage("⚡ $command"))
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
        reconnectAttempts = 0
        reconnectJob?.cancel()
        connect()
    }

    fun disconnect() {
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
                val array = org.json.JSONArray()
                // Save last 50 basic messages to not overwhelm prefs
                _uiState.value.messages.takeLast(50).forEach { msg ->
                    val obj = org.json.JSONObject()
                    when (msg) {
                        is ChatMessage.UserMessage -> {
                            obj.put("type", "user")
                            obj.put("text", msg.text)
                        }
                        is ChatMessage.AssistantMessage -> {
                            obj.put("type", "assistant")
                            obj.put("text", msg.text)
                        }
                        is ChatMessage.SystemMessage -> {
                            obj.put("type", "system")
                            obj.put("text", msg.text)
                        }
                        else -> return@forEach
                    }
                    array.put(obj)
                }
                prefs.chatHistory = array.toString()
            } catch (e: Exception) {
                Log.e("ChatVM", "Error saving history", e)
            }
        }
    }

    private fun loadHistory() {
        try {
            val history = prefs.chatHistory
            if (!history.isNullOrBlank()) {
                val array = org.json.JSONArray(history)
                val msgs = mutableListOf<ChatMessage>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    when (obj.getString("type")) {
                        "user" -> msgs.add(ChatMessage.UserMessage(obj.getString("text")))
                        "assistant" -> msgs.add(ChatMessage.AssistantMessage(obj.getString("text")))
                        "system" -> msgs.add(ChatMessage.SystemMessage(obj.getString("text")))
                    }
                }
                if (msgs.isNotEmpty()) {
                    msgs.add(ChatMessage.SystemMessage("--- Histórico de Sessão Anterior ---"))
                    _uiState.value = _uiState.value.copy(messages = msgs)
                }
            }
        } catch (e: Exception) {
            Log.e("ChatVM", "Error loading history", e)
        }
    }

    private fun parsePlanFromMetadata(metadata: com.google.gson.JsonObject): Plan {
        val id = metadata.get("id")?.asString ?: ""
        val goal = metadata.get("goal")?.asString ?: ""
        val riskLevel = metadata.get("risk_level")?.asString ?: "unknown"
        val stepsArray = metadata.getAsJsonArray("steps")
        val steps = stepsArray?.map { stepJson ->
            val stepObj = stepJson.asJsonObject
            Step(
                description = stepObj.get("description")?.asString ?: "",
                action = stepObj.get("action")?.asString,
                parameters = if (stepObj.has("parameters") && !stepObj.get("parameters").isJsonNull)
                    stepObj.getAsJsonObject("parameters") else null
            )
        } ?: emptyList()
        return Plan(id = id, goal = goal, steps = steps, riskLevel = riskLevel)
    }

    override fun onCleared() {
        super.onCleared()
        reconnectJob?.cancel()
        wsClient.disconnect()
    }
}

class ChatViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
