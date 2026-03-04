package com.example.daemonmobile.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.daemonmobile.ui.theme.*

/**
 * ToolCard — renders a ToolUse event with expandable ToolResult output.
 * Matches the JSX design: indigo accent, truncated parameters, terminal-green output.
 */
@Composable
fun ToolCard(
    toolName: String,
    toolId: String,
    parameters: String? = null,
    resultStatus: String? = null,
    resultOutput: String? = null,
    resultError: String? = null
) {
    var isExpanded by remember { mutableStateOf(false) }
    val hasResult = resultStatus != null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .animateContentSize()
    ) {
        // Tool header badge
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Indigo.copy(alpha = 0.04f),
                    RoundedCornerShape(8.dp)
                )
                .clickable { if (hasResult) isExpanded = !isExpanded }
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Tool icon
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(Indigo.copy(alpha = 0.15f), RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = getToolIcon(toolName),
                    fontSize = 12.sp,
                    color = Indigo
                )
            }

            // Tool name
            Text(
                text = toolName,
                color = Indigo,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = MonoFamily
            )

            // Parameters (truncated)
            if (parameters != null) {
                Text(
                    text = parameters,
                    color = T3,
                    fontSize = 9.sp,
                    fontFamily = MonoFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            // Status indicator
            if (hasResult) {
                val statusColor = if (resultStatus == "success") Neon else Red
                val statusIcon = if (resultStatus == "success") "✓" else "✕"
                Text(
                    text = statusIcon,
                    color = statusColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = MonoFamily
                )
            }
        }

        // Expanded result
        if (isExpanded && hasResult) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp)
                    .background(Bg0, RoundedCornerShape(0.dp, 0.dp, 8.dp, 8.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                if (resultError != null) {
                    Text(
                        text = resultError,
                        color = Red,
                        fontSize = 10.sp,
                        fontFamily = MonoFamily,
                        lineHeight = 15.sp
                    )
                } else if (resultOutput != null) {
                    Text(
                        text = resultOutput,
                        color = TerminalGreen,
                        fontSize = 10.sp,
                        fontFamily = MonoFamily,
                        lineHeight = 15.sp
                    )
                }
            }
        }
    }
}

/**
 * Map tool names to icons matching the JSX design.
 */
private fun getToolIcon(toolName: String): String {
    return when (toolName) {
        "shell" -> "▶"
        "read_file" -> "◈"
        "write_file" -> "◆"
        "edit" -> "✎"
        "grep" -> "◉"
        "glob" -> "◉"
        "ls" -> "▣"
        "web_search" -> "◈"
        "web_fetch" -> "◈"
        else -> "▸"
    }
}
