package com.virtixstudio.kruxai.utils;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.virtixstudio.kruxai.models.ChatMessage;
import com.virtixstudio.kruxai.ui.KruxTheme;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Export de la discussion : texte, PDF, CSV, ou archive ZIP (PDF + CSV + texte + fichiers créés par Krux).
 * Chaque format peut être enregistré dans Téléchargements ou partagé.
 */
public final class ChatExporter {

    private ChatExporter() {}

    public enum Format {
        TXT("Texte", "Discussion lisible, avec mise en forme markdown", ".txt", "text/plain"),
        PDF("PDF", "Un document prêt à lire ou imprimer", ".pdf", "application/pdf"),
        CSV("CSV", "Un message par ligne, pour un tableur", ".csv", "text/csv"),
        ZIP("Archive ZIP", "PDF + CSV + texte + fichiers créés par Krux", ".zip", "application/zip");

        final String label, hint, ext, mime;
        Format(String label, String hint, String ext, String mime) {
            this.label = label; this.hint = hint; this.ext = ext; this.mime = mime;
        }
    }

    // ------------------------------------------------------------------ interface

    public static void showSheet(Activity a, List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            Toast.makeText(a, "Rien à exporter : la discussion est vide.", Toast.LENGTH_SHORT).show();
            return;
        }
        final float d = a.getResources().getDisplayMetrics().density;
        final int bg = KruxTheme.get(a, "bg"), tx = KruxTheme.get(a, "text"), tx2 = KruxTheme.get(a, "text2"),
                input = KruxTheme.get(a, "input"), btn = KruxTheme.get(a, "button"), btnTx = KruxTheme.get(a, "buttontext");
        final BottomSheetDialog dialog = new BottomSheetDialog(a);

        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding((int) (18 * d), (int) (18 * d), (int) (18 * d), (int) (18 * d));
        GradientDrawable sheetBg = new GradientDrawable();
        sheetBg.setColor(bg);
        float r = 24 * d;
        sheetBg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        root.setBackground(sheetBg);

        TextView title = new TextView(a);
        title.setText("Exporter la discussion");
        title.setTextColor(tx);
        title.setTextSize(17);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(0, 0, 0, (int) (10 * d));
        root.addView(title);

