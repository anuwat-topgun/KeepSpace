package com.smartstorage.cleaner.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

@Composable
fun SmartStorageTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // Dynamic color is intentionally off: the brand palette is part of the product's calm, premium feel.
    val colors = if (darkTheme) DarkSmartColors else LightSmartColors
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = colors.accent,
            background = colors.background,
            surface = colors.background,
            surfaceContainer = colors.surface,
            secondaryContainer = colors.icyBlue,
            onBackground = colors.textPrimary,
            onSurface = colors.textPrimary,
            onSurfaceVariant = colors.textSecondary,
            outlineVariant = colors.separator,
        )
    } else {
        lightColorScheme(
            primary = colors.accent,
            background = colors.background,
            surface = colors.background,
            surfaceContainer = colors.surface,
            secondaryContainer = colors.icyBlue,
            onBackground = colors.textPrimary,
            onSurface = colors.textPrimary,
            onSurfaceVariant = colors.textSecondary,
            outlineVariant = colors.separator,
        )
    }

    CompositionLocalProvider(LocalSmartColors provides colors) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

object SmartTheme {
    val colors: SmartColors
        @Composable @ReadOnlyComposable
        get() = LocalSmartColors.current
}
