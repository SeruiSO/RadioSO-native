package com.seruiso.radio1

import android.graphics.Bitmap
import android.graphics.BitmapFactory

/** ICO — контейнер. Android малює PNG всередині, не весь файл. */
object IcoBitmap {
    @JvmStatic
    fun decode(data: ByteArray): Bitmap? {
        val png = extractPng(data)
        if (png != null) return BitmapFactory.decodeByteArray(png, 0, png.size)
        val bmp = extractBmp(data)
        if (bmp != null) {
            val b = BitmapFactory.decodeByteArray(bmp, 0, bmp.size)
            if (b != null) return b
        }
        if (isIco(data)) return null
        return BitmapFactory.decodeByteArray(data, 0, data.size)
    }

    @JvmStatic
    fun isIco(data: ByteArray): Boolean {
        if (data.size < 6) return false
        return u16(data, 0) == 0 && u16(data, 2) == 1
    }

    private fun extractPng(data: ByteArray): ByteArray? {
        val entries = entries(data) ?: return null
        var best: ByteArray? = null
        var area = -1
        for ((w, h, chunk) in entries) {
            if (chunk.size < 8) continue
            val png = chunk[0] == 0x89.toByte() && chunk[1] == 0x50.toByte() &&
                chunk[2] == 0x4E.toByte() && chunk[3] == 0x47.toByte()
            if (!png) continue
            val a = w * h
            if (a > area) { area = a; best = chunk }
        }
        return best
    }

    private fun extractBmp(data: ByteArray): ByteArray? {
        val entries = entries(data) ?: return null
        var best: ByteArray? = null
        var area = -1
        for ((w, h, chunk) in entries) {
            if (chunk.size < 40 || u32(chunk, 0) != 40) continue
            val a = w * h
            if (a <= area) continue
            val copy = chunk.copyOf()
            val storedH = u32(copy, 8)
            if (h > 0 && storedH == h * 2) writeU32(copy, 8, h)
            val bitCount = u16(copy, 14)
            val colors = u32(copy, 32)
            val palette = if (bitCount in 1..8) (if (colors == 0) 1 shl bitCount else colors) * 4 else 0
            val offBits = 14 + 40 + palette
            val file = ByteArray(14 + copy.size)
            file[0] = 'B'.code.toByte(); file[1] = 'M'.code.toByte()
            writeU32(file, 2, file.size)
            writeU32(file, 10, offBits)
            copy.copyInto(file, 14)
            area = a
            best = file
        }
        return best
    }

    private fun entries(data: ByteArray): List<Triple<Int, Int, ByteArray>>? {
        if (!isIco(data)) return null
        val count = u16(data, 4)
        if (count <= 0 || count > 64) return null
        if (6 + count * 16 > data.size) return null
        val out = ArrayList<Triple<Int, Int, ByteArray>>(count)
        for (i in 0 until count) {
            val e = 6 + i * 16
            val w = if (data[e].toInt() and 0xff == 0) 256 else data[e].toInt() and 0xff
            val h = if (data[e + 1].toInt() and 0xff == 0) 256 else data[e + 1].toInt() and 0xff
            val size = u32(data, e + 8)
            val ofs = u32(data, e + 12)
            if (size <= 8 || ofs < 0 || ofs + size > data.size) continue
            out.add(Triple(w, h, data.copyOfRange(ofs, ofs + size)))
        }
        return out
    }

    private fun u16(d: ByteArray, o: Int) =
        (d[o].toInt() and 0xff) or ((d[o + 1].toInt() and 0xff) shl 8)

    private fun u32(d: ByteArray, o: Int) =
        (d[o].toInt() and 0xff) or ((d[o + 1].toInt() and 0xff) shl 8) or
            ((d[o + 2].toInt() and 0xff) shl 16) or ((d[o + 3].toInt() and 0xff) shl 24)

    private fun writeU32(d: ByteArray, o: Int, v: Int) {
        d[o] = (v and 0xff).toByte()
        d[o + 1] = ((v shr 8) and 0xff).toByte()
        d[o + 2] = ((v shr 16) and 0xff).toByte()
        d[o + 3] = ((v shr 24) and 0xff).toByte()
    }
}
