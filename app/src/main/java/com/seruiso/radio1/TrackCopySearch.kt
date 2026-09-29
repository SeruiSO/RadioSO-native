package com.seruiso.radio1

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

fun openTrackCopySearch(ctx: Context, raw: String) {
    val q = raw.trim()
    if (q.isBlank()) return
    try {
        val uri = Uri.parse("https://www.google.com/search?q=" + Uri.encode(q))
        ctx.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {}
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackLongBox(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    Box(
        modifier.then(
            modifier.combinedClickable(
                onClick = onClick,
                onLongClick = { menu = true },
            ),
        ),
    ) {
        content()
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(ctx.getString(R.string.alarm_search_copy)) },
                onClick = {
                    try {
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("track", text.trim()))
                    } catch (_: Exception) {}
                    menu = false
                },
            )
            DropdownMenuItem(
                text = { Text(ctx.getString(R.string.alarm_search_google)) },
                onClick = {
                    try {
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("track", text.trim()))
                    } catch (_: Exception) {}
                    openTrackCopySearch(ctx, text)
                    menu = false
                },
            )
        }
    }
}
