/* Page de l'aperçu KRUX : affiche l'app dans une iframe isolée et gère les retouches locales (texte, couleurs).
 * Java appelle KruxPreview.load(json) puis KruxPreview.result() ; l'iframe n'a aucun accès à cette page. */
(function () {
  'use strict';
  var C = window.KruxCore;
  var files = {}, edited = 0, editMode = false, errorCount = 0, pendingText = null;
  var $ = function (id) { return document.getElementById(id); };
  var frame = $('frame');

  function flash(t) {
    var m = $('msg'); m.textContent = t || ''; m.hidden = !t;
    clearTimeout(flash.t); if (t) flash.t = setTimeout(function () { m.hidden = true; }, 4500);
  }
  function banner(t) { var b = $('banner'); b.textContent = t || ''; b.hidden = !t; }

  function render() {
    var compiled = C.compileProject(window.Babel, files);
    errorCount = compiled.errors.length;
    if (errorCount) {
      var e = compiled.errors[0];
      banner('Erreur dans ' + e.file + (e.line ? ' (ligne ' + e.line + ')' : '') + ' : ' + e.message);
      frame.srcdoc = '<!doctype html><body style="font:14px system-ui;padding:16px">L’aperçu n’est pas disponible : le code contient une erreur.</body>';
      return;
    }
    banner('');
    frame.srcdoc = C.buildSrcdoc(files, compiled, window.KRUX_RT_SRC || '');
  }

  function sendMode() { try { frame.contentWindow.postMessage({ type: 'mode', on: editMode }, '*'); } catch (e) { } }
  frame.addEventListener('load', sendMode);

  $('modeBtn').addEventListener('click', function () {
    editMode = !editMode;
    this.setAttribute('aria-pressed', String(editMode));
    $('hint').textContent = editMode ? 'Touche un texte pour le modifier' : '';
    sendMode();
  });

  // ---- texte
  function openText(text) {
    pendingText = text; $('dlgText').value = text; $('dlgErr').textContent = ''; $('dlg').hidden = false; $('dlgText').focus();
  }
  function closeText() { $('dlg').hidden = true; pendingText = null; }
  var REASONS = {
    not_found: 'Ce texte n’est pas écrit tel quel dans le code (il est calculé ou assemblé). Demande cette modification à l’IA.',
    multiple: function (r) { return 'Ce texte apparaît ' + r.count + ' fois dans le code : impossible de savoir lequel changer. Demande à l’IA.'; },
    same: 'Le texte est identique.', empty: 'Le texte ne peut pas être vide.', too_long: 'Le texte est trop long.'
  };
  $('dlgCancel').addEventListener('click', closeText);
  $('dlgOk').addEventListener('click', function () {
    var r = C.replaceText(files, pendingText, $('dlgText').value);
    if (r.ok) { files = r.files; edited++; closeText(); render(); flash('Texte modifié (aucun appel à l’IA).'); return; }
    var m = REASONS[r.reason]; $('dlgErr').textContent = typeof m === 'function' ? m(r) : (m || 'Modification impossible.');
  });

  // ---- couleurs
  function openPalette() {
    var list = $('palList'); list.textContent = '';
    var colors = C.listColors(files);
    if (!colors.length) { list.textContent = 'Aucune variable de couleur trouvée dans le CSS.'; }
    colors.forEach(function (c) {
      var row = document.createElement('div'); row.className = 'row';
      var sw = document.createElement('div'); sw.className = 'sw'; sw.style.background = c.value;
      var lbl = document.createElement('div'); lbl.className = 'lbl';
      lbl.textContent = c.name.replace(/^--/, '');
      if (c.dark) { var s = document.createElement('small'); s.textContent = ' (thème sombre)'; lbl.appendChild(s); }
      var inp = document.createElement('input'); inp.type = 'text'; inp.value = c.value; inp.style.width = '9em'; inp.style.flex = 'none';
      inp.addEventListener('change', function () {
        var r = C.setColor(files, c.name, c.dark, inp.value);
        if (!r.ok) { flash('Couleur invalide : écris par exemple #4f46e5'); inp.value = c.value; return; }
        files = r.files; edited++; c.value = inp.value.trim(); sw.style.background = c.value; render();
      });
      row.appendChild(sw); row.appendChild(lbl); row.appendChild(inp); list.appendChild(row);
    });
    $('pal').hidden = false;
  }
  $('colBtn').addEventListener('click', openPalette);
  $('palClose').addEventListener('click', function () { $('pal').hidden = true; });

  // ---- messages de l'iframe (seulement elle)
  window.addEventListener('message', function (e) {
    if (e.source !== frame.contentWindow) return;
    var d = e.data || {};
    if (d.type === 'tap') {
      if (d.kind !== 'text' || !d.text) { flash('Aucun texte à modifier ici.'); return; }
      var n = C.countText(files, d.text);
      if (n === 0) flash(REASONS.not_found);
      else if (n > 1) flash(REASONS.multiple({ count: n }));
      else openText(d.text);
    } else if (d.type === 'error') {
      banner('Erreur dans l’aperçu : ' + String(d.message).slice(0, 300));
    }
  });

  // ---- interface appelée par l'app Java
  window.KruxPreview = {
    load: function (json) { files = JSON.parse(json); edited = 0; render(); },
    result: function () { return JSON.stringify({ files: files, changed: edited > 0, hasErrors: errorCount > 0 }); }
  };
})();
