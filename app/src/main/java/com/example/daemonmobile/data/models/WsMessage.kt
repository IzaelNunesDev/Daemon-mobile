package com.example.daemonmobile.data.models

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName

// ═══════════════════════════════════════════════════════════════
// App → Daemon (WsIncoming)
// ═══════════════════════════════════════════════════════════════

data class AuthPayload(
    val secret: String,
    @SerializedName("device_id") val deviceId: String,
    @SerializedName("device_name") val deviceName: String
)

data class MessagePayload(
    val text: String
)

data class CommandPayload(
    val name: String,
    val args: Map<String, Any>
)

// ═══════════════════════════════════════════════════════════════
// Daemon → App (WsOutgoing)
// ═══════════════════════════════════════════════════════════════

/** 1. AuthResult — authentication response */
data class AuthResultPayload(
    val success: Boolean,
    val message: String?
)

/** 2. Thinking — agent processing indicator */
data class ThinkingPayload(
    val stage: String
)

/** 3. SessionInit — Gemini session started */
data class SessionInitPayload(
    @SerializedName("session_id") val sessionId: String,
    val model: String
)

/** 4. StreamChunk — incremental text (done=true means end) */
data class StreamChunkPayload(
    val content: String,
    val done: Boolean
)

/** 5. ToolUse — tool being invoked */
data class ToolUsePayload(
    @SerializedName("tool_name") val toolName: String,
    @SerializedName("tool_id") val toolId: String,
    val parameters: JsonObject?
)

/** 6. ToolResult — tool execution result */
data class ToolResultPayload(
    @SerializedName("tool_id") val toolId: String,
    val status: String,
    val output: String?,
    val error: String?
)

/** 7. PromptInput — agent requests user input */
data class PromptInputPayload(
    @SerializedName("prompt_type") val promptType: String,  // choice, text, yesno
    val label: String,
    val options: List<String>?,
    val hint: String? = null
)

/** 8. StreamError — error notification */
data class StreamErrorPayload(
    val severity: String,  // "error", "warning"
    val message: String
)

/** 9. ChatMessage — complete accumulated message */
data class ChatMessagePayload(
    val id: String,
    val text: String,
    @SerializedName("message_type") val messageType: String,
    val metadata: JsonObject? = null,
    val timestamp: String? = null
)

/** 10. Response — response to a Command */
data class ResponsePayload(
    val command: String,
    val success: Boolean,
    val data: JsonObject? = null,
    val error: String? = null
)

/** 11. ModelInfo — model information */
data class ModelInfoPayload(
    @SerializedName("current_model") val currentModel: String,
    @SerializedName("available_models") val availableModels: List<String>,
    @SerializedName("request_count") val requestCount: Int,
    @SerializedName("last_error") val lastError: String?
)

/** 12. ToolApprovalRequest — agent requests approval to execute a tool */
data class ToolApprovalRequestPayload(
    @SerializedName("tool_id") val toolId: String,
    @SerializedName("tool_name") val toolName: String,
    val command: String?,
    @SerializedName("risk_level") val riskLevel: String?,
    @SerializedName("correlation_id") val correlationId: String? = null
)

/** 13. AskUser — multi-field wizard request */
data class Question(
    val id: String,
    val text: String,
    val type: String? // text, multiline, boolean, etc.
)

data class AskUserPayload(
    val title: String,
    val questions: List<Question>,
    @SerializedName("correlation_id") val correlationId: String? = null
)

/** 14. BrowserAuth — OAuth browser request */
data class BrowserAuthPayload(
    val url: String,
    val code: String?,
    val instruction: String?,
    @SerializedName("correlation_id") val correlationId: String? = null
)

