package com.virtixstudio.kruxai.builder;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mode « web » de la création d'apps : l'IA n'écrit que du TypeScript/React (src/*.tsx), KRUX fournit tout le reste
 * (coque Android fixe avec WebView, configuration npm, workflow esbuild + tsc + Gradle). Java pur, non branché
 * tant que AppBuilder ne l'appelle pas. Dans les modèles, l'apostrophe simple ' est convertie en guillemet " (voir q).
 */
public final class WebTemplates {

    private WebTemplates() { }

    // Versions épinglées (vérifiées avec npm view le 2026-11-17).
    public static final String REACT = "19.3.1";
    public static final String TYPES_REACT = "19.3.0";
    public static final String ESBUILD = "0.28.2";
    public static final String TYPESCRIPT = "5.9.3";
    public static final String NODE = "22";

    public static final String WWW = "app/src/main/assets/www";
    public static final String SHELL_ACTIVITY = "app/src/main/java/com/krux/generated/MainActivity.java";

    /** Fichiers que l'IA ne doit jamais écrire ni remplacer. */
    public static boolean isShellPath(String path) {
        return path.equals("package.json") || path.equals("tsconfig.json") || path.equals("src/krux.d.ts")
            || path.startsWith("app/") || path.startsWith(".github/") || path.startsWith(WWW);
    }

    private static String q(String s) { return s.replace('\'', '"'); }

    private static String L(String... lines) { return q(String.join("\n", lines)) + "\n"; }

    private static String xml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    /** Fichiers fournis par KRUX (hors fichiers Gradle, déjà produits par BuildTemplates.gradleFiles). */
    public static Map<String, String> shellFiles(String appName) {
        String name = xml(appName == null || appName.trim().isEmpty() ? "App" : appName.trim());
        Map<String, String> m = new LinkedHashMap<>();
        m.put("package.json", L(
            "{",
            "  'name': 'krux-app', 'private': true, 'version': '1.0.0',",
            "  'dependencies': { 'react': '" + REACT + "', 'react-dom': '" + REACT + "' },",
            "  'devDependencies': { '@types/react': '" + TYPES_REACT + "', '@types/react-dom': '" + TYPES_REACT + "',",
            "    'esbuild': '" + ESBUILD + "', 'typescript': '" + TYPESCRIPT + "' }",
            "}"));
        m.put("tsconfig.json", L(
            "{ 'compilerOptions': { 'target': 'ES2020', 'module': 'ESNext', 'moduleResolution': 'Bundler',",
            "  'jsx': 'react-jsx', 'strict': false, 'noEmit': true, 'skipLibCheck': true, 'isolatedModules': true,",
            "  'esModuleInterop': true, 'lib': ['ES2020', 'DOM', 'DOM.Iterable'], 'types': [] },",
            "  'include': ['src'] }"));
        m.put("src/krux.d.ts", L(
            "export {};",
            "declare global {",
            "  /** Pont natif fourni par la coque Android ; absent dans un navigateur. */",
            "  interface Window { Krux?: { toast(text: string): void; share(text: string): void; copy(text: string): void; vibrate(ms: number): void } }",
            "}"));
        m.put(WWW + "/index.html", L(
            "<!doctype html><html lang='fr'><head><meta charset='utf-8'>",
            "<meta name='viewport' content='width=device-width,initial-scale=1,viewport-fit=cover'>",
            "<meta http-equiv='Content-Security-Policy' content=\"default-src 'none'; script-src file:; style-src file: 'unsafe-inline'; "
                + "img-src file: data: blob:; font-src file: data:; connect-src https:\">",
            "<title>" + name + "</title><link rel='stylesheet' href='app.css'></head>",
            "<body><div id='root'></div><script src='app.js'></script></body></html>"));
        m.put("app/src/main/AndroidManifest.xml", L(
            "<?xml version='1.0' encoding='utf-8'?>",
            "<manifest xmlns:android='http://schemas.android.com/apk/res/android'>",
            "    <uses-permission android:name='android.permission.INTERNET'/>",
            "    <uses-permission android:name='android.permission.VIBRATE'/>",
            "    <application android:label='" + name + "' android:allowBackup='false' android:usesCleartextTraffic='false'",
            "        android:theme='@style/Theme.AppCompat.DayNight.NoActionBar'>",
            "        <activity android:name='.MainActivity' android:exported='true'",
            "            android:configChanges='orientation|screenSize|keyboardHidden|smallestScreenSize|screenLayout|uiMode'>",
            "            <intent-filter>",
            "                <action android:name='android.intent.action.MAIN'/>",
            "                <category android:name='android.intent.category.LAUNCHER'/>",
            "            </intent-filter>",
            "        </activity>",
            "    </application>",
            "</manifest>"));
        m.put(SHELL_ACTIVITY, activity());
        return m;
    }

