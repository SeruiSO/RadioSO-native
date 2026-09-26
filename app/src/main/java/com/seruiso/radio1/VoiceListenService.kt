package com.seruiso.radio1

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener as VoskListener
import org.vosk.android.SpeechService
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.zip.ZipInputStream
import kotlin.concurrent.thread

/**
 * Одне вухо Vosk на два режими:
 *  WAKE — чекаємо «добре радіо»
 *  CMD  — наступна фраза = команда (без Google, без перехоплення мікрофона)
 */
class VoiceListenService : Service() {

    private val main = Handler(Looper.getMainLooper())
    private var alive = false
    private var mode = MODE_WAKE
    private var cmdUntil = 0L
    private var pausedForCmd = false
    private var coolUntil = 0L
    private var lastHeardShown = 0L

    private var model: Model? = null
    private var voskService: SpeechService? = null
    private var voskReady = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopMe()
            return START_NOT_STICKY
        }
        startInForeground(getString(R.string.voice_listen))
        if (!alive) {
            alive = true
            ensureModelThenStart()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        alive = false
        main.removeCallbacksAndMessages(null)
        stopVosk()
        try { model?.close() } catch (_: Exception) {}
        model = null
        super.onDestroy()
    }

    private fun stopMe() {
        alive = false
        main.removeCallbacksAndMessages(null)
        stopVosk()
        if (pausedForCmd) radio(RadioWatchService.ACTION_PLAY)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ---------- model ----------

    private fun modelDir(): File = File(filesDir, "vosk-uk-v3-nano")

    private fun modelReady(): Boolean {
        val d = modelDir()
        return File(d, "am").isDirectory || File(d, "conf").isDirectory ||
            d.listFiles()?.any { it.isDirectory } == true
    }

    private fun ensureModelThenStart() {
        if (modelReady()) {
            openModelAndListen()
            return
        }
        startInForeground(getString(R.string.voice_model_dl))
        thread(name = "vosk-dl") {
            val ok = downloadAndUnpack()
            main.post {
                if (!alive) return@post
                if (ok) {
                    startInForeground(getString(R.string.voice_model_ok))
                    openModelAndListen()
                } else {
                    startInForeground(getString(R.string.voice_model_fail))
                    main.postDelayed({ if (alive) stopMe() }, 3000)
                }
            }
        }
    }

    private fun downloadAndUnpack(): Boolean {
        val tmpZip = File(cacheDir, "vosk-uk.zip")
        val dest = modelDir()
        try {
            if (tmpZip.exists()) tmpZip.delete()
            val conn = URL(MODEL_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = 30_000
            conn.readTimeout = 120_000
            conn.instanceFollowRedirects = true
            conn.connect()
            if (conn.responseCode !in 200..299) return false
            BufferedInputStream(conn.inputStream).use { input ->
                FileOutputStream(tmpZip).use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                    }
                }
            }
            if (dest.exists()) dest.deleteRecursively()
            dest.mkdirs()
            ZipInputStream(tmpZip.inputStream().buffered()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val name = entry.name
                    val rel = name.substringAfter("/", name)
                    if (rel.isBlank()) {
                        entry = zis.nextEntry
                        continue
                    }
                    val outFile = File(dest, rel)
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { out -> zis.copyTo(out) }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            tmpZip.delete()
            val kids = dest.listFiles()?.filter { it.isDirectory } ?: emptyList()
            if (kids.size == 1 && !File(dest, "am").exists() && !File(dest, "conf").exists()) {
                val sub = kids[0]
                sub.listFiles()?.forEach { it.renameTo(File(dest, it.name)) }
                sub.delete()
            }
            return modelReady()
        } catch (_: Exception) {
            try { dest.deleteRecursively() } catch (_: Exception) {}
            return false
        }
    }

    private fun openModelAndListen() {
        thread(name = "vosk-open") {
            try {
                val m = Model(modelDir().absolutePath)
                main.post {
                    if (!alive) {
                        try { m.close() } catch (_: Exception) {}
                        return@post
                    }
                    model = m
                    voskReady = true
                    startInForeground(getString(R.string.voice_listen))
                    startVoskSession()
                }
            } catch (_: Exception) {
                main.post {
                    startInForeground(getString(R.string.voice_model_fail))
                    main.postDelayed({ if (alive) stopMe() }, 3000)
                }
            }
        }
    }

    // ---------- Vosk session (wake + cmd) ----------

    private fun startVoskSession() {
        if (!alive || !voskReady) return
        stopVosk()
        try {
            val rec = Recognizer(model, 16000.0f)
            val svc = SpeechService(rec, 16000.0f)
            voskService = svc
            svc.startListening(voskListener)
            if (mode == MODE_WAKE) {
                startInForeground(getString(R.string.voice_listen))
            } else {
                startInForeground(getString(R.string.voice_cmd))
            }
        } catch (_: Exception) {
            startInForeground(getString(R.string.voice_none))
            main.postDelayed({ if (alive) startVoskSession() }, 3000)
        }
    }

    private fun stopVosk() {
        try { voskService?.stop() } catch (_: Exception) {}
        try { voskService?.shutdown() } catch (_: Exception) {}
        voskService = null
    }

    private val voskListener = object : VoskListener {
        override fun onPartialResult(hypothesis: String?) {
            if (!alive) return
            val text = extractText(hypothesis ?: "")
            if (text.isNotBlank()) showHeard(text)
            if (mode == MODE_WAKE) maybeWake(text)
            // у CMD partial не виконуємо — чекаємо final
        }

        override fun onResult(hypothesis: String?) {
            handleUtterance(hypothesis)
        }

        override fun onFinalResult(hypothesis: String?) {
            handleUtterance(hypothesis)
            main.postDelayed({
                if (!alive) return@postDelayed
                if (mode == MODE_CMD && System.currentTimeMillis() > cmdUntil) {
                    endCommandMiss("")
                    return@postDelayed
                }
                // м'який restart — без stop/shutdown (не блимає індикатор мікрофона)
                resumeListening()
            }, 200)
        }

        override fun onError(exception: Exception?) {
            if (!alive) return
            val msg = exception?.message ?: "error"
            startInForeground("Vosk: $msg")
            main.postDelayed({ if (alive) startVoskSession() }, 2500)
        }

        override fun onTimeout() {
            if (!alive) return
            main.post {
                if (!alive) return@post
                if (mode == MODE_CMD && System.currentTimeMillis() > cmdUntil) {
                    endCommandMiss("")
                } else {
                    resumeListening()
                }
            }
        }
    }

    /** Продовжити слухання без повного recreate SpeechService. */
    private fun resumeListening() {
        if (!alive) return
        val svc = voskService
        if (svc == null) {
            startVoskSession()
            return
        }
        try {
            svc.startListening(voskListener)
        } catch (_: Exception) {
            startVoskSession()
        }
    }

    private fun handleUtterance(hypothesis: String?) {
        if (!alive) return
        val text = spoken(extractText(hypothesis ?: ""))
        if (text.isBlank()) return
        showHeard(text)
        when (mode) {
            MODE_WAKE -> maybeWake(text)
            MODE_CMD -> maybeCommand(text)
        }
    }

    private fun maybeWake(text: String) {
        if (mode != MODE_WAKE) return
        if (System.currentTimeMillis() < coolUntil) return
        val norm = spoken(text)
        if (!isWakePhrase(norm)) return
        coolUntil = System.currentTimeMillis() + 4000
        enterCommandMode()
    }

    private fun enterCommandMode() {
        mode = MODE_CMD
        cmdUntil = System.currentTimeMillis() + 12000
        startInForeground(getString(R.string.voice_cmd))
        playListenBeep()
        if (!pausedForCmd) {
            pausedForCmd = true
            radio(RadioWatchService.ACTION_PAUSE)
        }
        main.postDelayed({
            if (alive && mode == MODE_CMD && System.currentTimeMillis() >= cmdUntil) {
                endCommandMiss("")
            }
        }, 12500)
        // одразу готуємо слухання команди (м'яко)
        main.postDelayed({ if (alive && mode == MODE_CMD) resumeListening() }, 400)
    }

    private fun playListenBeep() {
        try {
            val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90)
            tg.startTone(ToneGenerator.TONE_PROP_ACK, 180)
            main.postDelayed({ try { tg.release() } catch (_: Exception) {} }, 400)
        } catch (_: Exception) {}
    }

    private fun maybeCommand(text: String) {
        if (mode != MODE_CMD) return
        val norm = spoken(text)
        if (norm.length < 2) return
        // ще раз wake — ігноруємо
        if (isWakePhrase(norm)) return
        // коротка фраза після wake — виконуємо
        runCommand(norm)
    }

    private fun endCommandMiss(raw: String) {
        mode = MODE_WAKE
        cmdUntil = 0
        startInForeground(
            if (raw.isBlank()) getString(R.string.voice_miss, "…")
            else getString(R.string.voice_miss, raw)
        )
        if (pausedForCmd) {
            pausedForCmd = false
            radio(RadioWatchService.ACTION_PLAY)
        }
        main.postDelayed({
            if (alive) {
                startInForeground(getString(R.string.voice_listen))
                startVoskSession()
            }
        }, 1600)
    }

    private fun runCommand(raw: String) {
        mode = MODE_WAKE
        cmdUntil = 0
        var q = spoken(raw)
        for (w in listOf("добре радіо ", "добре радио ", "добрий радіо ", "добрий радио ")) {
            q = q.removePrefix(w)
        }
        q = spoken(q)
        // показуємо що саме пішло в команду
        startInForeground("Команда: $q")

        val transport = when {
            listOf("пауз", "стоп", "зупини", "вимкн").any { it in q } &&
                !listOf("включ", "увімкн", "постав").any { it in q } ->
                RadioWatchService.ACTION_PAUSE
            listOf("далі", "наступ", "вперед", "наступн").any { it in q } ->
                RadioWatchService.ACTION_NOTIF_NEXT
            listOf("назад", "поперед", "попередн").any { it in q } ->
                RadioWatchService.ACTION_NOTIF_PREV
            listOf("грай", "продовж", "віднов", "плей").any { it in q } &&
                !listOf("включ", "увімкн").any { it in q } && q.length < 28 ->
                RadioWatchService.ACTION_PLAY
            else -> null
        }
        if (transport != null) {
            pausedForCmd = false
            radio(transport)
            main.postDelayed({
                if (alive) {
                    startInForeground(getString(R.string.voice_listen))
                    startVoskSession()
                }
            }, 1000)
            return
        }

        // знімаємо службові слова дії
        var nameQ = q
        for (pref in listOf(
            "включи ", "включити ", "увімкни ", "увімкнути ", "постав ", "поставити ",
            "переключи ", "переключити ", "станцію ", "станцию ", "радіо ", "радио ",
            "будь ласка ", "мені "
        )) {
            nameQ = nameQ.removePrefix(pref)
        }
        nameQ = spoken(nameQ)
        // синоніми популярних назв
        nameQ = expandAliases(nameQ)

        val all = stations()
        val hit = all.maxByOrNull { score(nameQ, it.name) }
        val sc = if (hit != null) score(nameQ, hit.name) else 0
        if (hit != null && sc >= 250) {
            pausedForCmd = false
            val i = Intent(this, RadioWatchService::class.java)
            i.action = RadioWatchService.ACTION_PLAY_URL
            i.putExtra(RadioWatchService.EXTRA_URL, hit.url)
            i.putExtra(RadioWatchService.EXTRA_NAME, hit.name)
            startForegroundService(i)
            startInForeground("${hit.name} ($sc)")
            main.postDelayed({
                if (alive) {
                    startInForeground(getString(R.string.voice_listen))
                    startVoskSession()
                }
            }, 1800)
        } else {
            endCommandMiss("$q →$nameQ")
        }
    }

    /** Синоніми, які Vosk часто чує інакше ніж назва в списку. */
    private fun expandAliases(q: String): String {
        var x = q
        val map = listOf(
            "люкс фм" to "люкс",
            "люксфм" to "люкс",
            "lux fm" to "люкс",
            "lux" to "люкс",
            "хіт фм" to "хіт",
            "хит фм" to "хіт",
            "hit fm" to "хіт",
            "наше радіо" to "наше",
            "наше радио" to "наше",
            "авторадіо" to "авторадіо",
            "авторадио" to "авторадіо",
            "мелоди" to "мелоді",
            "мелодия" to "мелоді",
            "ретро фм" to "ретро",
            "retro" to "ретро",
            "європа плюс" to "європа",
            "европа плюс" to "європа",
            "шanson" to "шансон",
            "шансон" to "шансон",
        )
        for ((a, b) in map) {
            if (a in x) x = x.replace(a, b)
        }
        return spoken(x)
    }

    // ---------- helpers ----------

    private fun isWakePhrase(norm: String): Boolean {
        if (norm.length < 4) return false
        val words = norm.split(" ").filter { it.isNotBlank() }
        if (words.size > 6) return false
        val forms = listOf("добре радіо", "добре радио", "добрий радіо", "добрий радио")
        if (forms.any { norm == it || norm.startsWith("$it ") || " $it " in " $norm " || norm.endsWith(" $it") }) {
            return true
        }
        val hasDob = words.any { it.startsWith("добр") }
        val hasRadio = words.any { it.startsWith("радіо") || it.startsWith("радио") || it == "radio" }
        return hasDob && hasRadio && words.size <= 4
    }

    private fun extractText(json: String): String {
        if (json.isBlank()) return ""
        return try {
            val o = JSONObject(json)
            when {
                o.has("text") -> o.optString("text", "")
                o.has("partial") -> o.optString("partial", "")
                else -> ""
            }
        } catch (_: Exception) {
            json
        }
    }

    private fun showHeard(text: String) {
        val now = System.currentTimeMillis()
        if (now - lastHeardShown < 700) return
        lastHeardShown = now
        val short = if (text.length > 40) text.take(40) + "…" else text
        if (mode == MODE_CMD) {
            startInForeground("${getString(R.string.voice_cmd)}: $short")
        } else {
            startInForeground("Чую: $short")
        }
    }

    private fun radio(action: String) {
        val i = Intent(this, RadioWatchService::class.java)
        i.action = action
        startForegroundService(i)
    }

    private fun stations(): List<Station> {
        val all = mutableListOf<Station>()
        try { all.addAll(StationRepo.load(this).second) } catch (_: Exception) {}
        try {
            TabStore.genreTabs(this).forEach { all.addAll(TabStore.extraStations(this, it)) }
        } catch (_: Exception) {}
        try { all.addAll(FavStore.stations(this)) } catch (_: Exception) {}
        return all.distinctBy { it.url }
    }

    private fun score(query: String, name: String): Int {
        val q = fold(query)
        val n = fold(name)
        if (q.length < 2 || n.length < 2) return 0
        if (n == q) return 1000
        if (q.contains(n) && n.length >= 3) return 500 + n.length
        if (n.contains(q) && q.length >= 3) return 450 + q.length

        // по словах: «люкс фм» vs «Lux FM»
        val qw = q.split(" ").filter { it.length >= 2 }
        val nw = n.split(" ").filter { it.length >= 2 }
        if (qw.isEmpty() || nw.isEmpty()) return 0
        var hits = 0
        var bonus = 0
        for (w in qw) {
            val m = nw.firstOrNull { it == w || it.startsWith(w) || w.startsWith(it) }
            if (m != null) {
                hits++
                bonus += m.length
            }
        }
        if (hits == 0) {
            // одне слово з запиту входить у всю назву
            for (w in qw) {
                if (w.length >= 3 && n.contains(w)) return 300 + w.length
            }
            return 0
        }
        return 200 + hits * 80 + bonus
    }

    private fun spoken(s: String): String {
        var x = s.lowercase(Locale.forLanguageTag("uk")).replace('ё', 'е')
        x = x.replace(Regex("[^a-zа-яіїєґ0-9 ]"), " ")
        x = x.replace("еф ем", "фм")
        return x.replace(Regex("\\s+"), " ").trim()
    }

    private fun fold(s: String): String {
        var x = spoken(s)
            .replace("lux", "люкс")
            .replace("radio", "радіо")
            .replace("fm", "фм")
        val pairs = listOf(
            "shch" to "щ", "sh" to "ш", "ch" to "ч", "zh" to "ж", "kh" to "х",
            "ya" to "я", "yu" to "ю", "ye" to "є",
            "a" to "а", "b" to "б", "c" to "к", "d" to "д", "e" to "е", "f" to "ф",
            "g" to "г", "h" to "х", "i" to "і", "j" to "й", "k" to "к", "l" to "л",
            "m" to "м", "n" to "н", "o" to "о", "p" to "п", "q" to "к", "r" to "р",
            "s" to "с", "t" to "т", "u" to "у", "v" to "в", "w" to "в", "x" to "кс",
            "y" to "и", "z" to "з",
        )
        val sb = StringBuilder()
        var i = 0
        while (i < x.length) {
            val hit = pairs.firstOrNull { x.startsWith(it.first, i) }
            if (hit != null && hit.first[0] in 'a'..'z') {
                sb.append(hit.second)
                i += hit.first.length
            } else {
                sb.append(x[i])
                i++
            }
        }
        return spoken(sb.toString())
    }

    private fun startInForeground(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Voice", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, VoiceListenService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Radio SO")
            .setContentText(text)
            .setOngoing(true)
            .addAction(0, getString(R.string.voice_stop), stop)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(77, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(77, n)
        }
    }

    companion object {
        const val ACTION_STOP = "com.seruiso.radio1.VOICE_STOP"
        private const val CHANNEL = "voice_listen"
        private const val MODE_WAKE = "wake"
        private const val MODE_CMD = "cmd"
        private const val MODEL_URL =
            "https://alphacephei.com/vosk/models/vosk-model-small-uk-v3-nano.zip"
    }
}
