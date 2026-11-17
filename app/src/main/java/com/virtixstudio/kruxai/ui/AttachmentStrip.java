package com.virtixstudio.kruxai.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import com.virtixstudio.kruxai.R;

import java.util.List;
import java.util.Locale;

/**
 * Pièces jointes au-dessus du champ de saisie : de petites cartes arrondies avec une croix en coin,
 * comme dans Claude. Un fichier s'affiche avec son nom et son type ; une image avec sa vignette.
 * Les couleurs suivent la personnalisation (KruxTheme).
 */
public final class AttachmentStrip extends HorizontalScrollView {

    public interface Listener { void onRemove(Item item); }

    public static final class Item {
        public final String name;
        public final String detail;     // ex. « TXT · 12 Ko » (peut être vide)
        public final Bitmap thumb;      // null pour un fichier
        public final Object tag;        // libre : l'appelant retrouve ainsi sa pièce jointe

        public Item(String name, String detail, Bitmap thumb, Object tag) {
            this.name = name == null ? "" : name;
            this.detail = detail == null ? "" : detail;
            this.thumb = thumb;
            this.tag = tag;
        }
    }

    private final LinearLayout row;
    private Listener listener;
    private final float d;

    public AttachmentStrip(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        setHorizontalScrollBarEnabled(false);
        setClipToPadding(false);
        setClipChildren(false);
        setPadding((int) (4 * d), (int) (4 * d), (int) (8 * d), (int) (6 * d));
        row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        addView(row, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    public void setListener(Listener l) { listener = l; }

    public void clear() {
        row.removeAllViews();
        setVisibility(GONE);
    }

    public void setItems(List<Item> items) {
        row.removeAllViews();
        if (items == null || items.isEmpty()) {
            setVisibility(GONE);
            return;
        }
        for (Item it : items) row.addView(card(it));
        setVisibility(VISIBLE);
        post(() -> fullScroll(View.FOCUS_RIGHT));
    }

    // ------------------------------------------------------------------ une carte

    private View card(final Item it) {
        Context c = getContext();
        int input = KruxTheme.get(c, "input"), text = KruxTheme.get(c, "text"),
                text2 = KruxTheme.get(c, "text2"), bg = KruxTheme.get(c, "bg");
        float radius = 16 * d;

        FrameLayout wrap = new FrameLayout(c);
        wrap.setClipChildren(false);
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wlp.rightMargin = (int) (6 * d);
        wrap.setLayoutParams(wlp);

        View body;
        if (it.thumb != null) {
            ImageView img = new ImageView(c);
            img.setImageBitmap(it.thumb);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            img.setContentDescription(it.name);
            img.setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), 16 * d); }
            });
            img.setClipToOutline(true);
            body = img;
            FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams((int) (76 * d), (int) (76 * d));
            ip.topMargin = (int) (8 * d);
            ip.rightMargin = (int) (8 * d);
            wrap.addView(body, ip);
        } else {
            LinearLayout file = new LinearLayout(c);
            file.setOrientation(LinearLayout.VERTICAL);
            file.setPadding((int) (12 * d), (int) (10 * d), (int) (12 * d), (int) (10 * d));
            GradientDrawable g = new GradientDrawable();
            g.setColor(input);
            g.setCornerRadius(radius);
            g.setStroke(Math.max(1, (int) d), ColorUtils.setAlphaComponent(text, 45));
            file.setBackground(g);

            TextView name = new TextView(c);
            name.setText(it.name);
            name.setTextColor(text);
            name.setTextSize(13);
            name.setTypeface(Typeface.DEFAULT_BOLD);
            name.setMaxLines(2);
            name.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            file.addView(name);

            TextView detail = new TextView(c);
            detail.setText(it.detail.isEmpty() ? extensionOf(it.name) : it.detail);
            detail.setTextColor(text2);
            detail.setTextSize(11);
            detail.setSingleLine(true);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-2, -2);
            dlp.topMargin = (int) (6 * d);
            file.addView(detail, dlp);

            body = file;
            FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams((int) (140 * d), (int) (76 * d));
            fp.topMargin = (int) (8 * d);
            fp.rightMargin = (int) (8 * d);
            wrap.addView(body, fp);
        }

        // croix en coin : pastille ronde de la couleur du texte, croix de la couleur du fond
        ImageView x = new ImageView(c);
        int pad = (int) (6 * d);
        x.setPadding(pad, pad, pad, pad);
        x.setImageResource(R.drawable.ic_close_small);
        x.setColorFilter(bg);
        GradientDrawable xb = new GradientDrawable();
        xb.setShape(GradientDrawable.OVAL);
        xb.setColor(text);
        x.setBackground(xb);
        x.setContentDescription("Retirer la pièce jointe");
        x.setOnClickListener(v -> { if (listener != null) listener.onRemove(it); });
        FrameLayout.LayoutParams xp = new FrameLayout.LayoutParams((int) (24 * d), (int) (24 * d), Gravity.TOP | Gravity.END);
        wrap.addView(x, xp);
        return wrap;
    }

    /** « TXT » pour « notes.txt » ; vide s'il n'y a pas d'extension. */
    public static String extensionOf(String name) {
        int i = name == null ? -1 : name.lastIndexOf('.');
        return (i < 0 || i == name.length() - 1) ? "" : name.substring(i + 1).toUpperCase(Locale.ROOT);
    }

    /** « TXT · 12 Ko » à partir du nom et du nombre de caractères. */
    public static String detailFor(String name, int chars) {
        String ext = extensionOf(name);
        String size = chars < 1000 ? chars + " car." : (chars / 1000) + " Ko";
        return ext.isEmpty() ? size : ext + " · " + size;
    }
}
