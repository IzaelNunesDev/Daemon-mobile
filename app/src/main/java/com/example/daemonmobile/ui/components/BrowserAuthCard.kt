package com.example.daemonmobile.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.daemonmobile.ui.theme.*

val AuthBlue = Color(0xFF2DA4FF)

@Composable
fun BrowserAuthCard(
    url: String,
    code: String?,
    instruction: String?,
    onDone: () -> Unit
) {
    var isSubmitted by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(
                Brush.linearGradient(listOf(Color(0xFF0A192A), Bg1)),
                RoundedCornerShape(14.dp)
            )
            .border(1.dp, AuthBlue.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
            .padding(14.dp)
            .animateContentSize()
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .background(AuthBlue.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                    .border(1.dp, AuthBlue.copy(alpha = 0.3f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("🌐", fontSize = 14.sp)
            }
            Column {
                Text(
                    text = "AÇÃO NO NAVEGADOR",
                    color = AuthBlue,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = MonoFamily,
                    letterSpacing = 2.sp
                )
                if (instruction != null) {
                    Text(
                        text = instruction,
                        color = T3,
                        fontSize = 9.sp,
                        fontFamily = MonoFamily,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }

        // Code display if exists
        if (code != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .background(Bg2, RoundedCornerShape(8.dp))
                    .border(1.dp, B1, RoundedCornerShape(8.dp))
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "CÓDIGO DE AUTENTICAÇÃO",
                    color = T3,
                    fontSize = 8.sp,
                    fontFamily = MonoFamily,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = code,
                    color = T1,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = MonoFamily,
                    letterSpacing = 4.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Open Browser Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .background(AuthBlue.copy(alpha = 0.15f), RoundedCornerShape(9.dp))
                .border(1.dp, AuthBlue.copy(alpha = 0.4f), RoundedCornerShape(9.dp))
                .clickable {
                    try {
                        uriHandler.openUri(url)
                    } catch (e: Exception) {
                        // ignore or log
                    }
                }
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Abrir Navegador",
                color = AuthBlue,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = MonoFamily
            )
        }

        // Done Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (isSubmitted) B2 else Bg2, RoundedCornerShape(9.dp))
                .border(1.dp, B1, RoundedCornerShape(9.dp))
                .clickable {
                    if (!isSubmitted) {
                        isSubmitted = true
                        onDone()
                    }
                }
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isSubmitted) "Continue no navegador..." else "Já Autentiquei",
                color = if (isSubmitted) T4 else T2,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = MonoFamily
            )
        }
    }
}