    private static String activity() {
        return L(
            "package com.krux.generated;",
            "",
            "import android.annotation.SuppressLint;",
            "import android.content.ClipData;",
            "import android.content.ClipboardManager;",
            "import android.content.Context;",
            "import android.content.Intent;",
            "import android.net.Uri;",
            "import android.os.Build;",
            "import android.os.Bundle;",
            "import android.os.VibrationEffect;",
            "import android.os.Vibrator;",
            "import android.webkit.JavascriptInterface;",
            "import android.webkit.WebResourceRequest;",
            "import android.webkit.WebSettings;",
            "import android.webkit.WebView;",
            "import android.webkit.WebViewClient;",
            "import android.widget.Toast;",
            "import androidx.activity.OnBackPressedCallback;",
            "import androidx.appcompat.app.AppCompatActivity;",
            "",
            "/** Coque fixe fournie par KRUX : affiche assets/www/index.html dans une WebView et expose un petit pont natif. */",
            "public class MainActivity extends AppCompatActivity {",
            "    private WebView web;",
            "",
            "    @SuppressLint('SetJavaScriptEnabled')",
            "    @Override protected void onCreate(Bundle state) {",
            "        super.onCreate(state);",
            "        web = new WebView(this);",
            "        setContentView(web);",
            "        web.setOnApplyWindowInsetsListener((v, insets) -> {",
            "            v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),",
            "                insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());",
            "            return insets;",
            "        });",
            "        WebSettings s = web.getSettings();",
            "        s.setJavaScriptEnabled(true);",
            "        s.setDomStorageEnabled(true);",
            "        s.setAllowFileAccess(false);",
            "        s.setAllowContentAccess(false);",
            "        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);",
            "        web.setWebViewClient(new WebViewClient() {",
            "            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {",
            "                Uri u = r.getUrl();",
            "                if ('file'.equals(u.getScheme()) && u.toString().startsWith('file:///android_asset/www/')) return false;",
            "                if ('https'.equals(u.getScheme())) {",
            "                    try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) { }",
            "                }",
            "                return true;",
            "            }",
            "        });",
            "        web.addJavascriptInterface(new Bridge(), 'Krux');",
            "        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {",
            "            @Override public void handleOnBackPressed() {",
            "                if (web.canGoBack()) { web.goBack(); }",
            "                else { setEnabled(false); getOnBackPressedDispatcher().onBackPressed(); }",
            "            }",
            "        });",
            "        if (state == null) web.loadUrl('file:///android_asset/www/index.html');",
            "        else web.restoreState(state);",
            "    }",
            "",
            "    @Override protected void onSaveInstanceState(Bundle out) {",
            "        super.onSaveInstanceState(out);",
            "        web.saveState(out);",
            "    }",
            "",
            "    @Override protected void onDestroy() {",
            "        if (web != null) { web.destroy(); web = null; }",
            "        super.onDestroy();",
            "    }",
            "",
            "    /** Appelé depuis JavaScript (thread WebView) : tout passe par le thread principal. */",
            "    private final class Bridge {",
            "        @JavascriptInterface public void toast(final String text) {",
            "            runOnUiThread(() -> Toast.makeText(MainActivity.this, String.valueOf(text), Toast.LENGTH_SHORT).show());",
            "        }",
            "        @JavascriptInterface public void share(final String text) {",
            "            runOnUiThread(() -> {",
            "                Intent i = new Intent(Intent.ACTION_SEND).setType('text/plain').putExtra(Intent.EXTRA_TEXT, String.valueOf(text));",
            "                startActivity(Intent.createChooser(i, null));",
            "            });",
            "        }",
            "        @JavascriptInterface public void copy(final String text) {",
            "            runOnUiThread(() -> {",
            "                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);",
            "                if (cm != null) cm.setPrimaryClip(ClipData.newPlainText('texte', String.valueOf(text)));",
            "            });",
            "        }",
            "        @JavascriptInterface public void vibrate(final int ms) {",
            "            runOnUiThread(() -> {",
            "                Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);",
            "                if (v == null) return;",
            "                long d = Math.max(1, Math.min(ms, 500));",
            "                if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(d, VibrationEffect.DEFAULT_AMPLITUDE));",
            "                else v.vibrate(d);",
            "            });",
            "        }",
            "    }",
            "}");
    }

