package com.virtixstudio.kruxai.builder;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Archive .zip du projet complet (code source + fichiers Gradle), rangée à côté de l'APK. */
public final class SourceZip {
    public static final String NAME = "krux-source.zip";

    private SourceZip() { }

    public static File file(File dir) { return new File(dir, NAME); }

    public static boolean write(File dir, Map<String, String> files) {
        if (dir == null || files == null || files.isEmpty()) return false;
        File tmp = new File(dir, NAME + ".tmp");
        try {
            try (ZipOutputStream z = new ZipOutputStream(new FileOutputStream(tmp))) {
                for (Map.Entry<String, String> e : files.entrySet()) {
                    String path = e.getKey().replace('\\', '/');
                    while (path.startsWith("/")) path = path.substring(1);
                    if (path.isEmpty() || path.contains("..")) continue;
                    z.putNextEntry(new ZipEntry(path));
                    z.write(e.getValue().getBytes(StandardCharsets.UTF_8));
                    z.closeEntry();
                }
            }
            File dst = file(dir);
            if (dst.exists()) dst.delete();
            return tmp.renameTo(dst);
        } catch (Exception ex) {
            tmp.delete();
            return false;
        }
    }
}
