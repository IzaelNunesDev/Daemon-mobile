package com.example.daemonmobile.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.daemonmobile.ui.theme.*
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

// ═══════════════════════════════════════════════════════════════
// User Bubble — gradient indigo→violet, border-radius 14/4/14/14
// ═══════════════════════════════════════════════════════════════
@Composable
fun UserMessageBubble(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.End
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .background(
                    brush = Brush.linearGradient(listOf(Indigo, Violet)),
                    shape = RoundedCornerShape(14.dp, 4.dp, 14.dp, 14.dp)
                )
                .padding(horizontal = 13.dp, vertical = 9.dp)
        ) {
            Text(
                text = text,
                color = Color.White,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                fontFamily = MonoFamily
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// Assistant Bubble — dark bg with optional ToolBadge & success state
// ═══════════════════════════════════════════════════════════════
@Composable
fun AssistantChatBubble(
    text: String,
    toolName: String? = null,
    toolCmd: String? = null,
    isSuccess: Boolean = false,
    timestamp: String? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .animateContentSize()
    ) {
        // Tool badge (if tool was used)
        if (toolName != null) {
            ToolBadge(toolName = toolName, toolCmd = toolCmd)
            Spacer(modifier = Modifier.height(4.dp))
        }

        // Bubble
        val bgBrush = if (isSuccess) {
            Brush.linearGradient(listOf(Neon.copy(alpha = 0.03f), Bg2))
        } else {
            Brush.linearGradient(listOf(Bg2, Bg1))
        }
        val borderColor = if (isSuccess) Neon.copy(alpha = 0.3f) else B1

        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .background(bgBrush, RoundedCornerShape(4.dp, 14.dp, 14.dp, 14.dp))
                .background(Color.Transparent) // overlay
                .padding(1.dp)
        ) {
            Box(
                modifier = Modifier
                    .background(bgBrush, RoundedCornerShape(4.dp, 14.dp, 14.dp, 14.dp))
                    .padding(horizontal = 12.dp, vertical = 9.dp)
            ) {
                Column {
                    Row {
                        if (isSuccess) {
                            Text(
                                text = "✓ ",
                                color = Neon,
                                fontSize = 12.sp,
                                fontFamily = MonoFamily
                            )
                        }
                        Text(
                            text = text,
                            color = if (isSuccess) Color(0xFFB8FFD8) else T2,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            fontFamily = MonoFamily
                        )
                    }
                    if (timestamp != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = formatTimestamp(timestamp),
                            color = T3,
                            fontSize = 9.sp,
                            fontFamily = MonoFamily,
                            modifier = Modifier.align(Alignment.End)
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// Tool Badge — small inline badge showing tool name + command
// ═══════════════════════════════════════════════════════════════
@Composable
fun ToolBadge(toolName: String, toolCmd: String? = null) {
    Row(
        modifier = Modifier
            .background(
                Indigo.copy(alpha = 0.04f),
                RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "▸",
            color = Indigo,
            fontSize = 8.sp,
            fontFamily = MonoFamily
        )
        Text(
            text = toolName,
            color = Indigo,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = MonoFamily
        )
        if (toolCmd != null) {
            Text(
                text = toolCmd,
                color = T3,
                fontSize = 9.sp,
                fontFamily = MonoFamily,
                maxLines = 1,
                modifier = Modifier.widthIn(max = 160.dp)
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// Stream Bubble — terminal green text on dark background
// ═══════════════════════════════════════════════════════════════
@Composable
fun StreamBubble(lines: List<String>) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(Bg0, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Column {
            lines.forEach { line ->
                Text(
                    text = line,
                    color = TerminalGreen,
                    fontSize = 10.sp,
                    lineHeight = 16.sp,
                    fontFamily = MonoFamily
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// Thinking Dots — 3 pulsing indigo dots with staggered animation
// ═══════════════════════════════════════════════════════════════
@Composable
fun ThinkingDots(stage: String = "Gemini processando...") {
    val infiniteTransition = rememberInfiniteTransition(label = "thinking")

    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            (0..2).forEach { i ->
                val scale by infiniteTransition.animateFloat(
                    initialValue = 0.7f,
                    targetValue = 1.3f,
                    animationSpec = infiniteRepeatable(
                        animation = keyframes {
                            durationMillis = 1300
                            0.7f at 0
                            1.3f at 650
                            0.7f at 1300
                        },
                        repeatMode = RepeatMode.Restart,
                        initialStartOffset = StartOffset(i * 180)
                    ),
                    label = "dot$i"
                )
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 0.2f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = keyframes {
                            durationMillis = 1300
                            0.2f at 0
                            1f at 650
                            0.2f at 1300
                        },
                        repeatMode = RepeatMode.Restart,
                        initialStartOffset = StartOffset(i * 180)
                    ),
                    label = "dotAlpha$i"
                )
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .scale(scale)
                        .alpha(alpha)
                        .clip(CircleShape)
                        .background(Indigo)
                )
            }
        }
        Text(
            text = stage,
            color = T3,
            fontSize = 10.sp,
            fontFamily = MonoFamily
        )
    }
}

// ═══════════════════════════════════════════════════════════════
// System Message — center-aligned subtle text
// ═══════════════════════════════════════════════════════════════
@Composable
fun SystemMessageBubble(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = SurfaceLight.copy(alpha = 0.4f),
            modifier = Modifier.animateContentSize()
        ) {
            Text(
                text = text,
                color = T3,
                fontSize = 10.sp,
                fontFamily = MonoFamily,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// Query Result Bubble
// ═══════════════════════════════════════════════════════════════
@Composable
fun QueryResultBubble(text: String, timestamp: String? = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = "Resultado",
            color = Neon,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = MonoFamily,
            modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
        )
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .background(QueryResultBg, RoundedCornerShape(4.dp, 14.dp, 14.dp, 14.dp))
                .padding(horizontal = 12.dp, vertical = 9.dp)
        ) {
            Column {
                Text(
                    text = text,
                    color = T1,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    fontFamily = MonoFamily
                )
                if (timestamp != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formatTimestamp(timestamp),
                        color = T3,
                        fontSize = 9.sp,
                        fontFamily = MonoFamily
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// Error Bubble  
// ═══════════════════════════════════════════════════════════════
@Composable
fun ErrorBubble(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(ErrorBubbleBg, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "✕ ",
                color = Red,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = MonoFamily
            )
            Text(
                text = text,
                color = Red,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                fontFamily = MonoFamily
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// Typing Indicator (legacy compat wrapper)
// ═══════════════════════════════════════════════════════════════
@Composable
fun TypingIndicator(stage: String) {
    ThinkingDots(stage = stage)
}

// ── Helper ────────────────────────────────────────────────────
private fun formatTimestamp(timestamp: String): String {
    return try {
        val zdt = ZonedDateTime.parse(timestamp)
        zdt.format(DateTimeFormatter.ofPattern("HH:mm"))
    } catch (e: Exception) {
        ""
    }
}
