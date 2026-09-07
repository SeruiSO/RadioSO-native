package com.seruiso.radio1.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.seruiso.radio1.Palette

@Composable
fun RadioSOTheme(
    accent: Color = AppAccent,
    content: @Composable () -> Unit
) {
    val colorScheme = if (Palette.isLight) {
        lightColorScheme(
            primary = accent,
            onPrimary = AppOnAccent,
            secondary = accent,
            onSecondary = AppOnAccent,
            tertiary = accent,
            onTertiary = AppOnAccent,
            background = Palette.bg,
            onBackground = Palette.text,
            surface = Palette.card,
            onSurface = Palette.text,
            surfaceVariant = Palette.panel,
            onSurfaceVariant = Palette.muted,
            error = AppError,
            onError = AppOnAccent
        )
    } else {
        darkColorScheme(
            primary = accent,
            onPrimary = AppOnAccent,
            secondary = accent,
            onSecondary = AppOnAccent,
            tertiary = accent,
            onTertiary = AppOnAccent,
            background = Palette.bg,
            onBackground = Palette.text,
            surface = Palette.card,
            onSurface = Palette.text,
            surfaceVariant = Palette.panel,
            onSurfaceVariant = Palette.muted,
            error = AppError,
            onError = AppOnAccent
        )
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
