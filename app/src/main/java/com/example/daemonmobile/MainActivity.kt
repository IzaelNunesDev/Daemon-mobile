package com.example.daemonmobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.daemonmobile.data.local.SledPreferences
import com.example.daemonmobile.ui.screens.ChatScreen
import com.example.daemonmobile.ui.screens.HistoryScreen
import com.example.daemonmobile.ui.screens.PairingScreen
import com.example.daemonmobile.ui.screens.SettingsScreen
import com.example.daemonmobile.ui.theme.DaemonMobileTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DaemonMobileTheme {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = SledPreferences(context)
    
    val startDestination = if (prefs.host != null && prefs.secret != null) "chat" else "pairing"

    NavHost(navController = navController, startDestination = startDestination) {
        composable("pairing") {
            PairingScreen(onPaired = { host, port, secret ->
                prefs.host = host
                prefs.port = port
                prefs.secret = secret
                navController.navigate("chat") {
                    popUpTo("pairing") { inclusive = true }
                }
            })
        }
        composable("chat") {
            ChatScreen(
                onNavigateBack = {
                    prefs.clear()
                    navController.navigate("pairing") {
                        popUpTo("chat") { inclusive = true }
                    }
                },
                onNavigateToSettings = { navController.navigate("settings") },
                onNavigateToHistory = { navController.navigate("history") }
            )
        }
        composable("settings") {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onDisconnect = {
                    navController.navigate("pairing") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
        composable("history") {
            HistoryScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}