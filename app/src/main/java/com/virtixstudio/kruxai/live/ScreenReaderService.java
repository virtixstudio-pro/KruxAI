package com.virtixstudio.kruxai.live;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

/**
 * Lit le texte de l'écran, seulement quand KRUX le demande (jamais en continu, rien n'est stocké).
 * Les champs de mot de passe sont ignorés.
 */
public class ScreenReaderService extends AccessibilityService {

    private static volatile ScreenReaderService instance;
    private static final int MAX_CHARS = 6000;

    public static boolean isOn() { return instance != null; }

    /** Texte visible de l'écran actuel, ou null si le service n'est pas activé. */
    public static String read() {
        ScreenReaderService s = instance;
        if (s == null) return null;
        AccessibilityNodeInfo root = s.getRootInActiveWindow();
        if (root == null) return "";
        StringBuilder sb = new StringBuilder();
        collect(root, sb, 0);
        return sb.toString().trim();
    }

    private static void collect(AccessibilityNodeInfo n, StringBuilder sb, int depth) {
        if (n == null || sb.length() >= MAX_CHARS || depth > 40) return;
        if (!n.isVisibleToUser() || n.isPassword()) return;
        CharSequence t = n.getText();
        if (t == null || t.length() == 0) t = n.getContentDescription();
        if (t != null && t.length() > 0) sb.append(t).append('\n');
        for (int i = 0; i < n.getChildCount(); i++) collect(n.getChild(i), sb, depth + 1);
    }

    @Override protected void onServiceConnected() { instance = this; }
    @Override public void onAccessibilityEvent(AccessibilityEvent e) { }
    @Override public void onInterrupt() { }
    @Override public boolean onUnbind(android.content.Intent i) { instance = null; return super.onUnbind(i); }
    @Override public void onDestroy() { instance = null; super.onDestroy(); }
}
