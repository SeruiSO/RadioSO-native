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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Overflow меню (гамбургер) + діалоги сну/теми з хедера.
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
                    .padding(top = 56.dp, end = 8.dp)
                    .width(220.dp)
                    .background(Palette.panel2, RoundedCornerShape(16.dp))
                    .border(1.dp, text.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                MenuRow(
                    icon = if (btWatch) Icons.Filled.Bluetooth else Icons.Filled.BluetoothDisabled,
                    label = if (btWatch) LocalContext.current.getString(R.string.bt_on)
                    else LocalContext.current.getString(R.string.bt_off),
                    selected = btWatch,
                    acc = acc,
                    text = text,
                    onClick = { onBt(); onCloseMenu() },
                )
                MenuRow(
                    icon = Icons.Filled.Timer,
                    label = sleepLabel,
                    selected = sleepMenu,
                    acc = acc,
                    text = text,
                    onClick = { onSleepMenu() },
                )
                if (sleepMenu) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(15 to 30, 60 to 0).forEach { (a, b) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(a, b).forEach { m ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(44.dp)
                                            .background(Palette.panel, RoundedCornerShape(12.dp))
                                            .clickable { onSleep(m); onCloseMenu() },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            if (m == 0) LocalContext.current.getString(R.string.off)
                                            else LocalContext.current.getString(R.string.mins_short, m),
                                            color = acc,
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                MenuRow(
                    icon = Icons.Filled.FileUpload,
                    label = LocalContext.current.getString(R.string.export),
                    selected = false,
                    acc = acc,
                    text = text,
                    onClick = { onExport() },
                )
                MenuRow(
                    icon = Icons.Filled.FileDownload,
                    label = LocalContext.current.getString(R.string.import_label),
                    selected = false,
                    acc = acc,
                    text = text,
                    onClick = { onImport() },
                )
            }
        }
    }
}

@Composable
private fun MenuRow(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    acc: Color,
    text: Color,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(
                if (selected) acc.copy(alpha = 0.18f) else Color.Transparent,
                shape
            )
            .then(
                if (selected) Modifier.border(1.dp, acc.copy(alpha = 0.45f), shape)
                else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp)
    ) {
        Icon(icon, contentDescription = label, tint = if (selected) acc else text, modifier = Modifier.size(26.dp))
        Text(
            label,
            color = if (selected) acc else text,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp)
        )
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
    acc: Color,
    muted: Color,
    text: Color,
    card: Color,
) {
    if (!open) return
    val ctx = LocalContext.current
    AlertDialog(
        containerColor = card,
        onDismissRequest = onDismiss,
        title = { Text(LocalContext.current.getString(R.string.theme), color = text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    LocalContext.current.getString(R.string.theme_title),
                    color = muted,
                    style = MaterialTheme.typography.labelLarge
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    listOf(
                        false to (Icons.Filled.DarkMode to R.string.theme_dark),
                        true to (Icons.Filled.LightMode to R.string.theme_light),
                    ).forEach { (light, pair) ->
                        val (ico, strRes) = pair
                        val selected = Palette.isLight == light
                        val shape = RoundedCornerShape(14.dp)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .background(
                                    if (selected) acc.copy(alpha = 0.22f) else Palette.panel,
                                    shape
                                )
                                .border(
                                    1.5.dp,
                                    if (selected) acc else muted.copy(alpha = 0.2f),
                                    shape
                                )
                                .clickable { Palette.setLight(ctx, light) }
                                .padding(horizontal = 12.dp)
                        ) {
                            Icon(ico, contentDescription = null, tint = if (selected) acc else text, modifier = Modifier.size(24.dp))
                            Text(
                                LocalContext.current.getString(strRes),
                                color = if (selected) acc else text,
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                    }
                }
                Text(
                    LocalContext.current.getString(R.string.theme),
                    color = muted,
                    style = MaterialTheme.typography.labelLarge
                )
                ThemeStore.all.chunked(4).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        row.forEach { th ->
                            val selected = th.id == themeName
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(Color(th.accent), RoundedCornerShape(14.dp))
                                    .then(
                                        if (selected) Modifier.border(3.dp, text, RoundedCornerShape(14.dp))
                                        else Modifier.border(1.dp, muted.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
                                    )
                                    .clickable {
                                        onPickTheme(th.id)
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
