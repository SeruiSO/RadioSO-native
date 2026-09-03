package com.seruiso.radio1;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import androidx.core.app.NotificationCompat;
import androidx.media3.session.MediaSession;

/** Media notification builder (stack 6). */
public final class RadioNotificationFactory {
    private RadioNotificationFactory() {}

    public static Notification build(
            Context ctx, String channelId, Class<?> serviceClass, Class<?> activityClass,
            boolean playing, String currentName, String lastTrackTitle, Bitmap stationArt,
            MediaSession mediaSession, boolean btWatchEnabled) {
        Intent open = new Intent(ctx, activityClass);
        open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(
            ctx, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        PendingIntent pausePi = servicePi(ctx, serviceClass, RadioWatchService.ACTION_NOTIF_PAUSE, 1);
        PendingIntent playPi = servicePi(ctx, serviceClass, RadioWatchService.ACTION_NOTIF_PLAY, 2);
        PendingIntent prevPi = servicePi(ctx, serviceClass, RadioWatchService.ACTION_NOTIF_PREV, 3);
        PendingIntent nextPi = servicePi(ctx, serviceClass, RadioWatchService.ACTION_NOTIF_NEXT, 4);

        String name = currentName != null ? currentName : "Radio S O";
        String body;
        if (playing) {
            body = (lastTrackTitle != null && !lastTrackTitle.isEmpty())
                ? name + " · " + lastTrackTitle : "Грає: " + name;
        } else {
            body = btWatchEnabled ? "На паузі · BT стеження увімк" : "На паузі · BT стеження вимк";
        }

        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, channelId)
            .setContentTitle(name)
            .setContentText(body)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setColor(0xFF121212);
        if (stationArt != null) b.setLargeIcon(stationArt);

        androidx.media.app.NotificationCompat.MediaStyle style =
            new androidx.media.app.NotificationCompat.MediaStyle()
                .setShowActionsInCompactView(0, 1, 2);
        try {
            if (mediaSession != null) style.setMediaSession(mediaSession.getSessionCompatToken());
        } catch (Exception ignored) {}
        b.setStyle(style);
        b.addAction(android.R.drawable.ic_media_previous, "Назад", prevPi);
        if (playing) b.addAction(android.R.drawable.ic_media_pause, "Пауза", pausePi);
        else b.addAction(android.R.drawable.ic_media_play, "Грати", playPi);
        b.addAction(android.R.drawable.ic_media_next, "Далі", nextPi);
        return b.build();
    }

    private static PendingIntent servicePi(Context ctx, Class<?> serviceClass, String action, int req) {
        Intent i = new Intent(ctx, serviceClass);
        i.setAction(action);
        return PendingIntent.getService(
            ctx, req, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
