package com.example.daemonmobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.daemonmobile.data.local.SledPreferences
import com.example.daemonmobile.ui.theme.*
import com.example.daemonmobile.ui.viewmodels.ArchivedChatSession
import com.example.daemonmobile.ui.viewmodels.deleteArchivedChatSession
import com.example.daemonmobile.ui.viewmodels.loadArchivedChatSessions
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HistoryScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { SledPreferences(context) }
    var sessions by remember { mutableStateOf<List<ArchivedChatSession>>(emptyList()) }
    var search by remember { mutableStateOf("") }

    val filtered = remember(sessions, search) {
        val query = search.trim().lowercase()
        if (query.isBlank()) sessions
        else sessions.filter {
            it.title.lowercase().contains(query) || it.preview.lowercase().contains(query)
        }
    }

    LaunchedEffect(Unit) {
        sessions = loadArchivedChatSessions(prefs)
    }

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
                text = "HISTÓRICO",
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

        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            singleLine = true,
            placeholder = {
                Text(
                    text = "Buscar sessão...",
                    color = T4,
                    fontSize = 10.sp,
                    fontFamily = MonoFamily
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Bg1,
                unfocusedContainerColor = Bg1,
                focusedBorderColor = B1,
                unfocusedBorderColor = B1,
                focusedTextColor = T1,
                unfocusedTextColor = T1,
                cursorColor = Indigo
            ),
            textStyle = androidx.compose.ui.text.TextStyle(
                fontFamily = MonoFamily,
                fontSize = 11.sp
            ),
            shape = RoundedCornerShape(10.dp)
        )

        // Empty state
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            if (filtered.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("◈", color = T3, fontSize = 32.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (sessions.isEmpty()) "Sem histórico" else "Nenhum resultado",
                        color = T3,
                        fontSize = 10.sp,
                        fontFamily = MonoFamily
                    )
                    Text(
                        text = if (sessions.isEmpty()) {
                            "Inicie um novo chat para arquivar sessões"
                        } else {
                            "Tente outro termo de busca"
                        },
                        color = T4,
                        fontSize = 9.sp,
                        fontFamily = MonoFamily
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(filtered, key = { it.id }) { session ->
                        HistorySessionCard(
                            session = session,
                            onDelete = {
                                deleteArchivedChatSession(prefs, session.id)
                                sessions = loadArchivedChatSessions(prefs)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistorySessionCard(session: ArchivedChatSession, onDelete: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(listOf(Bg2, Bg1)),
                RoundedCornerShape(12.dp)
            )
            .border(1.dp, B1, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = session.title,
                color = T1,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = MonoFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = formatSessionDate(session.updatedAt),
                color = T4,
                fontSize = 8.sp,
                fontFamily = MonoFamily
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "🗑",
                color = T4,
                fontSize = 11.sp,
                fontFamily = MonoFamily,
                modifier = Modifier.clickable { onDelete() }
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = session.preview,
            color = T3,
            fontSize = 9.sp,
            fontFamily = MonoFamily,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "${session.messageCount} mensagens",
            color = T4,
            fontSize = 8.sp,
            fontFamily = MonoFamily
        )
    }
}

private fun formatSessionDate(epochMs: Long): String {
    return try {
        val instant = Instant.ofEpochMilli(epochMs)
        val zoned = instant.atZone(ZoneId.systemDefault())
        DateTimeFormatter.ofPattern("dd/MM HH:mm").format(zoned)
    } catch (_: Exception) {
        "--/-- --:--"
    }
}
