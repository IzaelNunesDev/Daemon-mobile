package com.example.daemonmobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.daemonmobile.ui.theme.*

data class QuickAction(
    val icon: String,
    val label: String,
    val color: Color,
    val command: String
)

val QUICK_ACTIONS = listOf(
    QuickAction("◉", "Status", Neon, "get_status"),
    QuickAction("▣", "Logs", Amber, "get_logs"),
    QuickAction("◆", "Modelo", Indigo, "get_model_info"),
    QuickAction("◈", "Config", Cyan, "get_settings")
)

/**
 * QuickActionsBar — horizontal scrollable bar with only supported WS commands.
 */
@Composable
fun QuickActionsBar(
    onAction: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        itemsIndexed(QUICK_ACTIONS) { _, action ->
            QuickActionButton(action = action, onClick = { onAction(action.command) })
        }
    }
}

@Composable
private fun QuickActionButton(
    action: QuickAction,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Column(
        modifier = Modifier
            .scale(if (isPressed) 0.94f else 1f)
            .background(
                if (isPressed) action.color.copy(alpha = 0.12f) else action.color.copy(alpha = 0.04f),
                RoundedCornerShape(11.dp)
            )
            .border(
                1.dp,
                if (isPressed) action.color.copy(alpha = 0.5f) else action.color.copy(alpha = 0.12f),
                RoundedCornerShape(11.dp)
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = action.icon,
            color = action.color,
            fontSize = 14.sp
        )
        Text(
            text = action.label,
            color = if (isPressed) action.color else T3,
            fontSize = 8.sp,
            fontWeight = FontWeight.Normal,
            fontFamily = MonoFamily
        )
    }
}
