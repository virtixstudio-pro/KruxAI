// Proxy IA de Krux : l'app envoie le jeton Firebase de l'utilisateur, le Worker le vérifie
// puis appelle le fournisseur avec la clé stockée dans les secrets Cloudflare.

const MAX_BODY_BYTES = 256 * 1024;
const MAX_TOKENS_CAP = 16384;
const MODEL_RE = /^[A-Za-z0-9][A-Za-z0-9._:\/-]{0,99}$/;

const OPENAI_STYLE = {
  groq: { url: "https://api.groq.com/openai/v1/chat/completions", secret: "GROQ_API_KEY" },
  cerebras: { url: "https://api.cerebras.ai/v1/chat/completions", secret: "CEREBRAS_API_KEY" },
  mistral: { url: "https://api.mistral.ai/v1/chat/completions", secret: "MISTRAL_API_KEY" },
};

const JWK_URL =
  "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com";

function json(status, obj, extra) {
  return new Response(JSON.stringify(obj), {
    status,
    headers: { "content-type": "application/json", ...(extra || {}) },
  });
}

function b64urlToBytes(s) {
  s = s.replace(/-/g, "+").replace(/_/g, "/");
  while (s.length % 4) s += "=";
  const bin = atob(s);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

function b64urlToJson(s) {
  return JSON.parse(new TextDecoder().decode(b64urlToBytes(s)));
}

/** Vérifie un jeton Firebase (RS256) et retourne l'uid, ou lance une erreur. */
async function verifyFirebaseToken(token, projectId) {
  const parts = token.split(".");
  if (parts.length !== 3) throw new Error("jeton mal formé");
  const header = b64urlToJson(parts[0]);
  const payload = b64urlToJson(parts[1]);
  if (header.alg !== "RS256" || !header.kid) throw new Error("algorithme refusé");

  const now = Math.floor(Date.now() / 1000);
  if (payload.aud !== projectId) throw new Error("audience incorrecte");
  if (payload.iss !== "https://securetoken.google.com/" + projectId) throw new Error("émetteur incorrect");
  if (!payload.sub || typeof payload.sub !== "string") throw new Error("sujet manquant");
  if (!payload.exp || payload.exp < now) throw new Error("jeton expiré");
  if (payload.iat && payload.iat > now + 60) throw new Error("jeton daté du futur");

  const res = await fetch(JWK_URL, { cf: { cacheTtl: 3600, cacheEverything: true } });
  if (!res.ok) throw new Error("clés Google indisponibles");
  const jwk = (await res.json()).keys.find((k) => k.kid === header.kid);
  if (!jwk) throw new Error("clé de signature inconnue");

  const key = await crypto.subtle.importKey(
    "jwk", jwk, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["verify"]);
  const ok = await crypto.subtle.verify(
    "RSASSA-PKCS1-v1_5", key, b64urlToBytes(parts[2]), new TextEncoder().encode(parts[0] + "." + parts[1]));
  if (!ok) throw new Error("signature invalide");
  return payload.sub;
}

async function readLimited(request) {
  const buf = await request.arrayBuffer();
  if (buf.byteLength > MAX_BODY_BYTES) throw new Error("trop gros");
  return buf;
}

/** Renvoie la réponse du fournisseur telle quelle (flux SSE inclus), sans exposer d'en-têtes sensibles. */
function passthrough(upstream) {
  const headers = new Headers();
  headers.set("content-type", upstream.headers.get("content-type") || "application/json");
  const retry = upstream.headers.get("retry-after");
  if (retry) headers.set("retry-after", retry);
  headers.set("cache-control", "no-store");
  return new Response(upstream.body, { status: upstream.status, headers });
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (request.method !== "POST") return json(405, { error: "méthode non autorisée" });

    const m = url.pathname.match(/^\/ai\/(groq|cerebras|mistral|huggingface|gemini)\/(chat|stream)$/);
    if (!m) return json(404, { error: "route inconnue" });
    const provider = m[1];

    // 1) Authentification Firebase
    const auth = request.headers.get("authorization") || "";
    if (!auth.startsWith("Bearer ")) return json(401, { error: "connexion requise" });
    try {
      await verifyFirebaseToken(auth.slice(7), env.FIREBASE_PROJECT_ID);
    } catch (e) {
      return json(401, { error: "jeton refusé : " + e.message });
    }

    // 2) Corps de la requête
    let body;
    try {
      body = await readLimited(request);
    } catch (e) {
      return json(413, { error: "requête trop volumineuse (256 Ko max)" });
    }

    try {
      // Fournisseurs au format OpenAI : Groq, Cerebras, Mistral
      if (OPENAI_STYLE[provider]) {
        const cfg = OPENAI_STYLE[provider];
        const key = env[cfg.secret];
        if (!key) return json(503, { error: "clé " + provider + " non configurée" });
        const payload = JSON.parse(new TextDecoder().decode(body));
        if (!MODEL_RE.test(String(payload.model || ""))) return json(400, { error: "modèle invalide" });
        if (!payload.max_tokens || payload.max_tokens > MAX_TOKENS_CAP) payload.max_tokens = MAX_TOKENS_CAP;
        return passthrough(await fetch(cfg.url, {
          method: "POST",
          headers: { authorization: "Bearer " + key, "content-type": "application/json" },
          body: JSON.stringify(payload),
        }));
      }

      // Hugging Face : le modèle fait partie de l'URL
      if (provider === "huggingface") {
        const key = env.HF_API_KEY;
        if (!key) return json(503, { error: "clé huggingface non configurée" });
        const model = url.searchParams.get("model") || "";
        if (!MODEL_RE.test(model)) return json(400, { error: "modèle invalide" });
        const payload = JSON.parse(new TextDecoder().decode(body));
        if (!payload.max_tokens || payload.max_tokens > MAX_TOKENS_CAP) payload.max_tokens = MAX_TOKENS_CAP;
        return passthrough(await fetch(
          "https://api-inference.huggingface.co/models/" + model + "/v1/chat/completions", {
            method: "POST",
            headers: { authorization: "Bearer " + key, "content-type": "application/json" },
            body: JSON.stringify(payload),
          }));
      }

      // Gemini : flux SSE, clé envoyée en en-tête (jamais dans l'URL)
      const key = env.GEMINI_API_KEY;
      if (!key) return json(503, { error: "clé gemini non configurée" });
      const model = url.searchParams.get("model") || "";
      if (!MODEL_RE.test(model)) return json(400, { error: "modèle invalide" });
      return passthrough(await fetch(
        "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":streamGenerateContent?alt=sse", {
          method: "POST",
          headers: { "x-goog-api-key": key, "content-type": "application/json" },
          body,
        }));
    } catch (e) {
      return json(502, { error: "échec de l'appel au fournisseur" });
    }
  },
};
