package com.seruiso.radio1;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Build;
import android.util.Log;
import android.view.KeyEvent;

/** Media Play when process dead — wake our service only. */
public class RadioMediaButtonReceiver extends BroadcastReceiver {
    private static final String TAG = "RadioWatch";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;
        if (!Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction())) return;
        KeyEvent ke = null;
        try {
            ke = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
        } catch (Exception ignored) {}
        if (ke == null || ke.getAction() != KeyEvent.ACTION_DOWN || ke.getRepeatCount() > 0) return;
        int code = ke.getKeyCode();
        if (code != KeyEvent.KEYCODE_MEDIA_PLAY && code != KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) return;
        try {
            AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                int mode = am.getMode();
                if (mode == AudioManager.MODE_IN_CALL
                        || mode == AudioManager.MODE_IN_COMMUNICATION
                        || mode == AudioManager.MODE_RINGTONE) {
                    Log.i(TAG, "MediaButton ignored — voice call");
                    return;
                }
            }
        } catch (Exception ignored) {}
        Intent i = new Intent(context, RadioWatchService.class);
        i.setAction(RadioWatchService.ACTION_PLAY);
        try {
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i);
            else context.startService(i);
            Log.i(TAG, "MediaButton → ACTION_PLAY");
        } catch (Exception e) {
            Log.w(TAG, "MediaButton start denied: " + e.getClass().getSimpleName());
        }
    }
}
