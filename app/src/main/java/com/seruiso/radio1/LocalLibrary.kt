package com.seruiso.radio1

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore

object LocalLibrary {
    fun list(context: Context): List<LocalTrack> {
        val out = mutableListOf<LocalTrack>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.RELATIVE_PATH,
            MediaStore.Audio.Media.BUCKET_DISPLAY_NAME,
        )
        val selection = MediaStore.Audio.Media.IS_MUSIC + "!=0"
        val sort = MediaStore.Audio.Media.TITLE + " COLLATE NOCASE ASC"
        context.contentResolver.query(collection, projection, selection, null, sort)?.use { c ->
            val idI = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleI = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistI = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumI = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdI = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val nameI = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val relI = c.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
            val bucketI = c.getColumnIndex(MediaStore.Audio.Media.BUCKET_DISPLAY_NAME)
            while (c.moveToNext()) {
                val id = c.getLong(idI)
                val uri = ContentUris.withAppendedId(collection, id).toString()
                var title = c.getString(titleI)
                if (title.isNullOrBlank()) title = c.getString(nameI)
                var artist = c.getString(artistI)
                if (artist.isNullOrBlank() || artist == "<unknown>") artist = "Unknown"
                out.add(
                    LocalTrack(
                        id = id.toString(),
                        uri = uri,
                        title = title ?: "Unknown",
                        artist = artist ?: "Unknown",
                        album = c.getString(albumI) ?: "",
                        albumId = c.getLong(albumIdI).toString(),
                        folder = audioFolder(
                            if (relI >= 0) c.getString(relI) else null,
                            if (bucketI >= 0) c.getString(bucketI) else null,
                        ),
                    )
                )
            }
        }
        return out
    }

    private fun audioFolder(relative: String?, bucket: String?): String {
        val rel = relative?.trim()?.trim('/') ?: ""
        if (rel.isNotEmpty()) return rel
        val name = bucket?.trim() ?: ""
        if (name.isEmpty() || name.equals("<unknown>", true)) return ""
        return name
    }
}
