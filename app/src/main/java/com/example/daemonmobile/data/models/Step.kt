package com.example.daemonmobile.data.models

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName

data class Step(
    @SerializedName("step_index") val stepIndex: Int? = null,
    val description: String,
    val action: String? = null,
    val parameters: JsonObject? = null,
    val output: String? = null,
    val error: String? = null
)
