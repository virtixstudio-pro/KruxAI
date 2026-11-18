// Tests de l'aperçu KRUX (Node + jsdom). Prérequis : sh build.sh .out
const fs = require('fs'), path = require('path');
const { JSDOM } = require('jsdom');
const Core = require('../src/core.js');
const OUT = path.join(__dirname, '..', '.out');
const Babel = require(path.join(__dirname, '..', 'node_modules', '@babel', 'standalone'));
const fx = n => fs.readFileSync(path.join(__dirname, 'fixtures', n), 'utf8');

let fails = 0;
const ok = (c, n) => { console.log((c ? 'PASS ' : 'FAIL ') + n); if (!c) fails++; };
const sleep = ms => new Promise(r => setTimeout(r, ms));

const CSS = [
  ':root { --primary: #4f46e5; --surface: #ffffff; --radius: 12px; }',
  '/* --commentaire: #000000; */',
  '@media (prefers-color-scheme: dark) { :root { --primary: #a5b4fc; --surface: #111111; } }'
].join('\n');
const base = () => ({ 'src/main.tsx': fx('main.tsx'), 'src/App.tsx': fx('App.tsx'), 'src/styles.css': CSS });

function runIframe(files, rtSrc) {
  const comp = Core.compileProject(Babel, files);
  if (comp.errors.length) throw new Error('compile: ' + JSON.stringify(comp.errors));
  const html = Core.buildSrcdoc(files, comp, rtSrc);
  const dom = new JSDOM(html, { runScripts: 'dangerously', pretendToBeVisual: true, url: 'https://preview.test/' });
  const posted = [];
  dom.window.addEventListener('message', e => { if (e.data && e.data.type) posted.push(e.data); });
  return { dom, w: dom.window, d: dom.window.document, posted, html };
}
const setVal = (w, el, v) => {
  Object.getOwnPropertyDescriptor(w.HTMLInputElement.prototype, 'value').set.call(el, v);
  el.dispatchEvent(new w.Event('input', { bubbles: true }));
};

