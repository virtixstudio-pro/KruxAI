package com.virtixstudio.kruxai.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;

/** Petit éditeur de photo : pivoter, dessiner, entourer, flèche. Tout est dessiné sur la photo avant l'envoi à l'IA. */
final class PhotoEditorDialog {
    interface Done { void done(Bitmap edited); }

    private PhotoEditorDialog() { }

    private static final int[] COLORS = {0xFF22C55E, 0xFFEF4444, 0xFFFACC15, 0xFFFFFFFF};

    private static final class Stroke {
        int tool; int color;
        final ArrayList<float[]> pts = new ArrayList<>();
    }

    static void show(final Activity act, Bitmap src, final Done cb) {
        final DrawView dv = new DrawView(act, src);
        final Dialog d = new Dialog(act, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0B0B10);

        LinearLayout top = row(act);
        TextView cancel = btn(act, "Annuler");
        cancel.setOnClickListener(v -> d.dismiss());
        TextView title = btn(act, "Éditeur photo");
        title.setGravity(Gravity.CENTER);
        TextView ok = btn(act, "OK");
        ok.setTextColor(0xFF22D3EE);
        ok.setOnClickListener(v -> { Bitmap r = dv.result(); d.dismiss(); if (cb != null) cb.done(r); });
        top.addView(cancel);
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        top.addView(ok);
        root.addView(top);

        root.addView(dv, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout tools = row(act);
        final TextView[] toolBtns = new TextView[3];
        String[] names = {"✎ Trait", "◯ Cercle", "➚ Flèche"};
        for (int i = 0; i < 3; i++) {
            final int t = i;
            TextView b = btn(act, names[i]);
            toolBtns[i] = b;
            b.setOnClickListener(v -> { dv.tool = t; for (int j = 0; j < 3; j++) toolBtns[j].setBackground(chip(act, j == t)); });
            b.setBackground(chip(act, i == 1));
            tools.addView(b, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        dv.tool = 1;
        root.addView(tools);

        LinearLayout bottom = row(act);
        for (final int c : COLORS) {
            View dot = new View(act);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(c);
            g.setStroke(3, 0x66FFFFFF);
            dot.setBackground(g);
            dot.setOnClickListener(v -> dv.color = c);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(act, 34), dp(act, 34));
            lp.setMargins(dp(act, 6), dp(act, 4), dp(act, 6), dp(act, 4));
            bottom.addView(dot, lp);
        }
        TextView undo = btn(act, "↶");
        undo.setOnClickListener(v -> dv.undo());
        TextView rot = btn(act, "⟳ Pivoter");
        rot.setOnClickListener(v -> dv.rotate());
        TextView clr = btn(act, "✕");
        clr.setOnClickListener(v -> dv.clear());
        bottom.addView(undo);
        bottom.addView(rot, new LinearLayout.LayoutParams(0, -2, 1f));
        bottom.addView(clr);
        root.addView(bottom);

        d.setContentView(root, new ViewGroup.LayoutParams(-1, -1));
        d.show();
    }

    private static LinearLayout row(Context c) {
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(c, 8), dp(c, 6), dp(c, 8), dp(c, 6));
        return r;
    }

    private static TextView btn(Context c, String t) {
        TextView b = new TextView(c);
        b.setText(t);
        b.setTextColor(Color.WHITE);
        b.setTextSize(15);
        b.setPadding(dp(c, 12), dp(c, 8), dp(c, 12), dp(c, 8));
        b.setGravity(Gravity.CENTER);
        return b;
    }

    private static GradientDrawable chip(Context c, boolean on) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(c, 14));
        g.setColor(on ? 0xFF3B3B55 : 0x00000000);
        return g;
    }

    private static int dp(Context c, int v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }

    /** Zone de dessin : la photo est affichée en entier, les traits sont mémorisés en coordonnées de photo. */
    private static final class DrawView extends View {
        Bitmap base;
        int tool = 1, color = COLORS[0];
        final ArrayList<Stroke> strokes = new ArrayList<>();
        Stroke cur;
        final Matrix fit = new Matrix(), inv = new Matrix();
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        DrawView(Context c, Bitmap src) {
            super(c);
            int max = 1600;
            float k = Math.min(1f, (float) max / Math.max(src.getWidth(), src.getHeight()));
            Bitmap b = k < 1f ? Bitmap.createScaledBitmap(src, Math.round(src.getWidth() * k), Math.round(src.getHeight() * k), true) : src;
            base = b.copy(Bitmap.Config.ARGB_8888, true);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
        }

        float width() { return Math.max(6f, Math.max(base.getWidth(), base.getHeight()) / 110f); }

        void computeFit() {
            RectF s = new RectF(0, 0, base.getWidth(), base.getHeight());
            RectF d = new RectF(dp(getContext(), 6), dp(getContext(), 6), getWidth() - dp(getContext(), 6), getHeight() - dp(getContext(), 6));
            fit.setRectToRect(s, d, Matrix.ScaleToFit.CENTER);
            fit.invert(inv);
        }

        void drawStroke(Canvas c, Stroke s) {
            p.setColor(s.color);
            p.setStrokeWidth(width());
            int n = s.pts.size();
            if (n < 2) return;
            float[] a = s.pts.get(0), z = s.pts.get(n - 1);
            if (s.tool == 0) {
                Path path = new Path();
                path.moveTo(a[0], a[1]);
                for (int i = 1; i < n; i++) path.lineTo(s.pts.get(i)[0], s.pts.get(i)[1]);
                c.drawPath(path, p);
            } else if (s.tool == 1) {
                c.drawOval(new RectF(Math.min(a[0], z[0]), Math.min(a[1], z[1]), Math.max(a[0], z[0]), Math.max(a[1], z[1])), p);
            } else {
                c.drawLine(a[0], a[1], z[0], z[1], p);
                double ang = Math.atan2(z[1] - a[1], z[0] - a[0]);
                float h = width() * 4.5f;
                for (int sgn = -1; sgn <= 1; sgn += 2) {
                    double t = ang + Math.PI - sgn * 0.5;
                    c.drawLine(z[0], z[1], z[0] + (float) Math.cos(t) * h, z[1] + (float) Math.sin(t) * h, p);
                }
            }
        }

        @Override protected void onDraw(Canvas c) {
            computeFit();
            c.save();
            c.concat(fit);
            c.drawBitmap(base, 0, 0, null);
            for (Stroke s : strokes) drawStroke(c, s);
            if (cur != null) drawStroke(c, cur);
            c.restore();
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            float[] q = {e.getX(), e.getY()};
            inv.mapPoints(q);
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    cur = new Stroke(); cur.tool = tool; cur.color = color;
                    cur.pts.add(q);
                    getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (cur != null) { if (tool == 0) cur.pts.add(q); else { if (cur.pts.size() > 1) cur.pts.remove(1); cur.pts.add(q); } invalidate(); }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (cur != null) { if (cur.pts.size() > 1) strokes.add(cur); cur = null; invalidate(); }
                    return true;
                default: return true;
            }
        }

        void undo() { if (!strokes.isEmpty()) { strokes.remove(strokes.size() - 1); invalidate(); } }
        void clear() { strokes.clear(); invalidate(); }

        void bake() {
            Canvas c = new Canvas(base);
            for (Stroke s : strokes) drawStroke(c, s);
            strokes.clear();
        }

        void rotate() {
            bake();
            Matrix m = new Matrix();
            m.postRotate(90);
            Bitmap r = Bitmap.createBitmap(base, 0, 0, base.getWidth(), base.getHeight(), m, true);
            base = r.copy(Bitmap.Config.ARGB_8888, true);
            invalidate();
        }

        Bitmap result() { bake(); return base; }
    }
}
