package com.seruiso.radio1;

/** Pure reconnect timing (stack 6). */
public final class ReconnectPolicy {
    private ReconnectPolicy() {}
    public static final long FAST_MS = 60_000L;
    public static final long WINDOW_MS = 5 * 60_000L;

    public static long nextDelayMs(long elapsedSinceWindowStart, int attempt) {
        if (elapsedSinceWindowStart < FAST_MS) {
            return Math.min(3000L, 700L + attempt * 400L);
        }
        return 10_000L;
    }

    public static boolean windowExpired(long elapsedSinceWindowStart) {
        return elapsedSinceWindowStart > WINDOW_MS;
    }
}
