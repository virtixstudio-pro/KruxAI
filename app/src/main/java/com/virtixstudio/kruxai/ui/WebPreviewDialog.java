package com.virtixstudio.kruxai.ui;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Aperçu d'une app React (mode web) : page assets/preview/index.html dans une WebView, avec retouches locales
 * (textes, couleurs) sans appel IA. La page ne reçoit aucun pont Java : Java lui donne les fichiers avec
 * evaluateJavascript, puis relit le résultat de la même façon quand l'utilisateur valide.
 */
final class WebPreviewDialog {

    private static final String PAGE = "file:///android_asset/preview/index.html";

    private WebPreviewDialog() { }

    /** À appeler hors du thread principal : bloque jusqu'au choix. Retourne les fichiers (retouchés ou non), ou null si annulé. */
    static Map<String, String> show(final Activity act, final Map<String, String> files) {
        final String json = new JSONObject(files).toString();
        final AtomicReference<Map<String, String>> result = new AtomicReference<>(null);
        final CountDownLatch decided = new CountDownLatch(1);
        final Dialog[] shown = {null};

        act.runOnUiThread(() -> {
            final int bg = KruxTheme.get(act, "bg");
            final int accent = KruxTheme.get(act, "accent");
            final int tx = KruxTheme.get(act, "text");
            final int tx2 = KruxTheme.get(act, "text2");
            final Dialog dlg = new Dialog(act);
            dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);
            dlg.setCancelable(false);

            LinearLayout root = new LinearLayout(act);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(dp(act, 16), dp(act, 16), dp(act, 16), dp(act, 14));
            GradientDrawable sheet = new GradientDrawable();
            sheet.setColor(bg);
            sheet.setCornerRadius(dp(act, 24));
            root.setBackground(sheet);

            TextView title = new TextView(act);
            title.setText("Aperçu de ton application");
            title.setTextColor(tx);
            title.setTextSize(18);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            root.addView(title);
            TextView hint = new TextView(act);
            hint.setText("Teste l'app. « Retoucher » change un texte, « Couleurs » la palette : sans IA, donc gratuit.");
            hint.setTextColor(tx2);
            hint.setTextSize(13);
            hint.setPadding(0, dp(act, 4), 0, dp(act, 12));
            root.addView(hint);

            // Page locale : JavaScript permis, mais réseau, fichiers externes et stockage bloqués, aucun pont Java.
            final WebView web = new WebView(act);
            WebSettings ws = web.getSettings();
            ws.setJavaScriptEnabled(true);
            ws.setJavaScriptCanOpenWindowsAutomatically(false);
            ws.setSupportMultipleWindows(false);
            ws.setBlockNetworkLoads(true);
            ws.setAllowFileAccess(false);
            ws.setAllowContentAccess(false);
            ws.setDomStorageEnabled(false);
            ws.setDatabaseEnabled(false);
            ws.setGeolocationEnabled(false);
            final boolean[] loaded = {false};
            web.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                    return true; // aucune navigation en dehors de l'aperçu
                }

                @Override
                public void onPageFinished(WebView v, String url) {
                    if (loaded[0]) return;
                    loaded[0] = true;
                    v.evaluateJavascript("KruxPreview.load(" + JSONObject.quote(json) + ")", null);
                }
            });
            web.setBackgroundColor(Color.WHITE);
            web.loadUrl(PAGE);

            final FrameLayout frame = new FrameLayout(act);
            GradientDrawable fg = new GradientDrawable();
            fg.setColor(Color.WHITE);
            fg.setCornerRadius(dp(act, 20));
            fg.setStroke(dp(act, 2), (accent & 0x00FFFFFF) | 0x99000000);
            frame.setBackground(fg);
            frame.setClipToOutline(true);
            frame.addView(web, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            root.addView(frame, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

            LinearLayout row = new LinearLayout(act);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.END);
            row.setPadding(0, dp(act, 12), 0, 0);
            TextView cancel = button(act, "Annuler", tx2, Color.TRANSPARENT);
            int onAccent = androidx.core.graphics.ColorUtils.calculateLuminance(accent) > 0.5 ? Color.BLACK : Color.WHITE;
            TextView ok = button(act, "Créer l'app", onAccent, accent);
            cancel.setOnClickListener(v -> { decided.countDown(); dlg.dismiss(); });
            ok.setOnClickListener(v -> web.evaluateJavascript("KruxPreview.result()", value -> {
                result.set(parse(value, files));
                decided.countDown();
                dlg.dismiss();
            }));
            row.addView(cancel);
            LinearLayout.LayoutParams okLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            okLp.leftMargin = dp(act, 8);
            row.addView(ok, okLp);
            root.addView(row);

            dlg.setContentView(root);
            dlg.setOnDismissListener(d -> {
                web.stopLoading();
                frame.removeAllViews();
                web.destroy();
                decided.countDown(); // sécurité : jamais d'attente infinie
            });
            shown[0] = dlg;
            dlg.show();
            if (dlg.getWindow() != null) {
                DisplayMetrics dm = act.getResources().getDisplayMetrics();
                dlg.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                dlg.getWindow().setLayout(dm.widthPixels - dp(act, 20), (int) (dm.heightPixels * 0.88f));
                dlg.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            }
        });

        try {
            if (!decided.await(20, TimeUnit.MINUTES)) {
                act.runOnUiThread(() -> { if (shown[0] != null) shown[0].dismiss(); });
                return null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
        return result.get();
    }

    /** Résultat de KruxPreview.result() : texte JSON entouré de guillemets (evaluateJavascript). Si illisible : fichiers d'origine. */
    private static Map<String, String> parse(String value, Map<String, String> fallback) {
        try {
            String inner = new JSONArray("[" + value + "]").getString(0);
            JSONObject f = new JSONObject(inner).getJSONObject("files");
            Map<String, String> out = new LinkedHashMap<>();
            JSONArray names = f.names();
            for (int i = 0; names != null && i < names.length(); i++) out.put(names.getString(i), f.getString(names.getString(i)));
            return out.isEmpty() ? new LinkedHashMap<>(fallback) : out;
        } catch (Exception e) {
            return new LinkedHashMap<>(fallback);
        }
    }

    private static int dp(Activity a, int v) { return Math.round(v * a.getResources().getDisplayMetrics().density); }

    private static TextView button(Activity a, String label, int textColor, int fill) {
        TextView b = new TextView(a);
        b.setText(label);
        b.setTextSize(15);
        b.setTypeface(null, android.graphics.Typeface.BOLD);
        b.setTextColor(textColor);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(a, 20), dp(a, 12), dp(a, 20), dp(a, 12));
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(a, 14));
        b.setBackground(g);
        b.setClickable(true);
        return b;
    }
}