(async () => {
  const rtSrc = new Function('var window={};' + fs.readFileSync(path.join(OUT, 'rt-src.js'), 'utf8') + ';return window.KRUX_RT_SRC;')();
  ok(typeof rtSrc === 'string' && rtSrc.length > 100000, 'runtime React construit (' + Math.round(rtSrc.length / 1024) + ' ko)');

  // ---------------- couleurs
  let f = base();
  const cols = Core.listColors(f);
  ok(cols.length === 4, 'listColors : 4 couleurs (clair+sombre, sans rayon ni commentaire)');
  ok(cols.some(c => c.name === '--primary' && c.dark && c.value === '#a5b4fc'), 'listColors : thème sombre reconnu');
  let r = Core.setColor(f, '--primary', false, '#ff0000');
  ok(r.ok && /--primary: #ff0000;/.test(r.files['src/styles.css']) && /--primary: #a5b4fc/.test(r.files['src/styles.css']), 'setColor clair : le sombre reste intact');
  r = Core.setColor(f, '--primary', true, 'rgb(1, 2, 3)');
  ok(r.ok && /--primary: rgb\(1, 2, 3\)/.test(r.files['src/styles.css']) && /--primary: #4f46e5/.test(r.files['src/styles.css']), 'setColor sombre + rgb()');
  ok(Core.setColor(f, '--primary', false, 'red; } body{display:none').reason === 'invalid', 'setColor refuse une valeur piégée');
  ok(Core.setColor(f, '--nope', false, '#fff').reason === 'not_found', 'setColor : variable inconnue');
  ok(/--commentaire: #000000/.test(Core.setColor(f, '--primary', false, '#123456').files['src/styles.css']), 'setColor ne touche pas aux commentaires');

  // ---------------- textes
  r = Core.replaceText(f, 'Notes', 'Mes notes');
  ok(r.ok && r.files['src/App.tsx'].includes('<h1>Mes notes</h1>') && r.where.kind === 'jsx', 'texte JSX remplacé');
  r = Core.replaceText(f, 'Écrire une note…', 'Une idée…');
  ok(r.ok && r.files['src/App.tsx'].includes('placeholder="Une idée…"'), 'attribut (chaîne) remplacé, guillemets conservés');
  r = Core.replaceText(f, 'Aucune note pour l’instant.', 'Rien ici.');
  ok(r.ok && r.files['src/App.tsx'].includes('Rien ici.'), 'texte avec apostrophe typographique');
  ok(Core.replaceText(f, 'Hello', 'x').reason === 'not_found', 'texte inconnu -> not_found');
  ok(Core.replaceText(f, 'react', 'x').reason === 'not_found', 'les imports ne sont jamais modifiés');
  ok(Core.replaceText(f, 'Notes', 'Notes').reason === 'same' && Core.replaceText(f, 'Notes', '  ').reason === 'empty', 'identique / vide refusés');
  const dup = { 'src/A.tsx': 'export const A = () => <div><button>Valider</button><button>Valider</button></div>;' };
  r = Core.replaceText(dup, 'Valider', 'OK');
  ok(r.reason === 'multiple' && r.count === 2, 'texte en double -> multiple (2), aucune modification');
  const ml = { 'src/B.tsx': 'export const B = () => (\n  <p>\n    Bonjour   le\n    monde\n  </p>\n);' };
  r = Core.replaceText(ml, 'Bonjour le monde', 'Salut');
  ok(r.ok && /<p>\n    Salut\n  <\/p>/.test(r.files['src/B.tsx']), 'JSX multi-lignes : espaces normalisés, indentation conservée');
  r = Core.replaceText(f, 'Ajouter', 'A & B <ok> {x}');
  ok(r.ok && r.files['src/App.tsx'].includes('{"A & B <ok> {x}"}'), 'JSX : caractères spéciaux protégés par une expression');
  r = Core.replaceText(f, 'Écrire une note…', 'Il a dit "oui" \\ non');
  ok(r.ok && r.files['src/App.tsx'].includes('placeholder="Il a dit \\"oui\\" \\\\ non"'), 'chaîne : guillemets et antislash échappés');
  const tpl = { 'src/C.ts': 'export const t = `Titre fixe`;' };
  r = Core.replaceText(tpl, 'Titre fixe', 'a${b}`c');
  ok(r.ok && r.files['src/C.ts'] === 'export const t = `a\\${b}\\`c`;', 'gabarit : ${ et ` neutralisés');
  r = Core.replaceText(f, 'Notes', 'Mes notes');
  ok(Core.compileProject(Babel, r.files).errors.length === 0, 'le code retouché compile toujours');
  const inj = Core.replaceText({ 'src/D.tsx': 'export const D = () => <i title="x">Salut</i>;' }, 'Salut', '</i><script>alert(1)</script>');
  ok(inj.ok && inj.files['src/D.tsx'].includes('{"</i><script>alert(1)</script>"}') && Core.compileProject(Babel, inj.files).errors.length === 0, 'injection JSX neutralisée (reste du texte)');

  // ---------------- compilation
  const bad = Object.assign(base(), { 'src/Bad.tsx': 'export const x = (;' });
  const ce = Core.compileProject(Babel, bad).errors;
  ok(ce.length === 1 && ce[0].file === 'src/Bad.tsx' && ce[0].line === 1 && !/^\//.test(ce[0].message), 'erreur de syntaxe : fichier, ligne, message propre (' + (ce[0] && ce[0].message) + ')');

  // ---------------- exécution dans l'iframe (jsdom)
  let t = runIframe(base(), rtSrc);
  await sleep(150);
  ok(t.d.querySelector('h1') && t.d.querySelector('h1').textContent === 'Notes', 'l’app s’affiche (React + TSX compilé par Babel)');
  ok(t.d.querySelector('.empty') && /Aucune note/.test(t.d.querySelector('.empty').textContent), 'état vide affiché');
  ok(t.posted.some(m => m.type === 'ready'), 'message ready envoyé');
  ok(!!t.d.querySelector('style') && /--accent|--primary/.test(Array.from(t.d.querySelectorAll('style')).map(s => s.textContent).join('')), 'CSS importé injecté');
  setVal(t.w, t.d.querySelector('input'), 'idée un');
  Array.from(t.d.querySelectorAll('button')).find(b => b.textContent === 'Ajouter').click();
  await sleep(100);
  ok(t.d.querySelectorAll('li').length === 1 && t.d.querySelector('li span').textContent === 'idée un', 'interaction React : une note ajoutée');
  ok(/idée un/.test(t.w.localStorage.getItem('notes') || ''), 'localStorage simulé en mémoire (iframe sans stockage)');
  Array.from(t.d.querySelectorAll('button')).find(b => b.textContent === 'Partager').click();
  ok(/Partager : idée un/.test(t.d.body.textContent), 'pont window.Krux simulé (toast)');

  // mode retouche
  Array.from(t.d.querySelectorAll('button')).find(b => b.textContent === 'Supprimer');
  t.d.querySelector('h1').click(); await sleep(30);
  ok(!t.posted.some(m => m.type === 'tap'), 'hors mode retouche : aucun message tap');
  t.w.dispatchEvent(new t.w.MessageEvent('message', { data: { type: 'mode', on: true }, source: t.w.parent }));
  ok(t.d.documentElement.classList.contains('__krux_edit'), 'mode retouche : contours affichés');
  t.d.querySelector('h1').click(); await sleep(30);
  ok(t.posted.some(m => m.type === 'tap' && m.text === 'Notes'), 'mode retouche : le texte touché est envoyé');
  let before = t.d.querySelectorAll('li').length;
  Array.from(t.d.querySelectorAll('button')).find(b => b.textContent === 'Ajouter').click(); await sleep(30);
  ok(t.d.querySelectorAll('li').length === before && t.posted.some(m => m.type === 'tap' && m.text === 'Ajouter'), 'mode retouche : le clic ne déclenche pas le bouton');
  t.d.querySelector('input').click(); await sleep(30);
  ok(t.posted.some(m => m.type === 'tap' && m.text === 'Écrire une note…'), 'mode retouche : placeholder détecté');
  t.w.dispatchEvent(new t.w.MessageEvent('message', { data: { type: 'mode', on: false }, source: null }));
  ok(t.d.documentElement.classList.contains('__krux_edit'), 'messages d’une autre source ignorés');

  // erreurs d'exécution
  t = runIframe(Object.assign(base(), { 'src/main.tsx': "import App from './App';\nimport './Missing';\nconsole.log(App);" }), rtSrc);
  await sleep(50);
  ok(t.posted.some(m => m.type === 'error' && /introuvable/i.test(m.message)) && /Erreur/.test(t.d.body.textContent), 'import manquant : erreur signalée et affichée');
  t = runIframe(Object.assign(base(), { 'src/App.tsx': "throw new Error('boom');\nexport default function App(){return null}" }), rtSrc);
  await sleep(50);
  ok(t.posted.some(m => m.type === 'error' && /boom/.test(m.message)), 'exception au chargement : signalée');

  // sécurité de l'inclusion
  const sc = Object.assign(base(), { 'src/App.tsx': "export default function App(){return <p id=\"t\">{\"</script><b>x</b>\"}</p>}" });
  t = runIframe(sc, rtSrc); await sleep(100);
  ok(t.d.querySelector('#t') && t.d.querySelector('#t').textContent === '</script><b>x</b>' && !t.d.querySelector('b'), '</script> dans le code : pas de rupture du document, rendu en texte');
  ok(/Content-Security-Policy[^>]*default-src 'none'/.test(t.html) && !/unsafe-eval/.test(t.html) && !/allow-same-origin/.test(t.html), 'CSP de l’iframe : réseau coupé, sans eval');

  // ---------------- page de contrôle (parent)
  const page = await JSDOM.fromFile(path.join(OUT, 'index.html'), { runScripts: 'dangerously', resources: 'usable', pretendToBeVisual: true });
  await new Promise(res => page.window.addEventListener('load', res));
  const W = page.window, D = W.document, frame = D.getElementById('frame');
  ok(frame.getAttribute('sandbox') === 'allow-scripts', 'iframe : sandbox allow-scripts seulement');
  const start = base();
  W.KruxPreview.load(JSON.stringify(start));
  ok(/__KD\[/.test(frame.srcdoc) && D.getElementById('banner').hidden, 'KruxPreview.load : aperçu généré, pas d’erreur');
  let res = JSON.parse(W.KruxPreview.result());
  ok(res.changed === false && res.files['src/App.tsx'] === start['src/App.tsx'], 'result() : rien modifié au départ');
  const tap = (text, src) => W.dispatchEvent(new W.MessageEvent('message', { data: { type: 'tap', kind: 'text', text }, source: src || frame.contentWindow }));
  tap('Notes', W);
  ok(D.getElementById('dlg').hidden, 'tap venant d’une autre fenêtre ignoré');
  tap('Notes');
  ok(!D.getElementById('dlg').hidden && D.getElementById('dlgText').value === 'Notes', 'tap de l’iframe : fenêtre de texte ouverte');
  D.getElementById('dlgText').value = 'Mes notes'; D.getElementById('dlgOk').click();
  res = JSON.parse(W.KruxPreview.result());
  ok(res.changed && res.files['src/App.tsx'].includes('<h1>Mes notes</h1>') && D.getElementById('dlg').hidden, 'texte modifié sans IA : résultat renvoyé à Java');
  tap('Texte qui n’existe pas');
  ok(D.getElementById('dlg').hidden && /calculé|tel quel/.test(D.getElementById('msg').textContent), 'texte introuvable : prévenu tout de suite, sans fenêtre, avec renvoi vers l’IA');
  tap('Supprimer'); D.getElementById('dlgText').value = 'Effacer';
  ok(!D.getElementById('dlg').hidden, 'texte unique : la fenêtre s’ouvre');
  D.getElementById('dlgCancel').click();
  ok(Core.countText({ 'src/A.tsx': '<b>Valider</b><i>Valider</i>' }, 'Valider') === 2 && Core.countText(start, 'Notes') === 1 && Core.countText(start, '') === 0, 'countText');
  D.getElementById('colBtn').click();
  const rows = D.querySelectorAll('#palList .row');
  ok(rows.length === 4, 'panneau de couleurs : 4 lignes');
  const inp = rows[0].querySelector('input'); inp.value = '#00ff00'; inp.dispatchEvent(new W.Event('change'));
  res = JSON.parse(W.KruxPreview.result());
  ok(/#00ff00/.test(res.files['src/styles.css']) && res.files['src/App.tsx'].includes('Mes notes'), 'couleur modifiée en gardant la retouche de texte');
  inp.value = 'pas une couleur'; inp.dispatchEvent(new W.Event('change'));
  ok(!D.getElementById('msg').hidden && inp.value !== 'pas une couleur', 'couleur invalide refusée');
  W.KruxPreview.load(JSON.stringify(Object.assign(base(), { 'src/Bad.tsx': 'export const x = (;' })));
  ok(!D.getElementById('banner').hidden && /Bad\.tsx/.test(D.getElementById('banner').textContent) && JSON.parse(W.KruxPreview.result()).hasErrors, 'erreur de syntaxe : bandeau + hasErrors');

  console.log(fails === 0 ? 'ALL_OK' : 'FAILS=' + fails);
  process.exit(fails === 0 ? 0 : 1);
})().catch(e => { console.error('CRASH', e); process.exit(2); });
