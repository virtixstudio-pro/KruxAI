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
    private boolean editMode = false;

    /** JavaScript injecté dans l'aperçu (jamais dans le navigateur) : repère les textes, permet de les changer et d'ajouter des éléments. */
    private static final String KX_INIT =
        "(function(){if(window.__kx)return;var K=window.__kx={on:false,els:[],orig:[],ready:false};"
      + "K.mark=function(e){e.setAttribute('data-kx',K.els.indexOf(e));e.style.outline='1px dashed rgba(124,92,255,.9)';e.style.outlineOffset='2px';};"
      + "K.enable=function(){K.on=true;if(!K.ready){K.ready=true;[].slice.call(document.querySelectorAll('body *')).forEach(function(e){"
      + "if(e.children.length===0&&e.textContent.trim().length>0&&!/^(SCRIPT|STYLE)$/.test(e.tagName)){K.els.push(e);K.orig.push(e.textContent);}});}"
      + "K.els.forEach(K.mark);};"
      + "K.disable=function(){K.on=false;K.els.forEach(function(e){e.style.outline='';e.removeAttribute('data-kx');});};"
      + "K.text=function(i){return K.els[i]?K.els[i].textContent:'';};"
      + "K.set=function(i,t){if(K.els[i])K.els[i].textContent=t;};"
      + "K.add=function(kind,t){var b=kind==='b';var e=document.createElement(b?'button':'p');e.textContent=t;e.style.margin='12px';"
      + "if(b){e.style.cssText+=';padding:12px 18px;border:0;border-radius:12px;background:#7c5cff;color:#fff;font-size:15px;';}"
      + "document.body.appendChild(e);K.els.push(e);K.orig.push(null);if(K.on)K.mark(e);};"
      + "K.report=function(){var out=[];K.els.forEach(function(e,i){var o=K.orig[i],n=e.textContent.trim();"
      + "if(o===null){out.push(['add',e.tagName==='BUTTON'?'bouton':'texte',n]);}else if(o.trim()!==n){out.push(['edit',o.trim(),n]);}});return JSON.stringify(out);};"
      + "document.addEventListener('click',function(ev){if(!K.on)return;ev.preventDefault();ev.stopPropagation();"
      + "var t=ev.target.closest?ev.target.closest('[data-kx]'):null;if(t)console.log('KXEDIT:'+t.getAttribute('data-kx'));},true);})();";

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
            @Override public boolean onConsoleMessage(android.webkit.ConsoleMessage m) {
                String msg = m.message();
                if (!browser && msg != null && msg.startsWith("KXEDIT:")) {
                    try { editText(Integer.parseInt(msg.substring(7).trim())); } catch (Exception ignored) { }
                    return true;
                }
                return super.onConsoleMessage(m);
            }
            @Override public void onReceivedTitle(WebView v, String t) {
                if (browser && t != null && !t.trim().isEmpty()) title.setText(t.trim());
            }
        });
        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView v, String u) {
                if (!browser) v.evaluateJavascript(KX_INIT, null);
            }
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
        if (browser) {
            root.addView(holder, new LinearLayout.LayoutParams(-1, 0, 1f));
        } else {
            LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, 0, 1f);
            pp.setMargins((int) (10 * d), (int) (4 * d), (int) (10 * d), (int) (10 * d));
            root.addView(phoneFrame(holder, d), pp);
        }
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

    /** Mini-Android : coque sombre, barre d'état, écran arrondi, barre de navigation, tête de Krux au milieu à droite. */
    private View phoneFrame(FrameLayout screen, final float d) {
        final int ac = KruxTheme.get(this, "accent"), bg = KruxTheme.get(this, "bg");
        LinearLayout phone = new LinearLayout(this);
        phone.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable shell = new GradientDrawable();
        shell.setColor(0xFF0B0B0F);
        shell.setCornerRadius(28 * d);
        shell.setStroke((int) (2 * d), 0xFF2A2A33);
        phone.setBackground(shell);
        phone.setPadding((int) (8 * d), (int) (4 * d), (int) (8 * d), (int) (4 * d));

        LinearLayout status = new LinearLayout(this);
        status.setGravity(Gravity.CENTER_VERTICAL);
        status.setPadding((int) (10 * d), 0, (int) (10 * d), 0);
        status.addView(tiny("9:41"), new LinearLayout.LayoutParams(0, -2, 1f));
        status.addView(tiny("5G · 100 %"));
        phone.addView(status, new LinearLayout.LayoutParams(-1, (int) (22 * d)));

        screen.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View v, android.graphics.Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), 18 * d);
            }
        });
        screen.setClipToOutline(true);
        phone.addView(screen, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER);
        View tri = new View(this) {
            final android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            { p.setColor(0xFFB8B8C4); p.setStyle(android.graphics.Paint.Style.STROKE); p.setStrokeWidth(2 * d); p.setStrokeJoin(android.graphics.Paint.Join.ROUND); }
            @Override protected void onDraw(android.graphics.Canvas c) {
                android.graphics.Path path = new android.graphics.Path();
                float w = getWidth(), h = getHeight();
                path.moveTo(w * 0.85f, h * 0.1f); path.lineTo(w * 0.15f, h * 0.5f); path.lineTo(w * 0.85f, h * 0.9f); path.close();
                c.drawPath(path, p);
            }
        };
        nav.addView(tri, navLp(d));
        nav.addView(navShape(GradientDrawable.OVAL, d), navLp(d));
        nav.addView(navShape(GradientDrawable.RECTANGLE, d), navLp(d));
        phone.addView(nav, new LinearLayout.LayoutParams(-1, (int) (26 * d)));

        TextView head = new TextView(this);
        head.setText("K");
        head.setTextColor(bg);
        head.setTextSize(18);
        head.setTypeface(Typeface.DEFAULT_BOLD);
        head.setGravity(Gravity.CENTER);
        GradientDrawable hg = new GradientDrawable();
        hg.setShape(GradientDrawable.OVAL);
        hg.setColor(ac);
        head.setBackground(hg);
        head.setElevation(8 * d);
        FrameLayout.LayoutParams hlp = new FrameLayout.LayoutParams((int) (46 * d), (int) (46 * d), Gravity.END | Gravity.CENTER_VERTICAL);
        hlp.rightMargin = (int) (8 * d);
        screen.addView(head, hlp);
        head.setOnClickListener(v -> kruxMenu(v));
        return phone;
    }

    private TextView tiny(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(11);
        v.setTextColor(0xFFB8B8C4);
        return v;
    }

    private LinearLayout.LayoutParams navLp(float d) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams((int) (14 * d), (int) (14 * d));
        lp.setMargins((int) (26 * d), 0, (int) (26 * d), 0);
        return lp;
    }

    private View navShape(int shape, float d) {
        View v = new View(this);
        GradientDrawable g = new GradientDrawable();
        g.setShape(shape);
        if (shape == GradientDrawable.RECTANGLE) g.setCornerRadius(3 * d);
        g.setColor(Color.TRANSPARENT);
        g.setStroke((int) (2 * d), 0xFFB8B8C4);
        v.setBackground(g);
        return v;
    }

    private void kruxMenu(View anchor) {
        android.widget.PopupMenu pm = new android.widget.PopupMenu(this, anchor);
        pm.getMenu().add(0, 1, 0, "Aide");
        pm.getMenu().add(0, 2, 1, "Paramètres");
        pm.setOnMenuItemClickListener(it -> {
            if (it.getItemId() == 1) help(); else settings();
            return true;
        });
        pm.show();
    }

    private void help() {
        KruxTheme.skin(new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Aide")
                .setMessage("Touche la tête de Krux pour ouvrir ce menu.\n\n"
                        + "Dans Paramètres, choisis « Modifier textes et boutons », puis touche un texte ou un bouton pour le changer. "
                        + "Tu peux aussi ajouter un bouton ou un texte.\n\n"
                        + "« Modifier avec Krux » envoie tes changements dans la discussion : Krux les applique à l'application.")
                .setPositiveButton("Compris", null)
                .show(), this);
    }

    private void settings() {
        final String[] items = {
                "Modifier avec Krux",
                editMode ? "Terminer la modification" : "Modifier textes et boutons",
                "Ajouter un bouton",
                "Ajouter un texte",
                "Recharger l'aperçu",
                "Aperçu téléphone Krux"
        };
        KruxTheme.skin(new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Paramètres de l'aperçu")
                .setItems(items, (dd, which) -> {
                    if (which == 0) sendToKrux();
                    else if (which == 1) setEdit(!editMode);
                    else if (which == 2 || which == 3) addElement(which == 2);
                    else if (which == 5) {
                        java.util.Map<String, String> one = new java.util.LinkedHashMap<>();
                        one.put("index.html", html == null ? "" : html);
                        WebPreviewDialog.show(this, one);
                    }
                    else { setEdit(false); load(); }
                })
                .show(), this);
    }

    private void setEdit(boolean on) {
        editMode = on;
        if (web == null) return;
        web.evaluateJavascript(on ? "window.__kx&&window.__kx.enable()" : "window.__kx&&window.__kx.disable()", null);
        if (on) android.widget.Toast.makeText(this, "Touche un texte ou un bouton pour le modifier", android.widget.Toast.LENGTH_SHORT).show();
    }

    private static String jsString(String v) {
        try {
            Object o = new org.json.JSONTokener(v == null ? "null" : v).nextValue();
            return o instanceof String ? (String) o : "";
        } catch (Exception e) { return ""; }
    }

    private android.widget.EditText field(String text) {
        android.widget.EditText et = new android.widget.EditText(this);
        et.setText(text);
        et.setTextColor(KruxTheme.get(this, "text"));
        et.setSelection(et.getText().length());
        return et;
    }

    private void editText(final int index) {
        if (web == null) return;
        web.evaluateJavascript("window.__kx?window.__kx.text(" + index + "):''", v -> {
            final android.widget.EditText et = field(jsString(v));
            KruxTheme.skin(new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Modifier le texte")
                    .setView(et)
                    .setNegativeButton("Annuler", null)
                    .setPositiveButton("OK", (dd, w) -> {
                        if (web != null) web.evaluateJavascript("window.__kx.set(" + index + ","
                                + org.json.JSONObject.quote(et.getText().toString()) + ")", null);
                    })
                    .show(), this);
        });
    }

    private void addElement(final boolean button) {
        final android.widget.EditText et = field(button ? "Nouveau bouton" : "Nouveau texte");
        KruxTheme.skin(new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(button ? "Ajouter un bouton" : "Ajouter un texte")
                .setView(et)
                .setNegativeButton("Annuler", null)
                .setPositiveButton("Ajouter", (dd, w) -> {
                    String t = et.getText().toString().trim();
                    if (t.isEmpty() || web == null) return;
                    setEdit(true);
                    web.evaluateJavascript("window.__kx.add('" + (button ? "b" : "t") + "'," + org.json.JSONObject.quote(t) + ")", null);
                })
                .show(), this);
    }

    /** Résume les changements faits dans l'aperçu et les dépose dans le champ de saisie de la discussion. */
    private void sendToKrux() {
        if (web == null) return;
        web.evaluateJavascript("window.__kx?window.__kx.report():'[]'", v -> {
            StringBuilder sb = new StringBuilder();
            try {
                org.json.JSONArray a = new org.json.JSONArray(jsString(v));
                for (int k = 0; k < a.length(); k++) {
                    org.json.JSONArray r = a.getJSONArray(k);
                    if ("add".equals(r.getString(0))) sb.append("- Ajoute un ").append(r.getString(1)).append(" « ").append(r.getString(2)).append(" ».\n");
                    else sb.append("- Remplace le texte « ").append(r.getString(1)).append(" » par « ").append(r.getString(2)).append(" ».\n");
                }
            } catch (Exception ignored) { }
            if (sb.length() == 0) {
                android.widget.Toast.makeText(this, "Aucun changement à envoyer. Active d'abord « Modifier textes et boutons ».", android.widget.Toast.LENGTH_LONG).show();
                return;
            }
            Intent i = new Intent(this, MainActivity.class);
            i.putExtra("prefill_text", "Modifie mon application avec ces changements faits dans l'aperçu :\n" + sb);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(i);
            finish();
        });
    }

    private static String hostOf(String u) {
        try { String h = Uri.parse(u).getHost(); return h == null ? u : h; } catch (Exception e) { return u; }
    }

    private void load() {
        if (browser) { web.loadUrl(url); return; }
        editMode = false;
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
