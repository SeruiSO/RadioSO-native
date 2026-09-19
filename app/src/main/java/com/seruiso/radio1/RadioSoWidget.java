package com.seruiso.radio1;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.widget.RemoteViews;

import androidx.core.content.ContextCompat;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RadioSoWidget extends AppWidgetProvider {

    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile String lastFavUrl = "";
    private static volatile Bitmap lastFavBmp = null;

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        push(ctx, mgr, ids);
    }

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
        String fav = sp.getString(BluetoothAutoPlayPlugin.KEY_FAVICON, "");
        boolean playing = sp.getBoolean(BluetoothAutoPlayPlugin.KEY_IS_PLAYING, false)
            || sp.getBoolean(BluetoothAutoPlayPlugin.KEY_PLAY, false);
        if (name == null || name.isEmpty()) name = "Radio S O";
        if (track == null) track = "";
        if (fav == null) fav = "";

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (android.os.Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;

        Bitmap art = null;
        if (!fav.isEmpty() && fav.equals(lastFavUrl) && lastFavBmp != null && !lastFavBmp.isRecycled()) {
            art = lastFavBmp;
        }

        // play/pause icon dark on gold button
        Bitmap playBmp = tintedIcon(ctx,
            playing ? R.drawable.ic_notif_pause : R.drawable.ic_notif_play,
            0xFF1A1A1A);
        Bitmap prevBmp = tintedIcon(ctx, R.drawable.ic_notif_prev, 0xFFFFFFFF);
        Bitmap nextBmp = tintedIcon(ctx, R.drawable.ic_notif_next, 0xFFFFFFFF);

        for (int id : ids) {
            RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget_player);
            rv.setTextViewText(R.id.widget_name, name);
            rv.setTextViewText(R.id.widget_track, track.isEmpty() ? "—" : track);

            if (playBmp != null) rv.setImageViewBitmap(R.id.widget_play, playBmp);
            else rv.setImageViewResource(R.id.widget_play,
                playing ? R.drawable.ic_notif_pause : R.drawable.ic_notif_play);
            if (prevBmp != null) rv.setImageViewBitmap(R.id.widget_prev, prevBmp);
            if (nextBmp != null) rv.setImageViewBitmap(R.id.widget_next, nextBmp);

            if (art != null) rv.setImageViewBitmap(R.id.widget_art, art);
            else rv.setImageViewResource(R.id.widget_art, R.mipmap.ic_launcher);

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

        if (!fav.isEmpty() && !fav.equals(lastFavUrl)) {
            final String urlLoad = fav;
            EXEC.execute(() -> {
                Bitmap bmp = loadRounded(urlLoad, 176);
                if (bmp != null) {
                    lastFavUrl = urlLoad;
                    lastFavBmp = bmp;
                    MAIN.post(() -> refresh(ctx));
                }
            });
        }
    }

    private static Bitmap tintedIcon(Context ctx, int resId, int color) {
        try {
            Drawable d = ContextCompat.getDrawable(ctx, resId);
            if (d == null) return null;
            int s = 72;
            Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            d.setBounds(0, 0, s, s);
            d.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
            d.draw(c);
            d.setColorFilter(null);
            return b;
        } catch (Exception e) {
            return null;
        }
    }

    private static Bitmap loadRounded(String urlStr, int sizePx) {
        HttpURLConnection c = null;
        try {
            URL url = new URL(urlStr);
            c = (HttpURLConnection) url.openConnection();
            c.setConnectTimeout(4000);
            c.setReadTimeout(4000);
            c.setInstanceFollowRedirects(true);
            try (InputStream in = c.getInputStream()) {
                Bitmap raw = BitmapFactory.decodeStream(in);
                if (raw == null) return null;
                Bitmap scaled = Bitmap.createScaledBitmap(raw, sizePx, sizePx, true);
                if (scaled != raw) raw.recycle();
                return roundCorners(scaled, sizePx * 0.18f);
            }
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static Bitmap roundCorners(Bitmap src, float radius) {
        int w = src.getWidth();
        int h = src.getHeight();
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Rect rect = new Rect(0, 0, w, h);
        RectF rectF = new RectF(rect);
        canvas.drawRoundRect(rectF, radius, radius, paint);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(src, rect, rect, paint);
        if (out != src) src.recycle();
        return out;
    }

    private static PendingIntent svc(Context ctx, String action, int req, int flags) {
        Intent i = new Intent(ctx, RadioWatchService.class).setAction(action);
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            return PendingIntent.getForegroundService(ctx, req, i, flags);
        }
        return PendingIntent.getService(ctx, req, i, flags);
    }
}
