package com.virtixstudio.kruxai.utils;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;

public class MediaHelper {

    // Partager un fichier média téléchargé
    public static void shareMedia(Context context, File file) {
        if (file == null || !file.exists()) {
            Toast.makeText(context, "Fichier introuvable", Toast.LENGTH_SHORT).show();
            return;
        }
        Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", file);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.setType("*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        context.startActivity(Intent.createChooser(intent, "Partager via"));
    }

    // Ouvrir directement une vidéo ou une image dans le lecteur natif de l appareil
    public static void openMedia(Context context, Uri uri, String mimeType) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, mimeType);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            context.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(context, "Aucune application compatible pour ouvrir ce média", Toast.LENGTH_SHORT).show();
        }
    }
}
