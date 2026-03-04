package com.example.daemonmobile.ui.theme

import androidx.compose.ui.graphics.Color

// ═══════════════════════════════════════════════════════════════
// SLED — "Terminal Cyberware" Design System
// ═══════════════════════════════════════════════════════════════

// Backgrounds
val Bg0 = Color(0xFF05060A)   // Base absoluta
val Bg1 = Color(0xFF0C0D14)   // Superfície principal
val Bg2 = Color(0xFF111320)   // Cards elevados

// Primary accents
val Indigo = Color(0xFF6366F1)  // Acento primário
val Violet = Color(0xFF8B5CF6)  // Gradiente secundário

// Semantic colors
val Neon = Color(0xFF00FF9D)    // Sucesso / Ativo
val Cyan = Color(0xFF00D4FF)    // Dados / Fluxo
val Amber = Color(0xFFF59E0B)   // Atenção / Sudo
val Red = Color(0xFFFF4455)     // Perigo / Kill
val Rose = Color(0xFFFB7185)    // Acento alternativo

// Text hierarchy
val T1 = Color(0xFFF0F2FF)     // Texto brilhante
val T2 = Color(0xFF9BA3C2)     // Texto secundário
val T3 = Color(0xFF4A5070)     // Texto muted
val T4 = Color(0xFF272A3D)     // Texto background

// Borders
val B1 = Color(0x12FFFFFF)     // rgba(255,255,255,0.07)
val B2 = Color(0x0AFFFFFF)     // rgba(255,255,255,0.04)

// Terminal
val TerminalGreen = Color(0xFF3DDC84)

// ═══════════════════════════════════════════════════════════════
// Backwards-compatible aliases (used by existing components)
// ═══════════════════════════════════════════════════════════════
val BgPrimary = Bg1
val BgSecondary = Bg2
val SurfaceLight = Color(0xFF1A1F2E)
val TextBright = T1
val TextDim = T2
val BorderSubtle = B1
val StatusSuccess = Neon
val StatusError = Red
val StatusWarning = Amber
val StatusInfo = Cyan
val AccentPrimary = Indigo
val AccentSecondary = Violet

// Bubble backgrounds
val ChatBubbleBg = Bg2
val QueryResultBg = Color(0xFF0A1A14)  // Dark green tint
val PlanSummaryBg = Color(0xFF0D0E1A)  // Dark indigo tint
val ErrorBubbleBg = Color(0xFF1A0A0A)  // Dark red tint

// Typing indicator
val TypingDot = Indigo