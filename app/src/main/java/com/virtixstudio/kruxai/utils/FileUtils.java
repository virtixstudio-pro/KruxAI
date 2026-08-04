package com.virtixstudio.kruxai.utils;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

public class FileUtils {

    public static void saveTextFile(Context context, String text, String filenamePrefix) {
        String fileName = filenamePrefix + "_" + System.currentTimeMillis() + ".txt";
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                values.put(MediaStore.Downloads.MIME_TYPE, "text/plain");
                values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);

                Uri uri = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri != null) {
                    OutputStream os = context.getContentResolver().openOutputStream(uri);
                    if (os != null) {
                        os.write(text.getBytes());
                        os.close();
                        Toast.makeText(context, "Fichier TXT enregistré : " + fileName, Toast.LENGTH_LONG).show();
                    }
                }
            } else {
                File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (!dir.exists()) dir.mkdirs();
                File file = new File(dir, fileName);
                FileOutputStream os = new FileOutputStream(file);
                os.write(text.getBytes());
                os.close();
                Toast.makeText(context, "Fichier enregistré dans : " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(context, "Erreur lors de la sauvegarde : " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}
