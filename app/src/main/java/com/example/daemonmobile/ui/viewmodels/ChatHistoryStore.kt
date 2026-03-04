package com.example.daemonmobile.ui.viewmodels

import com.example.daemonmobile.data.local.SledPreferences
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
        else -> null
    }
}
