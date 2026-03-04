package com.example.daemonmobile.data.websocket

import android.util.Log
import com.example.daemonmobile.data.models.DaemonEvent
import com.example.daemonmobile.data.models.Plan
import com.google.gson.*
import java.lang.reflect.Type

class DaemonEventDeserializer : JsonDeserializer<DaemonEvent> {
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): DaemonEvent {
        // Handle string-type events like "ExecutionFinished", "DaemonStarted"
        if (json.isJsonPrimitive && json.asJsonPrimitive.isString) {
            return when (json.asString) {
                "ExecutionFinished" -> DaemonEvent.ExecutionFinished
                "DaemonStarted" -> DaemonEvent.DaemonStarted
                else -> DaemonEvent.ErrorMessage("Unknown string event: ${json.asString}")
            }
        }

        if (!json.isJsonObject) {
            return DaemonEvent.ErrorMessage("Unexpected event format: $json")
        }

        val obj = json.asJsonObject
        val key = obj.keySet().firstOrNull() ?: throw JsonParseException("Unknown event format")
        val payload = obj.get(key)
        
        return try {
            when (key) {
                "DaemonStarted" -> DaemonEvent.DaemonStarted
                "PlanCreated" -> {
                    val plan = context!!.deserialize<Plan>(payload, Plan::class.java)
                    DaemonEvent.PlanCreated(plan)
                }
                "PlanApproved" -> DaemonEvent.PlanApproved(payload.asJsonObject.get("id").asString)
                "StepStarted" -> {
                    val p = payload.asJsonObject
                    DaemonEvent.StepStarted(
                        p.get("step_index").asInt,
                        p.get("description")?.asString ?: "Step"
                    )
                }
                "StepCompleted" -> {
                    val p = payload.asJsonObject
                    DaemonEvent.StepCompleted(
                        p.get("step_index").asInt,
                        if (p.has("output") && !p.get("output").isJsonNull) p.get("output").asString else null
                    )
                }
                "StepFailed" -> {
                    val p = payload.asJsonObject
                    DaemonEvent.StepFailed(
                        p.get("step_index").asInt,
                        p.get("error")?.asString ?: "Unknown error"
                    )
                }
                "ExecutionFinished" -> DaemonEvent.ExecutionFinished
                "ExecutionAborted" -> {
                    if (payload.isJsonObject) {
                        DaemonEvent.ExecutionAborted(payload.asJsonObject.get("reason").asString)
                    } else {
                        DaemonEvent.ExecutionAborted(payload?.asString ?: "Unknown reason")
                    }
                }
                "Thinking" -> {
                    val p = payload.asJsonObject
                    DaemonEvent.Thinking(p.get("stage")?.asString ?: "Pensando...")
                }
                else -> DaemonEvent.ErrorMessage("Unknown event type: $key")
            }
        } catch (e: Exception) {
            Log.e("DaemonEventDeserializer", "Error parsing event key=$key payload=$payload", e)
            DaemonEvent.ErrorMessage("Erro ao parsear evento: $key")
        }
    }
}
