package com.virtixstudio.kruxai.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.Deflater;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;

/**
 * Compresse un texte JSON pour la synchronisation : "g1:" + base64(gzip(texte)).
 * Java pur (pas d'Android) pour pouvoir être testé ; base64 écrit à la main.
 */
public final class SyncCodec {
    public static final String PREFIX = "g1:";
    /** Version 2 : deflate niveau 9, sans en-tête, avec dictionnaire de mots français et de clés JSON (bien plus petit sur les textes courts). */
    public static final String PREFIX2 = "g2:";

    /** Ne JAMAIS modifier ce texte une fois publié : les données déjà envoyées en dépendent. Une nouvelle version = un nouveau préfixe. */
    static final byte[] DICT = (
            "{\"sid\":\"session_\",\"title\":\"\",\"pin\":0,\"msgs\":[{\"s\":\"user\",\"m\":\"\",\"t\":17}, {\"s\":\"ai\",\"m\":\"\",\"t\":17}]}"
          + "<KRUX_FILE name=\"\"></KRUX_FILE><REMEMBER></REMEMBER><KRUX_TOOL>web_search create_app image_search</KRUX_TOOL>"
          + "```java ```kotlin ```python ```javascript ```html ```xml ```json ```bash ```sql ```css ```markdown"
          + "package com.virtixstudio.kruxai; import android.os.Bundle; import androidx.appcompat.app.AppCompatActivity; public class extends private static final void return String int boolean null new"
          + "S'appelle Habite ou vient de Aime Préfère N'aime pas Travaille Développe Son projet : Est développeur "
          + "le la les un une des du de d'un d'une et ou mais donc car que qui quoi dont où ce cet cette ces mon ma mes ton ta tes son sa ses notre votre leur "
          + "je tu il elle on nous vous ils elles me te se lui y en ne pas plus jamais rien tout tous toute toutes très bien mal peu beaucoup trop assez aussi encore déjà toujours "
          + "est sont être avoir fait faire peut pouvoir veux veut vouloir dois doit devoir sais sait savoir vais va aller dit dire voir venir prendre donner trouver mettre "
          + "dans sur sous avec sans pour par chez entre vers depuis pendant avant après contre selon comme quand lorsque si alors puis ensuite enfin voici voilà "
          + "bonjour salut merci s'il te plaît d'accord oui non peut-être exemple fichier application utilisateur message réponse question texte code erreur problème solution "
          + "fonction classe méthode variable paramètre résultat liste tableau données base serveur réseau connexion compte mémoire historique conversation recherche "
          + "Voici ce que j'ai trouvé. Je peux t'aider. Comment puis-je t'aider ? Peux-tu préciser ? Je ne sais pas. Je suis Krux AI, créé par Virtix Studio. "
          + "the and for with that this from have not are you your can will what how why when where which "
          + "https://www. https://fr.wikipedia.org/wiki/ .com .fr .org .net .io /index.html "
          + "0123456789 abcdefghijklmnopqrstuvwxyz ABCDEFGHIJKLMNOPQRSTUVWXYZ éèêëàâùûôîïç ÉÈÀÇ "
          + "\n\n- \n\n1. \n\n## \n\n**").getBytes(StandardCharsets.UTF_8);
    private static final String ABC = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

    private SyncCodec() { }

    /** Compression maximale (version 2). */
    public static String pack(String text) throws Exception {
        return PREFIX2 + b64(deflate(text.getBytes(StandardCharsets.UTF_8)));
    }

    /** Octets compressés au maximum, sans en-tête ni base64 : pour les envois binaires (archives de fichiers). */
    public static byte[] deflate(byte[] raw) {
        Deflater d = new Deflater(Deflater.BEST_COMPRESSION, true);
        d.setStrategy(Deflater.DEFAULT_STRATEGY);
        d.setDictionary(DICT);
        d.setInput(raw);
        d.finish();
        ByteArrayOutputStream bo = new ByteArrayOutputStream(Math.max(64, raw.length / 3));
        byte[] buf = new byte[8192];
        while (!d.finished()) bo.write(buf, 0, d.deflate(buf));
        d.end();
        return bo.toByteArray();
    }

    /** Inverse de deflate ; null si invalide. */
    public static byte[] inflate(byte[] packed) {
        Inflater inf = new Inflater(true);
        try {
            inf.setDictionary(DICT);
            inf.setInput(packed);
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            while (!inf.finished()) {
                int n = inf.inflate(buf);
                if (n == 0 && !inf.finished() && (inf.needsInput() || inf.needsDictionary())) return null;
                bo.write(buf, 0, n);
                if (bo.size() > 8 * 1024 * 1024) return null; // garde-fou contre une bombe de décompression
            }
            return bo.toByteArray();
        } catch (Exception e) {
            return null;
        } finally {
            inf.end();
        }
    }

    /** Retourne le texte d'origine, ou null si la donnée est invalide. */
    public static String unpack(String data) {
        try {
            if (data != null && data.startsWith(PREFIX2)) {
                byte[] raw2 = unb64(data.substring(PREFIX2.length()));
                byte[] out = raw2 == null ? null : inflate(raw2);
                return out == null ? null : new String(out, StandardCharsets.UTF_8);
            }
            if (data == null || !data.startsWith(PREFIX)) return null;
            byte[] raw = unb64(data.substring(PREFIX.length()));
            if (raw == null) return null;
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            try (GZIPInputStream z = new GZIPInputStream(new ByteArrayInputStream(raw))) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = z.read(buf)) > 0) {
                    if (bo.size() + n > 8 * 1024 * 1024) return null; // garde-fou contre une bombe de décompression
                    bo.write(buf, 0, n);
                }
            }
            return new String(bo.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    static String b64(byte[] b) {
        StringBuilder sb = new StringBuilder((b.length + 2) / 3 * 4);
        for (int i = 0; i < b.length; i += 3) {
            int v = (b[i] & 255) << 16 | (i + 1 < b.length ? (b[i + 1] & 255) << 8 : 0) | (i + 2 < b.length ? b[i + 2] & 255 : 0);
            sb.append(ABC.charAt(v >> 18 & 63)).append(ABC.charAt(v >> 12 & 63));
            sb.append(i + 1 < b.length ? ABC.charAt(v >> 6 & 63) : '=');
            sb.append(i + 2 < b.length ? ABC.charAt(v & 63) : '=');
        }
        return sb.toString();
    }

    static byte[] unb64(String s) {
        if (s.length() % 4 != 0) return null;
        int pad = s.endsWith("==") ? 2 : s.endsWith("=") ? 1 : 0;
        byte[] out = new byte[s.length() / 4 * 3 - pad];
        int o = 0;
        for (int i = 0; i < s.length(); i += 4) {
            int v = 0;
            for (int j = 0; j < 4; j++) {
                char c = s.charAt(i + j);
                int d = c == '=' ? 0 : ABC.indexOf(c);
                if (d < 0) return null;
                v = v << 6 | d;
            }
            if (o < out.length) out[o++] = (byte) (v >> 16);
            if (o < out.length) out[o++] = (byte) (v >> 8);
            if (o < out.length) out[o++] = (byte) v;
        }
        return out;
    }
}
