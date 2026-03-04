package com.example.daemonmobile.ui.viewmodels

import com.example.daemonmobile.data.local.SledPreferences
import com.example.daemonmobile.data.models.Plan
import com.example.daemonmobile.data.models.Step
import com.google.gson.JsonParser
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private const val HISTORY_LIMIT = 50
private const val HISTORY_MARKER = "--- Histórico de Sessão Anterior ---"
private const val ARCHIVE_LIMIT = 60

data class ArchivedChatSession(
    val id: String,
    val title: String,
    val preview: String,
    val updatedAt: Long,
    val messageCount: Int
)

internal fun serializeChatHistory(messages: List<ChatMessage>): String {
    val array = JSONArray()
    messages.takeLast(HISTORY_LIMIT).forEach { msg ->
        val obj = JSONObject()
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
            is ChatMessage.ToolUseMsg -> {
                obj.put("type", "tool_use")
                obj.put("tool_name", msg.toolName)
                obj.put("tool_id", msg.toolId)
                obj.put("parameters", msg.parameters)
                obj.put("result_status", msg.resultStatus)
                obj.put("result_output", msg.resultOutput)
                obj.put("result_error", msg.resultError)
            }
            is ChatMessage.ToolApprovalRequestMsg -> {
                obj.put("type", "tool_approval")
                obj.put("tool_id", msg.toolId)
                obj.put("tool_name", msg.toolName)
                obj.put("command", msg.command)
                obj.put("risk_level", msg.riskLevel)
                obj.put("correlation_id", msg.correlationId)
                obj.put("is_answered", msg.isAnswered)
                obj.put("choice", msg.choice)
            }
            is ChatMessage.StepProgress -> {
                obj.put("type", "step_progress")
                obj.put("step_index", msg.stepIndex)
                obj.put("description", msg.description)
                obj.put("output", msg.output)
                obj.put("error", msg.error)
                obj.put("is_completed", msg.isCompleted)
            }
            is ChatMessage.PlanCardMsg -> {
                obj.put("type", "plan_card")
                obj.put("status", msg.status.name)
                obj.put("plan", planToJson(msg.plan))
            }
            else -> return@forEach
        }
        array.put(obj)
    }
    return array.toString()
}

internal fun deserializeChatHistory(rawHistory: String?): List<ChatMessage> {
    if (rawHistory.isNullOrBlank()) return emptyList()

    val array = JSONArray(rawHistory)
    val messages = mutableListOf<ChatMessage>()
    for (i in 0 until array.length()) {
        val obj = array.getJSONObject(i)
        when (obj.getString("type")) {
            "user" -> messages.add(ChatMessage.UserMessage(obj.getString("text")))
            "assistant" -> messages.add(ChatMessage.AssistantMessage(obj.getString("text")))
            "system" -> messages.add(ChatMessage.SystemMessage(obj.getString("text")))
            "tool_use" -> {
                messages.add(
                    ChatMessage.ToolUseMsg(
                        toolName = obj.optString("tool_name"),
                        toolId = obj.optString("tool_id"),
                        parameters = obj.optString("parameters").takeIf { it.isNotBlank() },
                        resultStatus = obj.optString("result_status").takeIf { it.isNotBlank() },
                        resultOutput = obj.optString("result_output").takeIf { it.isNotBlank() },
                        resultError = obj.optString("result_error").takeIf { it.isNotBlank() }
                    )
                )
            }
            "tool_approval" -> {
                messages.add(
                    ChatMessage.ToolApprovalRequestMsg(
                        toolId = obj.optString("tool_id"),
                        toolName = obj.optString("tool_name"),
                        command = obj.optString("command").takeIf { it.isNotBlank() },
                        riskLevel = obj.optString("risk_level").takeIf { it.isNotBlank() },
                        correlationId = obj.optString("correlation_id").takeIf { it.isNotBlank() },
                        isAnswered = obj.optBoolean("is_answered", false),
                        choice = obj.optString("choice").takeIf { it.isNotBlank() }
                    )
                )
            }
            "step_progress" -> {
                messages.add(
                    ChatMessage.StepProgress(
                        stepIndex = obj.optInt("step_index"),
                        description = obj.optString("description"),
                        output = obj.optString("output").takeIf { it.isNotBlank() },
                        error = obj.optString("error").takeIf { it.isNotBlank() },
                        isCompleted = obj.optBoolean("is_completed", false)
                    )
                )
            }
            "plan_card" -> {
                val planObj = obj.optJSONObject("plan")
                if (planObj != null) {
                    val status = runCatching {
                        PlanStatus.valueOf(obj.optString("status", PlanStatus.PENDING.name))
                    }.getOrElse { PlanStatus.PENDING }
                    messages.add(
                        ChatMessage.PlanCardMsg(
                            plan = jsonToPlan(planObj),
                            status = status
                        )
                    )
                }
            }
        }
    }
    if (messages.isNotEmpty()) {
        messages.add(ChatMessage.SystemMessage(HISTORY_MARKER))
    }
    return messages
}

internal fun saveChatHistory(prefs: SledPreferences, messages: List<ChatMessage>) {
    prefs.chatHistory = serializeChatHistory(messages)
}

