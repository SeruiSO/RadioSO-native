package com.seruiso.radio1.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// Застосунок завжди темний за дизайном (чорний фон, білий текст) —
// тож використовуємо ОДНУ узгоджену кольорову схему без прив'язки
// до системної теми чи Material You (dynamic color). Раніше стандартні
// AlertDialog/Button/OutlinedTextField/Slider підхоплювали дефолтну
// фіолетову (або системну динамічну) схему, що виглядало як стороннє
// UI, а на світлій системній темі текст у діалогах міг бути майже
// невидимим (темний текст на темному фоні діалогу).
private val AppColorScheme = darkColorScheme(
    primary = AppAccent,
    onPrimary = AppOnAccent,
    secondary = AppAccent,
    onSecondary = AppOnAccent,
    tertiary = AppAccent,
    onTertiary = AppOnAccent,
    background = AppBackground,
    onBackground = AppText,
    surface = AppSurface,
    onSurface = AppText,
    surfaceVariant = AppSurfaceVariant,
    onSurfaceVariant = AppTextMuted,
    error = AppError,
    onError = AppOnAccent
)

@Composable
fun RadioSOTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        typography = Typography,
        content = content
    )
}
