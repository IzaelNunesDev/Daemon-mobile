package com.example.daemonmobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.daemonmobile.ui.theme.*

@Composable
fun HistoryScreen(onNavigateBack: () -> Unit) {
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

        // Empty state
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("◈", color = T3, fontSize = 32.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Sem histórico",
                    color = T3,
                    fontSize = 10.sp,
                    fontFamily = MonoFamily
                )
                Text(
                    text = "As sessões aparecerão aqui",
                    color = T4,
                    fontSize = 9.sp,
                    fontFamily = MonoFamily
                )
            }
        }
    }
}
