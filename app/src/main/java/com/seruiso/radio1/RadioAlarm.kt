package com.seruiso.radio1

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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

    const val REPEAT_ONCE = "once"
    const val REPEAT_DAILY = "daily"
    const val REPEAT_WEEKDAYS = "weekdays"
    const val REPEAT_DATE = "date"

    private const val REQ = 7101

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
        val p = prefs(ctx)
        val daily = p.getBoolean(KEY_DAILY, true)
        var repeat = p.getString(KEY_REPEAT, null)
        if (repeat.isNullOrBlank()) repeat = if (daily) REPEAT_DAILY else REPEAT_ONCE
        return State(
            enabled = p.getBoolean(KEY_ON, false),
            hour = p.getInt(KEY_HOUR, 7).coerceIn(0, 23),
            minute = p.getInt(KEY_MIN, 0).coerceIn(0, 59),
            daily = repeat == REPEAT_DAILY,
            url = p.getString(KEY_URL, "") ?: "",
            name = p.getString(KEY_NAME, "") ?: "",
            favicon = p.getString(KEY_FAVICON, "") ?: "",
            genre = p.getString(KEY_GENRE, "") ?: "",
            country = p.getString(KEY_COUNTRY, "") ?: "",
            repeat = repeat,
            year = p.getInt(KEY_YEAR, 0),
            month = p.getInt(KEY_MONTH, 0),
            day = p.getInt(KEY_DAY, 0),
        )
    }

    fun save(ctx: Context, s: State) {
        val rep = s.repeat
        prefs(ctx).edit()
            .putBoolean(KEY_ON, s.enabled)
            .putInt(KEY_HOUR, s.hour.coerceIn(0, 23))
            .putInt(KEY_MIN, s.minute.coerceIn(0, 59))
            .putBoolean(KEY_DAILY, rep == REPEAT_DAILY)
            .putString(KEY_REPEAT, rep)
            .putInt(KEY_YEAR, s.year)
            .putInt(KEY_MONTH, s.month)
            .putInt(KEY_DAY, s.day)
            .putString(KEY_URL, s.url)
            .putString(KEY_NAME, s.name)
            .putString(KEY_FAVICON, s.favicon)
            .putString(KEY_GENRE, s.genre)
            .putString(KEY_COUNTRY, s.country)
            .apply()
        if (s.enabled) schedule(ctx) else cancel(ctx)
    }

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

    fun nextTriggerMs(s: State, fromMs: Long = System.currentTimeMillis()): Long {
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
                    val okDay = dow != Calendar.SATURDAY && dow != Calendar.SUNDAY
                    if (okDay && c.timeInMillis > fromMs + 15_000L) return c.timeInMillis
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

    fun nextLabel(ctx: Context, s: State, fromMs: Long = System.currentTimeMillis()): String {
        val ms = nextTriggerMs(s, fromMs)
        if (ms <= 0L) return ctx.getString(R.string.alarm_date_past)
        val loc = try { ctx.resources.configuration.locales[0] } catch (_: Exception) { java.util.Locale.getDefault() }
        val fmt = SimpleDateFormat("EEE, d MMM · HH:mm", loc)
        val whenStr = fmt.format(Date(ms))
        val mode = when (s.repeat) {
            REPEAT_DAILY -> ctx.getString(R.string.alarm_daily)
            REPEAT_WEEKDAYS -> ctx.getString(R.string.alarm_weekdays)
            REPEAT_DATE -> ctx.getString(R.string.alarm_once_date)
            else -> ctx.getString(R.string.alarm_once)
        }
        return ctx.getString(R.string.alarm_next, mode, whenStr)
    }

    private fun firePi(ctx: Context): PendingIntent {
        val i = Intent(ctx, AlarmReceiver::class.java)
        i.action = AlarmReceiver.ACTION_FIRE
        return PendingIntent.getBroadcast(
            ctx, REQ, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun showPi(ctx: Context): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java)
        i.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        return PendingIntent.getActivity(
            ctx, REQ + 1, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun schedule(ctx: Context) {
        val s = load(ctx)
        if (!s.enabled) {
            cancel(ctx)
            return
        }
        val whenMs = nextTriggerMs(s)
        if (whenMs <= 0L) {
            cancel(ctx)
            return
        }
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val fire = firePi(ctx)
        try {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(whenMs, showPi(ctx)), fire)
            android.util.Log.i("RadioAlarm", "scheduled $whenMs")
        } catch (e: SecurityException) {
            android.util.Log.w("RadioAlarm", "exact denied, inexact", e)
            try {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMs, fire)
            } catch (e2: Exception) {
                android.util.Log.w("RadioAlarm", "schedule fail", e2)
            }
        }
    }

    fun cancel(ctx: Context) {
        try {
            ctx.getSystemService(AlarmManager::class.java)?.cancel(firePi(ctx))
        } catch (_: Exception) {}
    }

    fun rescheduleIfEnabled(ctx: Context) {
        if (load(ctx).enabled) schedule(ctx)
    }

    fun onFired(ctx: Context) {
        val s = load(ctx)
        if (s.repeat == REPEAT_DAILY || s.repeat == REPEAT_WEEKDAYS) schedule(ctx)
        else save(ctx, s.copy(enabled = false))
    }

    fun postponeMinutes(ctx: Context, minutes: Int) {
        val s = load(ctx)
        if (!s.enabled) return
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val whenMs = System.currentTimeMillis() + minutes * 60_000L
        try {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(whenMs, showPi(ctx)), firePi(ctx))
        } catch (_: Exception) {
            try { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMs, firePi(ctx)) } catch (_: Exception) {}
        }
    }
}
