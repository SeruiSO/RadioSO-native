package com.seruiso.radio1;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;

public class RadioSoWidget extends AppWidgetProvider {

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        push(ctx, mgr, ids);
    }

    /** Оновити всі екземпляри з сервісу (play / pause / скіп / мета). */
    public static void refresh(Context ctx) {
        try {
            AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
            int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, RadioSoWidget.class));
            if (ids != null && ids.length > 0) push(ctx, mgr, ids);
        } catch (Exception ignored) {}
    }

    private static void push(Context ctx, AppWidgetManager mgr, int[] ids) {
        SharedPreferences sp = ctx.getSharedPreferences(
            BluetoothAutoPlayPlugin.PREFS, Context.MODE_PRIVATE);
        String name = sp.getString(BluetoothAutoPlayPlugin.KEY_NAME, "Radio S O");
        String track = sp.getString(BluetoothAutoPlayPlugin.KEY_TRACK, "");
        boolean playing = sp.getBoolean(BluetoothAutoPlayPlugin.KEY_IS_PLAYING, false)
            || sp.getBoolean(BluetoothAutoPlayPlugin.KEY_PLAY, false);
        if (name == null || name.isEmpty()) name = "Radio S O";
        if (track == null) track = "";

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (android.os.Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;

        for (int id : ids) {
            RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget_player);
            rv.setTextViewText(R.id.widget_name, name);
            rv.setTextViewText(R.id.widget_track, track.isEmpty() ? "—" : track);
            rv.setImageViewResource(R.id.widget_play,
                playing ? R.drawable.ic_notif_pause : R.drawable.ic_notif_play);

            Intent open = new Intent(ctx, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            rv.setOnClickPendingIntent(R.id.widget_root,
                PendingIntent.getActivity(ctx, 40, open, flags));
            rv.setOnClickPendingIntent(R.id.widget_art,
                PendingIntent.getActivity(ctx, 41, open, flags));

            rv.setOnClickPendingIntent(R.id.widget_prev, svc(ctx, RadioWatchService.ACTION_NOTIF_PREV, 42, flags));
            rv.setOnClickPendingIntent(R.id.widget_next, svc(ctx, RadioWatchService.ACTION_NOTIF_NEXT, 43, flags));
            String playAct = playing ? RadioWatchService.ACTION_NOTIF_PAUSE : RadioWatchService.ACTION_NOTIF_PLAY;
            rv.setOnClickPendingIntent(R.id.widget_play, svc(ctx, playAct, 44, flags));

            mgr.updateAppWidget(id, rv);
        }
    }

    private static PendingIntent svc(Context ctx, String action, int req, int flags) {
        Intent i = new Intent(ctx, RadioWatchService.class).setAction(action);
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            return PendingIntent.getForegroundService(ctx, req, i, flags);
        }
        return PendingIntent.getService(ctx, req, i, flags);
    }
}
