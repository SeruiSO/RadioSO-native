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
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
    onPrivacy: () -> Unit = {},
    onExit: () -> Unit = {},
    onAlarm: () -> Unit = {},
    lang: String = "uk",
    onLang: () -> Unit = {},
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
                    selected = false,
                    acc = acc,
                    text = text,
                    onClick = { onSleepMenu() },
                )


                MenuRow(
                    icon = Icons.Filled.Language,
                    label = LocalContext.current.getString(
                        R.string.menu_language,
                        if (lang == "en") LocalContext.current.getString(R.string.lang_en_short)
                        else LocalContext.current.getString(R.string.lang_uk_short),
                    ),
                    selected = false,
                    acc = acc,
                    text = text,
                    onClick = { onLang(); onCloseMenu() },
                )
                MenuRow(
                    icon = Icons.Filled.Alarm,
                    label = LocalContext.current.getString(R.string.alarm_title),
                    selected = false,
                    acc = acc,
                    text = text,
                    onClick = { onAlarm(); onCloseMenu() },
                )

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
                MenuRow(
                    icon = Icons.Filled.Policy,
                    label = LocalContext.current.getString(R.string.privacy_policy),
                    selected = false,
                    acc = acc,
                    text = text,
                    onClick = { onPrivacy(); onCloseMenu() },
                )
                MenuRow(
                    icon = Icons.Filled.Logout,
                    label = LocalContext.current.getString(R.string.menu_exit),
                    selected = false,
                    acc = acc,
                    text = text,
                    onClick = { onExit() },
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
    val ctxs = LocalContext.current
    AlertDialog(
        containerColor = card,
        onDismissRequest = onDismiss,
        title = { Text(ctxs.getString(R.string.sleep_timer), color = text) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(ctxs.getString(R.string.sleep_pick), color = muted, style = MaterialTheme.typography.labelLarge)
                Text(sleepLabel, color = acc, style = MaterialTheme.typography.titleMedium)
                val mins = listOf(5, 10, 15, 20, 30, 45, 60, 90, 120, 0)
                mins.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { m ->
                            val lab = if (m == 0) ctxs.getString(R.string.disable)
                            else ctxs.getString(R.string.mins_short, m)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                                    .background(acc.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                                    .border(1.dp, acc.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                                    .clickable { onSleep(m); onDismiss() },
                                contentAlignment = Alignment.Center,
                            ) { Text(lab, color = text) }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(ctxs.getString(R.string.close), color = muted) }
        },
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
                                    .clickable { onPickTheme(th.id) }
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

@Composable
fun AppAlarmDialog(
    open: Boolean,
    onDismiss: () -> Unit,
    nowUrl: String,
    nowName: String,
    nowFavicon: String,
    nowGenre: String,
    nowCountry: String,
    acc: Color,
    muted: Color,
    text: Color,
    card: Color,
) {
    if (!open) return
    val ctx = LocalContext.current
    var tick by remember(open) { mutableIntStateOf(0) }
    val list = remember(open, tick) { RadioAlarm.items(ctx) }
    val now = java.util.Calendar.getInstance()
    var hour by remember(open) { mutableIntStateOf(7) }
    var minute by remember(open) { mutableIntStateOf(0) }
    var year by remember(open) { mutableIntStateOf(now.get(java.util.Calendar.YEAR)) }
    var month by remember(open) { mutableIntStateOf(now.get(java.util.Calendar.MONTH)) }
    var day by remember(open) { mutableIntStateOf(now.get(java.util.Calendar.DAY_OF_MONTH)) }
    var showCal by remember { mutableStateOf(false) }
    var stOpen by remember { mutableStateOf(false) }
    var url by remember(open) { mutableStateOf(nowUrl) }
    var stName by remember(open) { mutableStateOf(nowName) }
    var stFav by remember(open) { mutableStateOf(nowFavicon) }
    var stGen by remember(open) { mutableStateOf(nowGenre) }
    var stCtry by remember(open) { mutableStateOf(nowCountry) }
    val favs = remember(open) { FavStore.stations(ctx) }
    val extra = if (nowUrl.isNotBlank() && favs.none { it.url == nowUrl }) {
        listOf(Station(nowUrl, nowName.ifBlank { ctx.getString(R.string.alarm_use_current) }, nowGenre, nowCountry, nowFavicon, "now"))
    } else emptyList()
    val choices = (extra + favs).distinctBy { it.url }
    fun addMode(rep: String) {
        val u = url.ifBlank { nowUrl }
        val nm = stName.ifBlank { nowName }
        if (u.isBlank()) return
        if (!RadioAlarm.canExact(ctx)) RadioAlarm.requestExact(ctx)
        RadioAlarm.add(
            ctx,
            RadioAlarm.Item(
                id = System.currentTimeMillis(),
                enabled = true,
                hour = hour, minute = minute, repeat = rep,
                year = year, month = month, day = day,
                url = u, name = nm, favicon = stFav.ifBlank { nowFavicon },
                genre = stGen.ifBlank { nowGenre }, country = stCtry.ifBlank { nowCountry },
            ),
        )
        tick++
    }
    if (showCal) {
        DisposableEffect(showCal) {
            val dlg = android.app.DatePickerDialog(
                ctx,
                { _, y, m, d ->
                    year = y; month = m; day = d
                    showCal = false
                    addMode(RadioAlarm.REPEAT_DATE)
                },
                year, month, day,
            )
            dlg.datePicker.minDate = System.currentTimeMillis() - 3_600_000L
            dlg.setOnCancelListener { showCal = false }
            dlg.show()
            onDispose { try { dlg.dismiss() } catch (_: Exception) {} }
        }
    }
    @Composable
    fun ItemCard(item: RadioAlarm.Item) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .background(acc.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    RadioAlarm.rowTitle(ctx, item),
                    color = text, modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall, maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Switch(checked = item.enabled, onCheckedChange = {
                    RadioAlarm.setEnabled(ctx, item.id, it); tick++
                })
                Text("✕", color = muted, modifier = Modifier.padding(start = 6.dp).clickable {
                    RadioAlarm.remove(ctx, item.id); tick++
                })
            }
            Text(RadioAlarm.nextLabel(ctx, item), color = acc, style = MaterialTheme.typography.bodySmall)
            if (item.name.isNotBlank()) {
                Text(item.name, color = muted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
    }
    AlertDialog(
        containerColor = card,
        onDismissRequest = onDismiss,
        title = { Text(ctx.getString(R.string.alarm_title), color = text) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Add, null, tint = acc, modifier = Modifier.size(26.dp).clickable { hour = (hour + 1) % 24 })
                        Text("%02d".format(hour), color = text, style = MaterialTheme.typography.displaySmall)
                        Icon(Icons.Filled.Remove, null, tint = acc, modifier = Modifier.size(26.dp).clickable { hour = (hour + 23) % 24 })
                    }
                    Text(" : ", color = text, style = MaterialTheme.typography.displaySmall)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Add, null, tint = acc, modifier = Modifier.size(26.dp).clickable { minute = (minute + 5) % 60 })
                        Text("%02d".format(minute), color = text, style = MaterialTheme.typography.displaySmall)
                        Icon(Icons.Filled.Remove, null, tint = acc, modifier = Modifier.size(26.dp).clickable { minute = (minute + 55) % 60 })
                    }
                }
                Text(ctx.getString(R.string.alarm_pick_station), color = muted, style = MaterialTheme.typography.labelLarge)
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(acc.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
                            .clickable { stOpen = !stOpen }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stName.ifBlank { ctx.getString(R.string.alarm_pick_station) },
                            color = text, modifier = Modifier.weight(1f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(if (stOpen) "▲" else "▼", color = acc)
                    }
                    if (stOpen) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Palette.panel, RoundedCornerShape(12.dp))
                                .padding(6.dp),
                        ) {
                            choices.take(24).forEach { s ->
                                val sel = s.url == url
                                Text(
                                    s.name,
                                    color = if (sel) acc else text,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(if (sel) acc.copy(alpha = 0.18f) else Color.Transparent, RoundedCornerShape(8.dp))
                                        .clickable {
                                            url = s.url; stName = s.name; stFav = s.favicon
                                            stGen = s.genre; stCtry = s.country
                                            stOpen = false
                                        }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                )
                            }
                        }
                    }
                }
                AlarmAddLine(ctx.getString(R.string.alarm_daily), acc, text) { addMode(RadioAlarm.REPEAT_DAILY) }
                list.filter { it.repeat == RadioAlarm.REPEAT_DAILY }.forEach { ItemCard(it) }
                AlarmAddLine(ctx.getString(R.string.alarm_weekdays), acc, text) { addMode(RadioAlarm.REPEAT_WEEKDAYS) }
                list.filter { it.repeat == RadioAlarm.REPEAT_WEEKDAYS }.forEach { ItemCard(it) }
                AlarmAddLine(ctx.getString(R.string.alarm_once), acc, text) { addMode(RadioAlarm.REPEAT_ONCE) }
                list.filter { it.repeat == RadioAlarm.REPEAT_ONCE }.forEach { ItemCard(it) }
                AlarmAddLine(ctx.getString(R.string.alarm_pick_date), acc, text) { showCal = true }
                list.filter { it.repeat == RadioAlarm.REPEAT_DATE }.forEach { ItemCard(it) }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(ctx.getString(R.string.close), color = acc) }
        },
        dismissButton = {},
    )
}

@Composable
private fun AlarmAddLine(label: String, acc: Color, text: Color, onAdd: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(acc.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .clickable { onAdd() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text("+", color = acc, style = MaterialTheme.typography.headlineSmall)
    }
}
