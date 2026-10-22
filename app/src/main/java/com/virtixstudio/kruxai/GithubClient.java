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

    public GithubClient(String token) { this.token = token; }

    private static class Http { int code; String body; String location; }

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

    private JSONObject req(String method, String path, JSONObject body) throws Exception {
        Http h = raw(method, path, body);
        if (h.code >= 400) throw new Exception("GitHub " + h.code + " " + path + " : " + h.body);
        return h.body.isEmpty() ? new JSONObject() : new JSONObject(h.body);
    }

    public void init() throws Exception {
        owner = req("GET", "/user", null).getString("login");
    }

    public void createRepo(String name) throws Exception {
        req("POST", "/user/repos", new JSONObject()
            .put("name", name).put("private", true).put("auto_init", true));
        Thread.sleep(3000);
    }

    public String defaultBranch(String repo) throws Exception {
        return req("GET", "/repos/" + owner + "/" + repo, null).optString("default_branch", "main");
    }

    public void putFile(String repo, String path, String content) throws Exception {
        String url = "/repos/" + owner + "/" + repo + "/contents/" + path;
        JSONObject b = new JSONObject()
            .put("message", "KRUX: " + path)
            .put("content", Base64.encodeToString(content.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP));
        Http g = raw("GET", url, null);
        if (g.code == 200) b.put("sha", new JSONObject(g.body).getString("sha"));
        req("PUT", url, b);
    }

    public void dispatch(String repo, String branch) throws Exception {
        String url = "/repos/" + owner + "/" + repo + "/actions/workflows/build.yml/dispatches";
        String last = "";
        for (int i = 0; i < 8; i++) {
            Http h = raw("POST", url, new JSONObject().put("ref", branch));
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
            JSONArray runs = req("GET", "/repos/" + owner + "/" + repo + "/actions/runs?per_page=1", null)
                .getJSONArray("workflow_runs");
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

            Http h = raw("GET", "/repos/" + owner + "/" + repo + "/actions/jobs/" + jobId + "/logs", null);
            if (h.location == null) return "Log indisponible (HTTP " + h.code + ")";

            HttpURLConnection c = (HttpURLConnection) new URL(h.location).openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(60000);
            StringBuilder out = new StringBuilder();
            String prev = "";
            int extra = 0;
            try (BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null && out.length() < 5000) {
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

        Http h = raw("GET", "/repos/" + owner + "/" + repo + "/actions/artifacts/" + id + "/zip", null);
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
                    return out;
                }
            }
        }
        throw new Exception("APK absent de l'artefact");
    }
}
