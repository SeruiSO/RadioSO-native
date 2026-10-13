package com.seruiso.radio1

import android.content.Context
import org.json.JSONArray

object SearchHints {
    val countries = listOf(
        "Germany", "France", "United Kingdom", "Italy", "Spain", "Netherlands",
        "Switzerland", "Belgium", "Sweden", "Norway", "Denmark", "Austria",
        "Poland", "Ukraine", "Canada", "United States", "Australia", "Japan",
        "South Korea", "New Zealand"
    )
    /** Перші — чіпи на Дім. Решта — підказки в полі жанру пошуку. */
    val homeGenres = listOf(
        "Pop", "Dance", "Techno", "Trance", "House", "Rock", "Chill",
        "Lounge", "Ambient", "Jazz", "Hip-Hop", "Classical", "News",
    )
    val genres = homeGenres + listOf(
        "Electronic", "EDM", "Rap", "Country", "Reggae",
        "Blues", "Folk", "Metal", "R&B", "Soul", "Indie", "Synthwave",
    )
    val names = emptyList<String>()

    fun past(ctx: Context): List<String> {
        val raw = ctx.getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, Context.MODE_PRIVATE)
            .getString("pastSearches", "[]") ?: "[]"
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
    }

    fun savePast(ctx: Context, list: List<String>) {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        ctx.getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, Context.MODE_PRIVATE)
            .edit().putString("pastSearches", arr.toString()).apply()
    }

    /** Один рядок → назва / країна RadioBrowser / жанр-тег. */
    fun parse(raw: String): Triple<String, String, String> {
        var text = " " + raw.trim().lowercase().replace(Regex("\\s+"), " ") + " "
        var country = ""
        var genre = ""
        val countries = listOf(
            "united states" to "United States", "united kingdom" to "United Kingdom",
            "south korea" to "South Korea", "new zealand" to "New Zealand",
            "україна" to "Ukraine", "ukraine" to "Ukraine", "украина" to "Ukraine",
            "німеччина" to "Germany", "германия" to "Germany", "germany" to "Germany", "deutschland" to "Germany",
            "франція" to "France", "france" to "France",
            "італія" to "Italy", "italy" to "Italy",
            "іспанія" to "Spain", "spain" to "Spain",
            "польща" to "Poland", "poland" to "Poland",
            "британія" to "United Kingdom", "англія" to "United Kingdom", "england" to "United Kingdom", "britain" to "United Kingdom", "uk" to "United Kingdom",
            "сша" to "United States", "usa" to "United States", "america" to "United States",
            "канада" to "Canada", "canada" to "Canada",
            "японія" to "Japan", "japan" to "Japan",
            "австрія" to "Austria", "austria" to "Austria",
            "нідерланди" to "Netherlands", "holland" to "Netherlands", "netherlands" to "Netherlands",
            "швейцарія" to "Switzerland", "switzerland" to "Switzerland",
            "бельгія" to "Belgium", "belgium" to "Belgium",
            "швеція" to "Sweden", "sweden" to "Sweden",
            "норвегія" to "Norway", "norway" to "Norway",
            "данія" to "Denmark", "denmark" to "Denmark",
            "австралія" to "Australia", "australia" to "Australia",
        )
        for ((k, v) in countries.sortedByDescending { it.first.length }) {
            val n = " $k "
            if (n in text) {
                country = v
                text = text.replace(n, " ")
                break
            }
        }
        val genres = listOf(
            "hip-hop" to "Hip-Hop", "hip hop" to "Hip-Hop", "хіп-хоп" to "Hip-Hop", "хип-хоп" to "Hip-Hop",
            "synthwave" to "Synthwave", "сінтвейв" to "Synthwave",
            "classical" to "Classical", "класика" to "Classical", "классика" to "Classical",
            "electronic" to "Electronic", "електро" to "Electronic",
            "techno" to "Techno", "техно" to "Techno",
            "trance" to "Trance", "транс" to "Trance",
            "house" to "House", "хаус" to "House",
            "dance" to "Dance", "денс" to "Dance",
            "rock" to "Rock", "рок" to "Rock",
            "jazz" to "Jazz", "джаз" to "Jazz",
            "blues" to "Blues", "блюз" to "Blues",
            "metal" to "Metal", "метал" to "Metal",
            "indie" to "Indie", "інді" to "Indie",
            "reggae" to "Reggae", "реггі" to "Reggae",
            "ambient" to "Ambient", "ембієнт" to "Ambient",
            "lounge" to "Lounge", "лаунж" to "Lounge",
            "chill" to "Chill", "чил" to "Chill",
            "news" to "News", "новини" to "News",
            "pop" to "Pop", "поп" to "Pop",
            "rap" to "Rap", "реп" to "Rap",
            "soul" to "Soul", "соул" to "Soul",
            "folk" to "Folk", "фолк" to "Folk",
            "edm" to "EDM",
        )
        for ((k, v) in genres.sortedByDescending { it.first.length }) {
            val n = " $k "
            if (n in text) {
                genre = v
                text = text.replace(n, " ")
                break
            }
        }
        val name = text.trim().replace(Regex("\\s+"), " ")
        return Triple(name, country, genre)
    }

}
