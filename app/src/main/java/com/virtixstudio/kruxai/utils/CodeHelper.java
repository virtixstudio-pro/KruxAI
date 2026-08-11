package com.virtixstudio.kruxai.utils;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Environment;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;

public class CodeHelper {

    public static void copyToClipboard(Context context, String code) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("KruxAI Code", code);
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
            Toast.makeText(context, "Code copié dans le presse-papier", Toast.LENGTH_SHORT).show();
        }
    }

    public static void shareCode(Context context, String language, String code) {
        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, code);
        sendIntent.setType("text/plain");
        Intent shareIntent = Intent.createChooser(sendIntent, "Partager le code (" + language + ")");
        context.startActivity(shareIntent);
    }

    public static void downloadCodeFile(Context context, String language, String code) {
        try {
            String extension = getExtensionForLanguage(language);
            String fileName = "krux_" + System.currentTimeMillis() + extension;
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File file = new File(downloadsDir, fileName);

            FileOutputStream fos = new FileOutputStream(file);
            fos.write(code.getBytes());
            fos.close();

            Toast.makeText(context, "Fichier enregistré dans Téléchargements : " + fileName, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(context, "Erreur d'enregistrement : " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private static String getExtensionForLanguage(String language) {
        if (language == null) return ".txt";
        switch (language.toLowerCase().trim()) {
            case "java": return ".java";
            case "python": case "py": return ".py";
            case "javascript": case "js": return ".js";
            case "xml": return ".xml";
            case "json": return ".json";
            case "bash": case "sh": return ".sh";
            case "cpp": case "c++": return ".cpp";
            case "c": return ".c";
            case "html": return ".html";
            case "css": return ".css";
            case "kotlin": return ".kt";
            default: return ".txt";
        }
    }
}
