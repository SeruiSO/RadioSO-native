package com.seruiso.radio1.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Застосунок завжди темний за дизайном (чорний фон, білий текст) —
// тож використовуємо ОДНУ узгоджену кольорову схему без прив'язки
// до системної теми чи Material You (dynamic color). Раніше стандартні
// AlertDialog/Button/OutlinedTextField/Slider підхоплювали дефолтну
// фіолетову (або системну динамічну) схему, що виглядало як стороннє
// UI, а на світлій системній темі текст у діалогах міг бути майже
// невидимим (темний текст на темному фоні діалогу).
//
// primary/secondary/tertiary тепер приймаються параметром (accent) —
// саме той колір, який користувач обрав у ThemeStore. Це гарантує, що
// й стандартні Material3-компоненти без явно заданого кольору (Slider,
// фокус OutlinedTextField, тощо) підхоплюють обрану тему, а не завжди
// лишаються зеленими.
@Composable
fun RadioSOTheme(
    accent: Color = AppAccent,
    content: @Composable () -> Unit
) {
    val colorScheme = darkColorScheme(
        primary = accent,
        onPrimary = AppOnAccent,
        secondary = accent,
        onSecondary = AppOnAccent,
        tertiary = accent,
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
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

