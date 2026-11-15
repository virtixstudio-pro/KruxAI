import { firebaseConfig, configured } from "./firebase-config.js";
const V = "10.12.2", B = "https://www.gstatic.com/firebasejs/" + V;
let mods = null, auth = null;

export { configured };

export async function init() {
  if (!configured) return null;
  if (auth) return auth;
  const app = await import(B + "/firebase-app.js");
  mods = await import(B + "/firebase-auth.js");
  auth = mods.getAuth(app.initializeApp(firebaseConfig));
  auth.languageCode = "fr";
  return auth;
}
export const api = () => mods;

export function frenchError(e) {
  const c = (e && e.code) || "";
  if (c.includes("invalid-credential") || c.includes("wrong-password") || c.includes("user-not-found")) return "E-mail ou mot de passe incorrect.";
  if (c.includes("invalid-email")) return "Cette adresse e-mail n'est pas valide.";
  if (c.includes("too-many-requests")) return "Trop de tentatives. Réessaie dans quelques minutes.";
  if (c.includes("network")) return "Pas de connexion. Vérifie ton réseau et réessaie.";
  if (c.includes("popup-closed") || c.includes("cancelled-popup")) return "Connexion annulée.";
  if (c.includes("unauthorized-domain")) return "Ce site n'est pas encore autorisé dans Firebase (domaines autorisés).";
  return "Connexion impossible. Réessaie.";
}

// Met à jour le bouton de l'en-tête : « Se connecter » ou « Mon compte ».
export async function navState() {
  const el = document.getElementById("navAccount");
  if (!el) return;
  const a = await init();
  if (!a) return;
  mods.onAuthStateChanged(a, (u) => {
    el.textContent = u ? "Mon compte" : "Se connecter";
    el.href = u ? "compte.html" : "connexion.html";
  });
}
