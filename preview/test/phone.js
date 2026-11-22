const { JSDOM } = require('jsdom');
(async () => {
  const page = await JSDOM.fromFile(require('path').join(__dirname, '..', '.out', 'index.html'), { runScripts: 'dangerously', resources: 'usable', pretendToBeVisual: true });
  await new Promise(r => page.window.addEventListener('load', r));
  const W = page.window, D = W.document; let fails = 0;
  const ok = (c, m) => { console.log((c ? 'PASS ' : 'FAIL ') + m); if (!c) fails++; };
  const click = id => D.getElementById(id).dispatchEvent(new W.MouseEvent('click', { bubbles: true }));
  const view = () => D.getElementById('screen').getAttribute('data-view');
  ok(view() === 'app', 'démarre sur l’app demandée');
  ok(D.querySelectorAll('.ico').length === 2, 'accueil : exactement 2 apps');
  ok(D.querySelectorAll('.face svg').length === 3, 'visage Krux présent (barre d’état, réglages, dictée)');
  const frameBefore = D.getElementById('frame');
  click('nHome'); ok(view() === 'home', 'bouton accueil');
  click('icoNotes'); ok(view() === 'notes', 'ouvre le Bloc-notes');
  click('nRecent'); ok(view() === 'app', 'apps récentes : bascule vers l’app');
  click('nBack'); ok(view() === 'home', 'retour : revient à l’accueil');
  click('icoApp'); ok(view() === 'app' && D.getElementById('frame') === frameBefore, 'iframe jamais déplacée ni recréée');
  click('status'); ok(D.getElementById('qs').classList.contains('open'), 'clic barre d’état : réglages rapides ouverts');
  click('qt-air'); ok(D.getElementById('qt-wifi').getAttribute('aria-pressed') === 'false' && D.getElementById('qt-bt').getAttribute('aria-pressed') === 'false', 'mode avion coupe Wi-Fi et Bluetooth');
  click('qt-bt'); ok(D.getElementById('qt-air').getAttribute('aria-pressed') === 'false', 'réactiver Bluetooth coupe le mode avion');
  click('qsHandle'); ok(!D.getElementById('qs').classList.contains('open'), 'panneau refermé');
  // glisser
  const st = D.getElementById('status'), pe = (t, y) => st.dispatchEvent(new W.MouseEvent(t, { bubbles: true, clientY: y }));
  pe('pointerdown', 5); pe('pointermove', 60);
  ok(D.getElementById('qs').classList.contains('open'), 'glisser vers le bas depuis le haut : ouvre les réglages');
  click('nBack'); ok(!D.getElementById('qs').classList.contains('open') && view() === 'app', 'retour ferme d’abord le panneau');
  // Krux sans pont natif
  D.querySelector('#status .face').dispatchEvent(new W.MouseEvent('click', { bubbles: true }));
  ok(!D.getElementById('kx').hidden, 'toucher le visage : mini-écran de dictée');
  D.getElementById('kxIn').value = 'mets le bouton en rouge'; click('kxGo');
  ok(/pas encore branché/.test(D.getElementById('kxState').textContent), 'sans pont natif : message honnête');
  // Krux avec pont natif simulé
  const calls = []; W.KruxNative = { listen() { calls.push('listen'); }, stopListening() { calls.push('stop'); }, ask(t) { calls.push('ask:' + t); } };
  click('kxClose'); D.querySelector('#status .face').dispatchEvent(new W.MouseEvent('click', { bubbles: true }));
  ok(calls.includes('listen'), 'ouverture : lance l’écoute');
  W.KruxPhone.heard('mets le bouton en rouge', false); ok(D.getElementById('kxText').textContent === 'mets le bouton en rouge', 'texte dicté affiché');
  W.KruxPhone.heard('mets le bouton en rouge', true); ok(calls.includes('ask:mets le bouton en rouge'), 'fin de dictée : envoie la demande à Krux');
  click('nHome'); W.KruxPhone.done(true); 
  ok(D.getElementById('kxState').textContent.indexOf('C’est fait') === 0 && view() === 'app', 'terminé : « C’est fait » et retour sur l’app');
  // nom de l'app via KruxPreview.load
  W.KruxPreview.load(JSON.stringify({ 'index.html': '<title>Todo Pro</title>', 'src/main.tsx': 'x' }));
  ok(D.getElementById('appName').textContent === 'Todo Pro' && D.getElementById('appLetter').textContent === 'T', 'nom de l’app tiré du <title>');
  ok(typeof W.KruxPreview.result === 'function', 'KruxPreview.result toujours disponible');
  delete W.KruxNative; W.KruxPhone.enableNative();
  ok(typeof W.KruxNative.listen === 'function' && typeof W.KruxNative.ask === 'function' && typeof W.KruxNative.stopListening === 'function', 'enableNative : pont listen / stopListening / ask défini');
  console.log(fails ? 'FAILS=' + fails : 'PHONE_ALL_OK'); process.exit(fails ? 1 : 0);
})();
