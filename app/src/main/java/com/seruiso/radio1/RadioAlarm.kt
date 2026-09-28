package com.seruiso.radio1

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object RadioAlarm {
    const val KEY_ON = "alarmEnabled"
    const val KEY_HOUR = "alarmHour"
    const val KEY_MIN = "alarmMinute"
    const val KEY_DAILY = "alarmDaily"
    const val KEY_REPEAT = "alarmRepeat"
    const val KEY_YEAR = "alarmYear"
    const val KEY_MONTH = "alarmMonth"
    const val KEY_DAY = "alarmDay"
    const val KEY_URL = "alarmUrl"
    const val KEY_NAME = "alarmName"
    const val KEY_FAVICON = "alarmFavicon"
    const val KEY_GENRE = "alarmGenre"
    const val KEY_COUNTRY = "alarmCountry"
    private const val KEY_ITEMS = "alarmItemsJson"
    private const val KEY_LAST_ID = "alarmLastId"

    const val REPEAT_ONCE = "once"
    const val REPEAT_DAILY = "daily"
    const val REPEAT_WEEKDAYS = "weekdays"
    const val REPEAT_DATE = "date"
    const val EXTRA_ID = "alarmId"

    private const val REQ_BASE = 7200

    data class Item(
        val id: Long,
        val enabled: Boolean = true,
        val hour: Int = 7,
        val minute: Int = 0,
        val repeat: String = REPEAT_DAILY,
        val year: Int = 0,
        val month: Int = 0,
        val day: Int = 0,
        val url: String = "",
        val name: String = "",
        val favicon: String = "",
        val genre: String = "",
        val country: String = "",
    )

    /** Старий одиночний стан — для міграції та Java. */
    data class State(
        val enabled: Boolean = false,
        val hour: Int = 7,
        val minute: Int = 0,
        val daily: Boolean = true,
        val url: String = "",
        val name: String = "",
        val favicon: String = "",
        val genre: String = "",
        val country: String = "",
        val repeat: String = REPEAT_DAILY,
        val year: Int = 0,
        val month: Int = 0,
        val day: Int = 0,
    )

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, Context.MODE_PRIVATE)

    fun load(ctx: Context): State {
        migrateIfNeeded(ctx)
        val first = items(ctx).firstOrNull()
        return if (first == null) State()
        else State(
            enabled = first.enabled,
            hour = first.hour,
            minute = first.minute,
            daily = first.repeat == REPEAT_DAILY,
            url = first.url,
            name = first.name,
            favicon = first.favicon,
            genre = first.genre,
            country = first.country,
            repeat = first.repeat,
            year = first.year,
            month = first.month,
            day = first.day,
        )
    }

    fun save(ctx: Context, s: State) {
        migrateIfNeeded(ctx)
        val cur = items(ctx).toMutableList()
        if (cur.isEmpty()) {
            if (s.enabled || s.url.isNotBlank()) {
                cur.add(fromState(s, System.currentTimeMillis()))
            }
        } else {
            cur[0] = fromState(s, cur[0].id)
        }
        writeItems(ctx, cur)
        scheduleAll(ctx)
    }

    private fun fromState(s: State, id: Long) = Item(
        id = id,
        enabled = s.enabled,
        hour = s.hour,
        minute = s.minute,
        repeat = s.repeat.ifBlank { if (s.daily) REPEAT_DAILY else REPEAT_ONCE },
        year = s.year, month = s.month, day = s.day,
        url = s.url, name = s.name, favicon = s.favicon, genre = s.genre, country = s.country,
    )

    fun items(ctx: Context): List<Item> {
        migrateIfNeeded(ctx)
        val raw = prefs(ctx).getString(KEY_ITEMS, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i -> parseItem(arr.optJSONObject(i)) }
        } catch (_: Exception) { emptyList() }
    }

    private fun parseItem(o: JSONObject?): Item? {
        if (o == null) return null
        val id = o.optLong("id", 0L)
        if (id == 0L) return null
        return Item(
            id = id,
            enabled = o.optBoolean("on", true),
            hour = o.optInt("h", 7).coerceIn(0, 23),
            minute = o.optInt("m", 0).coerceIn(0, 59),
            repeat = o.optString("rep", REPEAT_DAILY).ifBlank { REPEAT_DAILY },
            year = o.optInt("y"), month = o.optInt("mo"), day = o.optInt("d"),
            url = o.optString("url"), name = o.optString("name"),
            favicon = o.optString("fav"), genre = o.optString("gen"), country = o.optString("cty"),
        )
    }

    private fun writeItems(ctx: Context, list: List<Item>) {
        val arr = JSONArray()
        list.forEach { it ->
            arr.put(
                JSONObject()
                    .put("id", it.id).put("on", it.enabled)
                    .put("h", it.hour).put("m", it.minute).put("rep", it.repeat)
                    .put("y", it.year).put("mo", it.month).put("d", it.day)
                    .put("url", it.url).put("name", it.name)
                    .put("fav", it.favicon).put("gen", it.genre).put("cty", it.country),
            )
        }
        prefs(ctx).edit().putString(KEY_ITEMS, arr.toString()).apply()
    }

    @Volatile private var migrated = false
    private fun migrateIfNeeded(ctx: Context) {
        if (migrated) return
        val p = prefs(ctx)
        val raw = p.getString(KEY_ITEMS, null)
        if (!raw.isNullOrBlank() && raw != "[]") {
            migrated = true
            return
        }
        if (p.contains(KEY_HOUR) || p.getBoolean(KEY_ON, false)) {
            val daily = p.getBoolean(KEY_DAILY, true)
            var repeat = p.getString(KEY_REPEAT, null)
            if (repeat.isNullOrBlank()) repeat = if (daily) REPEAT_DAILY else REPEAT_ONCE
            val one = Item(
                id = System.currentTimeMillis(),
                enabled = p.getBoolean(KEY_ON, false),
                hour = p.getInt(KEY_HOUR, 7),
                minute = p.getInt(KEY_MIN, 0),
                repeat = repeat ?: REPEAT_DAILY,
                year = p.getInt(KEY_YEAR, 0),
                month = p.getInt(KEY_MONTH, 0),
                day = p.getInt(KEY_DAY, 0),
                url = p.getString(KEY_URL, "") ?: "",
                name = p.getString(KEY_NAME, "") ?: "",
                favicon = p.getString(KEY_FAVICON, "") ?: "",
                genre = p.getString(KEY_GENRE, "") ?: "",
                country = p.getString(KEY_COUNTRY, "") ?: "",
            )
            writeItems(ctx, listOf(one))
        }
        migrated = true
    }

    fun add(ctx: Context, item: Item) {
        val cur = items(ctx).toMutableList()
        cur.add(item)
        writeItems(ctx, cur)
        scheduleAll(ctx)
    }

    fun remove(ctx: Context, id: Long) {
        writeItems(ctx, items(ctx).filter { it.id != id })
        cancelOne(ctx, id)
        scheduleAll(ctx)
    }

    fun setEnabled(ctx: Context, id: Long, on: Boolean) {
        writeItems(ctx, items(ctx).map { if (it.id == id) it.copy(enabled = on) else it })
        scheduleAll(ctx)
    }

    fun itemById(ctx: Context, id: Long): Item? = items(ctx).firstOrNull { it.id == id }

    fun canExact(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        return try {
            ctx.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true
        } catch (_: Exception) { true }
    }

    fun requestExact(ctx: Context) {
        if (Build.VERSION.SDK_INT < 31) return
        try {
            val i = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            i.data = Uri.parse("package:" + ctx.packageName)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
        } catch (_: Exception) {}
    }

    fun nextTriggerMs(s: Item, fromMs: Long = System.currentTimeMillis()): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = fromMs
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        c.set(Calendar.HOUR_OF_DAY, s.hour)
        c.set(Calendar.MINUTE, s.minute)
        when (s.repeat) {
            REPEAT_DATE -> {
                if (s.year < 2000 || s.day < 1) return 0L
                c.set(Calendar.YEAR, s.year)
                c.set(Calendar.MONTH, s.month.coerceIn(0, 11))
                val maxD = c.getActualMaximum(Calendar.DAY_OF_MONTH)
                c.set(Calendar.DAY_OF_MONTH, s.day.coerceIn(1, maxD))
                if (c.timeInMillis <= fromMs + 15_000L) return 0L
                return c.timeInMillis
            }
            REPEAT_WEEKDAYS -> {
                var guard = 0
                while (guard++ < 10) {
                    val dow = c.get(Calendar.DAY_OF_WEEK)
                    val ok = dow != Calendar.SATURDAY && dow != Calendar.SUNDAY
                    if (ok && c.timeInMillis > fromMs + 15_000L) return c.timeInMillis
                    c.add(Calendar.DAY_OF_YEAR, 1)
                    c.set(Calendar.HOUR_OF_DAY, s.hour)
                    c.set(Calendar.MINUTE, s.minute)
                }
                return c.timeInMillis
            }
            else -> {
                if (c.timeInMillis <= fromMs + 15_000L) c.add(Calendar.DAY_OF_YEAR, 1)
                return c.timeInMillis
            }
        }
    }

    fun nextLabel(ctx: Context, s: Item, fromMs: Long = System.currentTimeMillis()): String {
        val ms = nextTriggerMs(s, fromMs)
        if (ms <= 0L) return ctx.getString(R.string.alarm_date_past)
        val loc = try { ctx.resources.configuration.locales[0] } catch (_: Exception) { Locale.getDefault() }
        val fmt = SimpleDateFormat("EEE, d MMM · HH:mm", loc)
        return ctx.getString(R.string.alarm_next, modeLabel(ctx, s), fmt.format(Date(ms)))
    }

    fun modeLabel(ctx: Context, s: Item): String = when (s.repeat) {
        REPEAT_DAILY -> ctx.getString(R.string.alarm_daily)
        REPEAT_WEEKDAYS -> ctx.getString(R.string.alarm_weekdays)
        REPEAT_DATE -> {
            if (s.year >= 2000 && s.day > 0) {
                val c = Calendar.getInstance()
                c.set(s.year, s.month, s.day, s.hour, s.minute, 0)
                val loc = try { ctx.resources.configuration.locales[0] } catch (_: Exception) { Locale.getDefault() }
                SimpleDateFormat("d MMM", loc).format(c.time)
            } else ctx.getString(R.string.alarm_once_date)
        }
        else -> ctx.getString(R.string.alarm_once)
    }

    fun rowTitle(ctx: Context, s: Item): String {
        val t = "%02d:%02d".format(s.hour, s.minute)
        return ctx.getString(R.string.alarm_row, modeLabel(ctx, s), t)
    }

    private fun firePi(ctx: Context, id: Long): PendingIntent {
        val i = Intent(ctx, AlarmReceiver::class.java)
        i.action = AlarmReceiver.ACTION_FIRE
        i.putExtra(EXTRA_ID, id)
        val req = REQ_BASE + (id % 800).toInt()
        return PendingIntent.getBroadcast(
            ctx, req, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun showPi(ctx: Context, id: Long): PendingIntent {
        val i = Intent(ctx, AlarmActivity::class.java)
        i.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        i.putExtra(EXTRA_ID, id)
        return PendingIntent.getActivity(
            ctx, REQ_BASE + 900 + (id % 80).toInt(), i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun cancelOne(ctx: Context, id: Long) {
        try { ctx.getSystemService(AlarmManager::class.java)?.cancel(firePi(ctx, id)) } catch (_: Exception) {}
    }

    fun scheduleAll(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        items(ctx).forEach { it ->
            cancelOne(ctx, it.id)
            if (!it.enabled) return@forEach
            val whenMs = nextTriggerMs(it)
            if (whenMs <= 0L) return@forEach
            val fire = firePi(ctx, it.id)
            try {
                am.setAlarmClock(AlarmManager.AlarmClockInfo(whenMs, showPi(ctx, it.id)), fire)
            } catch (e: SecurityException) {
                try { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMs, fire) } catch (_: Exception) {}
            }
        }
    }

    fun schedule(ctx: Context) = scheduleAll(ctx)

    fun cancel(ctx: Context) {
        items(ctx).forEach { cancelOne(ctx, it.id) }
    }

    fun rescheduleIfEnabled(ctx: Context) = scheduleAll(ctx)

    fun onFired(ctx: Context) {
        val id = prefs(ctx).getLong(KEY_LAST_ID, 0L)
        val it = itemById(ctx, id) ?: items(ctx).firstOrNull { item -> item.enabled }
        if (it != null) onFiredItem(ctx, it)
        else scheduleAll(ctx)
    }

    fun onFiredItem(ctx: Context, s: Item) {
        prefs(ctx).edit().putLong(KEY_LAST_ID, s.id).apply()
        if (s.repeat == REPEAT_DAILY || s.repeat == REPEAT_WEEKDAYS) {
            scheduleAll(ctx)
        } else {
            writeItems(ctx, items(ctx).map { if (it.id == s.id) it.copy(enabled = false) else it })
            scheduleAll(ctx)
        }
    }

    fun postponeMinutes(ctx: Context, minutes: Int) {
        val id = prefs(ctx).getLong(KEY_LAST_ID, 0L)
        val s = itemById(ctx, id) ?: items(ctx).firstOrNull { it.enabled } ?: return
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val whenMs = System.currentTimeMillis() + minutes * 60_000L
        try {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(whenMs, showPi(ctx, s.id)), firePi(ctx, s.id))
        } catch (_: Exception) {
            try { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMs, firePi(ctx, s.id)) } catch (_: Exception) {}
        }
    }

    fun onReceiveFire(ctx: Context, intent: Intent?) {
        migrateIfNeeded(ctx)
        val id = intent?.getLongExtra(EXTRA_ID, 0L) ?: 0L
        val s = itemById(ctx, id) ?: items(ctx).firstOrNull { it.enabled }
        if (s == null || !s.enabled) {
            android.util.Log.i("AlarmReceiver", "skip")
            return
        }
        onFiredItem(ctx, s)
        val svc = Intent(ctx, RadioWatchService::class.java)
        svc.action = RadioWatchService.ACTION_ALARM
        svc.putExtra(RadioWatchService.EXTRA_URL, s.url)
        svc.putExtra(RadioWatchService.EXTRA_NAME, s.name)
        svc.putExtra("favicon", s.favicon)
        svc.putExtra("genre", s.genre)
        svc.putExtra("country", s.country)
        svc.setPackage(ctx.packageName)
        try {
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(svc) else ctx.startService(svc)
        } catch (e: Exception) {
            android.util.Log.w("AlarmReceiver", "FGS", e)
        }
        val ui = Intent(ctx, AlarmActivity::class.java)
        ui.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NO_USER_ACTION,
        )
        ui.putExtra(EXTRA_ID, s.id)
        ui.putExtra(RadioWatchService.EXTRA_NAME, s.name)
        try { ctx.startActivity(ui) } catch (e: Exception) {
            android.util.Log.w("AlarmReceiver", "activity", e)
        }
    }
}
