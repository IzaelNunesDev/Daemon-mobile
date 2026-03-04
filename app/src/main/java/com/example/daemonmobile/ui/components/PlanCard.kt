package com.example.daemonmobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.daemonmobile.data.models.Plan
import com.example.daemonmobile.ui.theme.*
import com.example.daemonmobile.ui.viewmodels.PlanStatus

@Composable
fun PlanCard(
    plan: Plan,
    status: PlanStatus,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        // SLED avatar
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(AccentSecondary.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text("📋", fontSize = 16.sp)
        }
        Spacer(modifier = Modifier.width(8.dp))
        
        Card(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .border(
                    width = 1.dp,
                    color = AccentSecondary.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(4.dp, 12.dp, 12.dp, 12.dp)
                ),
            colors = CardDefaults.cardColors(containerColor = PlanSummaryBg),
            shape = RoundedCornerShape(4.dp, 12.dp, 12.dp, 12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // Title
                Text(
                    text = "📋 Plano de Execução",
                    color = AccentSecondary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Goal
                Text(
                    text = plan.goal,
                    color = TextBright,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(6.dp))

                // Risk badge
                val (riskLabel, riskColor, riskIcon) = when (plan.riskLevel.lowercase()) {
                    "low" -> Triple("Baixo Risco", StatusSuccess, "🟢")
                    "medium" -> Triple("Risco Médio", StatusWarning, "🟡")
                    "high" -> Triple("Alto Risco", StatusError, "🔴")
                    else -> Triple(plan.riskLevel, TextDim, "⚪")
                }
                Box(
                    modifier = Modifier
                        .background(riskColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "$riskIcon $riskLabel",
                        color = riskColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Steps
                plan.steps.forEachIndexed { index, step ->
                    Row(
                        modifier = Modifier.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .background(AccentPrimary.copy(alpha = 0.2f), RoundedCornerShape(4.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${index + 1}",
                                color = AccentPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = step.description,
                            color = TextBright.copy(alpha = 0.8f),
                            fontSize = 13.sp,
                            lineHeight = 17.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Action buttons
                if (status == PlanStatus.PENDING) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onApprove,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = StatusSuccess),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Text("✓ Aprovar", color = BgPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        OutlinedButton(
                            onClick = onReject,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusError),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Text("✗ Rejeitar", color = StatusError, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (status == PlanStatus.APPROVED) StatusSuccess.copy(alpha = 0.15f)
                                else StatusError.copy(alpha = 0.15f),
                                RoundedCornerShape(6.dp)
                            )
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (status == PlanStatus.APPROVED) "✅ Plano Aprovado" else "❌ Plano Rejeitado",
                            color = if (status == PlanStatus.APPROVED) StatusSuccess else StatusError,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}
