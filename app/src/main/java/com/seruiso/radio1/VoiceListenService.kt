package com.seruiso.radio1

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
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
 * Vosk — ТІЛЬКИ wake «добре радіо» (офлайн, легко).
 * Google SpeechRecognizer — команда після wake (краще розуміє українську).
 */
class VoiceListenService : Service() {

    private val main = Handler(Looper.getMainLooper())
    private var alive = false
    private var mode = MODE_WAKE
    private var cmdUntil = 0L
    private var pausedForCmd = false
    private var coolUntil = 0L
    private var lastHeardShown = 0L
    private var cmdAttempts = 0

    private var model: Model? = null
    private var voskService: SpeechService? = null
    private var voskReady = false

    private var google: SpeechRecognizer? = null
    private var googleBusy = false

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
        stopGoogle()
        stopVosk()
        try { model?.close() } catch (_: Exception) {}
        model = null
        super.onDestroy()
    }

    private fun stopMe() {
        alive = false
        main.removeCallbacksAndMessages(null)
        stopGoogle()
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
                    val rel = entry.name.substringAfter("/", entry.name)
                    if (rel.isNotBlank()) {
                        val outFile = File(dest, rel)
                        if (entry.isDirectory) outFile.mkdirs()
                        else {
                            outFile.parentFile?.mkdirs()
                            FileOutputStream(outFile).use { out -> zis.copyTo(out) }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            tmpZip.delete()
            val kids = dest.listFiles()?.filter { it.isDirectory } ?: emptyList()
            if (kids.size == 1 && !File(dest, "am").exists()) {
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
                    startWakeOnly()
                }
            } catch (_: Exception) {
                main.post {
                    startInForeground(getString(R.string.voice_model_fail))
                    main.postDelayed({ if (alive) stopMe() }, 3000)
                }
            }
        }
    }

    // ---------- WAKE: тільки Vosk, лише «добре радіо» ----------

    private fun startWakeOnly() {
        if (!alive || !voskReady) return
        mode = MODE_WAKE
        stopGoogle()
        stopVosk()
        try {
            val rec = Recognizer(model, 16000.0f)
            val svc = SpeechService(rec, 16000.0f)
            voskService = svc
            svc.startListening(voskWakeListener)
            startInForeground(getString(R.string.voice_listen))
        } catch (_: Exception) {
            main.postDelayed({ if (alive) startWakeOnly() }, 3000)
        }
    }

    private fun stopVosk() {
        try { voskService?.stop() } catch (_: Exception) {}
        try { voskService?.shutdown() } catch (_: Exception) {}
        voskService = null
    }

    private fun resumeWakeSoft() {
        if (!alive || mode != MODE_WAKE) return
        val svc = voskService
        if (svc == null) {
            startWakeOnly()
            return
        }
        try {
            svc.startListening(voskWakeListener)
        } catch (_: Exception) {
            startWakeOnly()
        }
    }

    private val voskWakeListener = object : VoskListener {
        override fun onPartialResult(hypothesis: String?) {
            if (!alive || mode != MODE_WAKE) return
            val text = spoken(extractText(hypothesis ?: ""))
            if (text.isNotBlank()) showHeard(text)
            if (isWakePhrase(text)) onWake()
        }

        override fun onResult(hypothesis: String?) {
            if (!alive || mode != MODE_WAKE) return
            val text = spoken(extractText(hypothesis ?: ""))
            if (text.isNotBlank()) showHeard(text)
            if (isWakePhrase(text)) onWake()
        }

        override fun onFinalResult(hypothesis: String?) {
            if (!alive || mode != MODE_WAKE) return
            val text = spoken(extractText(hypothesis ?: ""))
            if (text.isNotBlank()) showHeard(text)
            if (isWakePhrase(text)) {
                onWake()
            } else {
                main.postDelayed({ if (alive && mode == MODE_WAKE) resumeWakeSoft() }, 200)
            }
        }

        override fun onError(exception: Exception?) {
            if (!alive || mode != MODE_WAKE) return
            main.postDelayed({ if (alive && mode == MODE_WAKE) startWakeOnly() }, 2000)
        }

        override fun onTimeout() {
            if (!alive || mode != MODE_WAKE) return
            main.post { if (alive && mode == MODE_WAKE) resumeWakeSoft() }
        }
    }

    private fun onWake() {
        if (mode != MODE_WAKE) return
        if (System.currentTimeMillis() < coolUntil) return
        coolUntil = System.currentTimeMillis() + 5000
        mode = MODE_CMD
        cmdAttempts = 0
        cmdUntil = System.currentTimeMillis() + 14000
        // ПОВНІСТЮ звільняємо мікрофон від Vosk
        stopVosk()
        startInForeground(getString(R.string.voice_cmd))
        playListenBeep()
        if (!pausedForCmd) {
            pausedForCmd = true
            radio(RadioWatchService.ACTION_PAUSE)
        }
        // важлива пауза: Android відпускає mic після SpeechService
        main.postDelayed({
            if (alive && mode == MODE_CMD) startGoogleCommand()
        }, 1100)
        main.postDelayed({
            if (alive && mode == MODE_CMD && System.currentTimeMillis() >= cmdUntil) {
                endCommandMiss("час вийшов")
            }
        }, 14500)
    }

    private fun playListenBeep() {
        try {
            val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90)
            tg.startTone(ToneGenerator.TONE_PROP_ACK, 200)
            main.postDelayed({ try { tg.release() } catch (_: Exception) {} }, 500)
        } catch (_: Exception) {}
    }

    // ---------- CMD: Google (краще за nano-Vosk) ----------

    private fun startGoogleCommand() {
        if (!alive || mode != MODE_CMD) return
        stopGoogle()
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            endCommandMiss("немає розпізнавання")
            return
        }
        try {
            val g = SpeechRecognizer.createSpeechRecognizer(this)
            g.setRecognitionListener(googleListener)
            google = g
            googleBusy = true
            g.startListening(commandIntent())
            main.postDelayed({
                if (alive && mode == MODE_CMD && googleBusy) {
                    try { google?.stopListening() } catch (_: Exception) {}
                }
            }, 11000)
        } catch (_: Exception) {
            endCommandMiss("помилка Google")
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
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 2500L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2200L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1800L)
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
            val retry = error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                || error == SpeechRecognizer.ERROR_NO_MATCH
                || error == SpeechRecognizer.ERROR_CLIENT
            if (retry && cmdAttempts < 2) {
                cmdAttempts++
                startInForeground(getString(R.string.voice_cmd) + "…")
                main.postDelayed({ if (alive && mode == MODE_CMD) startGoogleCommand() }, 600)
                return
            }
            endCommandMiss("не розчув")
        }

        override fun onResults(results: Bundle?) {
            googleBusy = false
            if (!alive || mode != MODE_CMD) return
            val lines = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
            // беремо найкращий з кількох варіантів Google
            val best = lines.map { spoken(it) }.filter { it.length >= 2 }
                .maxByOrNull { candidateScore(it) } ?: ""
            if (best.isBlank() || isWakePhrase(best)) {
                if (cmdAttempts < 2) {
                    cmdAttempts++
                    main.postDelayed({ if (alive && mode == MODE_CMD) startGoogleCommand() }, 500)
                } else {
                    endCommandMiss(best.ifBlank { "…" })
                }
                return
            }
            runCommand(best)
        }
    }

    private fun candidateScore(q: String): Int {
        val transport = listOf("пауз", "стоп", "далі", "назад", "грай", "включ", "постав")
        var s = 0
        if (transport.any { it in q }) s += 50
        val all = stations()
        s += all.maxOfOrNull { score(expandAliases(stripAction(q)), it.name) } ?: 0
        return s
    }

    private fun endCommandMiss(raw: String) {
        mode = MODE_WAKE
        cmdUntil = 0
        stopGoogle()
        startInForeground(getString(R.string.voice_miss, raw))
        if (pausedForCmd) {
            pausedForCmd = false
            radio(RadioWatchService.ACTION_PLAY)
        }
        main.postDelayed({
            if (alive) {
                startInForeground(getString(R.string.voice_listen))
                startWakeOnly()
            }
        }, 1800)
    }

    private fun stripAction(q0: String): String {
        var q = spoken(q0)
        for (w in listOf("добре радіо ", "добре радио ")) q = q.removePrefix(w)
        for (pref in listOf(
            "включи ", "включити ", "увімкни ", "увімкнути ", "постав ", "поставити ",
            "переключи ", "переключити ", "станцію ", "станцию ", "радіо ", "радио ",
            "будь ласка ", "мені "
        )) {
            q = q.removePrefix(pref)
        }
        return spoken(q)
    }

    private fun runCommand(raw: String) {
        mode = MODE_WAKE
        cmdUntil = 0
        stopGoogle()
        var q = spoken(raw)
        startInForeground("Команда: $q")

        val transport = when {
            listOf("пауз", "стоп", "зупини", "вимкн").any { it in q } &&
                !listOf("включ", "увімкн", "постав").any { it in q } ->
                RadioWatchService.ACTION_PAUSE
            listOf("далі", "наступ", "вперед").any { it in q } ->
                RadioWatchService.ACTION_NOTIF_NEXT
            listOf("назад", "поперед").any { it in q } ->
                RadioWatchService.ACTION_NOTIF_PREV
            listOf("грай", "продовж", "віднов").any { it in q } &&
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
                    startWakeOnly()
                }
            }, 1200)
            return
        }

        val nameQ = expandAliases(stripAction(q))
        val hit = stations().maxByOrNull { score(nameQ, it.name) }
        val sc = if (hit != null) score(nameQ, hit.name) else 0
        if (hit != null && sc >= 250) {
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
                    startWakeOnly()
                }
            }, 1800)
        } else {
            endCommandMiss("$q →$nameQ")
        }
    }

    private fun expandAliases(q: String): String {
        var x = q
        val map = listOf(
            "люкс фм" to "люкс", "люксфм" to "люкс", "lux fm" to "люкс", "lux" to "люкс",
            "хіт фм" to "хіт", "хит фм" to "хіт", "hit fm" to "хіт",
            "наше радіо" to "наше", "наше радио" to "наше",
            "ретро фм" to "ретро", "retro" to "ретро",
            "європа плюс" to "європа", "европа плюс" to "європа",
        )
        for ((a, b) in map) if (a in x) x = x.replace(a, b)
        return spoken(x)
    }

    private fun isWakePhrase(norm: String): Boolean {
        if (norm.length < 4) return false
        val words = norm.split(" ").filter { it.isNotBlank() }
        if (words.size > 6) return false
        val forms = listOf("добре радіо", "добре радио", "добрий радіо", "добрий радио")
        if (forms.any { norm == it || norm.startsWith("$it ") || " $it " in " $norm " || norm.endsWith(" $it") }) {
            return true
        }
        val hasDob = words.any { it.startsWith("добр") }
        val hasRadio = words.any { it.startsWith("радіо") || it.startsWith("радио") }
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
        } catch (_: Exception) { json }
    }

    private fun showHeard(text: String) {
        val now = System.currentTimeMillis()
        if (now - lastHeardShown < 800) return
        lastHeardShown = now
        val short = if (text.length > 40) text.take(40) + "…" else text
        startInForeground("Чую: $short")
    }

    private fun radio(action: String) {
        startForegroundService(Intent(this, RadioWatchService::class.java).setAction(action))
    }

    private fun stations(): List<Station> {
        val all = mutableListOf<Station>()
        try { all.addAll(StationRepo.load(this).second) } catch (_: Exception) {}
        try { TabStore.genreTabs(this).forEach { all.addAll(TabStore.extraStations(this, it)) } } catch (_: Exception) {}
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
        val qw = q.split(" ").filter { it.length >= 2 }
        val nw = n.split(" ").filter { it.length >= 2 }
        if (qw.isEmpty() || nw.isEmpty()) return 0
        var hits = 0
        var bonus = 0
        for (w in qw) {
            val m = nw.firstOrNull { it == w || it.startsWith(w) || w.startsWith(it) }
            if (m != null) { hits++; bonus += m.length }
        }
        if (hits == 0) {
            for (w in qw) if (w.length >= 3 && n.contains(w)) return 300 + w.length
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
        var x = spoken(s).replace("lux", "люкс").replace("radio", "радіо").replace("fm", "фм")
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
                sb.append(hit.second); i += hit.first.length
            } else { sb.append(x[i]); i++ }
        }
        return spoken(sb.toString())
    }

    private fun startInForeground(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Voice", NotificationManager.IMPORTANCE_LOW))
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
