#!/bin/bash
set -e
cd /home/serui/AndroidStudioProjects/RadioSO

echo "=== 1. Font Space Grotesk ==="
mkdir -p app/src/main/res/font
if [ ! -f app/src/main/res/font/space_grotesk_bold.ttf ]; then
  curl -sL -o app/src/main/res/font/space_grotesk_bold.ttf \
    "https://github.com/googlefonts/space-grotesk/raw/main/fonts/ttf/SpaceGrotesk-Bold.ttf"
  echo "Font downloaded"
else
  echo "Font already exists"
fi

echo "=== 2. Shapes.kt ==="
cat > app/src/main/java/com/seruiso/radio1/Shapes.kt << 'EOF'
package com.seruiso.radio1

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

object AppShapes {
    val hero = RoundedCornerShape(26.dp)
    val card = RoundedCornerShape(16.dp)
    val chip = RoundedCornerShape(10.dp)
}
EOF
echo "Shapes.kt OK"

echo "=== 3. Patching MainActivity.kt (python) ==="
python3 - << 'ENDPY'
from pathlib import Path

def block_replace(path: Path, text: str, old: str, new: str, label: str) -> str:
    n = text.count(old)
    if n != 1:
        raise SystemExit(f"[{label}] expected 1 match, found {n}\n--- old ---\n{old[:300]}")
    return text.replace(old, new, 1)

path = Path("app/src/main/java/com/seruiso/radio1/MainActivity.kt")
s = path.read_text(encoding="utf-8")

# --- imports ---
s = block_replace(path, s, label="imports",
old="""import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring


/** Підписи вкладок (UA) — top-level, щоб StationScreen теж бачив */
""",
new="""import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight


/** Підписи вкладок (UA) — top-level, щоб StationScreen теж бачив */
""")

# --- logo font + dynamic color lift ---
s = block_replace(path, s, label="logo+dynamic",
old="""    val acc = Color(accent)
    val bg = Palette.bg
    val card = Palette.card
    val text = Palette.text
    val muted = Palette.muted

    @Composable
    fun PlayBtn(
""",
new="""    val acc = Color(accent)
    val bg = Palette.bg
    val card = Palette.card
    val text = Palette.text
    val muted = Palette.muted
    val logoFont = FontFamily(Font(R.font.space_grotesk_bold, FontWeight.Bold))

    val artCtx = LocalContext.current
    var dynamicArtColor by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Color?>(null) }
    LaunchedEffect(currentFavicon, currentUrl) {
        val art = artUrl(currentFavicon)
        if (art.startsWith("http") || art.startsWith("content:")) {
            try {
                val extracted = withContext(Dispatchers.IO) {
                    val req = ImageRequest.Builder(artCtx).data(art).allowHardware(false).size(120, 120).build()
                    val result = artCtx.imageLoader.execute(req)
                    val bmp = (result.drawable as? BitmapDrawable)?.bitmap
                    if (bmp != null) {
                        val sw = SwatchPalette.from(bmp).generate()
                        val c = sw.vibrantSwatch?.rgb ?: sw.dominantSwatch?.rgb ?: sw.mutedSwatch?.rgb
                        if (c != null) Color(c) else null
                    } else null
                }
                dynamicArtColor = extracted
            } catch (_: Exception) {
                dynamicArtColor = null
            }
        } else {
            dynamicArtColor = null
        }
    }
    val dynamicBg by animateColorAsState(
        targetValue = dynamicArtColor ?: acc,
        animationSpec = tween(650),
        label = "dynamicBg"
    )
    val haptic = LocalHapticFeedback.current

    @Composable
    fun PlayBtn(
""")

# --- PlayBtn shape + haptic ---
s = block_replace(path, s, label="playbtn",
old="""                .background(acc, RoundedCornerShape(16.dp))
                .clickable(
                    interactionSource = interaction,
                    indication = null
                ) { onClick() },
""",
new="""                .background(acc, AppShapes.hero)
                .clickable(
                    interactionSource = interaction,
                    indication = null
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                },
""")

# --- header left buttons ---
s = block_replace(path, s, label="header-left",
old="""                Box(
                    modifier = Modifier.size(40.dp).background(card, RoundedCornerShape(12.dp)).clickable { topThemeOpen = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Palette,
                        contentDescription = "Тема оформлення",
                        tint = text
                    )
                }
                Box(
                    modifier = Modifier.size(40.dp).background(card, RoundedCornerShape(12.dp)).clickable { openLeftSheet() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.LibraryMusic,
                        contentDescription = "Моя музика (локальні файли)",
                        tint = text
                    )
                }
""",
new="""                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(card, AppShapes.chip)
                        .springPress(0.90f) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            topThemeOpen = true
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Palette, contentDescription = "Тема оформлення", tint = text)
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(card, AppShapes.chip)
                        .springPress(0.90f) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            openLeftSheet()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.LibraryMusic, contentDescription = "Моя музика (локальні файли)", tint = text)
                }
""")

