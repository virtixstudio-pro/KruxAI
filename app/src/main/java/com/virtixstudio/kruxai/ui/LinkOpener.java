package com.virtixstudio.kruxai.ui;

import android.content.Context;
import android.widget.Toast;

/** Point d'entrée unique pour ouvrir un lien depuis l'app : il s'ouvre dans le navigateur intégré. */
public final class LinkOpener {

    private LinkOpener() {}

    public static void open(Context ctx, String url) {
        if (ctx == null || url == null) return;
        String u = url.trim();
        if (!(u.startsWith("https://") || u.startsWith("http://"))) return;
        try {
            HtmlPreviewActivity.openUrl(ctx, u);
        } catch (Exception e) {
            Toast.makeText(ctx, "Impossible d'ouvrir ce lien", Toast.LENGTH_SHORT).show();
        }
    }
}
