#!/usr/bin/env python3
from pathlib import Path
from datetime import datetime
import shutil, sys

root = Path.cwd()
files = [
    "app/src/main/java/com/virtixstudio/kruxai/ui/MainActivity.java",
    "app/src/main/java/com/virtixstudio/kruxai/core/ToolRouter.java",
    "app/src/main/java/com/virtixstudio/kruxai/utils/SystemPromptBuilder.java",
    "app/src/main/res/values/colors.xml",
]
missing = [f for f in files if not (root / f).is_file()]
if missing:
    print("Lance ce script depuis la racine du dépôt KruxAI. Fichiers manquants :", *missing, sep="\n")
    sys.exit(1)

backup = root / (".krux_local_backup_" + datetime.now().strftime("%Y%m%d_%H%M%S"))
for f in files:
    dst = backup / f
    dst.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(root / f, dst)

def replace_once(file, old, new, label):
    p = root / file
    s = p.read_text(encoding="utf-8")
    n = s.count(old)
    if n != 1:
        raise RuntimeError(f"{label}: attendu 1 occurrence, trouvé {n}; arrêt sans remplacement de ce bloc.")
    p.write_text(s.replace(old, new, 1), encoding="utf-8")
    print("OK:", label)

try:
    colors = "\n".join([
        '<?xml version="1.0" encoding="utf-8"?>',
        '<resources>',
        '    <!-- Palette monochrome KRUX -->',
        '    <color name="bg_dark_top">#000000</color>',
        '    <color name="bg_dark_bottom">#080808</color>',
        '    <color name="card_bg">#111111</color>',
        '    <color name="input_bg">#101010</color>',
        '    <color name="input_stroke">#383838</color>',
        '    <color name="accent_blue">#D8D8D8</color>',
        '    <color name="accent_purple">#D8D8D8</color>',
        '    <color name="accent_mauve">#BDBDBD</color>',
        '    <color name="accent_pink">#F0F0F0</color>',
        '    <color name="text_white">#FFFFFF</color>',
        '    <color name="text_gray">#B8B8B8</color>',
        '    <color name="text_muted">#777777</color>',
        '    <color name="surface_dark">#050505</color>',
        '    <color name="surface_mauve">#151515</color>',
        '    <color name="border_purple">#363636</color>',
        '</resources>', ''
    ])
    (root / files[3]).write_text(colors, encoding="utf-8")
    print("OK: palette noir et blanc")

    replace_once(files[2],
        '      + "Tu es direct, concis et efficace. "\n',
        '      + "Tu es direct, concis et efficace. "\n'
        '      + "Réponds en texte simple pour les demandes courtes. Utilise titres, listes ou tableaux Markdown seulement si cela améliore la lisibilité. "\n'
        '      + "Ne prétends jamais avoir recherché, vérifié, calculé ou lu un fichier si l’opération n’a pas réellement été effectuée. "\n'
        '      + "Ne révèle pas de raisonnement interne privé ; le statut visible résume uniquement les opérations réellement exécutées. "\n',
        "règles de réponse")

    old_router = '\n'.join([
        '    private static boolean isWebRequest(String p) {',
        '        return p.contains("dernière")',
        '                || p.contains("dernier")',
        '                || p.contains("aujourd\'hui")',
        '                || p.contains("actualités")',
        '                || p.contains("actualité")',
        '                || p.contains("news")',
        '                || p.contains("récent")',
        '                || p.contains("récente")',
        '                || p.contains("actuellement")',
        '                || p.contains("en ce moment")',
        '                || p.contains("version actuelle")',
        '                || p.contains("dernière version")',
        '                || p.contains("sur internet")',
        '                || p.contains("sur le web")',
        '                || p.contains("cherche sur internet")',
        '                || p.contains("sources");',
        '    }', ''
    ])
    new_router = '\n'.join([
        '    private static boolean isWebRequest(String p) {',
        '        return p.contains("dernière")',
        '                || p.contains("dernier")',
        '                || p.contains("aujourd\'hui")',
        '                || p.contains("actualité")',
        '                || p.contains("news")',
        '                || p.contains("récent")',
        '                || p.contains("récente")',
        '                || p.contains("actuellement")',
        '                || p.contains("en ce moment")',
        '                || p.contains("version actuelle")',
        '                || p.contains("sur internet")',
        '                || p.contains("sur le web")',
        '                || p.contains("cherche sur internet")',
        '                || p.contains("cherche sur le web")',
        '                || p.contains("recherche web")',
        '                || p.contains("fais une recherche")',
        '                || p.contains("vérifie sur internet")',
        '                || p.contains("vérifie en ligne")',
        '                || p.contains("fact-check")',
        '                || p.contains("sources")',
        '                || p.contains("source fiable")',
        '                || p.contains("prix actuel")',
        '                || p.contains("cours actuel")',
        '                || p.contains("météo")',
        '                || p.contains("taux de change")',
        '                || p.contains("résultats du match")',
        '                || p.contains("qui a gagné")',
        '                || p.contains("date de sortie");',
        '    }', ''
    ])
    replace_once(files[1], old_router, new_router, "routage Web")

    replace_once(files[0],
        '            @Override\n            public void onPartialResponse(String partialResponse, String modelBrand) {\n',
        '            @Override\n'
        '            public void onWebSearchStarted(String query) {\n'
        '                runOnUiThread(() -> setKruxState(KruxState.SEARCHING));\n'
        '                android.util.Log.d("KRUX_TOOLS", "Recherche Web lancée : " + query);\n'
        '            }\n\n'
        '            @Override\n'
        '            public void onWebSearchFinished(List<SearchResult> results) {\n'
        '                runOnUiThread(() -> {\n'
        '                    if (results != null && activeAiMessage == aiMessage) {\n'
        '                        aiMessage.setSources(results);\n'
        '                        int position = messageList.indexOf(aiMessage);\n'
        '                        if (position >= 0 && chatAdapter != null) {\n'
        '                            chatAdapter.notifyItemChanged(position);\n'
        '                        }\n'
        '                    }\n'
        '                    setKruxState(KruxState.GENERATING);\n'
        '                });\n'
        '                android.util.Log.d("KRUX_TOOLS",\n'
        '                        "Recherche Web terminée : " + (results == null ? 0 : results.size()) + " source(s)");\n'
        '            }\n\n'
        '            @Override\n'
        '            public void onPartialResponse(String partialResponse, String modelBrand) {\n',
        "callbacks de recherche et affichage des sources")

except Exception as e:
    print("ARRÊT :", e)
    print("Sauvegarde disponible :", backup)
    print("Si des modifications ont été appliquées avant l'erreur, restaure les fichiers depuis cette sauvegarde.")
    sys.exit(2)

print("\nTerminé : modifications locales seulement; aucun commit ni push.")
print("Sauvegarde :", backup)
print("À faire ensuite : compiler puis tester une demande nécessitant une recherche Web.")
