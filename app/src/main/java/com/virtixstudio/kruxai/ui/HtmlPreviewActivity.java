package com.virtixstudio.kruxai.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Aperçu plein écran d'une page HTML écrite par KRUX : la page tourne vraiment (JavaScript compris),
 * sans accès aux fichiers de l'app ni à ses données. Les liens externes s'ouvrent dans le navigateur intégré.
 *
 * Deuxième mode : openUrl(), navigateur intégré pour consulter un site (http/https seulement) sans quitter l'app.
 */
public class HtmlPreviewActivity extends AppCompatActivity {

    private static String pendingHtml = "";
    private static String pendingTitle = "";
    private static String pendingUrl = "";

    /** Ouvre un site dans le navigateur intégré (http/https uniquement). */
    public static void openUrl(Context c, String url) {
        if (url == null) return;
        String u = url.trim();
        if (!(u.startsWith("https://") || u.startsWith("http://"))) return;
        pendingUrl = u;
        pendingHtml = "";
        pendingTitle = "";
        Intent i = new Intent(c, HtmlPreviewActivity.class);
        if (!(c instanceof android.app.Activity)) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(i);
    }

    /** Ouvre l'aperçu. Le HTML passe par une variable (un Intent est limité à environ 1 Mo). */
    public static void open(Context c, String title, String html) {
        pendingHtml = html == null ? "" : html;
        pendingTitle = title == null ? "" : title;
        Intent i = new Intent(c, HtmlPreviewActivity.class);
        if (!(c instanceof android.app.Activity)) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        c.startActivity(i);
    }

    /** Vrai si le texte ressemble à une page web complète ou à un fragment affichable. */
    public static boolean looksLikeHtml(String language, String code) {
        if (code == null) return false;
        String l = language == null ? "" : language.trim().toLowerCase(java.util.Locale.ROOT);
        if (l.equals("html") || l.equals("htm")) return true;
        String h = code.trim().toLowerCase(java.util.Locale.ROOT);
        return h.startsWith("<!doctype html") || h.startsWith("<html");
    }

    private WebView web;
    private String html = "";
    private String url = "";
    private boolean browser = false;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        html = pendingHtml;
        url = pendingUrl;
        pendingUrl = "";
        browser = !url.isEmpty();
        final float d = getResources().getDisplayMetrics().density;
        final int bg = KruxTheme.get(this, "bg"), tx = KruxTheme.get(this, "text"), ac = KruxTheme.get(this, "accent"),
                btn = KruxTheme.get(this, "input");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding((int) (10 * d), (int) (6 * d), (int) (10 * d), (int) (6 * d));

        TextView close = pill("Fermer", tx, btn, d);
        close.setOnClickListener(v -> finish());
        TextView title = new TextView(this);
        title.setText(browser ? hostOf(url) : (pendingTitle.isEmpty() ? "Aperçu" : pendingTitle));
        title.setTextColor(tx);
        title.setTextSize(15);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        title.setGravity(Gravity.CENTER);
        TextView reload = pill(browser ? "↻" : "Recharger", ac, btn, d);
        TextView chrome = browser ? pill("Chrome", tx, btn, d) : null;
        bar.addView(close);
        bar.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        bar.addView(reload);
        if (chrome != null) {
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-2, -2);
            cp.leftMargin = (int) (6 * d);
            bar.addView(chrome, cp);
        }
        root.addView(bar, new LinearLayout.LayoutParams(-1, (int) (56 * d)));

        final ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgressTintList(android.content.res.ColorStateList.valueOf(ac));
        root.addView(progress, new LinearLayout.LayoutParams(-1, (int) (3 * d)));

        FrameLayout holder = new FrameLayout(this);
        web = new WebView(this);
        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setAllowFileAccess(false);
        ws.setAllowContentAccess(false);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        ws.setSupportMultipleWindows(false);
        ws.setLoadWithOverviewMode(true);
        ws.setUseWideViewPort(true);
        web.setBackgroundColor(Color.WHITE);
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView v, int p) {
                progress.setProgress(p);
                progress.setVisibility(p >= 100 ? View.GONE : View.VISIBLE);
            }
            @Override public void onReceivedTitle(WebView v, String t) {
                if (browser && t != null && !t.trim().isEmpty()) title.setText(t.trim());
            }
        });
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                String sch = u.getScheme() == null ? "" : u.getScheme();
                if (browser) {
                    // navigateur : on suit les liens http(s) dans la page, jamais de schémas étranges (intent:, file:…)
                    return !(sch.equals("http") || sch.equals("https"));
                }
                if (sch.equals("http") || sch.equals("https")) {
                    LinkOpener.open(HtmlPreviewActivity.this, u.toString());
                }
                return true; // aperçu : jamais de navigation dans la page, ni de schémas étranges
            }
        });
        holder.addView(web, new FrameLayout.LayoutParams(-1, -1));
        root.addView(holder, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets s = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(s.left, s.top, s.right, s.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        reload.setOnClickListener(v -> { if (browser) web.reload(); else load(); });
        if (chrome != null) {
            chrome.setOnClickListener(v -> {
                String cur = web != null && web.getUrl() != null ? web.getUrl() : url;
                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(cur))); } catch (Exception ignored) { }
            });
        }
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (browser && web != null && web.canGoBack()) web.goBack(); else finish();
            }
        });
        load();
    }

    private static String hostOf(String u) {
        try { String h = Uri.parse(u).getHost(); return h == null ? u : h; } catch (Exception e) { return u; }
    }

    private void load() {
        if (browser) { web.loadUrl(url); return; }
        String page = html;
        if (!page.toLowerCase(java.util.Locale.ROOT).contains("viewport")) {
            page = "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" + page;
        }
        web.loadDataWithBaseURL("https://apercu.krux.local/", page, "text/html", "UTF-8", null);
    }

    private TextView pill(String label, int color, int fill, float d) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(13);
        t.setTextColor(color);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER);
        t.setPadding((int) (14 * d), 0, (int) (14 * d), 0);
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(KruxTheme.radiusDp(this) * d);
        t.setBackground(g);
        t.setMinHeight((int) (38 * d));
        t.setClickable(true);
        return t;
    }

    @Override
    protected void onDestroy() {
        if (web != null) { web.stopLoading(); web.destroy(); web = null; }
        super.onDestroy();
    }
}
