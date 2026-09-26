package com.seruiso.radio1

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
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
            // Vosk після final зупиняється — знову сесія
            main.postDelayed({
                if (!alive) return@postDelayed
                if (mode == MODE_CMD && System.currentTimeMillis() > cmdUntil) {
                    endCommandMiss("")
                    return@postDelayed
                }
                startVoskSession()
            }, 350)
        }

        override fun onError(exception: Exception?) {
            if (!alive) return
            val msg = exception?.message ?: "error"
            startInForeground("Vosk: $msg")
            main.postDelayed({ if (alive) startVoskSession() }, 2000)
        }

        override fun onTimeout() {
            if (!alive) return
            main.post {
                if (!alive) return@post
                if (mode == MODE_CMD && System.currentTimeMillis() > cmdUntil) {
                    endCommandMiss("")
                } else {
                    startVoskSession()
                }
            }
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
        if (!pausedForCmd) {
            pausedForCmd = true
            radio(RadioWatchService.ACTION_PAUSE)
        }
        // таймер на випадок тиші
        main.postDelayed({
            if (alive && mode == MODE_CMD && System.currentTimeMillis() >= cmdUntil) {
                endCommandMiss("")
            }
        }, 12500)
        // сесію Vosk не рвемо насильно — final сам перезапустить у MODE_CMD
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

        val transport = when {
            listOf("пауз", "стоп", "зупини").any { it in q } && "включ" !in q ->
                RadioWatchService.ACTION_PAUSE
            listOf("далі", "наступ", "вперед").any { it in q } ->
                RadioWatchService.ACTION_NOTIF_NEXT
            listOf("назад", "поперед").any { it in q } ->
                RadioWatchService.ACTION_NOTIF_PREV
            listOf("грай", "продовж").any { it in q } && "включ" !in q && q.length < 24 ->
                RadioWatchService.ACTION_PLAY
            else -> null
        }
        if (transport != null) {
            pausedForCmd = false
            radio(transport)
            startInForeground(getString(R.string.voice_listen))
            main.postDelayed({ if (alive) startVoskSession() }, 1000)
            return
        }

        for (pref in listOf(
            "включи ", "увімкни ", "постав ", "переключи ",
            "станцію ", "станцию ", "радіо ", "радио "
        )) {
            q = q.removePrefix(pref)
        }
        q = spoken(q)
        val hit = stations().maxByOrNull { score(q, it.name) }
        if (hit != null && score(q, hit.name) >= 400) {
            pausedForCmd = false
            val i = Intent(this, RadioWatchService::class.java)
            i.action = RadioWatchService.ACTION_PLAY_URL
            i.putExtra(RadioWatchService.EXTRA_URL, hit.url)
            i.putExtra(RadioWatchService.EXTRA_NAME, hit.name)
            startForegroundService(i)
            startInForeground(hit.name)
            main.postDelayed({
                if (alive) {
                    startInForeground(getString(R.string.voice_listen))
                    startVoskSession()
                }
            }, 1600)
        } else {
            endCommandMiss(raw)
        }
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
        return when {
            n == q -> 1000
            q.contains(n) && n.length >= 3 -> 500 + n.length
            n.contains(q) && q.length >= 3 -> 400 + q.length
            else -> 0
        }
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
