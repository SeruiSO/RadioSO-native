package com.seruiso.radio1

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
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
 * Два «вуха»:
 *  1) Vosk offline — лише фрази «окей ес о» / «окей радіо»
 *  2) після wake — коротке вікно Google SpeechRecognizer для команди
 */
class VoiceListenService : Service() {

    private val main = Handler(Looper.getMainLooper())
    private var alive = false
    private var mode = MODE_WAKE // wake | cmd
    private var cmdUntil = 0L
    private var pausedForCmd = false
    private var coolUntil = 0L

    // Vosk
    private var model: Model? = null
    private var voskService: SpeechService? = null
    private var voskReady = false

    // Google (лише команда)
    private var google: SpeechRecognizer? = null
    private var googleBusy = false
    private var cmdAttempts = 0

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
        stopGoogle()
        try { model?.close() } catch (_: Exception) {}
        model = null
        super.onDestroy()
    }

    private fun stopMe() {
        alive = false
        main.removeCallbacksAndMessages(null)
        stopVosk()
        stopGoogle()
        if (pausedForCmd) radio(RadioWatchService.ACTION_PLAY)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ---------- model download / unpack ----------

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
        val zipUrl = MODEL_URL
        val tmpZip = File(cacheDir, "vosk-uk.zip")
        val dest = modelDir()
        try {
            if (tmpZip.exists()) tmpZip.delete()
            val conn = URL(zipUrl).openConnection() as HttpURLConnection
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
                    // zip root is usually vosk-model-small-uk-v3-nano/...
                    val rel = name.substringAfter("/", name)
                    if (rel.isBlank() || rel == name && name.endsWith("/")) {
                        entry = zis.nextEntry
                        continue
                    }
                    val outFile = File(dest, rel)
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { out ->
                            zis.copyTo(out)
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            tmpZip.delete()
            // if unpack left a single subfolder, flatten one level
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
                    startWakeListening()
                }
            } catch (e: Exception) {
                main.post {
                    startInForeground(getString(R.string.voice_model_fail))
                    main.postDelayed({ if (alive) stopMe() }, 3000)
                }
            }
        }
    }

    // ---------- WAKE (Vosk) ----------

    private fun startWakeListening() {
        if (!alive || !voskReady) return
        stopGoogle()
        stopVosk()
        mode = MODE_WAKE
        try {
            // без grammar: українська модель часто не має «ес/есо» у словнику граматики
            val rec = Recognizer(model, 16000.0f)
            val svc = SpeechService(rec, 16000.0f)
            voskService = svc
            svc.startListening(voskListener)
            startInForeground(getString(R.string.voice_listen))
        } catch (e: Exception) {
            startInForeground(getString(R.string.voice_none))
            main.postDelayed({ if (alive && mode == MODE_WAKE) startWakeListening() }, 4000)
        }
    }

    private fun stopVosk() {
        try { voskService?.stop() } catch (_: Exception) {}
        try { voskService?.shutdown() } catch (_: Exception) {}
        voskService = null
    }

    private var lastHeardShown = 0L

    private val voskListener = object : VoskListener {
        override fun onPartialResult(hypothesis: String?) {
            if (!alive || mode != MODE_WAKE) return
            val text = extractText(hypothesis ?: "")
            if (text.isNotBlank()) showHeard(text)
            checkWake(hypothesis)
        }

        override fun onResult(hypothesis: String?) {
            if (!alive || mode != MODE_WAKE) return
            val text = extractText(hypothesis ?: "")
            if (text.isNotBlank()) showHeard(text)
            checkWake(hypothesis)
        }

        override fun onFinalResult(hypothesis: String?) {
            if (!alive || mode != MODE_WAKE) return
            val text = extractText(hypothesis ?: "")
            if (text.isNotBlank()) showHeard(text)
            checkWake(hypothesis)
            // Vosk після final зупиняє сесію — знову wake
            main.postDelayed({
                if (alive && mode == MODE_WAKE) startWakeListening()
            }, 400)
        }

        override fun onError(exception: Exception?) {
            if (!alive) return
            val msg = exception?.message ?: "error"
            startInForeground("Vosk: $msg")
            main.postDelayed({ if (alive && mode == MODE_WAKE) startWakeListening() }, 2500)
        }

        override fun onTimeout() {
            if (!alive) return
            main.post {
                if (alive && mode == MODE_WAKE) startWakeListening()
            }
        }
    }

    /** Діагностика: що почув Vosk (обмежуємо частоту оновлень). */
    private fun showHeard(text: String) {
        val now = System.currentTimeMillis()
        if (now - lastHeardShown < 800) return
        lastHeardShown = now
        val short = if (text.length > 40) text.take(40) + "…" else text
        startInForeground("Чую: $short")
    }

    private fun checkWake(rawJson: String?) {
        if (rawJson.isNullOrBlank()) return
        if (System.currentTimeMillis() < coolUntil) return
        val text = extractText(rawJson)
        if (text.isBlank()) return
        val norm = spoken(text)
        if (!isWakePhrase(norm)) return

        coolUntil = System.currentTimeMillis() + 5000
        // звільняємо мікрофон від Vosk, даємо час системі
        stopVosk()
        beginCommand()
        // не стартуємо Google одразу — інакше ERROR / порожньо і «не знайшов»
        main.postDelayed({
            if (alive && mode == MODE_CMD) startCommandListening()
        }, 700)
    }

    private fun isWakePhrase(norm: String): Boolean {
        if (norm.length < 4) return false
        val words = norm.split(" ").filter { it.isNotBlank() }
        // довгі фрази = ефір/пісня
        if (words.size > 6) return false
        val forms = listOf(
            "добре радіо", "добре радио",
            "добрий радіо", "добрий радио",
            "добре радіова", // на випадок помилки ASR
        )
        if (forms.any { norm == it || norm.startsWith("$it ") || " $it " in " $norm " || norm.endsWith(" $it") }) {
            return true
        }
        // «добре» + «радіо» поруч (ASR може вставити сміття між ними рідко)
        val hasDob = words.any { it.startsWith("добр") } // добре / добрий / добра
        val hasRadio = words.any { it.startsWith("радіо") || it.startsWith("радио") || it == "radio" }
        return hasDob && hasRadio && words.size <= 4
    }

    private fun extractText(json: String): String {
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

    // ---------- COMMAND (Google) ----------

    private fun beginCommand() {
        mode = MODE_CMD
        cmdAttempts = 0
        cmdUntil = System.currentTimeMillis() + 12000
        startInForeground(getString(R.string.voice_cmd))
        if (!pausedForCmd) {
            pausedForCmd = true
            radio(RadioWatchService.ACTION_PAUSE)
        }
    }

    private fun startCommandListening() {
        if (!alive || mode != MODE_CMD) return
        stopGoogle()
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            finishCommandWindow(miss = true, raw = "")
            return
        }
        try {
            val g = SpeechRecognizer.createSpeechRecognizer(this)
            g.setRecognitionListener(googleListener)
            google = g
            googleBusy = true
            g.startListening(commandIntent())
            // вікно команди ~10 с
            main.postDelayed({
                if (alive && mode == MODE_CMD && googleBusy) {
                    try { google?.stopListening() } catch (_: Exception) {}
                }
            }, 10000)
        } catch (_: Exception) {
            finishCommandWindow(miss = true, raw = "")
        }
    }

    private fun stopGoogle() {
        googleBusy = false
        try { google?.cancel() } catch (_: Exception) {}
        try { google?.destroy() } catch (_: Exception) {}
        google = null
    }

    private fun commandIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "uk-UA")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 2000L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
    }

    private val googleListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onError(error: Int) {
            googleBusy = false
            if (!alive || mode != MODE_CMD) return
            // тиша / no match — даємо ще одну спробу, не одразу «не знайшов»
            val retryable = error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                || error == SpeechRecognizer.ERROR_NO_MATCH
                || error == SpeechRecognizer.ERROR_CLIENT
            if (retryable && cmdAttempts < 1) {
                cmdAttempts++
                startInForeground(getString(R.string.voice_cmd))
                main.postDelayed({
                    if (alive && mode == MODE_CMD) startCommandListening()
                }, 500)
                return
            }
            finishCommandWindow(miss = true, raw = "")
        }

        override fun onResults(results: Bundle?) {
            googleBusy = false
            if (!alive || mode != MODE_CMD) return
            val lines = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
            val text = spoken(lines.joinToString(" "))
            // ігноруємо якщо це знову лише wake-фраза
            if (isWakePhrase(text) || text.length < 2) {
                if (cmdAttempts < 1) {
                    cmdAttempts++
                    startInForeground(getString(R.string.voice_cmd))
                    main.postDelayed({
                        if (alive && mode == MODE_CMD) startCommandListening()
                    }, 400)
                } else {
                    finishCommandWindow(miss = true, raw = text)
                }
                return
            }
            runCommand(text)
        }
    }

    private fun finishCommandWindow(miss: Boolean, raw: String) {
        stopGoogle()
        mode = MODE_WAKE
        cmdUntil = 0
        if (miss) {
            startInForeground(
                if (raw.isBlank()) getString(R.string.voice_miss, "…")
                else getString(R.string.voice_miss, raw)
            )
        }
        if (pausedForCmd) {
            pausedForCmd = false
            radio(RadioWatchService.ACTION_PLAY)
        }
        main.postDelayed({
            if (alive) {
                startInForeground(getString(R.string.voice_listen))
                startWakeListening()
            }
        }, 1500)
    }

    private fun runCommand(raw: String) {
        mode = MODE_WAKE
        cmdUntil = 0
        var q = spoken(raw)
        // strip accidental wake leftover
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
            main.postDelayed({ if (alive) startWakeListening() }, 1200)
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
                    startWakeListening()
                }
            }, 1800)
        } else {
            finishCommandWindow(miss = true, raw = raw)
        }
    }

    // ---------- helpers ----------

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
        // Vosk grammar: only these phrases + unk
        private val WAKE_GRAMMAR =
            """["окей ес о", "окей есо", "ок ес о", "окей радіо", "окей радио", "ок радіо", "okay so", "ok so", "okay radio", "[unk]"]"""
    }
}
