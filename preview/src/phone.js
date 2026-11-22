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

  // ---- visage de Krux (même visage partout)
  var FACE = '<svg viewBox="0 0 40 40" width="100%" height="100%"><circle cx="20" cy="20" r="19" fill="#6d5dfc"/>' +
    '<ellipse cx="14" cy="11" rx="8" ry="4" fill="#fff" opacity=".18"/>' +
    '<ellipse class="eye" cx="14" cy="18" rx="2.6" ry="3.6" fill="#fff"/><ellipse class="eye" cx="26" cy="18" rx="2.6" ry="3.6" fill="#fff"/>' +
    '<path d="M13 26q7 6 14 0" stroke="#fff" stroke-width="2.2" fill="none" stroke-linecap="round"/></svg>';
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
    view(v === 'home' ? last : (v === 'app' ? 'notes' : 'app'));
  });
  $('icoApp').addEventListener('click', function () { view('app'); });
  $('icoNotes').addEventListener('click', function () { view('notes'); });

  // ---- heure
  function tick() {
    var d = new Date(), t = ('0' + d.getHours()).slice(-2) + ':' + ('0' + d.getMinutes()).slice(-2);
    $('clock').textContent = t; $('bigClock').textContent = t;
  }
  tick(); setInterval(tick, 20000);

  // ---- réglages rapides (le mode avion coupe Wi-Fi, Bluetooth et point d'accès, comme sur un vrai téléphone)
  var T = [['wifi', 'Wi‑Fi', '📶', 1], ['bt', 'Bluetooth', '🔷', 1], ['air', 'Mode avion', '✈️', 0], ['hot', 'Point d’accès', '📡', 0],
    ['torch', 'Lampe', '🔦', 0], ['rot', 'Rotation auto', '🔄', 1], ['dnd', 'Ne pas déranger', '🌙', 0], ['eco', 'Économie d’énergie', '🔋', 0]];
  var st = {};
  function paint() {
    var icons = '';
    T.forEach(function (t) {
      $('qt-' + t[0]).setAttribute('aria-pressed', st[t[0]] ? 'true' : 'false');
      if (st[t[0]] && t[0] !== 'rot' && t[0] !== 'eco') icons += t[2];
    });
    $('sIcons').textContent = icons;
  }
  function setTile(k, on) {
    st[k] = on;
    if (k === 'air' && on) { st.wifi = false; st.bt = false; st.hot = false; }
    if ((k === 'wifi' || k === 'bt' || k === 'hot') && on) st.air = false;
    paint();
  }
  T.forEach(function (t) {
    st[t[0]] = !!t[3];
    var b = document.createElement('button');
    b.className = 'qt'; b.id = 'qt-' + t[0];
    b.innerHTML = '<span>' + t[2] + '</span><small>' + t[1] + '</small>';
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
    setState(ok ? 'C’est fait ✓' : (msg || 'Impossible pour l’instant.'));
    if (ok) view('app'); // on revoit l'app modifiée
  }
  Array.prototype.forEach.call(document.querySelectorAll('.face'), function (el) { el.addEventListener('click', function (e) { e.stopPropagation(); openKx(); }); });
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

  window.KruxPhone = {
    view: view, setAppName: setAppName, done: done, state: setState,
    heard: function (text, fin) { $('kxText').textContent = text || ''; if (fin) submit(text); }
  };
})();
