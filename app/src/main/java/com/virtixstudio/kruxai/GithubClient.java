package com.virtixstudio.kruxai;

import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class GithubClient {
    private static final String API = "https://api.github.com";
    private final String token;
    public String owner;

    private final int retries;

    public GithubClient(String token) { this(token, 4); }

    public GithubClient(String token, int retries) {
        this.token = token;
        this.retries = Math.max(0, Math.min(8, retries));
    }

    private static class Http { int code; String body; String location; String retryAfter; String remaining; }

    private Http raw(String method, String url, JSONObject body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url.startsWith("http") ? url : API + url).openConnection();
        c.setRequestMethod(method);
        c.setRequestProperty("Authorization", "Bearer " + token);
        c.setRequestProperty("Accept", "application/vnd.github+json");
        c.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
        c.setInstanceFollowRedirects(false);
        c.setConnectTimeout(15000);
        c.setReadTimeout(60000);
        if (body != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            try (OutputStream os = c.getOutputStream()) {
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
        }
        Http h = new Http();
        h.code = c.getResponseCode();
        h.location = c.getHeaderField("Location");
        h.retryAfter = c.getHeaderField("Retry-After");
        h.remaining = c.getHeaderField("X-RateLimit-Remaining");
        InputStream is = h.code < 400 ? c.getInputStream() : c.getErrorStream();
        StringBuilder sb = new StringBuilder();
        if (is != null) {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String l;
                while ((l = br.readLine()) != null) sb.append(l);
            }
        }
        h.body = sb.toString();
        return h;
    }

    /** Limite de débit (primaire ou secondaire) : GitHub répond 429 ou 403 avec ces indices. */
    private static boolean rateLimited(Http h) {
        if (h.code == 429) return true;
        if (h.code != 403) return false;
        String b = h.body == null ? "" : h.body.toLowerCase();
        return h.retryAfter != null || "0".equals(h.remaining) || b.contains("rate limit") || b.contains("abuse") || b.contains("secondary");
    }

    private static long waitMs(Http h, int attempt) {
        if (h != null && h.retryAfter != null) {
            try { return Math.min(65000L, Math.max(1000L, Long.parseLong(h.retryAfter.trim()) * 1000L)); } catch (NumberFormatException ignore) { /* défaut */ }
        }
        if (h != null && rateLimited(h)) return 20000L * (attempt + 1) > 65000L ? 65000L : 20000L * (attempt + 1);
        return Math.min(30000L, 2000L << attempt);
    }

    /**
     * Appel avec reprises : réseau coupé, 5xx, limites de débit. Les erreurs "définitives" (401, 404, 422...)
     * sont rendues telles quelles. Un appel non idempotent (création) n'est repris que s'il n'a pas pu s'exécuter
     * (limite de débit) ou en cas d'échec réseau avant réponse.
     */
    private Http call(String method, String url, JSONObject body, boolean idempotent) throws Exception {
        Http last = null;
        Exception lastEx = null;
        for (int attempt = 0; attempt <= retries; attempt++) {
            try {
                Http h = raw(method, url, body);
                last = h;
                boolean transientErr = rateLimited(h) || (idempotent && h.code >= 500 && h.code < 600);
                if (!transientErr) return h;
                if (attempt == retries) return h;
                Thread.sleep(waitMs(h, attempt));
            } catch (java.io.IOException e) {
                lastEx = e;
                if (attempt == retries) throw new Exception("Réseau indisponible : " + e.getMessage());
                Thread.sleep(waitMs(null, attempt));
            }
        }
        if (last != null) return last;
        throw lastEx != null ? lastEx : new Exception("Appel GitHub impossible");
    }

    private JSONObject req(String method, String path, JSONObject body) throws Exception {
        return req(method, path, body, true);
    }

    private JSONObject req(String method, String path, JSONObject body, boolean idempotent) throws Exception {
        Http h = call(method, path, body, idempotent);
        if (h.code >= 400) throw new Exception("GitHub " + h.code + " " + path + " : " + h.body);
        return h.body.isEmpty() ? new JSONObject() : new JSONObject(h.body);
    }

    public void init() throws Exception {
        owner = req("GET", "/user", null).getString("login");
    }

    public void createRepo(String name) throws Exception {
        Http h = call("POST", "/user/repos", new JSONObject()
            .put("name", name).put("private", true).put("auto_init", true), false);
        // 422 "name already exists" : une reprise précédente l'a déjà créé, on continue
        if (h.code >= 400 && !(h.code == 422 && h.body != null && h.body.contains("already exists")))
            throw new Exception("GitHub " + h.code + " /user/repos : " + h.body);
        Thread.sleep(3000);
    }

    public String defaultBranch(String repo) throws Exception {
        return req("GET", "/repos/" + owner + "/" + repo, null).optString("default_branch", "main");
    }

    public void putFile(String repo, String path, String content) throws Exception {
        String url = "/repos/" + owner + "/" + repo + "/contents/" + path;
        String b64 = Base64.encodeToString(content.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        Http last = null;
        for (int i = 0; i < 4; i++) {
            JSONObject b = new JSONObject().put("message", "KRUX: " + path).put("content", b64);
            Http g = call("GET", url, null, true);
            if (g.code == 200) b.put("sha", new JSONObject(g.body).getString("sha"));
            Http h = call("PUT", url, b, false);
            last = h;
            if (h.code < 300) {
                Thread.sleep(400); // évite la limite "secondaire" de GitHub (créations rapprochées)
                return;
            }
            boolean conflict = h.code == 409 || (h.code == 422 && h.body != null && h.body.contains("sha"));
            if (!conflict) break;
            Thread.sleep(1500);
        }
        throw new Exception("GitHub " + last.code + " " + path + " : " + last.body);
    }

    public void dispatch(String repo, String branch) throws Exception {
        String url = "/repos/" + owner + "/" + repo + "/actions/workflows/build.yml/dispatches";
        String last = "";
        for (int i = 0; i < 8; i++) {
            Http h = call("POST", url, new JSONObject().put("ref", branch), false);
            if (h.code < 300) return;
            last = "HTTP " + h.code + " : " + h.body;
            Thread.sleep(4000);
        }
        throw new Exception("Impossible de lancer le build (" + last + ")");
    }

    public long latestRunId(String repo) throws Exception {
        JSONArray runs = req("GET", "/repos/" + owner + "/" + repo + "/actions/runs?per_page=1", null)
            .getJSONArray("workflow_runs");
        return runs.length() == 0 ? 0 : runs.getJSONObject(0).getLong("id");
    }

    public JSONObject waitRun(String repo, long afterId) throws Exception {
        long end = System.currentTimeMillis() + 15 * 60 * 1000;
        while (System.currentTimeMillis() < end) {
            Thread.sleep(8000);
            JSONArray runs;
            try {
                runs = req("GET", "/repos/" + owner + "/" + repo + "/actions/runs?per_page=1", null)
                    .getJSONArray("workflow_runs");
            } catch (Exception e) {
                String m = String.valueOf(e.getMessage());
                if (m.contains("GitHub 401") || m.contains("GitHub 404")) throw e;
                continue; // coupure passagère : on continue d'attendre
            }
            if (runs.length() == 0) continue;
            JSONObject r = runs.getJSONObject(0);
            if (r.getLong("id") > afterId && "completed".equals(r.getString("status"))) return r;
        }
        throw new Exception("Build trop long (timeout 15 min)");
    }

    private static final Pattern ERR = Pattern.compile(
        "error:|##\\[error\\]|FAILED|What went wrong|Caused by|cannot find symbol|AAPT|Execution failed|Could not|not found");

    public String failureLog(String repo, long runId) {
        try {
            JSONArray jobs = req("GET", "/repos/" + owner + "/" + repo + "/actions/runs/" + runId + "/jobs", null)
                .getJSONArray("jobs");
            long jobId = 0;
            for (int i = 0; i < jobs.length(); i++) {
                if ("failure".equals(jobs.getJSONObject(i).optString("conclusion"))) {
                    jobId = jobs.getJSONObject(i).getLong("id");
                    break;
                }
            }
            if (jobId == 0 && jobs.length() > 0) jobId = jobs.getJSONObject(0).getLong("id");

            Http h = call("GET", "/repos/" + owner + "/" + repo + "/actions/jobs/" + jobId + "/logs", null, true);
            if (h.location == null) return "Log indisponible (HTTP " + h.code + ")";

            HttpURLConnection c = (HttpURLConnection) new URL(h.location).openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(60000);
            StringBuilder out = new StringBuilder();
            String prev = "";
            int extra = 0;
            try (BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null && out.length() < 6000) {
                    line = line.replaceFirst("^\\d{4}-\\d{2}-\\d{2}T\\S+ ", "").trim();
                    if (line.isEmpty() || line.equals(prev)) continue;
                    if (ERR.matcher(line).find()) {
                        out.append(line).append('\n');
                        prev = line;
                        extra = 2;
                    } else if (extra > 0) {
                        out.append(line).append('\n');
                        extra--;
                    }
                }
            }
            return out.length() == 0 ? "Aucune ligne d'erreur détectée dans le log" : out.toString();
        } catch (Exception e) {
            return "Log indisponible : " + e.getMessage();
        }
    }

    public File downloadApk(String repo, long runId, File outDir) throws Exception {
        JSONArray arts = req("GET", "/repos/" + owner + "/" + repo + "/actions/runs/" + runId + "/artifacts", null)
            .getJSONArray("artifacts");
        if (arts.length() == 0) throw new Exception("Aucun artefact produit");
        long id = arts.getJSONObject(0).getLong("id");
        for (int i = 0; i < arts.length(); i++)
            if ("app-debug".equals(arts.getJSONObject(i).optString("name"))) id = arts.getJSONObject(i).getLong("id");

        Http h = call("GET", "/repos/" + owner + "/" + repo + "/actions/artifacts/" + id + "/zip", null, true);
        if (h.location == null) throw new Exception("Lien de téléchargement introuvable (HTTP " + h.code + ")");

        HttpURLConnection c = (HttpURLConnection) new URL(h.location).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(120000);
        outDir.mkdirs();
        File out = new File(outDir, "app-generated.apk");
        try (ZipInputStream zin = new ZipInputStream(c.getInputStream())) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                if (e.getName().endsWith(".apk")) {
                    try (FileOutputStream fo = new FileOutputStream(out)) {
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = zin.read(buf)) > 0) fo.write(buf, 0, n);
                    }
                    if (out.length() < 1024) throw new Exception("APK produit trop petit : téléchargement incomplet");
                    return out;
                }
            }
        }
        throw new Exception("APK absent de l'artefact");
    }
}
