package com.smartstorage.cleaner.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/** Color tokens from the design handoff (§2.1). Mirrors `Palette.swift` on iOS. */
@Immutable
data class SmartColors(
    val background: Color,
    val surface: Color,
    val surfaceMuted: Color,
    val separator: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accent: Color,
    val accentGradientStart: Color,
    val accentGradientEnd: Color,
    val icyBlue: Color,
    val isDark: Boolean,
) {
    val accentGradient: Brush get() = Brush.horizontalGradient(listOf(accentGradientStart, accentGradientEnd))

    /** Soft wash used behind hero cards. */
    val heroGradient: Brush get() = Brush.linearGradient(listOf(surface, icyBlue))
}

val LightSmartColors = SmartColors(
    background = Color(0xFFF8F7F4),
    surface = Color(0xFFFFFFFF),
    surfaceMuted = Color(0xFFEEF2F6),
    separator = Color(0xFFE4E8ED),
    textPrimary = Color(0xFF0B1B2B),
    textSecondary = Color(0xFF6B7A8F),
    accent = Color(0xFF1E8FB0),
    accentGradientStart = Color(0xFF52B6CE),
    accentGradientEnd = Color(0xFF16809F),
    icyBlue = Color(0xFFE7F4FA),
    isDark = false,
)

val DarkSmartColors = SmartColors(
    background = Color(0xFF0E1419),
    surface = Color(0xFF172029),
    surfaceMuted = Color(0xFF1F2A35),
    separator = Color(0xFF2A3642),
    textPrimary = Color(0xFFF2F5F8),
    textSecondary = Color(0xFF97A5B5),
    accent = Color(0xFF4FB8D4),
    accentGradientStart = Color(0xFF3FA7C4),
    accentGradientEnd = Color(0xFF1B7390),
    icyBlue = Color(0xFF16303D),
    isDark = true,
)

val LocalSmartColors = staticCompositionLocalOf { LightSmartColors }

/** Semantic tints for icon tiles, badges and status indicators. */
enum class Tint(
    private val lightFg: Long, private val lightBg: Long,
    private val darkFg: Long, private val darkBg: Long,
) {
    Teal(0xFF1E8FB0, 0xFFE3F3F6, 0xFF4FB8D4, 0xFF163239),
    Blue(0xFF2F7FE8, 0xFFE6F0FD, 0xFF6BA8F5, 0xFF192B42),
    Purple(0xFF6A5AE0, 0xFFEEEBFD, 0xFF9D91F2, 0xFF252242),
    Coral(0xFFE8534F, 0xFFFDE8E7, 0xFFF28782, 0xFF3A2224),
    Mint(0xFF2FA878, 0xFFE2F5EC, 0xFF5CCB9C, 0xFF173327),
    Amber(0xFFE89B1F, 0xFFFDF1DC, 0xFFF2B955, 0xFF3A2E17),
    Gray(0xFF6B7A8F, 0xFFEEF1F4, 0xFF97A5B5, 0xFF222C36);

    fun foreground(dark: Boolean) = Color(if (dark) darkFg else lightFg)
    fun background(dark: Boolean) = Color(if (dark) darkBg else lightBg)
}
