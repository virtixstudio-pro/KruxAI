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
