# krux-ai-proxy (Worker Cloudflare)

Les clés des fournisseurs IA (Groq, Cerebras, Mistral, Hugging Face, Gemini) restent **dans Cloudflare**.
L'app envoie seulement le jeton Firebase de l'utilisateur connecté ; le Worker le vérifie puis appelle le fournisseur.

## Déploiement (depuis `worker/ai-proxy`)

```bash
npx wrangler login
npx wrangler secret put GROQ_API_KEY
npx wrangler secret put CEREBRAS_API_KEY
npx wrangler secret put MISTRAL_API_KEY
npx wrangler secret put HF_API_KEY
npx wrangler secret put GEMINI_API_KEY
npx wrangler deploy
```

`wrangler deploy` affiche l'adresse du Worker (ex. `https://krux-ai-proxy.TON-COMPTE.workers.dev`).
Mets-la dans `app/src/main/assets/krux_config.json`, section `ai_proxy` → `url`.

## Routes

| Route (POST) | Fournisseur |
|---|---|
| `/ai/groq/chat` | Groq |
| `/ai/cerebras/chat` | Cerebras |
| `/ai/mistral/chat` | Mistral |
| `/ai/huggingface/chat?model=ID` | Hugging Face |
| `/ai/gemini/stream?model=ID` | Gemini (flux SSE) |

En-tête obligatoire : `Authorization: Bearer <jeton Firebase>`.
Limites : corps de 256 Ko max, `max_tokens` plafonné à 16384.

## Après le déploiement

Les anciennes clés ont été dans des APK : **révoque-les et crées-en de nouvelles** chez chaque fournisseur
avant de les mettre dans les secrets Cloudflare.

## Déployer depuis le tableau de bord Cloudflare (sans ligne de commande)

1. Workers et Pages → Create → Create Worker, nom `krux-ai-proxy`, puis Deploy.
2. Edit code : colle le contenu de `src/index.js`, puis Deploy.
3. Settings → Variables and Secrets : ajoute `FIREBASE_PROJECT_ID` = `krux-9fd33` (texte), puis les secrets
   `GROQ_API_KEY`, `CEREBRAS_API_KEY`, `MISTRAL_API_KEY`, `HF_API_KEY`, `GEMINI_API_KEY` (type Secret).
4. Settings → Bindings → Add → D1 database : nom de variable `DB`, base `krux-db`.
5. L'adresse du Worker est `https://krux-ai-proxy.<ton-sous-domaine>.workers.dev` ; mets-la dans `krux_config.json` (`ai_proxy.url`).

## Limites par utilisateur

120 appels par heure et 800 par jour et par compte (voir `LIMIT_HOUR` et `LIMIT_DAY` en haut de `index.js`).
Au-delà, le Worker répond 429. Sans la liaison D1 `DB`, il n'y a aucune limite.
