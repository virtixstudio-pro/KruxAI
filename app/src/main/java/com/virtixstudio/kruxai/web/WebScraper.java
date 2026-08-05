package com.virtixstudio.kruxai.web;

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WebScraper {

    private static final String TAG = "KruxWebScraper";

    /**
     * Extrait le texte principal d'une page Web (Articles, blogs, documentation)
     */
    public static String fetchPageContent(String urlString) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; KruxAI/1.0)");
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(6000);

            if (conn.getResponseCode() != 200) return "";

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder html = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                html.append(line).append("\n");
            }
            reader.close();

            // Clean HTML : Suppression des scripts, styles et balises
            String cleaned = html.toString()
                    .replaceAll("(?s)<script.*?>.*?</script>", "")
                    .replaceAll("(?s)<style.*?>.*?</style>", "")
                    .replaceAll("<[^>]+>", " ")
                    .replaceAll("\\s+", " ")
                    .trim();

            return cleaned.length() > 3000 ? cleaned.substring(0, 3000) : cleaned;
        } catch (Exception e) {
            Log.e(TAG, "Erreur lors de la lecture de la page web", e);
            return "";
        }
    }

    /**
     * Recherche de vidéos YouTube à partir d'un sujet
     */
    public static String searchVideos(String query) {
        StringBuilder results = new StringBuilder();
        try {
            String encodedQuery = URLEncoder.encode(query, "UTF-8");
            URL url = new URL("https://www.youtube.com/results?search_query=" + encodedQuery);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
            conn.setConnectTimeout(6000);

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder html = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                html.append(line);
            }
            reader.close();

            // Extraction des titres et liens de vidéos YouTube via Regex
            Pattern pattern = Pattern.compile("\"videoId\":\"([^\"]+)\".*?\"title\":\\{\"runs\":\\[\\{\"text\":\"([^\"]+)\"");
            Matcher matcher = pattern.matcher(html.toString());

            int count = 0;
            while (matcher.find() && count < 3) {
                String videoId = matcher.group(1);
                String title = matcher.group(2);
                results.append("🎥 ").append(title).append("\n")
                       .append("https://www.youtube.com/watch?v=").append(videoId).append("\n\n");
                count++;
            }
        } catch (Exception e) {
            Log.e(TAG, "Erreur recherche vidéo", e);
        }
        return results.toString();
    }
}
