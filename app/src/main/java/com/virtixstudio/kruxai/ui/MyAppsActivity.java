package com.virtixstudio.kruxai.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.WindowCompat;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.util.Calendar;
import java.util.Date;

public class MyAppsActivity extends AppCompatActivity {
    private LinearLayout list;
    private TextView quotaView;
    private int bg, text, text2, accent, surface;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        bg = KruxTheme.get(this, "bg");
        text = KruxTheme.get(this, "text");
        text2 = KruxTheme.get(this, "text2");
        accent = KruxTheme.get(this, "accent");
        surface = KruxTheme.get(this, "input");

        getWindow().setStatusBarColor(bg);
        getWindow().setNavigationBarColor(bg);
        boolean light = ColorUtils.calculateLuminance(bg) > 0.5;
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView()).setAppearanceLightStatusBars(light);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(bg);
        scroll.setFillViewport(true);
        scroll.setFitsSystemWindows(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(20));
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("Mes apps");
        title.setTextSize(24);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(text);
        root.addView(title);

        quotaView = new TextView(this);
        quotaView.setTextSize(13);
        quotaView.setTextColor(text2);
        quotaView.setPadding(0, dp(4), 0, dp(14));
        root.addView(quotaView);

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list);
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    private static String week() {
        Calendar c = Calendar.getInstance();
        return c.getWeekYear() + "-W" + c.get(Calendar.WEEK_OF_YEAR);
    }

    private int weeklyMax() {
        try {
            java.io.InputStream is = getAssets().open("krux_builder.json");
            byte[] buf = new byte[is.available()];
            is.read(buf);
            is.close();
            return new JSONObject(new String(buf, java.nio.charset.StandardCharsets.UTF_8)).optInt("weekly_quota", 2);
        } catch (Exception e) {
            return 2;
        }
    }

    private void render() {
        list.removeAllViews();
        SharedPreferences sp = getSharedPreferences("krux_builder", MODE_PRIVATE);
        int used = week().equals(sp.getString("quota_week", "")) ? sp.getInt("quota_used", 0) : 0;
        quotaView.setText("Créations cette semaine : " + used + "/" + weeklyMax());

        JSONArray apps;
        try {
            apps = new JSONArray(sp.getString("apps", "[]"));
        } catch (Exception e) {
            apps = new JSONArray();
        }
        if (apps.length() == 0) {
            TextView empty = new TextView(this);
            empty.setText("Aucune app pour le moment.\nDemande à KRUX : « fais-moi une app de liste de courses ».");
            empty.setTextColor(text2);
            empty.setTextSize(14);
            list.addView(empty);
            return;
        }
        for (int i = 0; i < apps.length(); i++) {
            JSONObject o = apps.optJSONObject(i);
            if (o != null) addCard(o, i);
        }
    }

    private void addCard(JSONObject o, final int index) {
        final File apk = new File(o.optString("apk"));
        String name = o.optString("repo");
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("^krux-", "").replaceAll("-\\d+$", "");
        if (name.isEmpty()) name = "app";

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(10));
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(surface);
        gd.setCornerRadius(dp(12));
        card.setBackground(gd);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(10);
        card.setLayoutParams(lp);

        TextView t = new TextView(this);
        t.setText(name);
        t.setTextSize(16);
        t.setTypeface(null, Typeface.BOLD);
        t.setTextColor(text);
        card.addView(t);

        TextView d = new TextView(this);
        String date = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT)
                .format(new Date(o.optLong("time")));
        d.setText(date + (apk.exists() ? "" : "  ·  APK supprimé"));
        d.setTextSize(12);
        d.setTextColor(text2);
        card.addView(d);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(8), 0, 0);

        Button install = new Button(this);
        install.setText("Installer");
        install.setAllCaps(false);
        install.setEnabled(apk.exists());
        install.setOnClickListener(v -> install(apk));
        row.addView(install);

        Button del = new Button(this);
        del.setText("Supprimer");
        del.setAllCaps(false);
        del.setOnClickListener(v -> remove(index));
        row.addView(del);

        card.addView(row);
        list.addView(card);
    }

    private void install(File apk) {
        try {
            android.net.Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", apk);
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, "application/vnd.android.package-archive");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "Installation impossible : " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void remove(int index) {
        try {
            SharedPreferences sp = getSharedPreferences("krux_builder", MODE_PRIVATE);
            JSONArray old = new JSONArray(sp.getString("apps", "[]"));
            JSONArray arr = new JSONArray();
            for (int i = 0; i < old.length(); i++) {
                if (i == index) {
                    File f = new File(old.getJSONObject(i).optString("apk"));
                    f.delete();
                    File dir = f.getParentFile();
                    if (dir != null && dir.isDirectory() && dir.list() != null && dir.list().length == 0) dir.delete();
                } else {
                    arr.put(old.getJSONObject(i));
                }
            }
            sp.edit().putString("apps", arr.toString()).apply();
        } catch (Exception ignored) { }
        render();
    }
}
