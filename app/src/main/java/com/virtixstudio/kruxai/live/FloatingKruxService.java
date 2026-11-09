package com.virtixstudio.kruxai.live;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import com.virtixstudio.kruxai.R;
import com.virtixstudio.kruxai.ui.MainActivity;

/**
 * Visage de KRUX transparent qui flotte par-dessus les autres apps. Se déplace au doigt,
 * un appui ouvre KRUX. Une notification permet de le masquer à tout moment.
 * Demande l'autorisation « Afficher par-dessus les autres apps » (gérée par FloatingKrux.toggle).
 */
public class FloatingKruxService extends Service {

    static final String ACTION_STOP = "com.virtixstudio.kruxai.FLOAT_STOP";
    private static final String CHANNEL = "krux_floating";
    private static volatile boolean running = false;

    private WindowManager wm;
    private KruxLiveView face;
    private WindowManager.LayoutParams lp;

    public static boolean isRunning() { return running; }

    /** Démarre ou arrête le visage ; retourne false si l'autorisation d'affichage manque (l'écran de réglage s'ouvre). */
    public static boolean toggle(Context c) {
        if (running) {
            c.stopService(new Intent(c, FloatingKruxService.class));
            return true;
        }
        if (!Settings.canDrawOverlays(c)) {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:" + c.getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
            return false;
        }
        Intent i = new Intent(c, FloatingKruxService.class);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
        return true;
    }

    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (face != null) return START_NOT_STICKY;
        startForegroundCompat();
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return START_NOT_STICKY; }
        addFace();
        running = true;
        return START_NOT_STICKY;
    }

    private void startForegroundCompat() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && nm != null) {
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Visage de KRUX", NotificationManager.IMPORTANCE_LOW));
        }
        int imm = Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0;
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), imm);
        PendingIntent stop = PendingIntent.getService(this, 1,
                new Intent(this, FloatingKruxService.class).setAction(ACTION_STOP), imm);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        b.setSmallIcon(R.drawable.ic_live).setContentTitle("KRUX est sur ton écran")
                .setContentText("Touche pour ouvrir. Masquer avec le bouton.").setContentIntent(open).setOngoing(true)
                .addAction(new Notification.Action.Builder((android.graphics.drawable.Icon) null, "Masquer", stop).build());
        if (Build.VERSION.SDK_INT >= 34) startForeground(7, b.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(7, b.build());
    }

    private void addFace() {
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        face = new KruxLiveView(this);
        face.setTransparent(true);
        face.setMaxFps(30);
        float d = getResources().getDisplayMetrics().density;
        int size = Math.round(140 * d);
        int type = Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        lp = new WindowManager.LayoutParams(size, size, type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = getResources().getDisplayMetrics().widthPixels - size - Math.round(8 * d);
        lp.y = Math.round(220 * d);
        face.setOnTouchListener(new View.OnTouchListener() {
            float downX, downY; int startX, startY; boolean moved;
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = e.getRawX(); downY = e.getRawY(); startX = lp.x; startY = lp.y; moved = false;
                        face.motion().flash(LiveMotion.Mood.SURPRISED, 0.6f);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = e.getRawX() - downX, dy = e.getRawY() - downY;
                        if (Math.abs(dx) + Math.abs(dy) > 12 * d) moved = true;
                        if (moved) { lp.x = Math.round(startX + dx); lp.y = Math.round(startY + dy); wm.updateViewLayout(face, lp); }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!moved) {
                            face.motion().flash(LiveMotion.Mood.HAPPY, 1.2f);
                            Intent i = new Intent(FloatingKruxService.this, MainActivity.class);
                            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                            startActivity(i);
                        }
                        return true;
                }
                return false;
            }
        });
        try {
            wm.addView(face, lp);
        } catch (Exception e) {
            face = null;
            stopSelf();
        }
    }

    @Override
    public void onDestroy() {
        running = false;
        try { if (face != null && wm != null) wm.removeView(face); } catch (Exception ignored) { }
        face = null;
        super.onDestroy();
    }
}
