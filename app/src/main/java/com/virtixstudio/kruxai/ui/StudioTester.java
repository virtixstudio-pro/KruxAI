package com.virtixstudio.kruxai.ui;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Vrai test d'une app HTML : elle est chargée dans un WebView caché, les erreurs JavaScript sont captées,
 * les boutons sont touchés pour de bon, puis on relève ce qui s'affiche. Rien n'est simulé par l'IA.
 */
public final class StudioTester {
    private StudioTester() { }

    public interface Callback { void done(Report r); }

    public static final class Report {
        public boolean ok;
        public String text = "";
        public final List<String> errors = new ArrayList<>();
    }

    private static final String CAPTURE = "<script>window.__kerr=[];"
        + "window.addEventListener('error',function(e){__kerr.push((e.message||'erreur')+' (ligne '+(e.lineno||'?')+')')});"
        + "window.addEventListener('unhandledrejection',function(e){__kerr.push('Promesse rejetée : '+((e.reason&&e.reason.message)||e.reason))});</script>";

    private static final String PROBE = "(function(){var r={clicks:0};"
        + "var els=document.querySelectorAll('button,[role=button],input[type=button],input[type=submit],.btn,.pad,.key');"
        + "for(var i=0;i<els.length&&i<10;i++){try{els[i].click();r.clicks++;}catch(x){__kerr.push(String(x));}}"
        + "r.n=document.getElementsByTagName('*').length;"
        + "r.c=document.querySelectorAll('canvas').length;"
        + "r.t=(document.body?document.body.innerText:'').replace(/\\s+/g,' ').slice(0,140);"
        + "return JSON.stringify(r);})()";

    public static void run(final Activity act, final String html, final Callback cb) {
        act.runOnUiThread(() -> {
            final Handler h = new Handler(Looper.getMainLooper());
            final boolean[] finished = {false};
            final List<String> consoleErrors = new ArrayList<>();
            final WebView w = new WebView(act);
            WebSettings s = w.getSettings();
            s.setJavaScriptEnabled(true);
            s.setDomStorageEnabled(true);
            s.setMediaPlaybackRequiresUserGesture(false);
            s.setAllowFileAccess(false);
            s.setAllowContentAccess(false);
            w.setWebChromeClient(new WebChromeClient() {
                @Override
                public boolean onConsoleMessage(ConsoleMessage m) {
                    if (m.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                        consoleErrors.add(m.message() + " (ligne " + m.lineNumber() + ")");
                    }
                    return true;
                }
            });
            final Runnable timeout = () -> {
                if (finished[0]) return;
                finished[0] = true;
                Report r = new Report();
                r.errors.add("L'app ne s'est pas chargée en 10 secondes.");
                r.text = "Test réel : échec. L'app ne s'est pas chargée en 10 secondes.";
                w.destroy();
                cb.done(r);
            };
            h.postDelayed(timeout, 10000);
            w.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageFinished(WebView view, String url) {
                    h.postDelayed(() -> {
                        if (finished[0]) return;
                        view.evaluateJavascript(PROBE, v1 -> h.postDelayed(() -> {
                            if (finished[0]) return;
                            view.evaluateJavascript("JSON.stringify(window.__kerr||[])", v2 -> {
                                if (finished[0]) return;
                                finished[0] = true;
                                h.removeCallbacks(timeout);
                                Report r = build(unquote(v1), unquote(v2), consoleErrors);
                                view.destroy();
                                cb.done(r);
                            });
                        }, 700));
                    }, 1200);
                }
            });
            w.loadDataWithBaseURL("https://krux.local/", inject(html), "text/html", "utf-8", null);
        });
    }

    private static String inject(String html) {
        Matcher m = Pattern.compile("<head[^>]*>", Pattern.CASE_INSENSITIVE).matcher(html);
        if (m.find()) return html.substring(0, m.end()) + CAPTURE + html.substring(m.end());
        return CAPTURE + html;
    }

    private static String unquote(String v) {
        try {
            if (v != null && v.startsWith("\"")) return String.valueOf(new JSONTokener(v).nextValue());
        } catch (Exception ignored) { }
        return v == null ? "" : v;
    }

    private static Report build(String probeJson, String errsJson, List<String> consoleErrors) {
        Report r = new Report();
        int clicks = 0, nodes = 0, canvas = 0;
        String visible = "";
        try {
            JSONObject o = new JSONObject(probeJson);
            clicks = o.optInt("clicks");
            nodes = o.optInt("n");
            canvas = o.optInt("c");
            visible = o.optString("t");
        } catch (Exception ignored) { }
        try {
            JSONArray a = new JSONArray(errsJson);
            for (int i = 0; i < a.length() && r.errors.size() < 6; i++) {
                String e = a.optString(i);
                if (!r.errors.contains(e)) r.errors.add(e);
            }
        } catch (Exception ignored) { }
        for (String e : consoleErrors) if (r.errors.size() < 6 && !r.errors.contains(e)) r.errors.add(e);
        boolean blank = nodes < 6 || (visible.trim().isEmpty() && canvas == 0);
        if (blank) r.errors.add("L'écran est vide : rien ne s'affiche.");
        r.ok = r.errors.isEmpty();
        StringBuilder sb = new StringBuilder("Test réel : ");
        sb.append(r.ok ? "réussi" : "échec").append(". ")
          .append(nodes).append(" éléments, ").append(clicks).append(" bouton(s) touché(s), ")
          .append(r.errors.size()).append(" erreur(s).");
        for (String e : r.errors) sb.append("\n• ").append(e);
        if (r.ok && !visible.isEmpty()) sb.append("\nÀ l'écran : ").append(visible);
        r.text = sb.toString();
        return r;
    }
}
