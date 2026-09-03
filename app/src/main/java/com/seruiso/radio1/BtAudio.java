package com.seruiso.radio1;

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;

public final class BtAudio {
    private BtAudio() {}

    public static boolean hasRoute(Context ctx) {
        try {
            AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
            if (am == null) return false;
            if (am.isBluetoothA2dpOn() || am.isBluetoothScoOn()) return true;
            AudioDeviceInfo[] devs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
            for (AudioDeviceInfo d : devs) {
                int ty = d.getType();
                if (ty == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                        || ty == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                        || ty == 26
                        || ty == 27
                        || ty == AudioDeviceInfo.TYPE_HEARING_AID) return true;
            }
        } catch (Exception ignored) {}
        return false;
    }
}
