#!/usr/bin/env python3
from pathlib import Path
from datetime import datetime
import shutil
import sys

relative = Path("app/src/main/java/com/virtixstudio/kruxai/utils/SystemPromptBuilder.java")
root = Path.cwd()
target = root / relative

if not target.is_file():
    print("ERREUR : lance ce script depuis la racine de ~/KruxAI.")
    print("Fichier introuvable :", relative)
    sys.exit(1)

source = target.read_text(encoding="utf-8")
end_marker = '      + "Ne révèle pas les balises <REMEMBER> à l\'utilisateur dans ta réponse finale.";'
if "PROTOCOLE DE RÉFLEXION OPÉRATIONNELLE" in source:
    print("ARRÊT : le protocole semble déjà présent. Aucune modification effectuée.")
    sys.exit(0)
if source.count(end_marker) != 1:
    print("ARRÊT : marqueur final attendu une seule fois ; trouvé :", source.count(end_marker))
    print("Aucune modification effectuée.")
    sys.exit(2)

backup_dir = root / (".krux_prompt_backup_" + datetime.now().strftime("%Y%m%d_%H%M%S"))
backup = backup_dir / relative
backup.parent.mkdir(parents=True, exist_ok=True)
shutil.copy2(target, backup)

addition = (
    '      + "Ne révèle pas les balises <REMEMBER> à l\'utilisateur dans ta réponse finale. "\n'
    '      + "\\n\\nPROTOCOLE DE RÉFLEXION OPÉRATIONNELLE : "\n'
    '      + "Pour une demande complexe, suis intérieurement un cycle : comprendre l’objectif, repérer les informations manquantes, formuler des hypothèses, choisir une action ou un outil, examiner le résultat, puis décider s’il faut corriger ou répondre. "\n'
    '      + "Avant chaque action importante, vérifie si elle est nécessaire, si les données disponibles la justifient et si elle risque de modifier des fichiers ou des données. "\n'
    '      + "Pose-toi les questions utiles : quel résultat est attendu ? qu’est-ce qui pourrait échouer ? quelle preuve confirmerait le résultat ? quelle est la vérification minimale suivante ? "\n'
    '      + "Ne pose pas ces questions à l’utilisateur si tu peux les résoudre à partir du contexte ou d’un outil autorisé. Demande seulement lorsqu’une information indispensable manque ou qu’une décision lui appartient. "\n'
    '      + "Après chaque étape, compare le résultat obtenu au résultat attendu. En cas d’échec, utilise l’erreur réelle pour choisir la correction suivante et évite les répétitions sans information nouvelle. "\n'
    '      + "Pour une demande simple, réponds directement sans afficher un processus inutile. "\n'
    '      + "\\n\\nAFFICHAGE DU PROCESSUS : "\n'
    '      + "Ne révèle pas de raisonnement interne privé ni de longue chaîne de pensées. Si utile, fournis un résumé bref et vérifiable : objectif, action effectuée, résultat observé, vérification et prochaine étape. "\n'
    '      + "Chaque étape affichée doit correspondre à une opération réellement exécutée. Distingue EN COURS, RÉUSSI, ÉCHEC et NON VÉRIFIÉ. "\n'
    '      + "Ne dis jamais qu’un outil, un test, une recherche ou une correction a réussi sans résultat qui le confirme.";'
)
target.write_text(source.replace(end_marker, addition, 1), encoding="utf-8")
print("OK : protocole de réflexion ajouté sans remplacer les instructions existantes.")
print("Sauvegarde créée :", backup)
print("Aucun commit ni push effectué.")
print("Ce script modifie le prompt uniquement ; il ne crée pas encore l’interface visuelle des étapes.")
