package com.seruiso.radio1

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object BackupStore {
    fun exportJson(ctx: Context): String {
        val p = ctx.getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, Context.MODE_PRIVATE)
        return JSONObject()
            .put("customTabs", JSONArray(p.getString("customTabs", "[]")))
            .put("userAddedStations", JSONObject(p.getString("userAddedStations", "{}")))
            .put("favoriteUrls", JSONArray(p.getString(BluetoothAutoPlayPlugin.KEY_FAVORITES, "[]")))
            .put("localBestUrls", JSONArray(p.getString(BluetoothAutoPlayPlugin.KEY_LOCAL_BEST, "[]")))
            .put("btWatchEnabled", p.getBoolean(BluetoothAutoPlayPlugin.KEY_BT_WATCH, true))
            .toString(2)
    }

    fun importJson(ctx: Context, raw: String): String {
        val o = JSONObject(raw)
        val e = ctx.getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, Context.MODE_PRIVATE).edit()
        if (o.has("customTabs")) e.putString("customTabs", o.getJSONArray("customTabs").toString())
        if (o.has("userAddedStations")) e.putString("userAddedStations", o.getJSONObject("userAddedStations").toString())
        if (o.has("favoriteUrls")) e.putString(BluetoothAutoPlayPlugin.KEY_FAVORITES, o.getJSONArray("favoriteUrls").toString())
        if (o.has("localBestUrls")) e.putString(BluetoothAutoPlayPlugin.KEY_LOCAL_BEST, o.getJSONArray("localBestUrls").toString())
        if (o.has("btWatchEnabled")) e.putBoolean(BluetoothAutoPlayPlugin.KEY_BT_WATCH, o.getBoolean("btWatchEnabled"))
        e.commit()
        return "імпорт ок"
    }
}
