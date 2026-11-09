package com.virtixstudio.kruxai.core;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Client du Worker Cloudflare (stockage R2 par utilisateur). Aucun secret dans l'app :
 * on envoie le jeton Firebase de l'utilisateur, le Worker le vérifie et isole les fichiers par uid.
 * Toutes les méthodes bloquent : à appeler hors du thread principal.
 */
public final class WorkerStore {

    public static final long MAX_BYTES = 10L * 1024 * 1024;

    private WorkerStore() { }

    private static String token(FirebaseUser u) throws Exception {
        if (u == null) throw new Exception("Non connecté");
        String t = Tasks.await(u.getIdToken(false), 15, TimeUnit.SECONDS).getToken();
        if (t == null || t.isEmpty()) throw new Exception("Jeton Firebase indisponible");
        return t;
    }

    private static HttpURLConnection open(String method, String path, FirebaseUser u) throws Exception {
        if (!KruxConfig.workerEnabled()) throw new Exception("Stockage Virtix Studio non configuré");
        String base = KruxConfig.workerUrl();
        String p = path == null ? "" : path;
        if (!p.isEmpty() && !p.matches("[A-Za-z0-9_-][A-Za-z0-9_.-]{0,79}(/[A-Za-z0-9_-][A-Za-z0-9_.-]{0,79}){0,3}"))
            throw new Exception("Chemin invalide");
        HttpURLConnection c = (HttpURLConnection) new URL(base + "/" + p).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(15000);
        c.setReadTimeout(60000);
        c.setRequestProperty("Authorization", "Bearer " + token(u));
        return c;
    }

    private static byte[] readAll(InputStream is, int max) throws Exception {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        if (is == null) return bo.toByteArray();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) > 0) {
            if (bo.size() + n > max) throw new Exception("Réponse trop grosse");
            bo.write(buf, 0, n);
        }
        return bo.toByteArray();
    }

    private static void check(HttpURLConnection c) throws Exception {
        int code = c.getResponseCode();
        if (code < 400) return;
        String body = new String(readAll(c.getErrorStream(), 4000), StandardCharsets.UTF_8);
        String msg = body;
        try { msg = new JSONObject(body).optString("error", body); } catch (Exception ignored) { }
        throw new Exception("Stockage " + code + " : " + msg);
    }

    /** Envoie des octets sous le chemin donné (ex. "apps/app-123.apk"). */
    public static void put(FirebaseUser u, String path, byte[] data) throws Exception {
        if (data.length > MAX_BYTES) throw new Exception("Fichier trop gros (10 Mo maximum)");
        HttpURLConnection c = open("PUT", path, u);
        c.setDoOutput(true);
        c.setFixedLengthStreamingMode(data.length);
        c.setRequestProperty("Content-Type", "application/octet-stream");
        try (OutputStream o = c.getOutputStream()) { o.write(data); }
        check(c);
    }

    public static void put(FirebaseUser u, String path, File f) throws Exception {
        if (f == null || !f.isFile()) throw new Exception("Fichier introuvable");
        if (f.length() > MAX_BYTES) throw new Exception("Fichier trop gros (10 Mo maximum)");
        HttpURLConnection c = open("PUT", path, u);
        c.setDoOutput(true);
        c.setFixedLengthStreamingMode((int) f.length());
        c.setRequestProperty("Content-Type", "application/octet-stream");
        try (OutputStream o = c.getOutputStream(); InputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
        }
        check(c);
    }

    /** Télécharge un fichier (null s'il n'existe pas). */
    public static byte[] get(FirebaseUser u, String path) throws Exception {
        HttpURLConnection c = open("GET", path, u);
        if (c.getResponseCode() == 404) return null;
        check(c);
        return readAll(c.getInputStream(), (int) MAX_BYTES);
    }

    public static void delete(FirebaseUser u, String path) throws Exception {
        HttpURLConnection c = open("DELETE", path, u);
        check(c);
    }

    /** Liste des fichiers et quota : {used, quota, files:[{path,size,at}]}. */
    public static JSONObject list(FirebaseUser u) throws Exception {
        HttpURLConnection c = open("GET", "", u);
        check(c);
        return new JSONObject(new String(readAll(c.getInputStream(), 1 << 20), StandardCharsets.UTF_8));
    }
}
