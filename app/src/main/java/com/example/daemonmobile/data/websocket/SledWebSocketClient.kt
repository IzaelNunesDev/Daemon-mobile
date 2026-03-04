package com.example.daemonmobile.data.websocket

import android.util.Log
import com.example.daemonmobile.data.models.*
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import okhttp3.*
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class SledWebSocketClient {
    private var webSocket: WebSocket? = null
    private val client: OkHttpClient
    val gson: Gson

    // ── Callbacks ──────────────────────────────────────────────
    var onConnectionStateChanged: ((Boolean) -> Unit)? = null
    var onAuthResult: ((AuthResultPayload) -> Unit)? = null
    var onThinkingReceived: ((ThinkingPayload) -> Unit)? = null
    var onSessionInit: ((SessionInitPayload) -> Unit)? = null
    var onStreamChunk: ((StreamChunkPayload) -> Unit)? = null
    var onToolUse: ((ToolUsePayload) -> Unit)? = null
    var onToolResult: ((ToolResultPayload) -> Unit)? = null
    var onPromptInput: ((PromptInputPayload) -> Unit)? = null
    var onStreamError: ((StreamErrorPayload) -> Unit)? = null
    var onChatMessageReceived: ((ChatMessagePayload) -> Unit)? = null
    var onResponseReceived: ((ResponsePayload) -> Unit)? = null
    var onModelInfo: ((ModelInfoPayload) -> Unit)? = null
    var onToolApprovalRequest: ((ToolApprovalRequestPayload) -> Unit)? = null
    var onAskUser: ((AskUserPayload) -> Unit)? = null
    var onBrowserAuth: ((BrowserAuthPayload) -> Unit)? = null

    init {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })

        val sslContext = SSLContext.getInstance("SSL")
        sslContext.init(null, trustAllCerts, SecureRandom())
        val sslSocketFactory = sslContext.socketFactory

        client = OkHttpClient.Builder()
            .sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .build()

        gson = GsonBuilder().create()
    }

    fun connect(host: String, port: Int, secret: String, deviceId: String) {
        val url = "wss://$host:$port"
        val request = Request.Builder().url(url).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("SledWS", "Connected to $url")
                onConnectionStateChanged?.invoke(true)

                // Send Auth as first message
                val authMsg = mapOf(
                    "type" to "Auth",
                    "payload" to mapOf(
                        "secret" to secret,
                        "device_id" to deviceId,
                        "device_name" to android.os.Build.MODEL
                    )
                )
                webSocket.send(gson.toJson(authMsg))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d("SledWS", "Received: $text")
                try {
                    val jsonObj = gson.fromJson(text, JsonObject::class.java)
                    val type = jsonObj.get("type")?.asString
                    val payload = jsonObj.getAsJsonObject("payload")

                    when (type) {
                        "AuthResult" -> {
                            if (payload != null) {
                                onAuthResult?.invoke(AuthResultPayload(
                                    success = payload.get("success")?.asBoolean ?: false,
                                    message = payload.get("message")?.asString
                                ))
                            }
                        }
                        "Thinking" -> {
                            if (payload != null) {
                                onThinkingReceived?.invoke(ThinkingPayload(
                                    stage = payload.get("stage")?.asString ?: "Processando..."
                                ))
                            }
                        }
                        "SessionInit" -> {
                            if (payload != null) {
                                onSessionInit?.invoke(SessionInitPayload(
                                    sessionId = payload.get("session_id")?.asString ?: "",
                                    model = payload.get("model")?.asString ?: ""
                                ))
                            }
                        }
                        "StreamChunk" -> {
                            if (payload != null) {
                                onStreamChunk?.invoke(StreamChunkPayload(
                                    content = payload.get("content")?.asString ?: "",
                                    done = payload.get("done")?.asBoolean ?: false
                                ))
                            }
                        }
                        "ToolUse" -> {
                            if (payload != null) {
                                onToolUse?.invoke(ToolUsePayload(
                                    toolName = payload.get("tool_name")?.asString ?: "",
                                    toolId = payload.get("tool_id")?.asString ?: "",
                                    parameters = if (payload.has("parameters") && !payload.get("parameters").isJsonNull)
                                        payload.getAsJsonObject("parameters") else null
                                ))
                            }
                        }
                        "ToolResult" -> {
                            if (payload != null) {
                                onToolResult?.invoke(ToolResultPayload(
                                    toolId = payload.get("tool_id")?.asString ?: "",
                                    status = payload.get("status")?.asString ?: "unknown",
                                    output = if (payload.has("output") && !payload.get("output").isJsonNull)
                                        payload.get("output").asString else null,
                                    error = if (payload.has("error") && !payload.get("error").isJsonNull)
                                        payload.get("error").asString else null
                                ))
                            }
                        }
                        "PromptInput" -> {
                            if (payload != null) {
                                val options = if (payload.has("options") && !payload.get("options").isJsonNull) {
                                    payload.getAsJsonArray("options").map { it.asString }
                                } else null
                                onPromptInput?.invoke(PromptInputPayload(
                                    promptType = payload.get("prompt_type")?.asString ?: "text",
                                    label = payload.get("label")?.asString ?: "",
                                    options = options,
                                    hint = payload.get("hint")?.asString
                                ))
                            }
                        }
                        "StreamError" -> {
                            if (payload != null) {
                                onStreamError?.invoke(StreamErrorPayload(
                                    severity = payload.get("severity")?.asString ?: "error",
                                    message = payload.get("message")?.asString ?: "Unknown error"
                                ))
                            }
                        }
                        "ChatMessage" -> {
                            if (payload != null) {
                                onChatMessageReceived?.invoke(ChatMessagePayload(
                                    id = payload.get("id")?.asString ?: "",
                                    text = payload.get("text")?.asString ?: "",
                                    messageType = payload.get("message_type")?.asString ?: "chat",
                                    metadata = if (payload.has("metadata") && !payload.get("metadata").isJsonNull)
                                        payload.getAsJsonObject("metadata") else null,
                                    timestamp = payload.get("timestamp")?.asString
                                ))
                            }
                        }
                        "Response" -> {
                            if (payload != null) {
                                val resp = ResponsePayload(
                                    command = payload.get("command")?.asString ?: "unknown",
                                    success = payload.get("success")?.asBoolean ?: false,
                                    data = if (payload.has("data") && !payload.get("data").isJsonNull)
                                        payload.getAsJsonObject("data") else null,
                                    error = if (payload.has("error") && !payload.get("error").isJsonNull)
                                        payload.get("error").asString else null
                                )
                                onResponseReceived?.invoke(resp)
                            }
                        }
                        "ModelInfo" -> {
                            if (payload != null) {
                                val models = if (payload.has("available_models") && !payload.get("available_models").isJsonNull) {
                                    payload.getAsJsonArray("available_models").map { it.asString }
                                } else emptyList()
                                onModelInfo?.invoke(ModelInfoPayload(
                                    currentModel = payload.get("current_model")?.asString ?: "",
                                    availableModels = models,
                                    requestCount = payload.get("request_count")?.asInt ?: 0,
                                    lastError = if (payload.has("last_error") && !payload.get("last_error").isJsonNull)
                                        payload.get("last_error").asString else null
                                ))
                            }
                        }
                        "ToolApprovalRequest" -> {
                            if (payload != null) {
                                onToolApprovalRequest?.invoke(gson.fromJson(payload, ToolApprovalRequestPayload::class.java))
                            }
                        }
                        "AskUser" -> {
                            if (payload != null) {
                                onAskUser?.invoke(gson.fromJson(payload, AskUserPayload::class.java))
                            }
                        }
                        "BrowserAuth" -> {
                            if (payload != null) {
                                onBrowserAuth?.invoke(gson.fromJson(payload, BrowserAuthPayload::class.java))
                            }
                        }
                        else -> {
                            Log.w("SledWS", "Unknown message type: $type")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("SledWS", "Error parsing message: $text", e)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("SledWS", "Closed: $reason")
                onConnectionStateChanged?.invoke(false)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("SledWS", "Failure", t)
                onConnectionStateChanged?.invoke(false)
            }
        })
    }

    /** Send a chat message — daemon routes via GeminiProcess streaming */
    fun sendMessage(text: String) {
        val msg = mapOf(
            "type" to "Message",
            "payload" to mapOf("text" to text)
        )
        webSocket?.send(gson.toJson(msg))
    }

    /** Send a structured command */
    fun sendCommand(name: String, args: Map<String, Any>) {
        val msg = mapOf(
            "type" to "Command",
            "payload" to mapOf("name" to name, "args" to args)
        )
        webSocket?.send(gson.toJson(msg))
    }

    /** Send a ToolApprovalResponse */
    fun sendToolApprovalResponse(toolId: String, choice: String) {
        val msg = mapOf(
            "type" to "ToolApprovalResponse",
            "payload" to mapOf("tool_id" to toolId, "choice" to choice)
        )
        webSocket?.send(gson.toJson(msg))
    }

    /** Send an AskUserResponse */
    fun sendAskUserResponse(answers: Map<String, String>) {
        val msg = mapOf(
            "type" to "AskUserResponse",
            "payload" to mapOf("answers" to answers)
        )
        webSocket?.send(gson.toJson(msg))
    }

    /** Send a StdinResponse */
    fun sendStdinResponse(text: String) {
        val msg = mapOf(
            "type" to "StdinResponse",
            "payload" to mapOf("text" to text)
        )
        webSocket?.send(gson.toJson(msg))
    }

    fun disconnect() {
        webSocket?.close(1000, "User disconnected")
        webSocket = null
    }
}
