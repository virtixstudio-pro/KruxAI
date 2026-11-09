package com.virtixstudio.kruxai.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * Pastilles rondes qui se chevauchent (une par site, avec son initiale), comme le résumé de sources de ChatGPT.
 * Aucun réseau : stable et instantané.
 */
public class SourceStackView extends View {

    private static final int[] PALETTE = {0xFF6366F1, 0xFF0EA5E9, 0xFF10B981, 0xFFF59E0B, 0xFFEF4444, 0xFFA855F7, 0xFFEC4899, 0xFF14B8A6};

    private final List<String> domains = new ArrayList<>();
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int ringColor = 0xFF120B24;

    public SourceStackView(Context c) { super(c); init(); }
    public SourceStackView(Context c, AttributeSet a) { super(c, a); init(); }

    private void init() {
        text.setColor(0xFFFFFFFF);
        text.setTextAlign(Paint.Align.CENTER);
        text.setFakeBoldText(true);
        ring.setStyle(Paint.Style.STROKE);
    }

    /** Couleur du liseré : celle du fond de la pastille, pour que les cercles se détachent. */
    public void setRingColor(int color) { ringColor = color; invalidate(); }

    public void setDomains(List<String> list, int max) {
        domains.clear();
        if (list != null) for (String d : list) {
            if (domains.size() >= max) break;
            domains.add(d == null || d.isEmpty() ? "web" : d);
        }
        requestLayout();
        invalidate();
    }

    private float d() { return getResources().getDisplayMetrics().density; }

    @Override
    protected void onMeasure(int w, int h) {
        float size = 22 * d();
        int n = Math.max(1, domains.size());
        int width = Math.round(size + (n - 1) * size * 0.62f);
        setMeasuredDimension(width, Math.round(size));
    }

    @Override
    protected void onDraw(Canvas c) {
        float size = 22 * d(), r = size / 2f;
        text.setTextSize(size * 0.5f);
        ring.setStrokeWidth(2f * d());
        ring.setColor(ringColor);
        for (int i = 0; i < domains.size(); i++) {
            String dom = domains.get(i);
            float cx = r + i * size * 0.62f;
            fill.setColor(PALETTE[Math.abs(dom.hashCode()) % PALETTE.length]);
            c.drawCircle(cx, r, r - d(), fill);
            c.drawCircle(cx, r, r - d(), ring);
            String letter = dom.substring(0, 1).toUpperCase();
            c.drawText(letter, cx, r - (text.descent() + text.ascent()) / 2f, text);
        }
    }
}
