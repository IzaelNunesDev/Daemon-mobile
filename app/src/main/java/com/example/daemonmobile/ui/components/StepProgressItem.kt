package com.example.daemonmobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.daemonmobile.ui.theme.*

@Composable
fun StepProgressItem(
    stepIndex: Int,
    description: String,
    output: String? = null,
    error: String? = null,
    isCompleted: Boolean = false
) {
    val statusIcon = when {
        error != null -> "✗"
        isCompleted -> "✓"
        else -> "▶"
    }
    val statusColor = when {
        error != null -> StatusError
        isCompleted -> StatusSuccess
        else -> StatusInfo
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 12.dp)
    ) {
        Row {
            Text(text = statusIcon, color = statusColor, modifier = Modifier.padding(end = 8.dp))
            Text(text = "Etapa $stepIndex: $description", color = TextBright)
        }
        
        if (output != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, top = 4.dp)
                    .background(BgPrimary, RoundedCornerShape(4.dp))
                    .padding(8.dp)
            ) {
                Text(
                    text = output,
                    color = TextDim,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        
        if (error != null) {
             Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, top = 4.dp)
                    .background(BgPrimary, RoundedCornerShape(4.dp))
                    .padding(8.dp)
            ) {
                Text(
                    text = error,
                    color = StatusError,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