    /** Workflow : npm install, vérification des types (tsc), bundle (esbuild), puis Gradle comme avant. */
    public static String workflow(BuildSpec s) {
        return "name: Build APK\n"
            + "on:\n  workflow_dispatch:\n"
            + "permissions:\n  contents: read\n"
            + "concurrency:\n  group: build-${{ github.ref }}\n  cancel-in-progress: true\n"
            + "jobs:\n  build:\n    runs-on: ubuntu-latest\n    timeout-minutes: 25\n    steps:\n"
            + "      - uses: actions/checkout@v4\n"
            + "      - uses: actions/setup-node@v4\n        with:\n          node-version: \"" + NODE + "\"\n"
            + "      - name: Install npm packages\n        run: npm install --no-audit --no-fund --ignore-scripts\n"
            + "      - name: Type check (tsc)\n        run: npx tsc --noEmit -p .\n"
            + "      - name: Bundle (esbuild)\n        run: >-\n"
            + "          npx esbuild src/main.tsx --bundle --format=iife --target=chrome87 --minify\n"
            + "          --define:process.env.NODE_ENV=\\\"production\\\" --loader:.svg=dataurl\n"
            + "          --outdir=" + WWW + " --entry-names=app\n"
            + "      - uses: actions/setup-java@v4\n        with:\n          distribution: temurin\n          java-version: " + s.jdk + "\n"
            + BuildTemplates.sdkStep(s)
            + "      - uses: gradle/actions/setup-gradle@v4\n        with:\n          gradle-version: " + s.gradle + "\n"
            + "      - run: gradle assembleDebug --no-daemon --console=plain\n"
            + "      - uses: actions/upload-artifact@v4\n        with:\n          name: app-debug\n"
            + "          path: app/build/outputs/apk/debug/app-debug.apk\n"
            + "          if-no-files-found: error\n"
            + "          retention-days: 1\n";
    }

    /** Petite app d'exemple : sert à tester la chaîne complète (CI, APK, pont natif) sans dépenser de tokens. */
    public static Map<String, String> sampleApp() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("src/main.tsx", L(
            "import { createRoot } from 'react-dom/client';",
            "import App from './App';",
            "import './styles.css';",
            "",
            "createRoot(document.getElementById('root')!).render(<App />);"));
        m.put("src/App.tsx", L(
            "import { useEffect, useState } from 'react';",
            "",
            "type Note = { id: number; text: string };",
            "const KEY = 'notes';",
            "",
            "function load(): Note[] {",
            "  try { return JSON.parse(localStorage.getItem(KEY) || '[]'); } catch { return []; }",
            "}",
            "",
            "export default function App() {",
            "  const [notes, setNotes] = useState<Note[]>(load);",
            "  const [text, setText] = useState('');",
            "  useEffect(() => { localStorage.setItem(KEY, JSON.stringify(notes)); }, [notes]);",
            "",
            "  const add = () => {",
            "    const t = text.trim();",
            "    if (!t) return;",
            "    setNotes([{ id: Date.now(), text: t }, ...notes]);",
            "    setText('');",
            "    window.Krux?.vibrate(20);",
            "  };",
            "",
            "  return (",
            "    <main>",
            "      <h1>Notes</h1>",
            "      <div className='row'>",
            "        <input value={text} onChange={(e) => setText(e.target.value)} placeholder='Écrire une note…' />",
            "        <button onClick={add}>Ajouter</button>",
            "      </div>",
            "      {notes.length === 0 && <p className='empty'>Aucune note pour l’instant.</p>}",
            "      <ul>",
            "        {notes.map((n) => (",
            "          <li key={n.id}>",
            "            <span>{n.text}</span>",
            "            <button className='ghost' onClick={() => window.Krux?.share(n.text)}>Partager</button>",
            "            <button className='ghost' onClick={() => setNotes(notes.filter((x) => x.id !== n.id))}>Supprimer</button>",
            "          </li>",
            "        ))}",
            "      </ul>",
            "    </main>",
            "  );",
            "}"));
        m.put("src/styles.css", L(
            ":root { color-scheme: light dark; --accent: #4f46e5; }",
            "* { box-sizing: border-box; }",
            "body { margin: 0; font-family: system-ui, sans-serif; background: Canvas; color: CanvasText; }",
            "main { max-width: 560px; margin: 0 auto; padding: 16px; }",
            ".row { display: flex; gap: 8px; }",
            "input { flex: 1; padding: 12px; border: 1px solid #8884; border-radius: 12px; font-size: 16px; background: transparent; color: inherit; }",
            "button { padding: 12px 16px; border: 0; border-radius: 12px; background: var(--accent); color: #fff; font-size: 15px; }",
            "button.ghost { background: transparent; color: var(--accent); padding: 8px; }",
            "ul { list-style: none; padding: 0; } li { display: flex; align-items: center; gap: 4px; padding: 12px 0; border-bottom: 1px solid #8883; }",
            "li span { flex: 1; } .empty { opacity: .6; }"));
        return m;
    }
}
