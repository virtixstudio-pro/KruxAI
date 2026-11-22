#!/bin/sh
# Construit les fichiers de l'aperçu dans app/src/main/assets/preview (ou le dossier donné en argument).
# Appelé par le workflow de build de l'app avant Gradle. Nécessite Node.
set -e
cd "$(dirname "$0")"
OUT="${1:-../app/src/main/assets/preview}"
npm install --omit=dev --no-audit --no-fund --ignore-scripts
mkdir -p "$OUT"
npx esbuild src/runtime-entry.js --bundle --format=iife --target=chrome87 --minify \
  --define:process.env.NODE_ENV=\"production\" --outfile=.rt.js
node -e 'const fs=require("fs");fs.writeFileSync(process.argv[1],"window.KRUX_RT_SRC="+JSON.stringify(fs.readFileSync(".rt.js","utf8"))+";")' "$OUT/rt-src.js"
cp node_modules/@babel/standalone/babel.min.js "$OUT/babel.min.js"
cp src/core.js src/chrome.js src/phone.js src/index.html "$OUT/"
rm -f .rt.js
echo "Aperçu construit dans $OUT"
