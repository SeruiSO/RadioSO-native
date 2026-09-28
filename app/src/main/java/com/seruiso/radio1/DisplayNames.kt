package com.seruiso.radio1

import android.content.Context

/**
 * Country/genre labels for UI. Storage stays as-is (EN or legacy UA).
 * Canonical key is English; UI language picks UA or EN label.
 */
object DisplayNames {

    /** map: lowercase aliases → english canonical */
    private val countryCanon: Map<String, String> = mapOf(
        // EN + long RadioBrowser forms
        "ukraine" to "Ukraine",
        "germany" to "Germany",
        "spain" to "Spain",
        "poland" to "Poland",
        "france" to "France",
        "canada" to "Canada",
        "netherlands" to "Netherlands",
        "the netherlands" to "Netherlands",
        "greece" to "Greece",
        "cyprus" to "Cyprus",
        "united kingdom" to "United Kingdom",
        "the united kingdom of great britain and northern ireland" to "United Kingdom",
        "uk" to "United Kingdom",
        "united states" to "United States",
        "the united states of america" to "United States",
        "usa" to "United States",
        "us" to "United States",
        "italy" to "Italy",
        "romania" to "Romania",
        "switzerland" to "Switzerland",
        "mexico" to "Mexico",
        "bulgaria" to "Bulgaria",
        "belgium" to "Belgium",
        "australia" to "Australia",
        "croatia" to "Croatia",
        "albania" to "Albania",
        "estonia" to "Estonia",
        "ireland" to "Ireland",
        "slovenia" to "Slovenia",
        "hungary" to "Hungary",
        "japan" to "Japan",
        "argentina" to "Argentina",
        "colombia" to "Colombia",
        "costa rica" to "Costa Rica",
        "bosnia and herzegovina" to "Bosnia and Herzegovina",
        "the russian federation" to "Russia",
        "russia" to "Russia",
        "the united arab emirates" to "United Arab Emirates",
        "united arab emirates" to "United Arab Emirates",
        "uae" to "United Arab Emirates",
        "the marshall islands" to "Marshall Islands",
        // UA legacy (stations.json / favs)
        "україна" to "Ukraine",
        "німеччина" to "Germany",
        "нідерланди" to "Netherlands",
        "іспанія" to "Spain",
        "греція" to "Greece",
        "велика британія" to "United Kingdom",
        "сша" to "United States",
        "польща" to "Poland",
        "франція" to "France",
        "канада" to "Canada",
        "кіпр" to "Cyprus",
        "італія" to "Italy",
        "росія" to "Russia",
        "невідомо" to "Unknown",
        "unknown" to "Unknown",
    )

    private val countryUk: Map<String, String> = mapOf(
        "Ukraine" to "Україна",
        "Germany" to "Німеччина",
        "Spain" to "Іспанія",
        "Poland" to "Польща",
        "France" to "Франція",
        "Canada" to "Канада",
        "Netherlands" to "Нідерланди",
        "Greece" to "Греція",
        "Cyprus" to "Кіпр",
        "United Kingdom" to "Велика Британія",
        "United States" to "США",
        "Italy" to "Італія",
        "Romania" to "Румунія",
        "Switzerland" to "Швейцарія",
        "Mexico" to "Мексика",
        "Bulgaria" to "Болгарія",
        "Belgium" to "Бельгія",
        "Australia" to "Австралія",
        "Croatia" to "Хорватія",
        "Albania" to "Албанія",
        "Estonia" to "Естонія",
        "Ireland" to "Ірландія",
        "Slovenia" to "Словенія",
        "Hungary" to "Угорщина",
        "Japan" to "Японія",
        "Argentina" to "Аргентина",
        "Colombia" to "Колумбія",
        "Costa Rica" to "Коста-Рика",
        "Bosnia and Herzegovina" to "Боснія і Герцеговина",
        "Russia" to "Росія",
        "United Arab Emirates" to "ОАЕ",
        "Marshall Islands" to "Маршаллові Острови",
        "Unknown" to "Невідомо",
    )

    private val genreCanon: Map<String, String> = mapOf(
        "поп" to "Pop",
        "поп-музика" to "Pop",
        "поп/романтична музика" to "Pop",
        "поп/рок" to "Pop/Rock",
        "рок" to "Rock",
        "рок/український рок" to "Rock",
        "українська музика" to "Ukrainian music",
        "танцювальна/поп" to "Dance/Pop",
        "танцювальна/поп/електронна/топ-40" to "Dance/Pop",
        "діп-хаус" to "Deep House",
        "deep house" to "Deep House",
        "techno" to "Techno",
        "electronic" to "Electronic",
        "house" to "House",
        "trance" to "Trance",
        "pop" to "Pop",
        "rock" to "Rock",
    )

    private val genreUk: Map<String, String> = mapOf(
        "Pop" to "Поп",
        "Pop/Rock" to "Поп/Рок",
        "Rock" to "Рок",
        "Ukrainian music" to "Українська музика",
        "Dance/Pop" to "Танцювальна/Поп",
        "Deep House" to "Діп-хаус",
        "Techno" to "Техно",
        "Electronic" to "Електроніка",
        "House" to "Хаус",
        "Trance" to "Транс",
    )

    fun canonicalCountry(raw: String?): String {
        val t = raw?.trim().orEmpty()
        if (t.isEmpty() || t == "-") return ""
        return countryCanon[t.lowercase()] ?: t
    }

    fun countryLabel(ctx: Context, raw: String?): String {
        val canon = canonicalCountry(raw)
        if (canon.isEmpty()) return ""
        val uk = LocaleHelper.current(ctx) == "uk"
        return if (uk) countryUk[canon] ?: canon else canon
    }

    fun canonicalGenre(raw: String?): String {
        val t = raw?.trim().orEmpty()
        if (t.isEmpty() || t == "-") return ""
        // multi: "techno, electronic" — label each part if known
        if ("," in t) {
            return t.split(",").map { part ->
                val p = part.trim()
                genreCanon[p.lowercase()] ?: p
            }.filter { it.isNotEmpty() }.joinToString(", ")
        }
        return genreCanon[t.lowercase()] ?: t
    }

    fun genreLabel(ctx: Context, raw: String?): String {
        val canon = canonicalGenre(raw)
        if (canon.isEmpty()) return ""
        val uk = LocaleHelper.current(ctx) == "uk"
        if (!uk) return canon
        if ("," in canon) {
            return canon.split(",").joinToString(", ") { part ->
                val p = part.trim()
                genreUk[p] ?: p
            }
        }
        return genreUk[canon] ?: canon
    }
}