        for (Format f : Format.values()) {
            LinearLayout card = new LinearLayout(a);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding((int) (14 * d), (int) (12 * d), (int) (14 * d), (int) (12 * d));
            GradientDrawable cg = new GradientDrawable();
            cg.setColor(input);
            cg.setCornerRadius(Math.max(14, KruxTheme.radiusDp(a)) * d);
            card.setBackground(cg);

            TextView name = new TextView(a);
            name.setText(f.label);
            name.setTextColor(tx);
            name.setTextSize(15);
            name.setTypeface(Typeface.DEFAULT_BOLD);
            card.addView(name);

            TextView hint = new TextView(a);
            hint.setText(f.hint);
            hint.setTextColor(tx2);
            hint.setTextSize(12);
            card.addView(hint);

            LinearLayout row = new LinearLayout(a);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, (int) (10 * d), 0, 0);
            row.addView(pill(a, "Enregistrer", btnTx, btn, d, v -> { dialog.dismiss(); run(a, messages, f, true); }));
            android.view.View gap = new android.view.View(a);
            row.addView(gap, new LinearLayout.LayoutParams((int) (8 * d), 1));
            row.addView(pill(a, "Partager", tx, bg, d, v -> { dialog.dismiss(); run(a, messages, f, false); }));
            card.addView(row);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = (int) (8 * d);
            root.addView(card, lp);
        }

        ScrollView scroll = new ScrollView(a);
        scroll.addView(root);
        dialog.setContentView(scroll);
        dialog.setOnShowListener(x -> {
            android.view.View sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet != null) sheet.setBackgroundColor(Color.TRANSPARENT);
        });
        dialog.show();
    }

    private static TextView pill(Activity a, String label, int fg, int fill, float d, android.view.View.OnClickListener l) {
        TextView t = new TextView(a);
        t.setText(label);
        t.setTextSize(13);
        t.setTextColor(fg);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER);
        t.setPadding((int) (16 * d), (int) (9 * d), (int) (16 * d), (int) (9 * d));
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(100 * d);
        g.setStroke((int) d, Color.argb(60, 128, 128, 128));
        t.setBackground(g);
        t.setClickable(true);
        t.setOnClickListener(l);
        return t;
    }

    // ------------------------------------------------------------------ génération

    private static void run(Activity a, List<ChatMessage> messages, Format f, boolean save) {
        final List<ChatMessage> snapshot = new ArrayList<>(messages);
        Toast.makeText(a, "Préparation de l'export…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                byte[] data = build(snapshot, f);
                String name = "krux_discussion_" + new SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(new Date()) + f.ext;
                a.runOnUiThread(() -> {
                    if (a.isFinishing()) return;
                    if (save) saveToDownloads(a, data, name, f.mime);
                    else share(a, data, name, f.mime);
                });
            } catch (Throwable t) {
                a.runOnUiThread(() -> Toast.makeText(a, "Export impossible : " + t.getMessage(), Toast.LENGTH_LONG).show());
            }
        }, "krux-export").start();
    }

    static byte[] build(List<ChatMessage> ms, Format f) throws Exception {
        switch (f) {
            case TXT: return text(ms).getBytes(StandardCharsets.UTF_8);
            case CSV: return csv(ms).getBytes(StandardCharsets.UTF_8);
            case PDF: return pdf(ms);
            default: return zip(ms);
        }
    }

    private static String clean(ChatMessage m) {
        String t = m.getText();
        if (t == null) return "";
        t = FileDelivery.stripForDisplay(t).replaceAll("(?s)<REFLEXION>.*?</REFLEXION>", "");
        return LinkButtons.toPlain(t).trim();
    }

    private static String dateOf(ChatMessage m, DateFormat df) {
        long ts = m.getTimestamp();
        if (ts > 100000000000L) return df.format(new Date(ts));
        if (ts > 1000000000L) return df.format(new Date(ts * 1000L));
        return "";
    }

    // ---- texte

    private static String text(List<ChatMessage> ms) {
        DateFormat df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
        StringBuilder sb = new StringBuilder("# Discussion Krux\n");
        sb.append("Exportée le ").append(df.format(new Date())).append("\n\n");
        for (ChatMessage m : ms) {
            String t = clean(m);
            if (t.isEmpty()) continue;
            sb.append(m.isUser() ? "**Moi**" : "**Krux**");
            String when = dateOf(m, df);
            if (!when.isEmpty()) sb.append(" · ").append(when);
            sb.append("\n\n").append(t).append("\n\n---\n\n");
        }
        return sb.toString();
    }

    // ---- CSV

    private static String csv(List<ChatMessage> ms) {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        StringBuilder sb = new StringBuilder("\uFEFF"); // BOM : Excel lit bien l'UTF-8
        sb.append("numero,date,auteur,modele,message\r\n");
        int n = 0;
        for (ChatMessage m : ms) {
            String t = clean(m);
            if (t.isEmpty()) continue;
            n++;
            String model = m.isUser() || m.getModel() == null ? "" : m.getModel();
            sb.append(n).append(',')
              .append(q(dateOf(m, df))).append(',')
              .append(q(m.isUser() ? "Moi" : "Krux")).append(',')
              .append(q(model)).append(',')
              .append(q(safeCell(t))).append("\r\n");
        }
        return sb.toString();
    }

    private static String q(String s) {
        return "\"" + (s == null ? "" : s.replace("\"", "\"\"")) + "\"";
    }

    /** Empêche un tableur d'exécuter une cellule qui commence comme une formule. */
    private static String safeCell(String s) {
        if (s.isEmpty()) return s;
        char c = s.charAt(0);
        boolean minusNotBullet = c == '-' && s.length() > 1 && s.charAt(1) != ' ';
        if (c == '=' || c == '+' || c == '@' || minusNotBullet || c == '\t' || c == '\r') return "'" + s;
        return s;
    }

    // ---- ZIP

    private static byte[] zip(List<ChatMessage> ms) throws Exception {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        ZipOutputStream z = new ZipOutputStream(bo);
        put(z, "discussion.pdf", pdf(ms));
        put(z, "discussion.csv", csv(ms).getBytes(StandardCharsets.UTF_8));
        put(z, "discussion.txt", text(ms).getBytes(StandardCharsets.UTF_8));
        Set<String> used = new HashSet<>();
        int idx = 0;
        for (ChatMessage m : ms) {
            idx++;
            if (m.isUser() || m.getText() == null) continue;
            for (FileDelivery.DFile f : FileDelivery.parse(m.getText())) {
                String name = f.name;
                if (!used.add(name.toLowerCase(Locale.ROOT))) {
                    name = idx + "_" + name;
                    used.add(name.toLowerCase(Locale.ROOT));
                }
                put(z, "fichiers/" + name, f.content.getBytes(StandardCharsets.UTF_8));
            }
        }
        z.close();
        return bo.toByteArray();
    }

    private static void put(ZipOutputStream z, String name, byte[] data) throws Exception {
        z.putNextEntry(new ZipEntry(name));
        z.write(data);
        z.closeEntry();
    }

    // ---- PDF

    private static final int PAGE_W = 595, PAGE_H = 842, MARGIN = 40;
    private static final int MAX_CHARS = 400_000;

    private static byte[] pdf(List<ChatMessage> ms) throws Exception {
        DateFormat df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
        PdfOut out = new PdfOut();
        out.begin();

        TextPaint titleP = paint(18, Typeface.DEFAULT_BOLD, Color.rgb(17, 17, 17));
        out.draw(layout("Discussion Krux", titleP, 1.1f), null);
        TextPaint subP = paint(9, Typeface.DEFAULT, Color.rgb(110, 110, 110));
        out.draw(layout("Exportée le " + df.format(new Date()), subP, 1.1f), null);
        out.gap(14);

        int total = 0;
        for (ChatMessage m : ms) {
            String t = VoiceStyler.removeEmojis(clean(m));
            if (t.isEmpty()) continue;
            total += t.length();
            if (total > MAX_CHARS) {
                out.draw(layout("(la suite de la discussion est tronquée : utilise l'export CSV ou texte)",
                        paint(10, Typeface.DEFAULT, Color.rgb(160, 60, 60)), 1.2f), null);
                break;
            }
            String when = dateOf(m, df);
            int headColor = m.isUser() ? Color.rgb(37, 99, 235) : Color.rgb(124, 58, 237);
            out.keepWithNext(40);
            out.draw(layout((m.isUser() ? "Moi" : "Krux") + (when.isEmpty() ? "" : "  ·  " + when),
                    paint(9.5f, Typeface.DEFAULT_BOLD, headColor), 1.1f), null);
            out.gap(3);

            for (String[] part : splitBlocks(t)) {
                boolean code = part[0].equals("c");
                String body = part[1];
                if (body.trim().isEmpty()) continue;
                if (code) {
                    TextPaint cp = paint(8.5f, Typeface.MONOSPACE, Color.rgb(20, 20, 20));
                    out.draw(layout(body, cp, 1.15f, PAGE_W - 2 * MARGIN - 16), Color.rgb(243, 244, 246));
                    out.gap(4);
                } else {
                    out.draw(layout(mdToPlain(body), paint(10.5f, Typeface.DEFAULT, Color.rgb(34, 34, 34)), 1.2f), null);
                    out.gap(4);
                }
            }
            out.gap(10);
        }
        return out.finish();
    }

    private static TextPaint paint(float size, Typeface tf, int color) {
        TextPaint p = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        p.setTextSize(size);
        p.setTypeface(tf);
        p.setColor(color);
        return p;
    }

    private static StaticLayout layout(String s, TextPaint p, float spacing) {
        return layout(s, p, spacing, PAGE_W - 2 * MARGIN);
    }

    private static StaticLayout layout(String s, TextPaint p, float spacing, int width) {
        return StaticLayout.Builder.obtain(s, 0, s.length(), p, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, spacing)
                .setIncludePad(false)
                .build();
    }

    /** Découpe le texte en blocs {"t"|"c", contenu} selon les clôtures ```. */
    static List<String[]> splitBlocks(String text) {
        List<String[]> out = new ArrayList<>();
        int pos = 0;
        boolean code = false;
        while (pos <= text.length()) {
            int i = text.indexOf("```", pos);
            String chunk = i < 0 ? text.substring(pos) : text.substring(pos, i);
            if (code) {
                int nl = chunk.indexOf('\n');
                if (nl >= 0 && chunk.substring(0, nl).trim().matches("[A-Za-z0-9+#._-]{0,20}")) chunk = chunk.substring(nl + 1);
                chunk = chunk.replaceAll("\\s+$", "");
            } else {
                chunk = chunk.trim();
            }
            out.add(new String[]{code ? "c" : "t", chunk});
            if (i < 0) break;
            pos = i + 3;
            code = !code;
        }
        return out;
    }

    static String mdToPlain(String t) {
        return t.replaceAll("(?m)^\\s{0,3}#{1,6}\\s*", "")
                .replaceAll("(?m)^\\s*[-*+]\\s+", "• ")
                .replaceAll("\\*\\*(.+?)\\*\\*", "$1")
                .replaceAll("__(.+?)__", "$1")
                .replaceAll("`([^`]*)`", "$1")
                .replaceAll("!?\\[([^\\]]*)\\]\\(([^)]*)\\)", "$1 ($2)");
    }

    /** Écrit des blocs de texte sur des pages A4 successives, en coupant entre deux lignes. */
    private static final class PdfOut {
        final PdfDocument doc = new PdfDocument();
        PdfDocument.Page page;
        Canvas c;
        int pageNo = 0;
        float y;

        void begin() { newPage(); }

        void newPage() {
            if (page != null) closePage();
            pageNo++;
            page = doc.startPage(new PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create());
            c = page.getCanvas();
            y = MARGIN;
        }

        void closePage() {
            Paint f = paint(8, Typeface.DEFAULT, Color.rgb(150, 150, 150));
            f.setTextAlign(Paint.Align.CENTER);
            c.drawText("Krux · " + pageNo, PAGE_W / 2f, PAGE_H - 18, f);
            doc.finishPage(page);
            page = null;
        }

        void gap(float h) { y += h; }

        /** Passe à la page suivante s'il reste moins de `need` points (évite un titre isolé en bas). */
        void keepWithNext(float need) {
            if (y + need > PAGE_H - MARGIN) newPage();
        }

        void draw(StaticLayout l, Integer bgColor) {
            int n = l.getLineCount();
            int line = 0;
            float pad = bgColor != null ? 8 : 0;
            while (line < n) {
                float avail = PAGE_H - MARGIN - y - 2 * (bgColor != null ? 4 : 0);
                int last = line;
                float top = l.getLineTop(line);
                while (last < n && l.getLineBottom(last) - top <= avail) last++;
                if (last == line) {
                    if (y > MARGIN + 1) { newPage(); continue; }
                    last = line + 1; // une ligne seule plus haute qu'une page : on la force
                }
                float bottom = l.getLineBottom(last - 1);
                float h = bottom - top;
                if (bgColor != null) {
                    Paint bp = new Paint();
                    bp.setColor(bgColor);
                    c.drawRoundRect(MARGIN, y, PAGE_W - MARGIN, y + h + 8, 4, 4, bp);
                }
                c.save();
                c.translate(MARGIN + pad, y + (bgColor != null ? 4 : 0) - top);
                c.clipRect(0, top, l.getWidth(), bottom);
                l.draw(c);
                c.restore();
                y += h + (bgColor != null ? 8 : 0);
                line = last;
                if (line < n) newPage();
            }
        }

        byte[] finish() throws Exception {
            if (page != null) closePage();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            doc.writeTo(bo);
            doc.close();
            return bo.toByteArray();
        }
    }

    // ------------------------------------------------------------------ sortie

    private static void share(Activity a, byte[] data, String name, String mime) {
        try {
            File dir = new File(a.getCacheDir(), "shared");
            if (!dir.exists()) dir.mkdirs();
            long old = System.currentTimeMillis() - 24L * 3600_000L;
            File[] olds = dir.listFiles();
            if (olds != null) for (File o : olds) if (o.getName().startsWith("krux_discussion_") && o.lastModified() < old) o.delete();
            File f = new File(dir, name);
            try (FileOutputStream os = new FileOutputStream(f)) { os.write(data); }
            Uri uri = FileProvider.getUriForFile(a, a.getPackageName() + ".fileprovider", f);
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType(mime);
            i.putExtra(Intent.EXTRA_STREAM, uri);
            i.putExtra(Intent.EXTRA_SUBJECT, "Discussion Krux");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            a.startActivity(Intent.createChooser(i, "Partager la discussion"));
        } catch (Exception e) {
            Toast.makeText(a, "Partage impossible : " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private static void saveToDownloads(Activity a, byte[] data, String name, String mime) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues v = new ContentValues();
                v.put(MediaStore.Downloads.DISPLAY_NAME, name);
                v.put(MediaStore.Downloads.MIME_TYPE, mime);
                v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                Uri uri = a.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                if (uri == null) throw new Exception("création du fichier refusée");
                try (OutputStream os = a.getContentResolver().openOutputStream(uri)) {
                    if (os == null) throw new Exception("écriture impossible");
                    os.write(data);
                }
            } else {
                File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (!dir.exists()) dir.mkdirs();
                try (FileOutputStream os = new FileOutputStream(new File(dir, name))) { os.write(data); }
            }
            Toast.makeText(a, "Enregistré dans Téléchargements : " + name, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(a, "Enregistrement impossible : " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
