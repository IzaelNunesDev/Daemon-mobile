package com.example.daemonmobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.daemonmobile.data.local.SledPreferences
import com.example.daemonmobile.ui.theme.*

@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onDisconnect: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { SledPreferences(context) }

    var host by remember { mutableStateOf(prefs.host ?: "") }
    var port by remember { mutableStateOf(prefs.port.toString()) }
    var autoApprove by remember { mutableStateOf(prefs.autoApproveLowRisk) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg0)
            .statusBarsPadding()
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Bg2, Bg1)))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clickable { onNavigateBack() },
                contentAlignment = Alignment.Center
            ) {
                Text("←", color = T1, fontSize = 16.sp, fontFamily = MonoFamily)
            }
            Text(
                text = "CONFIGURAÇÕES",
                color = T1,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = MonoFamily,
                letterSpacing = 2.sp
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(B1)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Connection Section ──────────────────────────
            Text(
                text = "CONEXÃO",
                color = Indigo,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                fontFamily = MonoFamily,
                letterSpacing = 2.sp
            )

            OutlinedTextField(
                value = host,
                onValueChange = { host = it; prefs.host = it },
                label = { Text("Daemon IP / Host", color = T3, fontFamily = MonoFamily, fontSize = 10.sp) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = T1,
                    unfocusedTextColor = T1,
                    focusedBorderColor = Indigo,
                    unfocusedBorderColor = B1,
                    focusedContainerColor = Bg1,
                    unfocusedContainerColor = Bg1,
                    cursorColor = Indigo
                ),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = MonoFamily,
                    fontSize = 12.sp
                ),
                shape = RoundedCornerShape(10.dp)
            )

            OutlinedTextField(
                value = port,
                onValueChange = { port = it; it.toIntOrNull()?.let { p -> prefs.port = p } },
                label = { Text("Porta", color = T3, fontFamily = MonoFamily, fontSize = 10.sp) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = T1,
                    unfocusedTextColor = T1,
                    focusedBorderColor = Indigo,
                    unfocusedBorderColor = B1,
                    focusedContainerColor = Bg1,
                    unfocusedContainerColor = Bg1,
                    cursorColor = Indigo
                ),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = MonoFamily,
                    fontSize = 12.sp
                ),
                shape = RoundedCornerShape(10.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ── Behavior Section ────────────────────────────
            Text(
                text = "COMPORTAMENTO",
                color = Indigo,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                fontFamily = MonoFamily,
                letterSpacing = 2.sp
            )

            // Auto-approve toggle
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Bg1, RoundedCornerShape(10.dp))
                    .border(1.dp, B1, RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "⚡ Auto-aprovar risco baixo",
                            color = T1,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = MonoFamily
                        )
                        Text(
                            text = "Executa planos de baixo risco automaticamente",
                            color = T3,
                            fontSize = 9.sp,
                            fontFamily = MonoFamily,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = autoApprove,
                        onCheckedChange = {
                            autoApprove = it
                            prefs.autoApproveLowRisk = it
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = T1,
                            checkedTrackColor = Neon,
                            uncheckedThumbColor = T3,
                            uncheckedTrackColor = Bg2
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // ── Danger Zone ─────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(listOf(Red.copy(alpha = 0.15f), Bg1)),
                        RoundedCornerShape(10.dp)
                    )
                    .border(1.dp, Red.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                    .clickable {
                        prefs.clear()
                        onDisconnect()
                    }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "✕ Desconectar do SLED",
                    color = Red,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = MonoFamily
                )
            }
        }
    }
}
