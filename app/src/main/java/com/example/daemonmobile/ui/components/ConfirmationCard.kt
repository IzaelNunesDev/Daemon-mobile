package com.example.daemonmobile.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.daemonmobile.ui.theme.*

/**
 * ConfirmationCard — renders PromptInput events.
 * Supports: choice (vertical buttons), yesno (two buttons), text (input field).
 */
@Composable
fun ConfirmationCard(
    promptType: String,
    label: String,
    options: List<String>?,
    hint: String? = null,
    onSubmit: (String) -> Unit
) {
    when (promptType) {
        "choice" -> ChoicePrompt(label, hint, options ?: emptyList(), onSubmit)
        "yesno" -> YesNoPrompt(label, hint, onSubmit)
        "text" -> TextInputPrompt(label, hint, onSubmit)
        "password" -> PasswordInputPrompt(label, hint, onSubmit)
        else -> TextInputPrompt(label, hint, onSubmit)
    }
}

// ═══════════════════════════════════════════════════════════════
// Choice Prompt — vertical button list matching JSX ConfirmPrompt
// ═══════════════════════════════════════════════════════════════
@Composable
private fun ChoicePrompt(
    label: String,
    hint: String?,
    options: List<String>,
    onSubmit: (String) -> Unit
) {
    var selectedOption by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(
                Brush.linearGradient(listOf(Color(0xFF0D0E1A), Bg1)),
                RoundedCornerShape(14.dp)
            )
            .border(1.dp, Indigo.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
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
                    .background(Indigo.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                    .border(1.dp, Indigo.copy(alpha = 0.3f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("⚡", fontSize = 14.sp)
            }
            Column {
                Text(
                    text = "CONFIRMAÇÃO",
                    color = Indigo,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = MonoFamily,
                    letterSpacing = 2.sp
                )
                if (hint != null) {
                    Text(
                        text = hint,
                        color = T3,
                        fontSize = 9.sp,
                        fontFamily = MonoFamily,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }

        // Label
        Text(
            text = label,
            color = T1,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = MonoFamily,
            modifier = Modifier.padding(bottom = 10.dp)
        )

        // Options
        options.forEachIndexed { index, option ->
            val letter = if (option.isNotEmpty()) option[0].toString() else "${index + 1}"
            val optionText = if (option.length > 4 && option[1] == ' ' && option[2] == '—' && option[3] == ' ') {
                option.substring(4)
            } else {
                option
            }
            val isSelected = selectedOption == letter
            val buttonColor = getOptionColor(letter)

            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
                    .scale(if (isPressed || isSelected) 0.98f else 1f)
                    .background(
                        if (isSelected) buttonColor.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.02f),
                        RoundedCornerShape(9.dp)
                    )
                    .border(
                        1.dp,
                        if (isSelected) buttonColor.copy(alpha = 0.4f) else B2,
                        RoundedCornerShape(9.dp)
                    )
                    .clickable(interactionSource = interactionSource, indication = null) {
                        if (selectedOption == null) {
                            selectedOption = letter
                            onSubmit(letter)
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    // Letter badge
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .background(buttonColor.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                            .border(1.dp, buttonColor.copy(alpha = 0.5f), RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = letter,
                            color = buttonColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = MonoFamily
                        )
                    }

                    Text(
                        text = optionText,
                        color = if (isSelected) buttonColor else T3,
                        fontSize = 11.sp,
                        fontFamily = MonoFamily,
                        modifier = Modifier.weight(1f)
                    )

                    if (isSelected) {
                        Text(
                            text = "✓",
                            color = buttonColor,
                            fontSize = 10.sp,
                            fontFamily = MonoFamily
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// Yes/No Prompt
// ═══════════════════════════════════════════════════════════════
@Composable
private fun YesNoPrompt(label: String, hint: String?, onSubmit: (String) -> Unit) {
    ChoicePrompt(
        label = label,
        hint = hint,
        options = listOf("S — Sim", "N — Não"),
        onSubmit = onSubmit
    )
}

// ═══════════════════════════════════════════════════════════════
// Text Input Prompt
// ═══════════════════════════════════════════════════════════════
@Composable
private fun TextInputPrompt(label: String, hint: String?, onSubmit: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    var sent by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(
                Brush.linearGradient(listOf(Color(0xFF0D0E1A), Bg1)),
                RoundedCornerShape(14.dp)
            )
            .border(1.dp, Cyan.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
            .padding(14.dp)
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
                    .background(Cyan.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                    .border(1.dp, Cyan.copy(alpha = 0.3f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("◈", fontSize = 14.sp, color = Cyan)
            }
            Column {
                Text(
                    text = "INPUT NECESSÁRIO",
                    color = Cyan,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = MonoFamily,
                    letterSpacing = 2.sp
                )
                if (hint != null) {
                    Text(text = hint, color = T3, fontSize = 9.sp, fontFamily = MonoFamily)
                }
            }
        }

        Text(
            text = label,
            color = T2,
            fontSize = 11.sp,
            fontFamily = MonoFamily,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                value = text,
                onValueChange = { if (!sent) text = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("Resposta...", color = T4, fontFamily = MonoFamily, fontSize = 12.sp) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Cyan.copy(alpha = 0.3f),
                    unfocusedBorderColor = B1,
                    focusedTextColor = T1,
                    unfocusedTextColor = T1,
                    cursorColor = Cyan,
                    focusedContainerColor = Bg0,
                    unfocusedContainerColor = Bg0
                ),
                shape = RoundedCornerShape(9.dp),
                keyboardActions = KeyboardActions(onDone = {
                    if (text.isNotBlank() && !sent) {
                        sent = true
                        onSubmit(text)
                    }
                }),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
            )
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        if (text.isNotBlank() && !sent)
                            Brush.linearGradient(listOf(Cyan, Indigo))
                        else Brush.linearGradient(listOf(Color(0xFF111320), Color(0xFF111320))),
                        RoundedCornerShape(9.dp)
                    )
                    .clickable {
                        if (text.isNotBlank() && !sent) {
                            sent = true
                            onSubmit(text)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (sent) "✓" else "→",
                    color = if (text.isNotBlank() && !sent) Color.White else T3,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = MonoFamily
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// Password Input Prompt — matching JSX PasswordPrompt
// ═══════════════════════════════════════════════════════════════
@Composable
private fun PasswordInputPrompt(label: String, hint: String?, onSubmit: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(
                Brush.linearGradient(listOf(Color(0xFF120E04), Bg1)),
                RoundedCornerShape(14.dp)
            )
            .border(1.dp, Amber.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
            .padding(14.dp)
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
                    .background(Amber.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                    .border(1.dp, Amber.copy(alpha = 0.3f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("🔐", fontSize = 14.sp)
            }
            Column {
                Text(
                    text = "SENHA NECESSÁRIA",
                    color = Amber,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = MonoFamily,
                    letterSpacing = 2.sp
                )
                if (hint != null) {
                    Text(text = hint, color = T3, fontSize = 9.sp, fontFamily = MonoFamily)
                }
            }
        }

        Text(
            text = label,
            color = T2,
            fontSize = 11.sp,
            fontFamily = MonoFamily,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                value = password,
                onValueChange = { if (!sent) password = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                placeholder = { Text("••••••••", color = T4, fontFamily = MonoFamily, fontSize = 12.sp) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Amber.copy(alpha = 0.3f),
                    unfocusedBorderColor = B1,
                    focusedTextColor = T1,
                    unfocusedTextColor = T1,
                    cursorColor = Amber,
                    focusedContainerColor = Bg0,
                    unfocusedContainerColor = Bg0
                ),
                shape = RoundedCornerShape(9.dp),
                trailingIcon = {
                    Text(
                        text = if (showPassword) "○" else "●",
                        color = T3,
                        fontSize = 11.sp,
                        fontFamily = MonoFamily,
                        modifier = Modifier
                            .clickable { showPassword = !showPassword }
                            .padding(4.dp)
                    )
                },
                keyboardActions = KeyboardActions(onDone = {
                    if (password.isNotBlank() && !sent) {
                        sent = true
                        onSubmit(password)
                    }
                }),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                )
            )
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        if (password.isNotBlank() && !sent)
                            Brush.linearGradient(listOf(Amber, Red))
                        else Brush.linearGradient(listOf(Color(0xFF111320), Color(0xFF111320))),
                        RoundedCornerShape(9.dp)
                    )
                    .clickable {
                        if (password.isNotBlank() && !sent) {
                            sent = true
                            onSubmit(password)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (sent) "✓" else "→",
                    color = if (password.isNotBlank() && !sent) Color.White else T3,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = MonoFamily
                )
            }
        }
    }
}

// ── Helpers ────────────────────────────────────────────────────
private fun getOptionColor(letter: String): Color {
    return when (letter.uppercase()) {
        "S", "Y" -> Neon
        "N" -> Red
        "V", "D" -> Cyan
        else -> T2
    }
}
