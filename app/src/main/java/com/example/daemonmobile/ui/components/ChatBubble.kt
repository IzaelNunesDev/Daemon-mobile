package com.example.daemonmobile.ui.components

import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.daemonmobile.ui.theme.*
import java.time.Instant
import java.time.ZoneId
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
                .copyOnLongPress(text)
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

        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .copyOnLongPress(text)
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
                    if (isSuccess) {
                        Text(
                            text = "✓ concluído",
                            color = Neon,
                            fontSize = 10.sp,
                            fontFamily = MonoFamily,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    MarkdownMessageText(
                        text = text,
                        textColor = if (isSuccess) Color(0xFFB8FFD8) else T2
                    )
                    if (timestamp != null) {
                        val formatted = formatTimestamp(timestamp)
                        if (formatted.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = formatted,
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
}

@Composable
private fun MarkdownMessageText(text: String, textColor: Color) {
    val blocks = remember(text) { parseMarkdownBlocks(text) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.TextBlock -> {
                    Text(
                        text = parseInlineBold(block.content),
                        color = textColor,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        fontFamily = MonoFamily
                    )
                }
                is MarkdownBlock.CodeBlock -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF10131F), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = block.content.trimEnd(),
                            color = TerminalGreen,
                            fontSize = 11.sp,
                            lineHeight = 17.sp,
                            fontFamily = MonoFamily
                        )
                    }
                }
            }
        }
    }
}

private sealed class MarkdownBlock {
    data class TextBlock(val content: String) : MarkdownBlock()
    data class CodeBlock(val content: String) : MarkdownBlock()
}

private fun parseMarkdownBlocks(text: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val regex = Regex("(?s)```(?:\\w+)?\\n(.*?)```")
    var cursor = 0
    regex.findAll(text).forEach { match ->
        if (match.range.first > cursor) {
            val plain = text.substring(cursor, match.range.first).trim('\n')
            if (plain.isNotBlank()) blocks.add(MarkdownBlock.TextBlock(plain))
        }
        val code = match.groupValues.getOrElse(1) { "" }
        if (code.isNotBlank()) blocks.add(MarkdownBlock.CodeBlock(code))
        cursor = match.range.last + 1
    }
    if (cursor < text.length) {
        val tail = text.substring(cursor).trim('\n')
        if (tail.isNotBlank()) blocks.add(MarkdownBlock.TextBlock(tail))
    }
    if (blocks.isEmpty()) blocks.add(MarkdownBlock.TextBlock(text))
    return blocks
}

private fun parseInlineBold(text: String): AnnotatedString {
    val regex = Regex("\\*\\*(.+?)\\*\\*")
    val builder = buildAnnotatedString {
        var cursor = 0
        regex.findAll(text).forEach { match ->
            if (match.range.first > cursor) {
                append(text.substring(cursor, match.range.first))
            }
            pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
            append(match.groupValues[1])
            pop()
            cursor = match.range.last + 1
        }
        if (cursor < text.length) {
            append(text.substring(cursor))
        }
    }
    return builder
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.copyOnLongPress(text: String): Modifier = composed {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }

    this.combinedClickable(
        interactionSource = interactionSource,
        indication = null,
        onClick = {},
        onLongClick = {
            clipboard.setText(AnnotatedString(text))
            Toast.makeText(context, "Texto copiado", Toast.LENGTH_SHORT).show()
        }
    )
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
            modifier = Modifier
                .animateContentSize()
                .copyOnLongPress(text)
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
                .copyOnLongPress(text)
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
                    val formatted = formatTimestamp(timestamp)
                    if (formatted.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = formatted,
                            color = T3,
                            fontSize = 9.sp,
                            fontFamily = MonoFamily
                        )
                    }
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
            .copyOnLongPress(text)
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
    val formatter = DateTimeFormatter.ofPattern("HH:mm")
    return runCatching {
        ZonedDateTime.parse(timestamp).format(formatter)
    }.recoverCatching {
        Instant.parse(timestamp).atZone(ZoneId.systemDefault()).format(formatter)
    }.getOrElse {
        val marker = timestamp.indexOf('T')
        if (marker in 1 until (timestamp.length - 5)) {
            timestamp.substring(marker + 1).take(5)
        } else {
            ""
        }
    }
}
