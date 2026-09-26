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
import java.util.Locale

class VoiceListenService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var alive = false
    private var mode = "wake"
    private var cmdUntil = 0L
    private var pausedForCmd = false
    private var busy = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopMe()
            return START_NOT_STICKY
        }
        startInForeground(getString(R.string.voice_listen))
        if (!alive) {
            alive = true
            if (!SpeechRecognizer.isRecognitionAvailable(this)) {
                startInForeground(getString(R.string.voice_none))
                main.postDelayed({ stopMe() }, 2500)
                return START_NOT_STICKY
            }
            recognizer = SpeechRecognizer.createSpeechRecognizer(this).also { it.setRecognitionListener(listener) }
            arm(300)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        alive = false
        main.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
        super.onDestroy()
    }

    private fun stopMe() {
        alive = false
        recognizer?.cancel()
        if (pausedForCmd) radio(RadioWatchService.ACTION_PLAY)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun arm(delay: Long) {
        main.postDelayed({
            if (!alive || busy) return@postDelayed
            if (mode == "cmd" && System.currentTimeMillis() > cmdUntil) {
                mode = "wake"
                startInForeground(getString(R.string.voice_listen))
                if (pausedForCmd) {
                    pausedForCmd = false
                    radio(RadioWatchService.ACTION_PLAY)
                }
            }
            try {
                busy = true
                recognizer?.startListening(listenIntent())
            } catch (_: Exception) {
                busy = false
                arm(700)
            }
        }, delay)
    }

    private fun listenIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "uk-UA")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 800L)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() { busy = false }
        override fun onError(error: Int) {
            busy = false
            if (!alive) return
            arm(if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 800 else 350)
        }
        override fun onResults(results: Bundle?) {
            busy = false
            heard(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty())
            if (alive) arm(250)
        }
        override fun onPartialResults(partialResults: Bundle?) {
            heard(partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty())
        }
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun heard(lines: List<String>) {
        if (!alive || lines.isEmpty()) return
        val text = spoken(lines.first())
        val wakeAt = wakeEnd(text)
        if (mode == "wake") {
            if (wakeAt < 0) return
            val rest = text.substring(wakeAt).trim()
            if (rest.length >= 3) runCommand(rest) else beginCommand()
            return
        }
        val rest = if (wakeAt >= 0) text.substring(wakeAt).trim() else text
        if (rest.length >= 3) runCommand(rest)
    }

    private fun beginCommand() {
        mode = "cmd"
        cmdUntil = System.currentTimeMillis() + 7000
        startInForeground(getString(R.string.voice_cmd))
        if (!pausedForCmd) {
            pausedForCmd = true
            radio(RadioWatchService.ACTION_PAUSE)
        }
    }

    private fun runCommand(raw: String) {
        mode = "wake"
        cmdUntil = 0
        var q = spoken(raw)
        val transport = when {
            listOf("пауз", "стоп", "зупини").any { it in q } && "включ" !in q -> RadioWatchService.ACTION_PAUSE
            listOf("далі", "наступ", "вперед").any { it in q } -> RadioWatchService.ACTION_NOTIF_NEXT
            listOf("назад", "поперед").any { it in q } -> RadioWatchService.ACTION_NOTIF_PREV
            listOf("грай", "продовж").any { it in q } && "включ" !in q && q.length < 24 -> RadioWatchService.ACTION_PLAY
            else -> null
        }
        if (transport != null) {
            pausedForCmd = false
            radio(transport)
            startInForeground(getString(R.string.voice_listen))
            return
        }
        for (p in listOf("включи ", "увімкни ", "постав ", "переключи ", "станцію ", "станцию ", "радіо ", "радио ")) {
            q = q.removePrefix(p)
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
            main.postDelayed({ if (alive && mode == "wake") startInForeground(getString(R.string.voice_listen)) }, 1800)
        } else {
            startInForeground(getString(R.string.voice_miss, raw))
            if (pausedForCmd) {
                pausedForCmd = false
                radio(RadioWatchService.ACTION_PLAY)
            }
            main.postDelayed({ if (alive && mode == "wake") startInForeground(getString(R.string.voice_listen)) }, 1800)
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
        try { TabStore.genreTabs(this).forEach { all.addAll(TabStore.extraStations(this, it)) } } catch (_: Exception) {}
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

    private fun wakeEnd(text: String): Int {
        val forms = listOf("окей ес о", "ок ес о", "окей есо", "ок есо", "окей со", "ок со", "okay so", "ok so")
        var at = -1
        var end = -1
        for (f in forms) {
            val i = text.indexOf(f)
            if (i >= 0 && (at < 0 || i < at || (i == at && f.length > end - at))) {
                at = i
                end = i + f.length
            }
        }
        return end
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
    }
}
