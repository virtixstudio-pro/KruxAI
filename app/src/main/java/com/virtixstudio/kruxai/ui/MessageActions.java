package com.virtixstudio.kruxai.ui;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.Selection;
import android.text.Spannable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.virtixstudio.kruxai.R;

/**
 * Menu d'appui long sur une réponse de l'IA : Copier, Tout sélectionner, Partager.
 * Arrondi et aux couleurs de la personnalisation (KruxTheme).
 */
public final class MessageActions {

    private MessageActions() {}

    /** Branche l'appui long sur une vue ; le texte copié est celui passé ici. */
    public static void attach(View v, String plainText) {
        final float[] last = {0f, 0f};
        v.setOnTouchListener((view, e) -> {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) { last[0] = e.getRawX(); last[1] = e.getRawY(); }
            return false;
        });
        v.setOnLongClickListener(view -> {
            show(view, plainText, (int) last[0], (int) last[1]);
            return true;
        });
    }

    private static Activity activity(Context c) {
        while (c instanceof ContextWrapper) {
            if (c instanceof Activity) return (Activity) c;
            c = ((ContextWrapper) c).getBaseContext();
        }
        return null;
    }

    private static int dp(Context c, int v) {
        return (int) (v * c.getResources().getDisplayMetrics().density);
    }

    private static GradientDrawable rounded(int color, float radiusPx) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radiusPx);
        return g;
    }

    public static void show(View anchor, String text, int rawX, int rawY) {
        final Context ctx = anchor.getContext();
        if (text == null || text.trim().isEmpty()) return;

        LinearLayout menu = new LinearLayout(ctx);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setPadding(dp(ctx, 6), dp(ctx, 6), dp(ctx, 6), dp(ctx, 6));
        menu.setBackground(rounded(KruxTheme.get(ctx, "input"), dp(ctx, Math.max(14, KruxTheme.radiusDp(ctx)))));

        final int width = dp(ctx, 230);
        final PopupWindow popup = new PopupWindow(menu, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setElevation(dp(ctx, 10));
        popup.setOutsideTouchable(true);

        addItem(menu, R.drawable.ic_copy, "Copier", () -> { popup.dismiss(); copy(ctx, text); });
        addItem(menu, R.drawable.ic_select_text, "Tout sélectionner", () -> { popup.dismiss(); selectSheet(ctx, text); });
        addItem(menu, R.drawable.ic_share, "Partager", () -> { popup.dismiss(); share(ctx, text); });

        menu.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int screenW = ctx.getResources().getDisplayMetrics().widthPixels;
        int screenH = ctx.getResources().getDisplayMetrics().heightPixels;
        int x = Math.max(dp(ctx, 8), Math.min(rawX - width / 2, screenW - width - dp(ctx, 8)));
        int y = rawY + dp(ctx, 12);
        if (y + menu.getMeasuredHeight() > screenH - dp(ctx, 24)) y = rawY - menu.getMeasuredHeight() - dp(ctx, 12);
        popup.showAtLocation(anchor.getRootView(), Gravity.NO_GRAVITY, x, Math.max(dp(ctx, 24), y));
    }

    private static void addItem(LinearLayout menu, int icon, String label, Runnable action) {
        Context ctx = menu.getContext();
        int textColor = KruxTheme.get(ctx, "text");
        TextView item = new TextView(ctx);
        item.setText(label);
        item.setTextColor(textColor);
        item.setTextSize(15);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(dp(ctx, 14), dp(ctx, 12), dp(ctx, 14), dp(ctx, 12));
        item.setCompoundDrawablePadding(dp(ctx, 12));
        Drawable d = ctx.getDrawable(icon);
        if (d != null) {
            d = d.mutate();
            d.setTint(textColor);
            d.setBounds(0, 0, dp(ctx, 20), dp(ctx, 20));
            item.setCompoundDrawablesRelative(d, null, null, null);
        }
        item.setOnClickListener(v -> action.run());
        menu.addView(item, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private static void copy(Context ctx, String text) {
        ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Krux", text));
        Toast.makeText(ctx, "Copié", Toast.LENGTH_SHORT).show();
    }

    private static void share(Context ctx, String text) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, text);
        Intent chooser = Intent.createChooser(i, null);
        if (!(ctx instanceof Activity)) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(chooser);
    }

    /** Feuille du bas : le texte entier est sélectionné, on peut ajuster la sélection ou tout copier. */
    private static void selectSheet(Context ctx, String text) {
        Activity act = activity(ctx);
        if (act == null) return;
        final BottomSheetDialog dialog = new BottomSheetDialog(act);
        float r = dp(ctx, 24);

        LinearLayout root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(ctx, 18), dp(ctx, 18), dp(ctx, 18), dp(ctx, 18));
        GradientDrawable bg = rounded(KruxTheme.get(ctx, "bg"), 0);
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        root.setBackground(bg);

        TextView title = new TextView(act);
        title.setText("Sélectionner le texte");
        title.setTextColor(KruxTheme.get(ctx, "text"));
        title.setTextSize(16);
        title.setPadding(0, 0, 0, dp(ctx, 10));
        root.addView(title);

        ScrollView scroll = new ScrollView(act);
        TextView body = new TextView(act);
        body.setText(text, TextView.BufferType.SPANNABLE);
        body.setTextColor(KruxTheme.get(ctx, "text"));
        body.setTextSize(15);
        body.setLineSpacing(0, 1.16f);
        body.setTextIsSelectable(true);
        scroll.addView(body);
        int maxH = (int) (ctx.getResources().getDisplayMetrics().heightPixels * 0.55f);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        scroll.post(() -> {
            if (scroll.getHeight() > maxH) {
                ViewGroup.LayoutParams lp = scroll.getLayoutParams();
                lp.height = maxH;
                scroll.setLayoutParams(lp);
            }
        });

        TextView copyAll = new TextView(act);
        copyAll.setText("Tout copier");
        copyAll.setGravity(Gravity.CENTER);
        copyAll.setTextSize(15);
        copyAll.setTextColor(KruxTheme.get(ctx, "buttontext"));
        copyAll.setBackground(rounded(KruxTheme.get(ctx, "button"), dp(ctx, 100)));
        copyAll.setPadding(dp(ctx, 20), dp(ctx, 12), dp(ctx, 20), dp(ctx, 12));
        copyAll.setOnClickListener(v -> { copy(ctx, text); dialog.dismiss(); });
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.topMargin = dp(ctx, 14);
        root.addView(copyAll, bp);

        dialog.setContentView(root);
        dialog.setOnShowListener(d -> {
            View sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet != null) sheet.setBackgroundColor(Color.TRANSPARENT);
            body.requestFocus();
            if (body.getText() instanceof Spannable) Selection.selectAll((Spannable) body.getText());
        });
        dialog.show();
    }
}
