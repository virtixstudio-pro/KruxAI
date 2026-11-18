/* KRUX aperçu : logique pure (retouches locales, compilation Babel, document de l'iframe).
 * Chargé dans la page de l'aperçu (global KruxCore) et par les tests Node (require). */
(function (root, factory) {
  if (typeof module === 'object' && module.exports) module.exports = factory();
  else root.KruxCore = factory();
})(typeof self !== 'undefined' ? self : this, function () {
  'use strict';

  var COLOR_RE = /^(#[0-9a-fA-F]{3,4}|#[0-9a-fA-F]{6}|#[0-9a-fA-F]{8}|rgba?\([^)]*\)|hsla?\([^)]*\))$/;
  var DARK_RE = /@media[^{]*prefers-color-scheme\s*:\s*dark/;

  function isColor(v) { return COLOR_RE.test(String(v).trim()); }
  function copy(files) { var o = {}; for (var k in files) o[k] = files[k]; return o; }
  function extOf(p) { var m = /\.([A-Za-z0-9]+)$/.exec(p); return m ? m[1].toLowerCase() : ''; }
  function norm(s) { return String(s).replace(/\s+/g, ' ').trim(); }

  // ------------------------------------------------------------------ couleurs (variables CSS)

  function maskComments(s) { return s.replace(/\/\*[\s\S]*?\*\//g, function (m) { return m.replace(/[^\n]/g, ' '); }); }

  /** Déclarations --nom: valeur; avec leur position et si elles sont dans le thème sombre. */
  function scanCssVars(css) {
    var s = maskComments(css), out = [], stack = [], segStart = 0;
    function flush(end) {
      if (!stack.length) return;
      var seg = s.slice(segStart, end);
      var m = /^(\s*)(--[A-Za-z0-9_-]+)(\s*:\s*)([\s\S]*?)(\s*)$/.exec(seg);
      if (!m) return;
      var vs = segStart + m[1].length + m[2].length + m[3].length;
      out.push({ name: m[2], value: m[4], start: vs, end: vs + m[4].length,
                 dark: stack.some(function (h) { return DARK_RE.test(h); }) });
    }
    for (var i = 0; i < s.length; i++) {
      var c = s.charAt(i);
      if (c === '{') { stack.push(s.slice(segStart, i)); segStart = i + 1; }
      else if (c === '}') { flush(i); stack.pop(); segStart = i + 1; }
      else if (c === ';') { flush(i); segStart = i + 1; }
    }
    return out;
  }

  /** [{name, value, dark}] : variables dont la valeur est une couleur, sans doublon nom+thème. */
  function listColors(files) {
    var seen = {}, out = [];
    Object.keys(files).sort().forEach(function (p) {
      if (extOf(p) !== 'css') return;
      scanCssVars(files[p]).forEach(function (d) {
        if (!isColor(d.value)) return;
        var k = d.name + '|' + d.dark;
        if (seen[k]) return;
        seen[k] = true;
        out.push({ name: d.name, value: d.value.trim(), dark: d.dark });
      });
    });
    return out;
  }

  function setColor(files, name, dark, value) {
    value = String(value).trim();
    if (!isColor(value)) return { ok: false, reason: 'invalid' };
    var out = copy(files), count = 0;
    Object.keys(files).forEach(function (p) {
      if (extOf(p) !== 'css') return;
      var css = files[p];
      var decls = scanCssVars(css).filter(function (d) { return d.name === name && d.dark === !!dark && isColor(d.value); });
      for (var i = decls.length - 1; i >= 0; i--) { css = css.slice(0, decls[i].start) + value + css.slice(decls[i].end); count++; }
      out[p] = css;
    });
    return count ? { ok: true, files: out, count: count } : { ok: false, reason: 'not_found' };
  }

  // ------------------------------------------------------------------ textes

  var STR_RE = /(['"])((?:\\[\s\S]|(?!\1)[^\\\n])*)\1|`((?:\\[\s\S]|[^`\\$]|\$(?!\{))*)`/g;
  var JSX_RE = /(?<![=-])>([^<>{}]+)</g;
  var IMPORT_BEFORE = /(?:\bfrom|\bimport|\brequire\(|\bimport\()\s*$/;

  function unescapeLit(s) { return s.replace(/\\n/g, ' ').replace(/\\(['"`\\])/g, '$1'); }
  function escLit(s, q) { return s.replace(/\\/g, '\\\\').split(q).join('\\' + q); }
  function escTpl(s) { return s.replace(/\\/g, '\\\\').replace(/`/g, '\\`').replace(/\$\{/g, '\\${'); }

  /** Tous les endroits du code où ce texte (normalisé) est écrit tel quel : chaînes, gabarits et texte JSX. */
  function findTextHits(files, want) {
    var hits = [];
    Object.keys(files).forEach(function (p) {
      var e = extOf(p);
      if (e !== 'ts' && e !== 'tsx') return;
      var src = files[p], m;
      STR_RE.lastIndex = 0;
      while ((m = STR_RE.exec(src)) !== null) {
        var isTpl = m[1] === undefined;
        var body = isTpl ? m[3] : m[2];
        if (IMPORT_BEFORE.test(src.slice(Math.max(0, m.index - 14), m.index))) continue;
        if (norm(unescapeLit(body)) !== want) continue;
        hits.push({ file: p, start: m.index, end: m.index + m[0].length, kind: isTpl ? 'tpl' : 'str', q: m[1] });
      }
      JSX_RE.lastIndex = 0;
      while ((m = JSX_RE.exec(src)) !== null) {
        if (norm(m[1]) !== want) continue;
        hits.push({ file: p, start: m.index + 1, end: m.index + 1 + m[1].length, kind: 'jsx', raw: m[1] });
      }
    });
    return hits;
  }

  /** Remplace un texte affiché par un autre, seulement s'il est écrit tel quel et une seule fois dans src/*.ts(x). */
  function replaceText(files, oldText, newText) {
    var want = norm(oldText);
    var nt = String(newText).replace(/\s*\n\s*/g, ' ').trim();
    if (!want || !nt) return { ok: false, reason: 'empty' };
    if (nt.length > 300) return { ok: false, reason: 'too_long' };
    if (norm(nt) === want) return { ok: false, reason: 'same' };

    var hits = findTextHits(files, want);
    if (hits.length === 0) return { ok: false, reason: 'not_found' };
    if (hits.length > 1) return { ok: false, reason: 'multiple', count: hits.length };

    var h = hits[0], src2 = files[h.file], rep;
    if (h.kind === 'str') rep = h.q + escLit(nt, h.q) + h.q;
    else if (h.kind === 'tpl') rep = '`' + escTpl(nt) + '`';
    else {
      var lead = /^\s*/.exec(h.raw)[0], trail = /\s*$/.exec(h.raw)[0];
      rep = lead + (/[{}<>&]/.test(nt) ? '{' + JSON.stringify(nt) + '}' : nt) + trail;
    }
    var out = copy(files);
    out[h.file] = src2.slice(0, h.start) + rep + src2.slice(h.end);
    return { ok: true, files: out, where: { file: h.file, kind: h.kind } };
  }

  // ------------------------------------------------------------------ compilation (Babel) et document de l'iframe

  function compileProject(Babel, files) {
    var modules = {}, errors = [];
    Object.keys(files).sort().forEach(function (p) {
      var e = extOf(p);
      if (e !== 'ts' && e !== 'tsx') return;
      try {
        modules[p] = Babel.transform(files[p], {
          filename: p, sourceType: 'module', sourceMaps: false,
          presets: ['typescript', ['react', { runtime: 'automatic' }]],
          plugins: ['transform-modules-commonjs']
        }).code;
      } catch (err) {
        errors.push({ file: p, line: (err.loc && err.loc.line) || null,
                      message: String(err.message || err).split('\n')[0].replace(/^\/?[^:]*:\s*/, '') });
      }
    });
    return { modules: modules, errors: errors };
  }

  /** Code exécuté DANS l'iframe isolée (sans accès au parent hors messages). Sérialisé avec toString(). */
  function iframeMain() {
    var RT = window.__KRUX_RT || {}, D = window.__KD || {}, A = window.__KA || {}, cache = {}, editMode = false, mem = {};
    function post(m) { try { parent.postMessage(m, '*'); } catch (e) { } }
    try {
      Object.defineProperty(window, 'localStorage', { configurable: true, value: {
        getItem: function (k) { return Object.prototype.hasOwnProperty.call(mem, k) ? mem[k] : null; },
        setItem: function (k, v) { mem[k] = String(v); },
        removeItem: function (k) { delete mem[k]; },
        clear: function () { mem = {}; },
        key: function (i) { return Object.keys(mem)[i] || null; },
        get length() { return Object.keys(mem).length; } } });
    } catch (e) { }
    var toastEl, toastT;
    function toast(t) {
      if (!toastEl) {
        toastEl = document.createElement('div');
        toastEl.style.cssText = 'position:fixed;left:50%;bottom:72px;transform:translateX(-50%);background:#222;color:#fff;padding:10px 16px;border-radius:20px;font:14px system-ui,sans-serif;z-index:2147483647;max-width:80%;opacity:0;transition:opacity .2s;pointer-events:none';
        document.body.appendChild(toastEl);
      }
      toastEl.textContent = t; toastEl.style.opacity = '1';
      clearTimeout(toastT); toastT = setTimeout(function () { toastEl.style.opacity = '0'; }, 1800);
    }
    window.Krux = { toast: function (t) { toast(String(t)); }, share: function (t) { toast('Partager : ' + t); },
                    copy: function () { toast('Copié'); }, vibrate: function () { } };

    function dirOf(p) { var i = p.lastIndexOf('/'); return i < 0 ? '' : p.slice(0, i); }
    function join(dir, rel) {
      var parts = dir ? dir.split('/') : [];
      rel.split('/').forEach(function (s) { if (!s || s === '.') return; if (s === '..') parts.pop(); else parts.push(s); });
      return parts.join('/');
    }
    function find(base) {
      var c = [base, base + '.tsx', base + '.ts', base + '.json', base + '.css', base + '.svg', base + '/index.tsx', base + '/index.ts'];
      for (var i = 0; i < c.length; i++) if (D[c[i]] || A[c[i]]) return c[i];
      return null;
    }
    function req(spec, from) {
      if (spec.charAt(0) !== '.') { if (RT[spec]) return RT[spec]; throw new Error('Module introuvable : ' + spec); }
      var p = find(join(dirOf(from), spec));
      if (!p) throw new Error('Fichier introuvable : ' + spec + ' (importé par ' + from + ')');
      return load(p);
    }
    function load(p) {
      if (cache[p]) return cache[p].exports;
      var a = A[p], mod = { exports: {} };
      cache[p] = mod;
      if (a) {
        if (a.t === 'css') { var st = document.createElement('style'); st.textContent = a.c; document.head.appendChild(st); }
        else if (a.t === 'json') { var o = JSON.parse(a.c); mod.exports = Object.assign({ default: o, __esModule: true }, o && typeof o === 'object' ? o : {}); }
        else if (a.t === 'svg') { mod.exports = { default: a.c, __esModule: true }; }
        return mod.exports;
      }
      D[p](function (s) { return req(s, p); }, mod, mod.exports);
      return mod.exports;
    }
    function showError(msg) {
      msg = String(msg);
      post({ type: 'error', message: msg });
      var d = document.createElement('pre');
      d.style.cssText = 'margin:0;padding:12px;background:#fee;color:#900;font:12px monospace;white-space:pre-wrap';
      d.textContent = 'Erreur : ' + msg;
      document.body.insertBefore(d, document.body.firstChild);
    }
    window.addEventListener('error', function (e) { showError(e.message || (e.error && e.error.message) || 'erreur'); });
    window.addEventListener('unhandledrejection', function (e) { showError((e.reason && e.reason.message) || e.reason); });

    var st2 = document.createElement('style');
    st2.textContent = '.__krux_edit *{outline:1px dashed rgba(120,120,120,.55);outline-offset:-1px;cursor:pointer !important}';
    document.head.appendChild(st2);
    window.addEventListener('message', function (e) {
      if (e.source !== parent) return;
      var d = e.data || {};
      if (d.type === 'mode') { editMode = !!d.on; document.documentElement.classList.toggle('__krux_edit', editMode); }
    });
    function own(el) { var t = ''; for (var n = el.firstChild; n; n = n.nextSibling) if (n.nodeType === 3) t += n.nodeValue; return t.replace(/\s+/g, ' ').trim(); }
    function target(el) {
      for (var i = 0; el && i < 5 && el !== document.body; i++, el = el.parentElement) {
        var t = own(el); if (t) return { kind: 'text', text: t };
        var a = el.getAttribute && (el.getAttribute('placeholder') || el.getAttribute('aria-label') || el.getAttribute('title'));
        if (a) return { kind: 'text', text: a };
      }
      return null;
    }
    document.addEventListener('click', function (e) {
      if (!editMode) return;
      e.preventDefault(); e.stopPropagation();
      var t = target(e.target);
      post(t ? { type: 'tap', kind: t.kind, text: t.text } : { type: 'tap', kind: 'none' });
    }, true);

    try {
      if (!D['src/main.tsx']) throw new Error('src/main.tsx manquant');
      load('src/main.tsx');
      post({ type: 'ready' });
    } catch (e) { showError((e && e.message) || e); }
  }

  function inl(s) { return String(s).replace(/<\/(script)/gi, '<\\/$1'); }

  function buildSrcdoc(files, compiled, rtSrc) {
    var assets = {};
    Object.keys(files).forEach(function (p) {
      var e = extOf(p);
      if (e === 'css') assets[p] = { t: 'css', c: files[p] };
      else if (e === 'json') assets[p] = { t: 'json', c: files[p] };
      else if (e === 'svg') assets[p] = { t: 'svg', c: 'data:image/svg+xml;utf8,' + encodeURIComponent(files[p]) };
    });
    var defs = 'window.__KD={};\n' + Object.keys(compiled.modules).map(function (p) {
      return '__KD[' + JSON.stringify(p) + ']=function(require,module,exports){\n' + inl(compiled.modules[p]) + '\n};';
    }).join('\n') + '\nwindow.__KA=' + inl(JSON.stringify(assets)) + ';';
    return '<!doctype html><html><head><meta charset="utf-8">'
      + '<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">'
      + '<meta http-equiv="Content-Security-Policy" content="default-src \'none\'; script-src \'unsafe-inline\'; style-src \'unsafe-inline\'; img-src data:; font-src data:">'
      + '</head><body><div id="root"></div>'
      + '<script>' + inl(rtSrc) + '</script>'
      + '<script>' + defs + '</script>'
      + '<script>(' + inl(iframeMain.toString()) + ')();</script>'
      + '</body></html>';
  }

  function countText(files, text) { var w = norm(text); return w ? findTextHits(files, w).length : 0; }

  return { countText: countText, isColor: isColor, listColors: listColors, setColor: setColor, replaceText: replaceText,
           compileProject: compileProject, buildSrcdoc: buildSrcdoc, scanCssVars: scanCssVars };
});
