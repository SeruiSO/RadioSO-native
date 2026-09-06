package com.seruiso.radio1

import android.content.Context
import org.json.JSONObject

/**
 * Пам'ять гучності STREAM_MUSIC по конкретному BT-пристрою (за MAC-адресою).
 * Найпростіший варіант: зберігаємо на штатному дисконекті, застосовуємо на конекті.
 * Якщо для адреси нічого не збережено — нічого не міняємо.
 */
object BtVolumeStore {
    private const val KEY = "bt_volume_by_device"

    private fun raw(context: Context): JSONObject {
        val s = context.getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "{}") ?: "{}"
        return try { JSONObject(s) } catch (e: Exception) { JSONObject() }
    }

    @JvmStatic
    fun get(context: Context, address: String?): Int? {
        if (address.isNullOrBlank()) return null
        val o = raw(context)
        if (!o.has(address)) return null
        return o.optInt(address, -1).takeIf { it >= 0 }
    }

    @JvmStatic
    fun save(context: Context, address: String?, level: Int) {
        if (address.isNullOrBlank() || level < 0) return
        val o = raw(context)
        o.put(address, level)
        context.getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, o.toString()).commit()
    }
}
