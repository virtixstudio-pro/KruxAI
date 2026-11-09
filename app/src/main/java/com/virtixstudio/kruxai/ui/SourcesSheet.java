package com.virtixstudio.kruxai.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.virtixstudio.kruxai.models.SearchResult;

import java.util.ArrayList;
import java.util.List;

/** Feuille « Sources » façon ChatGPT : la liste des pages consultées, un appui ouvre la page. */
public final class SourcesSheet {

    private SourcesSheet() { }

    public static void show(Context ctx, List<SearchResult> sources) {
        if (sources == null || sources.isEmpty()) return;
        final float d = ctx.getResources().getDisplayMetrics().density;
        final int bg = KruxTheme.get(ctx, "bg"), text = KruxTheme.get(ctx, "text"), text2 = KruxTheme.get(ctx, "text2");
        final int card = KruxTheme.get(ctx, "input");

        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(Math.round(16 * d), Math.round(14 * d), Math.round(16 * d), Math.round(24 * d));

        TextView title = new TextView(ctx);
        title.setText("Sources · " + sources.size());
        title.setTextColor(text);
        title.setTextSize(17);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(0, 0, 0, Math.round(10 * d));
        col.addView(title);

        for (final SearchResult s : sources) {
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(Math.round(12 * d), Math.round(10 * d), Math.round(12 * d), Math.round(10 * d));
            GradientDrawable g = new GradientDrawable();
            g.setColor(card);
            g.setCornerRadius(14 * d);
            row.setBackground(g);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = Math.round(8 * d);
            row.setLayoutParams(lp);

            SourceStackView icon = new SourceStackView(ctx);
            List<String> one = new ArrayList<>();
            one.add(s.getDomain());
            icon.setDomains(one, 1);
            icon.setRingColor(card);
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(-2, -2);
            ip.rightMargin = Math.round(10 * d);
            ip.topMargin = Math.round(2 * d);
            row.addView(icon, ip);

            LinearLayout info = new LinearLayout(ctx);
            info.setOrientation(LinearLayout.VERTICAL);
            row.addView(info, new LinearLayout.LayoutParams(0, -2, 1f));

            TextView dom = new TextView(ctx);
            dom.setText(s.getDomain() == null ? "web" : s.getDomain());
            dom.setTextColor(text2);
            dom.setTextSize(11);
            info.addView(dom);

            TextView ti = new TextView(ctx);
            ti.setText(s.getTitle() == null || s.getTitle().isEmpty() ? "Source Web" : s.getTitle());
            ti.setTextColor(text);
            ti.setTextSize(14);
            ti.setMaxLines(2);
            ti.setEllipsize(android.text.TextUtils.TruncateAt.END);
            ti.setTypeface(null, android.graphics.Typeface.BOLD);
            info.addView(ti);

            if (s.getSnippet() != null && !s.getSnippet().trim().isEmpty()) {
                TextView sn = new TextView(ctx);
                sn.setText(s.getSnippet().trim());
                sn.setTextColor(text2);
                sn.setTextSize(12);
                sn.setMaxLines(2);
                sn.setEllipsize(android.text.TextUtils.TruncateAt.END);
                info.addView(sn);
            }

            row.setOnClickListener(v -> {
                try {
                    String u = s.getUrl();
                    if (u != null && (u.startsWith("https://") || u.startsWith("http://"))) {
                        ctx.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(u)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    }
                } catch (Exception ignored) { }
            });
            col.addView(row);
        }

        ScrollView sv = new ScrollView(ctx);
        sv.setBackgroundColor(Color.TRANSPARENT);
        sv.addView(col);
        BottomSheetDialog dialog = new BottomSheetDialog(ctx);
        dialog.setContentView(sv);
        dialog.show();
        KruxTheme.skin(dialog, ctx);
    }
}
