import { init, api, configured } from "./auth.js";
const $ = (i) => document.getElementById(i);
(async () => {
  if (!configured) { $("hello").textContent = "L'espace compte sera bientôt disponible."; return; }
  const auth = await init(), m = api();
  m.onAuthStateChanged(auth, (u) => {
    if (!u) return location.replace("connexion.html");
    $("hello").textContent = "Tu es connecté.";
    $("n").textContent = u.displayName || "—";
    $("e").textContent = u.email || "";
    $("info").hidden = false; $("out").hidden = false;
  });
  $("out").addEventListener("click", async () => { await m.signOut(auth); location.replace("./"); });
})();
