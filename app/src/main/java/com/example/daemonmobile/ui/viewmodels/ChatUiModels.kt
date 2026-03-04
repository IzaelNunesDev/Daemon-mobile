package com.example.daemonmobile.ui.viewmodels

import com.example.daemonmobile.data.models.Plan
import com.example.daemonmobile.data.models.Question

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
    val pendingActions: Set<String> = emptySet(),
    val deviceName: String = ""
) {
    val isWaitingInput: Boolean
        get() = pendingActions.isNotEmpty()
}
