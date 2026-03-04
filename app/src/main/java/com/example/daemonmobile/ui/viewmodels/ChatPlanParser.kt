package com.example.daemonmobile.ui.viewmodels

import com.example.daemonmobile.data.models.Plan
import com.example.daemonmobile.data.models.Step
import com.google.gson.JsonObject

internal fun parsePlanFromMetadata(metadata: JsonObject): Plan {
    val id = metadata.get("id")?.asString ?: ""
    val goal = metadata.get("goal")?.asString ?: ""
    val riskLevel = metadata.get("risk_level")?.asString ?: "unknown"

    val steps = metadata.getAsJsonArray("steps")?.map { stepJson ->
        val stepObj = stepJson.asJsonObject
        Step(
            description = stepObj.get("description")?.asString ?: "",
            action = stepObj.get("action")?.asString,
            parameters = if (stepObj.has("parameters") && !stepObj.get("parameters").isJsonNull) {
                stepObj.getAsJsonObject("parameters")
            } else {
                null
            }
        )
    } ?: emptyList()

    return Plan(
        id = id,
        goal = goal,
        steps = steps,
        riskLevel = riskLevel
    )
}
