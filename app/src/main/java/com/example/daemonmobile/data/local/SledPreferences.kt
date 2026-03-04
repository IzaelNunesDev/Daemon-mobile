package com.example.daemonmobile.data.local

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

class SledPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("sled_prefs", Context.MODE_PRIVATE)

    var host: String?
        get() = prefs.getString("host", null)
        set(value) = prefs.edit().putString("host", value).apply()

    var port: Int
        get() = prefs.getInt("port", 3030)
        set(value) = prefs.edit().putInt("port", value).apply()

    var secret: String?
        get() = prefs.getString("secret", null)
        set(value) = prefs.edit().putString("secret", value).apply()

    var deviceId: String
        get() {
            var id = prefs.getString("device_id", null)
            if (id == null) {
                id = UUID.randomUUID().toString()
                prefs.edit().putString("device_id", id).apply()
            }
            return id
        }
        private set(value) {} // Generated automatically

    /**
     * When enabled, plans with risk_level "low" are automatically approved
     * without user confirmation, for a more fluid experience.
     */
    var autoApproveLowRisk: Boolean
        get() = prefs.getBoolean("auto_approve_low_risk", false)
        set(value) = prefs.edit().putBoolean("auto_approve_low_risk", value).apply()
        
    fun clear() {
        val savedAutoApprove = autoApproveLowRisk
        prefs.edit().clear().apply()
        autoApproveLowRisk = savedAutoApprove // preserve user preference
    }
}
