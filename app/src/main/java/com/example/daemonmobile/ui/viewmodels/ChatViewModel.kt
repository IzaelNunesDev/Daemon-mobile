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

class ChatViewModel(context: Context) : ViewModel() {
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
            addMessage(ChatMessage.StreamErrorMsg(
                severity = error.severity,
                message = error.message
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
            // Finalize: convert accumulated text to AssistantMessage
            if (streamBuffer.isNotEmpty()) {
                // Remove the streaming msg
                val msgs = _uiState.value.messages.filterNot { it is ChatMessage.StreamChunkMsg }
                _uiState.value = _uiState.value.copy(messages = msgs)
                // The final ChatMessage will arrive separately, so just clear
                streamBuffer.clear()
            }
            removeThinking()
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
        // Mark prompt as answered
        val msgs = _uiState.value.messages.toMutableList()
        val idx = msgs.indexOfLast { it is ChatMessage.PromptInputMsg && !(it as ChatMessage.PromptInputMsg).isAnswered }
        if (idx >= 0) {
            val prompt = msgs[idx] as ChatMessage.PromptInputMsg
            msgs[idx] = prompt.copy(isAnswered = true, answeredText = response)
            _uiState.value = _uiState.value.copy(messages = msgs, isWaitingInput = false)
        }
        // Send response as a Message
        wsClient.sendMessage(response)
        _uiState.value = _uiState.value.copy(isThinking = true, thinkingStage = "Processando...")
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
    }

    private fun updatePlanStatus(planId: String, status: PlanStatus) {
        val msgs = _uiState.value.messages.map {
            if (it is ChatMessage.PlanCardMsg && it.plan.id == planId) it.copy(status = status) else it
        }
        _uiState.value = _uiState.value.copy(messages = msgs)
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
