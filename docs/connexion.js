import { init, api, frenchError, configured } from "./auth.js";
const $ = (i) => document.getElementById(i), msg = $("msg");
const say = (t, ok) => { msg.textContent = t; msg.className = "msg" + (ok ? " ok" : ""); };
function busy(on) { $("go").disabled = on; $("google").disabled = on; }

(async () => {
  if (!configured) { say("La connexion sera bientôt disponible."); busy(true); return; }
  const auth = await init(), m = api();
  m.onAuthStateChanged(auth, (u) => { if (u) location.replace("compte.html"); });

  $("f").addEventListener("submit", async (e) => {
    e.preventDefault();
    const email = $("email").value.trim(), pw = $("pw").value;
    if (!email || !pw) return say("Entre ton e-mail et ton mot de passe.");
    busy(true); say("");
    try { await m.signInWithEmailAndPassword(auth, email, pw); }
    catch (err) { say(frenchError(err)); busy(false); }
  });

  $("google").addEventListener("click", async () => {
    busy(true); say("");
    const p = new m.GoogleAuthProvider();
    try { await m.signInWithPopup(auth, p); }
    catch (err) {
      if ((err.code || "").includes("popup-blocked")) { try { await m.signInWithRedirect(auth, p); return; } catch (e2) { err = e2; } }
      say(frenchError(err)); busy(false);
    }
  });

  $("reset").addEventListener("click", async () => {
    const email = $("email").value.trim();
    if (!email) return say("Entre d'abord ton e-mail ci-dessus.");
    try { await m.sendPasswordResetEmail(auth, email); say("Un e-mail de réinitialisation vient d'être envoyé.", true); }
    catch (err) { say(frenchError(err)); }
  });
})();