internal fun loadChatHistory(prefs: SledPreferences): List<ChatMessage> {
    return deserializeChatHistory(prefs.chatHistory)
}

internal fun clearChatHistory(prefs: SledPreferences) {
    prefs.chatHistory = null
}

internal fun loadArchivedChatSessions(prefs: SledPreferences): List<ArchivedChatSession> {
    val raw = prefs.chatArchive ?: return emptyList()
    val array = JSONArray(raw)
    val items = mutableListOf<ArchivedChatSession>()
    for (i in 0 until array.length()) {
        val obj = array.getJSONObject(i)
        items.add(
            ArchivedChatSession(
                id = obj.optString("id"),
                title = obj.optString("title"),
                preview = obj.optString("preview"),
                updatedAt = obj.optLong("updated_at"),
                messageCount = obj.optInt("message_count")
            )
        )
    }
    return items.sortedByDescending { it.updatedAt }
}

internal fun archiveCurrentChatSession(prefs: SledPreferences, messages: List<ChatMessage>) {
    val entry = buildArchiveEntry(messages) ?: return
    val current = loadArchivedChatSessions(prefs).toMutableList()

    current.removeAll { it.preview == entry.preview && it.title == entry.title }
    current.add(0, entry)

    val limited = current.take(ARCHIVE_LIMIT)
    val array = JSONArray()
    limited.forEach { session ->
        val obj = JSONObject()
        obj.put("id", session.id)
        obj.put("title", session.title)
        obj.put("preview", session.preview)
        obj.put("updated_at", session.updatedAt)
        obj.put("message_count", session.messageCount)
        array.put(obj)
    }
    prefs.chatArchive = array.toString()
}

internal fun deleteArchivedChatSession(prefs: SledPreferences, sessionId: String) {
    val updated = loadArchivedChatSessions(prefs).filterNot { it.id == sessionId }
    val array = JSONArray()
    updated.forEach { session ->
        val obj = JSONObject()
        obj.put("id", session.id)
        obj.put("title", session.title)
        obj.put("preview", session.preview)
        obj.put("updated_at", session.updatedAt)
        obj.put("message_count", session.messageCount)
        array.put(obj)
    }
    prefs.chatArchive = array.toString()
}

private fun buildArchiveEntry(messages: List<ChatMessage>): ArchivedChatSession? {
    val printable = messages.mapNotNull { messageText(it) }
        .filter { it.isNotBlank() && it != HISTORY_MARKER }
    if (printable.isEmpty()) return null

    val firstUser = messages.firstOrNull { it is ChatMessage.UserMessage }
        ?.let { (it as ChatMessage.UserMessage).text }
        ?.trim()
        .orEmpty()
    val titleBase = if (firstUser.isNotBlank()) firstUser else printable.first()
    val title = titleBase.take(48)
    val preview = printable.last().replace("\n", " ").take(80)

    return ArchivedChatSession(
        id = UUID.randomUUID().toString(),
        title = title,
        preview = preview,
        updatedAt = System.currentTimeMillis(),
        messageCount = printable.size
    )
}

private fun messageText(message: ChatMessage): String? {
    return when (message) {
        is ChatMessage.UserMessage -> message.text
        is ChatMessage.AssistantMessage -> message.text
        is ChatMessage.SystemMessage -> message.text
        is ChatMessage.StreamChunkMsg -> message.accumulated
        is ChatMessage.ToolUseMsg -> "${message.toolName} ${message.parameters.orEmpty()}".trim()
        is ChatMessage.StepProgress -> message.description
        is ChatMessage.ToolApprovalRequestMsg -> "${message.toolName} ${message.command.orEmpty()}".trim()
        is ChatMessage.PlanCardMsg -> message.plan.goal
        else -> null
    }
}

private fun planToJson(plan: Plan): JSONObject {
    val obj = JSONObject()
    obj.put("id", plan.id)
    obj.put("goal", plan.goal)
    obj.put("risk_level", plan.riskLevel)
    val steps = JSONArray()
    plan.steps.forEach { step ->
        val sObj = JSONObject()
        sObj.put("description", step.description)
        sObj.put("action", step.action)
        sObj.put("parameters", step.parameters?.toString())
        steps.put(sObj)
    }
    obj.put("steps", steps)
    return obj
}

private fun jsonToPlan(obj: JSONObject): Plan {
    val steps = mutableListOf<Step>()
    val stepsArray = obj.optJSONArray("steps") ?: JSONArray()
    for (i in 0 until stepsArray.length()) {
        val sObj = stepsArray.optJSONObject(i) ?: continue
        val parametersRaw = sObj.optString("parameters").takeIf { it.isNotBlank() }
        val parameters = try {
            parametersRaw?.let { JsonParser.parseString(it).asJsonObject }
        } catch (_: Exception) {
            null
        }
        steps.add(
            Step(
                description = sObj.optString("description"),
                action = sObj.optString("action").takeIf { it.isNotBlank() },
                parameters = parameters
            )
        )
    }
    return Plan(
        id = obj.optString("id"),
        goal = obj.optString("goal"),
        steps = steps,
        riskLevel = obj.optString("risk_level", "unknown")
    )
}
