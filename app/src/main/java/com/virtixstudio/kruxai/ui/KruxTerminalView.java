package com.virtixstudio.kruxai.ui;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayDeque;

/**
 * Petit terminal de KRUX : montre en direct ce qu'il fait (recherches, fichiers écrits).
 * Petit en vertical, grand en horizontal. Les lignes s'écrivent lettre par lettre, sans saccade.
 */
public class KruxTerminalView extends LinearLayout {

    public static final int CMD = 0, OK = 1, INFO = 2, ERR = 3, FIX = 4;
    private static final int MAX_CHARS = 12000;

    private static final class Line {
        final String text; final int kind;
        Line(String t, int k) { text = t; kind = k; }
    }

    private final TextView title;
    private final TextView body;
    private final ScrollView scroll;
    private final SpannableStringBuilder buffer = new SpannableStringBuilder();
    private final ArrayDeque<Line> queue = new ArrayDeque<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Line current;
    private int typed;
    private boolean typing;
    private int accent = 0xFFA855F7, text = 0xFFE5E7EB, dim = 0xFF8B8FA3, okColor = 0xFF34D399, errColor = 0xFFF87171, fixColor = 0xFFFBBF24;

    public KruxTerminalView(Context c) { this(c, null); }

    public KruxTerminalView(Context c, AttributeSet a) {
        super(c, a);
        setOrientation(VERTICAL);
        final float d = c.getResources().getDisplayMetrics().density;

        LinearLayout bar = new LinearLayout(c);
        bar.setOrientation(HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Math.round(10 * d), Math.round(6 * d), Math.round(6 * d), Math.round(4 * d));
        for (int col : new int[]{0xFFF87171, 0xFFFBBF24, 0xFF34D399}) {
            View dot = new View(c);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(col);
            dot.setBackground(g);
            LayoutParams lp = new LayoutParams(Math.round(8 * d), Math.round(8 * d));
            lp.rightMargin = Math.round(5 * d);
            bar.addView(dot, lp);
        }
        title = new TextView(c);
        title.setText("krux — terminal");
        title.setTextSize(11);
        title.setTypeface(Typeface.MONOSPACE);
        title.setPadding(Math.round(6 * d), 0, 0, 0);
        bar.addView(title, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView close = new TextView(c);
        close.setText("✕");
        close.setTextSize(14);
        close.setPadding(Math.round(12 * d), Math.round(4 * d), Math.round(12 * d), Math.round(4 * d));
        close.setOnClickListener(v -> hide());
        bar.addView(close);
        addView(bar);

        scroll = new ScrollView(c);
        scroll.setVerticalScrollBarEnabled(false);
        body = new TextView(c);
        body.setTypeface(Typeface.MONOSPACE);
        body.setTextSize(11.5f);
        body.setLineSpacing(0, 1.12f);
        body.setPadding(Math.round(12 * d), Math.round(2 * d), Math.round(12 * d), Math.round(10 * d));
        scroll.addView(body);
        addView(scroll, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setVisibility(GONE);
        applyColors(0xFF0A0812, 0xFFA855F7, 0xFFE5E7EB);
        applyOrientation();
    }

    /** Couleurs du thème : fond, accent (invite « $ »), texte. */
    public void applyColors(int bg, int accentColor, int textColor) {
        accent = accentColor;
        text = textColor;
        dim = (textColor & 0x00FFFFFF) | 0x99000000;
        // fond un peu plus sombre que le thème, comme un vrai terminal
        int r = (int) (Color.red(bg) * 0.55f), g = (int) (Color.green(bg) * 0.55f), b = (int) (Color.blue(bg) * 0.55f);
        GradientDrawable box = new GradientDrawable();
        box.setColor(Color.rgb(r, g, b));
        box.setCornerRadius(12 * getResources().getDisplayMetrics().density);
        box.setStroke(Math.round(getResources().getDisplayMetrics().density), (accentColor & 0x00FFFFFF) | 0x55000000);
        setBackground(box);
        title.setTextColor(dim);
        body.setTextColor(text);
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            if (v instanceof LinearLayout) {
                LinearLayout bar = (LinearLayout) v;
                View last = bar.getChildAt(bar.getChildCount() - 1);
                if (last instanceof TextView) ((TextView) last).setTextColor(dim);
            }
        }
    }

    /** Petit en vertical (environ 130 dp), grand en horizontal (environ la moitié de l'écran). */
    public void applyOrientation() {
        float d = getResources().getDisplayMetrics().density;
        boolean land = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        int h = land ? Math.round(getResources().getDisplayMetrics().heightPixels * 0.5f) : Math.round(130 * d);
        ViewGroup.LayoutParams lp = getLayoutParams();
        if (lp != null) { lp.height = h; setLayoutParams(lp); }
        else setMinimumHeight(h);
    }

    public void show() {
        applyOrientation();
        if (getVisibility() != VISIBLE) {
            setAlpha(0f);
            setVisibility(VISIBLE);
            animate().alpha(1f).setDuration(180).start();
        }
    }

    public void hide() {
        if (getVisibility() != VISIBLE) return;
        animate().alpha(0f).setDuration(160).withEndAction(() -> { if (getAlpha() < 0.5f) { setVisibility(GONE); setAlpha(1f); } }).start();
    }

    private final Runnable hideRunnable = this::hide;

    /** Se referme tout seul après ce délai, sauf si une nouvelle ligne arrive entre-temps. */
    public void hideLater(long ms) { handler.removeCallbacks(hideRunnable); handler.postDelayed(hideRunnable, ms); }

    public void clear() {
        queue.clear();
        current = null;
        typing = false;
        buffer.clear();
        body.setText(buffer);
    }

    /** Ajoute une ligne ; elle s'écrit progressivement. */
    public void println(String line, int kind) {
        if (line == null) return;
        handler.removeCallbacks(hideRunnable);
        animate().cancel();
        setAlpha(1f);
        show();
        queue.add(new Line(line, kind));
        if (!typing) { typing = true; handler.post(step); }
    }

    private int colorOf(int kind) {
        switch (kind) {
            case OK: return okColor;
            case ERR: return errColor;
            case FIX: return fixColor;
            case INFO: return dim;
            default: return text;
        }
    }

    private final Runnable step = new Runnable() {
        @Override public void run() {
            if (current == null) {
                current = queue.poll();
                typed = 0;
                if (current == null) { typing = false; return; }
                String prefix = current.kind == CMD ? "$ " : current.kind == OK ? "✓ " : current.kind == FIX ? "↻ " : current.kind == ERR ? "✗ " : "  ";
                int start = buffer.length();
                buffer.append(prefix);
                buffer.setSpan(new ForegroundColorSpan(current.kind == CMD ? accent : colorOf(current.kind)), start, buffer.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            // plus la file est longue, plus on écrit vite : jamais de retard visible
            int chunk = Math.max(2, 2 + queue.size() * 3);
            int end = Math.min(current.text.length(), typed + chunk);
            int start = buffer.length();
            buffer.append(current.text, typed, end);
            buffer.setSpan(new ForegroundColorSpan(colorOf(current.kind)), start, buffer.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            typed = end;
            if (typed >= current.text.length()) { buffer.append('\n'); current = null; }
            if (buffer.length() > MAX_CHARS) buffer.delete(0, buffer.length() - MAX_CHARS + 2000);
            body.setText(buffer);
            scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
            handler.postDelayed(this, 16);
        }
    };

    @Override protected void onDetachedFromWindow() { handler.removeCallbacksAndMessages(null); super.onDetachedFromWindow(); }
}
