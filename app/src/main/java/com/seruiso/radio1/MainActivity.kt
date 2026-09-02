package com.seruiso.radio1

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.seruiso.radio1.ui.theme.RadioSOTheme

class MainActivity : ComponentActivity() {

    private var stationName by mutableStateOf("Radio S O")
    private var trackTitle by mutableStateOf("")
    private var isPlaying by mutableStateOf(false)
    private var statusText by mutableStateOf("готово")

    private val uiReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                RadioWatchService.ACTION_PLAYBACK_UI -> {
                    isPlaying = intent.getBooleanExtra("playing", false)
                }
                RadioWatchService.ACTION_TRACK_META -> {
                    trackTitle = intent.getStringExtra(RadioWatchService.EXTRA_TRACK) ?: ""
                }
                RadioWatchService.ACTION_STATUS_UI -> {
                    val st = intent.getStringExtra("status") ?: ""
                    val attempt = intent.getIntExtra("attempt", 0)
                    statusText = if (attempt > 0) "$st #$attempt" else st
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        askNotifyPermission()
        readPrefs()
        setContent {
            RadioSOTheme {
                MiniPlayer(
                    name = stationName,
                    track = trackTitle,
                    playing = isPlaying,
                    status = statusText,
                    onPlay = { playTest() },
                    onPause = { sendAction(RadioWatchService.ACTION_PAUSE) },
                    onNext = { sendAction(RadioWatchService.ACTION_NOTIF_NEXT) },
                    onPrev = { sendAction(RadioWatchService.ACTION_NOTIF_PREV) },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val f = IntentFilter().apply {
            addAction(RadioWatchService.ACTION_PLAYBACK_UI)
            addAction(RadioWatchService.ACTION_TRACK_META)
            addAction(RadioWatchService.ACTION_STATUS_UI)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(uiReceiver, f, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(uiReceiver, f)
        }
        readPrefs()
    }

    override fun onStop() {
        try { unregisterReceiver(uiReceiver) } catch (_: Exception) {}
        super.onStop()
    }

    private fun readPrefs() {
        val p = getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE)
        stationName = p.getString(BluetoothAutoPlayPlugin.KEY_NAME, "Radio S O") ?: "Radio S O"
        trackTitle = p.getString(BluetoothAutoPlayPlugin.KEY_TRACK, "") ?: ""
        isPlaying = p.getBoolean(BluetoothAutoPlayPlugin.KEY_IS_PLAYING, false)
    }

    private fun askNotifyPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }
    }

    private fun playTest() {
        val url = "https://icecast.omroep.nl/radio2-bb-mp3"
        val name = "NPO Radio 2"
        stationName = name
        getSharedPreferences(BluetoothAutoPlayPlugin.PREFS, MODE_PRIVATE).edit()
            .putString(BluetoothAutoPlayPlugin.KEY_URL, url)
            .putString(BluetoothAutoPlayPlugin.KEY_NAME, name)
            .putBoolean(BluetoothAutoPlayPlugin.KEY_PLAY, true)
            .putString(
                BluetoothAutoPlayPlugin.KEY_QUEUE_URLS,
                "[\"$url\"]"
            )
            .putString(
                BluetoothAutoPlayPlugin.KEY_QUEUE_NAMES,
                "[\"$name\"]"
            )
            .putInt(BluetoothAutoPlayPlugin.KEY_QUEUE_INDEX, 0)
            .commit()
        val i = Intent(this, RadioWatchService::class.java)
        i.action = RadioWatchService.ACTION_PLAY_URL
        i.putExtra(RadioWatchService.EXTRA_URL, url)
        i.putExtra(RadioWatchService.EXTRA_NAME, name)
        startForegroundService(i)
        statusText = "start"
    }

    private fun sendAction(action: String) {
        val i = Intent(this, RadioWatchService::class.java)
        i.action = action
        startForegroundService(i)
    }
}

@Composable
fun MiniPlayer(
    name: String,
    track: String,
    playing: Boolean,
    status: String,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(name, style = MaterialTheme.typography.headlineSmall)
        Text(if (track.isBlank()) "—" else track, style = MaterialTheme.typography.bodyLarge)
        Text(status, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 24.dp)) {
            Button(onClick = onPrev) { Text("Prev") }
            if (playing) Button(onClick = onPause) { Text("Pause") }
            else Button(onClick = onPlay) { Text("Play") }
            Button(onClick = onNext) { Text("Next") }
        }
    }
}