# --- logo ---
s = block_replace(path, s, label="logo",
old="""            Text("Radio S O", color = text, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.align(Alignment.Center))
""",
new="""            Row(modifier = Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                Text("Radio ", color = text, style = MaterialTheme.typography.headlineSmall.copy(fontFamily = logoFont, fontWeight = FontWeight.Bold))
                Text("S O", color = acc, style = MaterialTheme.typography.headlineSmall.copy(fontFamily = logoFont, fontWeight = FontWeight.Bold))
            }
""")

# --- header right buttons ---
s = block_replace(path, s, label="header-right",
old="""                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(card, RoundedCornerShape(12.dp))
                        .clickable { openRightSheet() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = "Пошук радіостанцій",
                        tint = text
                    )
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(card, RoundedCornerShape(12.dp))
                        .clickable { onMenu() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = "Ще налаштування",
                        tint = text
                    )
                }
""",
new="""                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(card, AppShapes.chip)
                        .springPress(0.90f) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            openRightSheet()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Search, contentDescription = "Пошук радіостанцій", tint = text)
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(card, AppShapes.chip)
                        .springPress(0.90f) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onMenu()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Ще налаштування", tint = text)
                }
""")

# --- mini-player art ---
s = block_replace(path, s, label="mini-art",
old="""            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(Palette.panel2, RoundedCornerShape(12.dp))
                    .clickable { onCloseMenu(); onNow() },
                contentAlignment = Alignment.Center
            ) {
                if (artUrl(favicon).startsWith("http") || artUrl(favicon).startsWith("content:")) {
                    AsyncImage(model = artUrl(favicon), contentDescription = null, modifier = Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                } else {
                    Icon(Icons.Filled.MusicNote, contentDescription = "Немає обкладинки", tint = muted)
                }
            }
""",
new="""            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(Palette.panel2, AppShapes.hero)
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onCloseMenu(); onNow()
                    },
                contentAlignment = Alignment.Center
            ) {
                if (artUrl(favicon).startsWith("http") || artUrl(favicon).startsWith("content:")) {
                    AsyncImage(model = artUrl(favicon), contentDescription = null, modifier = Modifier.size(72.dp).clip(AppShapes.hero), contentScale = ContentScale.Crop)
                } else {
                    Icon(Icons.Filled.MusicNote, contentDescription = "Немає обкладинки", tint = muted)
                }
            }
""")

# --- remove old dynamic block inside Now Playing ---
s = block_replace(path, s, label="remove-old-dynamic",
old="""            // Динамічний колір з поточної обкладинки (як у Spotify) — для розмитого фону картки Now Playing
            val artCtx = LocalContext.current
            val currentArt = arts.getOrNull(curI) ?: ""
            var dynamicArtColor by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Color?>(null) }
            LaunchedEffect(currentArt) {
                if (currentArt.startsWith("http") || currentArt.startsWith("content:")) {
                    try {
                        val extracted = withContext(Dispatchers.IO) {
                            val req = ImageRequest.Builder(artCtx).data(currentArt).allowHardware(false).size(120, 120).build()
                            val result = artCtx.imageLoader.execute(req)
                            val bmp = (result.drawable as? BitmapDrawable)?.bitmap
                            if (bmp != null) {
                                val sw = SwatchPalette.from(bmp).generate()
                                val c = sw.vibrantSwatch?.rgb ?: sw.dominantSwatch?.rgb ?: sw.mutedSwatch?.rgb
                                if (c != null) Color(c) else null
                            } else null
                        }
                        dynamicArtColor = extracted
                    } catch (e: Exception) {
                        dynamicArtColor = null
                    }
                } else dynamicArtColor = null
            }
            val dynamicBg by animateColorAsState(
                targetValue = dynamicArtColor ?: acc,
                animationSpec = tween(650),
                label = "dynamicBg"
            )
""",
new="""            // dynamicArtColor / dynamicBg підняті на рівень StationScreen
""")

path.write_text(s, encoding="utf-8")
print("MainActivity.kt — first part patched OK")
ENDPY

echo "=== 4. Build ==="
./gradlew assembleDebug --no-configuration-cache
