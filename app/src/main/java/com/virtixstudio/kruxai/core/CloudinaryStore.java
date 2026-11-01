package com.virtixstudio.kruxai.core;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Envoie un fichier (l'APK d'une app créée) sur Cloudinary avec un preset NON SIGNÉ.
 * Aucun secret dans l'application : seulement le nom du cloud et le nom du preset (krux_config.json).
 * Limite du plan gratuit pour les fichiers "raw" : 10 Mo.
 */
public final class CloudinaryStore {

    public static final long MAX_BYTES = 10L * 1024 * 1024;

    private CloudinaryStore() { }

    private static void field(DataOutputStream o, String boundary, String name, String value) throws Exception {
        o.writeBytes("--" + boundary + "\r\n");
        o.writeBytes("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n");
        o.write(value.getBytes(StandardCharsets.UTF_8));
        o.writeBytes("\r\n");
    }

    /** Retourne l'URL https du fichier envoyé, ou lève une exception. À appeler hors du thread principal. */
    public static String upload(File file, String folder, String publicId) throws Exception {
        if (!KruxConfig.cloudEnabled()) throw new Exception("Cloudinary non configuré");
        if (file == null || !file.isFile()) throw new Exception("Fichier introuvable");
        if (file.length() > MAX_BYTES) throw new Exception("Fichier trop gros pour Cloudinary gratuit (10 Mo)");
        if (!publicId.matches("[A-Za-z0-9_.-]{1,80}") || !folder.matches("[A-Za-z0-9_/-]{1,80}"))
            throw new Exception("Nom de fichier ou de dossier invalide");

        String boundary = "krux" + System.currentTimeMillis();
        URL url = new URL("https://api.cloudinary.com/v1_1/" + KruxConfig.cloudName() + "/raw/upload");
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setConnectTimeout(15000);
        c.setReadTimeout(120000);
        c.setChunkedStreamingMode(16384);
        c.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

        try (DataOutputStream o = new DataOutputStream(c.getOutputStream())) {
            field(o, boundary, "upload_preset", KruxConfig.uploadPreset());
            field(o, boundary, "folder", folder);
            field(o, boundary, "public_id", publicId);
            o.writeBytes("--" + boundary + "\r\n");
            o.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"" + publicId + "\"\r\n");
            o.writeBytes("Content-Type: application/octet-stream\r\n\r\n");
            try (InputStream in = new FileInputStream(file)) {
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
            }
            o.writeBytes("\r\n--" + boundary + "--\r\n");
        }

        int code = c.getResponseCode();
        InputStream is = code < 400 ? c.getInputStream() : c.getErrorStream();
        StringBuilder sb = new StringBuilder();
        if (is != null) {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String l;
                while ((l = br.readLine()) != null && sb.length() < 4000) sb.append(l);
            }
        }
        if (code >= 400) throw new Exception("Cloudinary " + code + " : " + sb);
        String u = new JSONObject(sb.toString()).optString("secure_url", "");
        if (!u.startsWith("https://")) throw new Exception("Réponse Cloudinary sans URL");
        return u;
    }
}
