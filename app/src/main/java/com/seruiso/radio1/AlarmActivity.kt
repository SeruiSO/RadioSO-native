package com.seruiso.radio1

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable

class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD,
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        val name = intent.getStringExtra(RadioWatchService.EXTRA_NAME)
            ?: RadioAlarm.itemById(this, intent.getLongExtra(RadioAlarm.EXTRA_ID, 0L))?.name
            ?: ""
        val id = intent.getLongExtra(RadioAlarm.EXTRA_ID, 0L)
        setContent {
            AlarmBanner(
                station = name,
                acc = Color(ThemeStore.get(this).accent),
                onDismiss = { finish() },
                onSnooze = {
                    RadioAlarm.postponeMinutes(this, 5)
                    finish()
                },
            )
        }
    }
}

@Composable
private fun AlarmBanner(
    station: String,
    acc: Color,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0B0D)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
        ) {
            Text(
                "RADIO SO",
                color = Color.White,
                fontSize = 42.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 6.sp,
                textAlign = TextAlign.Center,
            )
            Text(
                "ALARM",
                color = acc,
                fontSize = 56.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 10.sp,
                textAlign = TextAlign.Center,
            )
            if (station.isNotBlank()) {
                Text(
                    station,
                    color = Color(0xFFCCCCCC),
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "5 хв",
                    color = Color.White,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .background(Color(0xFF2A2A30), RoundedCornerShape(16.dp))
                        .clickable { onSnooze() }
                        .padding(vertical = 16.dp),
                )
                Text(
                    "OK",
                    color = Color.Black,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .background(acc, RoundedCornerShape(16.dp))
                        .clickable { onDismiss() }
                        .padding(vertical = 16.dp),
                )
            }
        }
    }
}
