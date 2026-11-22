/* Faux téléphone de l'aperçu KRUX : accueil à 2 apps (l'app demandée + Bloc-notes), barre de navigation,
 * réglages rapides (glisser vers le bas) et visage de Krux (dictée de modifications).
 * L'iframe de l'app n'est jamais déplacée ni rechargée par ce fichier : on change seulement la vue affichée.
 * Krux : l'app Java peut exposer window.KruxNative { listen(), stopListening(), ask(texte) } et appeler
 * KruxPhone.heard(texte, final) puis KruxPhone.done(ok, message). */
(function () {
  'use strict';
  var $ = function (id) { return document.getElementById(id); };
  var screen = $('screen'), qs = $('qs'), kx = $('kx');
  var last = 'app', busy = false;
  var native = function () { return window.KruxNative || null; };

  // ---- icônes SVG (aucun emoji dans le téléphone)
  var ICON = {
    wifi: '<path d="M2 9a15 15 0 0 1 20 0M5 12.5a10 10 0 0 1 14 0M8.5 16a5 5 0 0 1 7 0"/><circle cx="12" cy="19.5" r="1" fill="currentColor"/>',
    bt: '<path d="M7 7l10 10-5 4V3l5 4L7 17"/>',
    air: '<path d="M21 16v-2l-8-5V3.5a1.5 1.5 0 0 0-3 0V9l-8 5v2l8-2.5V19l-2 1.5V22l3.5-1 3.5 1v-1.5L13 19v-5.5z"/>',
    hot: '<circle cx="12" cy="12" r="2"/><path d="M16.2 7.8a6 6 0 0 1 0 8.4M7.8 16.2a6 6 0 0 1 0-8.4M19 5a10 10 0 0 1 0 14M5 19A10 10 0 0 1 5 5"/>',
    torch: '<path d="M8 2h8l-1.5 5h-5zM9.5 7h5v14a1 1 0 0 1-1 1h-3a1 1 0 0 1-1-1z"/>',
    rot: '<path d="M21 12a9 9 0 0 1-15.5 6.2M3 12A9 9 0 0 1 18.5 5.8"/><path d="M18.5 2v4h-4M5.5 22v-4h4"/>',
    dnd: '<path d="M21 13.5A9 9 0 1 1 10.5 3a7 7 0 0 0 10.5 10.5z"/>',
    eco: '<rect x="2" y="7" width="17" height="10" rx="2"/><path d="M22 10.5v3M7 12h5M9.5 9.5v5"/>',
    web: '<circle cx="12" cy="12" r="9"/><path d="M3 12h18M12 3c3 3 3 15 0 18M12 3c-3 3-3 15 0 18"/>',
    notes: '<path d="M6 3h9l4 4v14H6z"/><path d="M14 3v5h5M9 13h7M9 17h5"/>',
    mic: '<rect x="9" y="3" width="6" height="11" rx="3"/><path d="M5 11a7 7 0 0 0 14 0M12 18v3"/>',
    back: '<path d="M15 5l-8 7 8 7z"/>', home: '<circle cx="12" cy="12" r="6.5"/>', recent: '<rect x="6" y="6" width="12" height="12" rx="2"/>',
    close: '<path d="M6 6l12 12M18 6L6 18"/>'
  };
  function ic(n) {
    return '<svg viewBox="0 0 24 24" width="1em" height="1em" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' + ICON[n] + '</svg>';
  }
  Array.prototype.forEach.call(document.querySelectorAll('[data-ic]'), function (el) { el.innerHTML = ic(el.getAttribute('data-ic')); });

  // ---- visage de Krux (même visage partout)
  var FACE = '<svg viewBox="40 30 432 420" width="100%" height="100%"><circle cx="256" cy="250" r="214" fill="#0b0b14"/><g fill="none" stroke-linecap="round" stroke-linejoin="round"><path d="M66 252C52 170 108 90 190 78M446 252C460 170 404 90 322 78" stroke="#22D3EE" stroke-width="14"/><path d="M256 96C366 96 436 176 436 264C436 360 364 428 256 428C148 428 76 360 76 264C76 176 146 96 256 96Z" stroke="#22D3EE" stroke-width="16"/><path d="M128 190C160 140 232 118 306 130C270 138 220 152 196 188C176 170 150 172 128 190Z" stroke="#A855F7" stroke-width="13"/><path d="M256 96C252 64 276 44 302 52" stroke="#A855F7" stroke-width="12"/><circle cx="302" cy="52" r="14" fill="#22D3EE" stroke="none"/><path d="M160 214Q196 196 230 212M282 212Q316 196 352 214" stroke="#A855F7" stroke-width="12"/></g><g class="eye"><ellipse cx="196" cy="268" rx="28" ry="34" fill="none" stroke="#A855F7" stroke-width="15"/><circle cx="196" cy="272" r="15" fill="#A855F7"/><circle cx="191" cy="264" r="6" fill="#22D3EE"/></g><g class="eye"><ellipse cx="316" cy="268" rx="28" ry="34" fill="none" stroke="#A855F7" stroke-width="15"/><circle cx="316" cy="272" r="15" fill="#A855F7"/><circle cx="311" cy="264" r="6" fill="#22D3EE"/></g><g fill="none" stroke-linecap="round"><path d="M124 318l14-10M140 326l14-10M358 308l14 10M374 300l14 10" stroke="#FF7AA8" stroke-width="10"/><path d="M224 340Q256 372 288 340Q256 364 224 340Z" stroke="#A855F7" stroke-width="13" stroke-linejoin="round"/></g></svg>';
  Array.prototype.forEach.call(document.querySelectorAll('.face'), function (el) { el.innerHTML = FACE; });

  // ---- vues : home | app | notes
  function closeQs() { qs.classList.remove('open'); }
  function openQs() { closeKx(); qs.classList.add('open'); }
  function view(v) { if (v !== 'home') last = v; screen.setAttribute('data-view', v); closeQs(); closeKx(); }
  $('nHome').addEventListener('click', function () { view('home'); });
  $('nBack').addEventListener('click', function () {
    if (!kx.hidden) closeKx(); else if (qs.classList.contains('open')) closeQs(); else if (screen.getAttribute('data-view') !== 'home') view('home');
  });
  $('nRecent').addEventListener('click', function () {
    var v = screen.getAttribute('data-view');
    view(v === 'home' ? last : (v === 'app' ? 'notes' : (v === 'notes' ? 'web' : 'app')));
  });
  $('icoApp').addEventListener('click', function () { view('app'); });
  $('icoNotes').addEventListener('click', function () { view('notes'); });
  $('icoWeb').addEventListener('click', function () { view('web'); });
  // ---- Navigateur : adresse ou recherche (Wikipédia), certains sites refusent d'être affichés ici
  var wf = $('webFrame'), wu = $('webUrl'), wh = [];
  var START = '<body style="font:16px system-ui;padding:22px;background:#fff;color:#111"><h2>Navigateur</h2><p>Tape une adresse ou un mot en haut.</p><p><a href="https://fr.wikipedia.org" target="_self">Wikipédia</a> · <a href="https://www.openstreetmap.org" target="_self">Cartes</a> · <a href="https://example.com" target="_self">example.com</a></p><p style="opacity:.6;font-size:13px">Certains sites interdisent l’affichage intégré : utilise ↗.</p></body>';
  function webGo(q) {
    q = (q || '').trim(); if (!q) return;
    var u = /^https?:\/\//i.test(q) ? q : (/^[\w-]+(\.[\w-]+)+(\/\S*)?$/.test(q) ? 'https://' + q : 'https://fr.wikipedia.org/w/index.php?search=' + encodeURIComponent(q));
    wh.push(u); wu.value = u; wf.removeAttribute('srcdoc'); wf.src = u;
  }
  $('webGo').addEventListener('click', function () { webGo(wu.value); });
  wu.addEventListener('keydown', function (e) { if (e.key === 'Enter') webGo(wu.value); });
  $('webBack').addEventListener('click', function () { wh.pop(); var u = wh.pop(); if (u) webGo(u); else { wu.value = ''; wf.removeAttribute('src'); wf.srcdoc = START; } });
  $('webOut').addEventListener('click', function () {
    var u = wu.value; if (!u) return;
    try { if (native() && native().openUrl) native().openUrl(u); else window.open(u, '_blank'); } catch (e) { }
  });
  wf.srcdoc = START;


  // ---- heure
  function tick() {
    var d = new Date(), t = ('0' + d.getHours()).slice(-2) + ':' + ('0' + d.getMinutes()).slice(-2);
    $('clock').textContent = t; $('bigClock').textContent = t;
  }
  tick(); setInterval(tick, 20000);

  // ---- réglages rapides (le mode avion coupe Wi-Fi, Bluetooth et point d'accès, comme sur un vrai téléphone)
  var T = [['wifi', 'Wi‑Fi', 1], ['bt', 'Bluetooth', 1], ['air', 'Mode avion', 0], ['hot', 'Point d’accès', 0],
    ['torch', 'Lampe', 0], ['rot', 'Rotation auto', 1], ['dnd', 'Ne pas déranger', 0], ['eco', 'Économie d’énergie', 0]];
  var st = {};
  function paint() {
    var icons = '';
    T.forEach(function (t) {
      $('qt-' + t[0]).setAttribute('aria-pressed', st[t[0]] ? 'true' : 'false');
      if (st[t[0]] && t[0] !== 'rot' && t[0] !== 'eco') icons += ic(t[0]);
    });
    $('sIcons').innerHTML = icons;
  }
  function setTile(k, on) {
    st[k] = on;
    if (k === 'air' && on) { st.wifi = false; st.bt = false; st.hot = false; }
    if ((k === 'wifi' || k === 'bt' || k === 'hot') && on) st.air = false;
    paint();
  }
  T.forEach(function (t) {
    st[t[0]] = !!t[2];
    var b = document.createElement('button');
    b.className = 'qt'; b.id = 'qt-' + t[0];
    b.innerHTML = '<span>' + ic(t[0]) + '</span><small>' + t[1] + '</small>';
    b.addEventListener('click', function () { setTile(t[0], !st[t[0]]); });
    $('qsGrid').appendChild(b);
  });
  paint();
  $('bright').addEventListener('input', function () { $('dim').style.opacity = String((1 - this.value / 100) * 0.7); });

  // glisser vers le bas depuis la barre d'état = ouvrir ; vers le haut sur le panneau = fermer
  function swipe(el, onUp, onDown) {
    var y0 = null;
    el.addEventListener('pointerdown', function (e) { y0 = e.clientY; });
    el.addEventListener('pointermove', function (e) {
      if (y0 === null) return;
      var dy = e.clientY - y0;
      if (dy > 36) { y0 = null; onDown(); } else if (dy < -36) { y0 = null; onUp(); }
    });
    ['pointerup', 'pointercancel', 'pointerleave'].forEach(function (n) { el.addEventListener(n, function () { y0 = null; }); });
  }
  swipe($('status'), closeQs, openQs);
  swipe(qs, closeQs, function () { });
  $('status').addEventListener('click', function (e) {
    if (e.target.closest && e.target.closest('.face')) return;
    if (qs.classList.contains('open')) closeQs(); else openQs();
  });
  $('qsHandle').addEventListener('click', closeQs);

  // ---- Bloc-notes
  var notes = $('notesText');
  try { notes.value = window.localStorage.getItem('krux_phone_notes') || ''; } catch (e) { }
  notes.addEventListener('input', function () { try { window.localStorage.setItem('krux_phone_notes', notes.value); } catch (e) { } });

  // ---- Krux : dictée de modifications (l'app existante est modifiée, jamais recréée)
  function setState(t) { $('kxState').textContent = t; }
  function closeKx() {
    if (kx.hidden) return;
    kx.hidden = true;
    var n = native();
    if (n && n.stopListening) { try { n.stopListening(); } catch (e) { } }
  }
  function listen() {
    var n = native();
    if (n && n.listen) { try { n.listen(); setState('Je t’écoute…'); } catch (e) { setState('Micro indisponible : écris ta demande.'); } }
    else setState('Écris ta demande ci-dessous.');
  }
  function openKx() {
    closeQs(); kx.hidden = false; $('kxText').textContent = ''; $('kxIn').value = '';
    if (!busy) listen();
  }
  function submit(text) {
    text = (text || '').trim();
    if (!text || busy) return;
    var n = native();
    $('kxText').textContent = text;
    if (!n || !n.ask) { setState('Krux n’est pas encore branché dans cet aperçu.'); return; }
    busy = true; setState('Krux travaille…');
    try { n.ask(text); } catch (e) { done(false, 'Erreur : ' + e.message); }
  }
  function done(ok, msg) {
    busy = false;
    setState(ok ? 'C’est fait.' : (msg || 'Impossible pour l’instant.'));
    if (ok) view('app'); // on revoit l'app modifiée
  }
  Array.prototype.forEach.call(document.querySelectorAll('.face'), function (el) { el.addEventListener('click', function (e) { e.stopPropagation(); openKx(); }); });
  (function () { // avatar Krux hors du téléphone : déplaçable, un appui ouvre la dictée
    var f = $('kxFab'), st = $('stage'); if (!f) return;
    f.innerHTML = FACE;
    var ox = 0, oy = 0, sx = 0, sy = 0, moved = false, on = false;
    f.addEventListener('pointerdown', function (e) { on = true; moved = false; sx = e.clientX; sy = e.clientY; ox = f.offsetLeft; oy = f.offsetTop; try { f.setPointerCapture(e.pointerId); } catch (x) { } });
    f.addEventListener('pointermove', function (e) {
      if (!on) return;
      var dx = e.clientX - sx, dy = e.clientY - sy;
      if (Math.abs(dx) + Math.abs(dy) > 6) moved = true;
      if (!moved) return;
      f.style.left = Math.max(0, Math.min(st.clientWidth - f.offsetWidth, ox + dx)) + 'px';
      f.style.top = Math.max(0, Math.min(st.clientHeight - f.offsetHeight, oy + dy)) + 'px';
    });
    ['pointerup', 'pointercancel'].forEach(function (n) { f.addEventListener(n, function () { if (on && !moved && n === 'pointerup') openKx(); on = false; }); });
  })();
  $('kxClose').addEventListener('click', closeKx);
  $('kxMic').addEventListener('click', function () { if (!busy) listen(); });
  $('kxGo').addEventListener('click', function () { submit($('kxIn').value); $('kxIn').value = ''; });

  // nom de l'app sur l'accueil : <title> de index.html si présent, sinon « Mon app »
  function setAppName(n) {
    n = (n || '').trim() || 'Mon app';
    $('appName').textContent = n; $('appLetter').textContent = n.charAt(0).toUpperCase();
  }
  var KP = window.KruxPreview;
  if (KP && KP.load) {
    var load = KP.load;
    KP.load = function (json) {
      try {
        var f = JSON.parse(json), h = f['index.html'] || f['public/index.html'] || '', m = /<title>([^<]{1,30})<\/title>/i.exec(h);
        setAppName(m ? m[1] : '');
      } catch (e) { }
      return load.apply(KP, arguments);
    };
  }

  // Pont vers l'app Java : navigation du cadre principal vers krux://listen | stop | ask?t=... (interceptée par la WebView).
  // Défini seulement quand Java l'active (enableNative) ; l'iframe de l'app n'y a pas accès.
  var navN = 0;
  function go(cmd, text) {
    navN++;
    window.location.href = 'krux://' + cmd + '?n=' + navN + (text ? '&t=' + encodeURIComponent(text) : '');
  }
  function enableNative() {
    window.KruxNative = {
      listen: function () { go('listen'); },
      stopListening: function () { go('stop'); },
      ask: function (t) { go('ask', t); }
    };
  }

  window.KruxPhone = {
    enableNative: enableNative, view: view, setAppName: setAppName, done: done, state: setState,
    heard: function (text, fin) { $('kxText').textContent = text || ''; if (fin) submit(text); }
  };
})();
