package com.seruiso.radio1

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Overflow ⋯ меню + діалоги сну/теми з хедера.
 */
@Composable
fun AppOverflowMenu(
    menuOpen: Boolean,
    onCloseMenu: () -> Unit,
    btWatch: Boolean,
    onBt: () -> Unit,
    sleepLabel: String,
    sleepMenu: Boolean,
    onSleepMenu: () -> Unit,
    onSleep: (Int) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    acc: Color,
    text: Color,
) {
    AnimatedVisibility(
        visible = menuOpen,
        modifier = Modifier.fillMaxSize(),
        enter = fadeIn() + scaleIn(initialScale = 0.92f),
        exit = fadeOut()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize().clickable { onCloseMenu() })
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 76.dp, end = 12.dp)
                    .width(220.dp)
                    .background(Palette.panel2, RoundedCornerShape(12.dp))
                    .padding(8.dp)
            ) {
                val ctxForTheme = LocalContext.current
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { Palette.toggle(ctxForTheme) }
                        .padding(8.dp)
                ) {
                    Icon(
                        if (Palette.isLight) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                        contentDescription = if (Palette.isLight) LocalContext.current.getString(R.string.theme_light)
                        else LocalContext.current.getString(R.string.theme_dark),
                        tint = text,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        if (Palette.isLight) LocalContext.current.getString(R.string.theme_light)
                        else LocalContext.current.getString(R.string.theme_dark),
                        color = text
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onBt(); onCloseMenu() }
                        .padding(8.dp)
                ) {
                    Icon(
                        if (btWatch) Icons.Filled.Bluetooth else Icons.Filled.BluetoothDisabled,
                        contentDescription = if (btWatch) LocalContext.current.getString(R.string.bt_watch_on_long)
                        else LocalContext.current.getString(R.string.bt_watch_off_long),
                        tint = text,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        if (btWatch) LocalContext.current.getString(R.string.bt_on)
                        else LocalContext.current.getString(R.string.bt_off),
                        color = text
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSleepMenu() }
                        .padding(8.dp)
                ) {
                    Icon(
                        Icons.Filled.Timer,
                        contentDescription = LocalContext.current.getString(R.string.sleep_timer),
                        tint = text,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(sleepLabel, color = text)
                }
                if (sleepMenu) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(15 to 30, 60 to 0).forEach { (a, b) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(a, b).forEach { m ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(36.dp)
                                            .background(Palette.panel, RoundedCornerShape(10.dp))
                                            .clickable { onSleep(m); onCloseMenu() },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            if (m == 0) LocalContext.current.getString(R.string.off)
                                            else LocalContext.current.getString(R.string.mins_short, m),
                                            color = acc,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onExport() }
                        .padding(8.dp)
                ) {
                    Icon(
                        Icons.Filled.FileUpload,
                        contentDescription = LocalContext.current.getString(R.string.export_settings),
                        tint = text,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(LocalContext.current.getString(R.string.export), color = text)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onImport() }
                        .padding(8.dp)
                ) {
                    Icon(
                        Icons.Filled.FileDownload,
                        contentDescription = LocalContext.current.getString(R.string.import_settings),
                        tint = text,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(LocalContext.current.getString(R.string.import_label), color = text)
                }
            }
        }
    }
}

@Composable
fun AppSleepDialog(
    open: Boolean,
    onDismiss: () -> Unit,
    sleepLabel: String,
    onSleep: (Int) -> Unit,
    acc: Color,
    muted: Color,
    text: Color,
    card: Color,
) {
    if (!open) return
    AlertDialog(
        containerColor = card,
        onDismissRequest = onDismiss,
        title = { Text(LocalContext.current.getString(R.string.sleep_timer), color = text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15 to 30, 60 to 0).forEach { (a, b) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(a, b).forEach { m ->
                            val lab = if (m == 0) LocalContext.current.getString(R.string.disable)
                            else LocalContext.current.getString(R.string.mins_short, m)
                            val selected = if (m == 0) sleepLabel == LocalContext.current.getString(R.string.sleep_timer)
                            else sleepLabel.contains(LocalContext.current.getString(R.string.mins_short, m))
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp)
                                    .background(
                                        if (selected) acc.copy(alpha = 0.28f) else Palette.panel,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (selected) acc else muted.copy(alpha = 0.25f),
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable {
                                        onSleep(m)
                                        onDismiss()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(lab, color = if (selected) acc else text)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(LocalContext.current.getString(R.string.close), color = muted)
            }
        }
    )
}

@Composable
fun AppThemeDialog(
    open: Boolean,
    onDismiss: () -> Unit,
    themeName: String,
    onPickTheme: (String) -> Unit,
    muted: Color,
    text: Color,
    card: Color,
) {
    if (!open) return
    AlertDialog(
        containerColor = card,
        onDismissRequest = onDismiss,
        title = { Text(LocalContext.current.getString(R.string.theme), color = text) },
        text = {
            Column {
                // 4 в ряд
                ThemeStore.all.chunked(4).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        row.forEach { th ->
                            val selected = th.id == themeName
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(Color(th.accent), RoundedCornerShape(12.dp))
                                    .then(
                                        if (selected) Modifier.border(2.dp, text, RoundedCornerShape(12.dp))
                                        else Modifier
                                    )
                                    .clickable {
                                        onPickTheme(th.id)
                                        onDismiss()
                                    }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(LocalContext.current.getString(R.string.close), color = muted)
            }
        }
    )
}
