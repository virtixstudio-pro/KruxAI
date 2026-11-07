import com.virtixstudio.kruxai.builder.PreviewHtml;

public class PreviewHtmlTests {
    static int ok = 0, bad = 0;
    static void check(boolean c, String n) { if (c) ok++; else { bad++; System.out.println("ECHEC : " + n); } }

    public static void main(String[] a) {
        String good = "<!doctype html><html><head><title>t</title><style>body{background:#111;color:#fff}.b{border-radius:12px}</style></head>"
            + "<body style=\"padding:8px\"><h1 class=\"b\">Mes notes</h1><p>Bonjour</p></body></html>";
        String o = PreviewHtml.prepare(good);
        check(o.startsWith("<!doctype html>"), "document complet");
        check(o.contains("Mes notes") && o.contains("Bonjour"), "contenu gardé");
        check(o.contains("background:#111") && o.contains("border-radius:12px"), "style gardé");
        check(o.contains("Content-Security-Policy") && o.contains("default-src 'none'"), "CSP présente");
        check(o.contains("id=\"krux-body\" style=\"padding:8px\""), "body devient un div avec ses attributs");
        check(!o.contains("<title>"), "titre retiré");

        String evil = "<p>Salut</p><script>alert(1)</script><SCRIPT SRC=http://x/y.js></SCRIPT><script>\nfetch('http://evil')\n</script>";
        String e1 = PreviewHtml.prepare(evil);
        check(!e1.toLowerCase().contains("<script") && !e1.contains("alert") && !e1.contains("fetch(") && e1.contains("Salut"), "scripts supprimés");

        String ev = "<div onclick=\"steal()\" ONMOUSEOVER='x()' onload=bad>Texte long de test ok</div>";
        String e2 = PreviewHtml.prepare(ev);
        check(!e2.toLowerCase().contains("onclick") && !e2.toLowerCase().contains("onmouseover") && !e2.toLowerCase().contains("onload") && e2.contains("Texte long"), "gestionnaires d'événements supprimés");

        String net = "<img src=\"http://evil/x.png\"><img src='https://evil/y.png'><a href=\"javascript:alert(1)\">lien de test ici</a>"
            + "<iframe src=\"http://evil\"></iframe><link rel=stylesheet href=\"http://evil/a.css\"><form action=\"http://evil\"><input></form>";
        String e3 = PreviewHtml.prepare(net);
        check(!e3.contains("evil") && !e3.toLowerCase().contains("javascript") && !e3.toLowerCase().contains("<iframe") && !e3.toLowerCase().contains("<link") && !e3.toLowerCase().contains("<form"), "ressources externes supprimées");

        String css = "<style>@import url(http://evil/a.css); .x{background:url(https://evil/b.png);color:red} .y{background:url(\"data:image/png;base64,AAAA\")}</style><p class=x>texte suffisamment long</p>";
        String e4 = PreviewHtml.prepare(css);
        check(!e4.contains("evil") && !e4.contains("@import") && e4.contains("color:red"), "CSS externe supprimé");
        check(e4.contains("data:image/png;base64,AAAA"), "image data: autorisée");

        String ok1 = "<svg viewBox=\"0 0 10 10\"><circle cx=\"5\" cy=\"5\" r=\"4\" fill=\"#a855f7\"/></svg><a href=\"#haut\">ancre locale</a>";
        String e5 = PreviewHtml.prepare(ok1);
        check(e5.contains("<circle") && e5.contains("href=\"#haut\""), "SVG et ancre locale gardés");

        check(PreviewHtml.prepare(null).isEmpty() && PreviewHtml.prepare("").isEmpty() && PreviewHtml.prepare("   ").isEmpty(), "vide / null");
        check(PreviewHtml.prepare("<script>x</script><div></div>  ").isEmpty(), "rien de visible après nettoyage");
        check(PreviewHtml.prepare("a".repeat(PreviewHtml.MAX_CHARS + 1)).isEmpty(), "trop long refusé");
        check(PreviewHtml.prepare("<div>" + "a".repeat(100) + "<!-- <script>alert(1)</script> --></div>").contains("alert") == false, "commentaires retirés");
        check(!PreviewHtml.prepare("<p style=\"width:expression(alert(1))\">texte assez long pour passer</p>").contains("expression"), "expression() retiré");

        System.out.println("Aperçu : " + ok + "/" + (ok + bad) + (bad == 0 ? " OK" : " ECHECS"));
        if (bad > 0) System.exit(1);
    }
}
