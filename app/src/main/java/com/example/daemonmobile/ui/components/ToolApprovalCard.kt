package com.example.daemonmobile.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.daemonmobile.ui.theme.*

@Composable
fun ToolApprovalCard(
    toolName: String,
    command: String?,
    riskLevel: String?,
    onChoice: (String) -> Unit
) {
    var selectedOption by remember { mutableStateOf<String?>(null) }
    
    val riskColor = when (riskLevel?.lowercase()) {
        "high" -> Red
        "medium" -> Amber
        "low" -> Neon
        else -> Cyan
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(
                Brush.linearGradient(listOf(Color(0xFF0D0E1A), Bg1)),
                RoundedCornerShape(14.dp)
            )
            .border(1.dp, riskColor.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
            .padding(14.dp)
            .animateContentSize()
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .background(riskColor.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                    .border(1.dp, riskColor.copy(alpha = 0.3f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("⚠️", fontSize = 14.sp)
            }
            Column {
                Text(
                    text = "AUTORIZAÇÃO DE FERRAMENTA",
                    color = riskColor,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = MonoFamily,
                    letterSpacing = 2.sp
                )
                Text(
                    text = "Ação de risco ${riskLevel?.uppercase() ?: "DESCONHECIDO"}",
                    color = T3,
                    fontSize = 9.sp,
                    fontFamily = MonoFamily,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        // Details
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .background(Bg2, RoundedCornerShape(8.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Ferramenta: $toolName",
                color = T1,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = MonoFamily
            )
            if (command != null) {
                Text(
                    text = "$> $command",
                    color = TerminalGreen,
                    fontSize = 10.sp,
                    fontFamily = MonoFamily
                )
            }
        }

        // Action Buttons
        val options = listOf(
            "once" to "Permitir uma vez",
            "session" to "Permitir nesta sessão",
            "deny" to "Negar"
        )
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            options.forEach { (value, label) ->
                val isDeny = value == "deny"
                val baseColor = if (isDeny) Red else Neon
                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .scale(if (isPressed) 0.95f else 1f)
                        .background(
                            baseColor.copy(alpha = 0.1f),
                            RoundedCornerShape(8.dp)
                        )
                        .border(
                            1.dp,
                            baseColor.copy(alpha = 0.3f),
                            RoundedCornerShape(8.dp)
                        )
                        .clickable(interactionSource = interactionSource, indication = null) {
                            if (selectedOption == null) {
                                selectedOption = value
                                onChoice(value)
                            }
                        }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = baseColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = MonoFamily
                    )
                }
            }
        }
    }
}
