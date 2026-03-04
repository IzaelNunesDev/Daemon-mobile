package com.example.daemonmobile.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.daemonmobile.data.models.Question
import com.example.daemonmobile.ui.theme.*

@Composable
fun AskUserMultiFieldCard(
    title: String,
    questions: List<Question>,
    onSubmit: (Map<String, String>) -> Unit
) {
    val answers = remember { mutableStateMapOf<String, String>() }
    var isSubmitted by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(
                Brush.linearGradient(listOf(Color(0xFF0D0E1A), Bg1)),
                RoundedCornerShape(14.dp)
            )
            .border(1.dp, Violet.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
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
                    .background(Violet.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                    .border(1.dp, Violet.copy(alpha = 0.3f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("📝", fontSize = 14.sp)
            }
            Column {
                Text(
                    text = "FORMULÁRIO",
                    color = Violet,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = MonoFamily,
                    letterSpacing = 2.sp
                )
                Text(
                    text = title,
                    color = T1,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = MonoFamily,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        // Fields
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            questions.forEach { question ->
                val type = question.type ?: "text"
                val isMultiline = type == "multiline"
                val isPassword = type == "password"
                val isBoolean = type == "boolean"

                Column {
                    Text(
                        text = question.text,
                        color = T2,
                        fontSize = 10.sp,
                        fontFamily = MonoFamily,
                        modifier = Modifier.padding(bottom = 4.dp, start = 2.dp)
                    )

                    OutlinedTextField(
                        value = answers[question.id] ?: "",
                        onValueChange = { if (!isSubmitted) answers[question.id] = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = !isMultiline,
                        maxLines = if (isMultiline) 4 else 1,
                        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
                        placeholder = { Text("Preencher...", color = T4, fontFamily = MonoFamily, fontSize = 11.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Violet.copy(alpha = 0.4f),
                            unfocusedBorderColor = B1,
                            focusedTextColor = T1,
                            unfocusedTextColor = T1,
                            cursorColor = Violet,
                            focusedContainerColor = Bg0,
                            unfocusedContainerColor = Bg0
                        ),
                        shape = RoundedCornerShape(9.dp),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Text,
                            imeAction = if (isMultiline) ImeAction.Default else ImeAction.Next
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Submit Button
        val isComplete = questions.all { !answers[it.id].isNullOrBlank() }
        val btnColor = if (isComplete && !isSubmitted) Violet else B2
        val textColor = if (isComplete && !isSubmitted) Color.White else T3

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(btnColor, RoundedCornerShape(9.dp))
                .clickable {
                    if (isComplete && !isSubmitted) {
                        isSubmitted = true
                        onSubmit(answers.toMap())
                    }
                }
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isSubmitted) "Enviado ✓" else "Confirmar e Enviar",
                color = textColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = MonoFamily
            )
        }
    }
}
