package com.example.daemonmobile.data.models

import com.google.gson.annotations.SerializedName

data class Plan(
    val id: String,
    val goal: String,
    val steps: List<Step>,
    @SerializedName("risk_level") val riskLevel: String
)
