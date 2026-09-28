package com.seruiso.radio1

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import java.util.Calendar

object RadioAlarm {
    const val KEY_ON = "alarmEnabled"
    const val KEY_HOUR = "alarmHour"
    const val KEY_MIN = "alarmMinute"
    const val KEY_DAILY = "alarmDaily"
    const val KEY_URL = "alarmUrl"
    const val KEY_NAME = "alarmName"
    const val KEY_FAVICON = "alarmFavicon"
    const val KEY_GENRE = "alarmGenre"
    const val KEY_COUNTRY = "alarmCountry"

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
    )

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, Context.MODE_PRIVATE)

    fun load(ctx: Context): State {
        val p = prefs(ctx)
        return State(
            enabled = p.getBoolean(KEY_ON, false),
            hour = p.getInt(KEY_HOUR, 7).coerceIn(0, 23),
            minute = p.getInt(KEY_MIN, 0).coerceIn(0, 59),
            daily = p.getBoolean(KEY_DAILY, true),
            url = p.getString(KEY_URL, "") ?: "",
            name = p.getString(KEY_NAME, "") ?: "",
            favicon = p.getString(KEY_FAVICON, "") ?: "",
            genre = p.getString(KEY_GENRE, "") ?: "",
            country = p.getString(KEY_COUNTRY, "") ?: "",
        )
    }

    fun save(ctx: Context, s: State) {
        prefs(ctx).edit()
            .putBoolean(KEY_ON, s.enabled)
            .putInt(KEY_HOUR, s.hour.coerceIn(0, 23))
            .putInt(KEY_MIN, s.minute.coerceIn(0, 59))
            .putBoolean(KEY_DAILY, s.daily)
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

    fun nextTriggerMs(hour: Int, minute: Int, fromMs: Long = System.currentTimeMillis()): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = fromMs
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        c.set(Calendar.HOUR_OF_DAY, hour)
        c.set(Calendar.MINUTE, minute)
        if (c.timeInMillis <= fromMs + 15_000L) c.add(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
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
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val whenMs = nextTriggerMs(s.hour, s.minute)
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

    /** After fire: next day or off. */
    fun onFired(ctx: Context) {
        val s = load(ctx)
        if (s.daily) schedule(ctx)
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
