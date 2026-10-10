package com.seruiso.radio1

import android.content.Context

object MusicTvFavStore {
    private const val PREF = "music_tv_favs"
    private const val KEY = "urls"

    fun load(ctx: Context): Set<String> {
        return try {
            ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .getStringSet(KEY, emptySet())
                ?.toSet()
                .orEmpty()
        } catch (_: Exception) {
            emptySet()
        }
    }

    fun toggle(ctx: Context, url: String): Set<String> {
        val cur = load(ctx).toMutableSet()
        if (!cur.add(url)) cur.remove(url)
        try {
            ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit()
                .putStringSet(KEY, HashSet(cur))
                .apply()
        } catch (_: Exception) {}
        return cur
    }

    fun isFav(ctx: Context, url: String): Boolean = url in load(ctx)
}
