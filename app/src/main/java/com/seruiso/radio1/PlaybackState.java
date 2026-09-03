package com.seruiso.radio1;

import androidx.media3.common.Player;

/** Coarse playback state for UI / logs (stack 1). */
public enum PlaybackState {
    IDLE, BUFFERING, PLAYING, PAUSED, ENDED, ERROR;

    public static PlaybackState fromPlayer(Player player) {
        if (player == null) return IDLE;
        try {
            int st = player.getPlaybackState();
            if (st == Player.STATE_BUFFERING) return BUFFERING;
            if (st == Player.STATE_ENDED) return ENDED;
            if (st == Player.STATE_IDLE) return IDLE;
            if (player.isPlaying()) return PLAYING;
            return PAUSED;
        } catch (Exception e) {
            return ERROR;
        }
    }
}
