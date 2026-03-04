package com.example.daemonmobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = Indigo,
    secondary = Violet,
    tertiary = Neon,
    background = Bg0,
    surface = Bg1,
    surfaceVariant = Bg2,
    onPrimary = T1,
    onSecondary = T1,
    onTertiary = Bg0,
    onBackground = T1,
    onSurface = T1,
    onSurfaceVariant = T2,
    error = Red,
    onError = T1,
    outline = B1,
    outlineVariant = B2
)

@Composable
fun DaemonMobileTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}