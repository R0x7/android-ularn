/* game.js - the web front end for Ularn (see web/README.md).
 *
 * The game (ularn.wasm) writes its whole display into one UlarnShared struct
 * (app/src/main/cpp/shared_state.h); this page draws from it whenever its
 * `seq` changes, and feeds keys back with ularn_web_push_key(). It plays the
 * role of the Android app's GameView / ControlsOverlay, much simplified.
 */
'use strict';

const WILD_ALL = 0x7ff;
const TILE_SRC = 32, ATLAS_COLS = 16;
const TILE_CURSOR = 174, TILE_PET_HEART = 397;
const SAVE = '/ularn/home/ularn_player.sav';
const CKP = '/ularn/home/ularn_player.ckp';
const $ = (id) => document.getElementById(id);

const store = {
  get(k, d) { try { const v = localStorage.getItem('ularn.' + k); return v === null ? d : JSON.parse(v); } catch (e) { return d; } },
  set(k, v) { try { localStorage.setItem('ularn.' + k, JSON.stringify(v)); } catch (e) { /* private mode */ } },
};

let L = null;          // struct field offsets (ularn_web_layout)
let base = 0;          // address of the struct in wasm memory
let lastSeq = -1;
let running = false;
let runNext = false;   // the "run" button: next direction is Shift+dir
const tiles = new Image();

// ---------------------------------------------------------------- Emscripten
var Module = {
  print: (s) => console.log(s),
  printErr: (s) => console.warn(s),
  onRuntimeInitialized() {
    const FS = Module.FS;
    try { FS.mkdir('/ularn'); } catch (e) { /* exists */ }
    FS.mount(FS.filesystems.IDBFS, {}, '/ularn');
    FS.syncfs(true, (err) => {
      if (err) console.warn('IndexedDB load failed', err);
      try { FS.mkdir('/ularn/home'); } catch (e) { /* exists */ }
      L = JSON.parse(Module.UTF8ToString(Module._ularn_web_layout()));
      base = Module._ularn_web_state();
      showTitle();
    });
  },
  onGameExit(status) {
    running = false;
    render(true);
    syncSave(() => {
      const end = i32(L.end_state);
      $('endmsg').textContent = end ? 'Game over: ' + str(L.end_reason, 81) + ' (score ' + i32(L.end_score) + ')'
        : exists(SAVE) ? 'Game saved.' : 'The game has ended.';
      $('ended').hidden = false;
    });
  },
};

function exists(p) { try { Module.FS.stat(p); return true; } catch (e) { return false; } }
function syncSave(cb) { Module.FS.syncfs(false, (err) => { if (err) console.warn('save sync failed', err); if (cb) cb(); }); }

// ----------------------------------------------------------- struct access
const u8 = (o) => Module.HEAPU8[base + o];
const i32 = (o) => Module.HEAP32[(base + o) >> 2];
const i16 = (o) => Module.HEAP16[(base + o) >> 1];
function str(o, max) {
  const h = Module.HEAPU8; let s = '';
  for (let i = 0; i < max; i++) { const c = h[base + o + i]; if (!c) break; s += String.fromCharCode(c); }
  return s;
}

// ---------------------------------------------------------------- title
function showTitle() {
  $('loading').hidden = true;
  $('title').hidden = false;
  $('name').value = store.get('name', 'Adventurer');
  $('gender').value = store.get('gender', 'female');
  $('wild').checked = store.get('wild', true);
  $('skipintro').checked = store.get('skipIntro', true);
  $('continue').hidden = !exists(SAVE);
  $('checkpoint').hidden = exists(SAVE) || !exists(CKP);
  $('newgame').textContent = exists(SAVE) ? 'New game (replaces the save)' : 'New game';
  ($('continue').hidden ? $('newgame') : $('continue')).focus();
}

function start(mode) {
  const name = ($('name').value.replace(/[,:\r\n]/g, ' ').trim().slice(0, 30)) || 'Adventurer';
  const gender = $('gender').value, wild = $('wild').checked, skip = $('skipintro').checked;
  store.set('name', name); store.set('gender', gender); store.set('wild', wild); store.set('skipIntro', skip);
  const FS = Module.FS;
  if (mode === 'new') { for (const p of [SAVE, CKP]) if (exists(p)) FS.unlink(p); }
  const opts = ['name:' + name, 'gender:' + gender, 'noenhanced_interface'];
  if (skip) opts.push('nowelcome');
  FS.writeFile('/ularn/home/ularn.opt', '# Written by Ularn Web\nOPTION ' + opts.join(',') + '\n');

  $('title').hidden = true;
  $('game').hidden = false;
  running = true;
  Module.ccall('ularn_web_run', null, ['number', 'number'], [wild ? WILD_ALL : 0, mode === 'ckp' ? 1 : 0], { async: true });
  requestAnimationFrame(frame);
  setInterval(() => { if (running) syncSave(); }, 15000);
}

// ---------------------------------------------------------------- input
function send(ch) {
  if (!running) return;
  for (const c of ch) Module._ularn_web_push_key(c.charCodeAt(0) & 0xff);
}

const DIR_KEYS = {
  ArrowLeft: 'h', ArrowRight: 'l', ArrowUp: 'k', ArrowDown: 'j',
  Home: 'y', PageUp: 'u', End: 'b', PageDown: 'n', Clear: '.',
};
const NUMPAD = { Numpad1: 'b', Numpad2: 'j', Numpad3: 'n', Numpad4: 'h', Numpad5: '.', Numpad6: 'l', Numpad7: 'y', Numpad8: 'k', Numpad9: 'u' };

function inputMode() { return L ? i32(L.input_mode) : 0; }
function isDirKey(k) { return 'hjklyubn'.includes(k); }

function sendDir(k) {
  // Running (Shift) only makes sense at the command prompt, not when the game
  // asks for a direction.
  if (runNext && inputMode() === 1 && isDirKey(k)) k = k.toUpperCase();
  runNext = false; $('run').classList.remove('on');
  send(k);
}

document.addEventListener('keydown', (e) => {
  if (!running || e.target === $('name')) return;
  if (e.ctrlKey || e.metaKey || e.altKey) return;
  let k = null;
  if (DIR_KEYS[e.key]) {
    k = DIR_KEYS[e.key];
    if (e.shiftKey && inputMode() === 1 && isDirKey(k)) k = k.toUpperCase();
  } else if (NUMPAD[e.code] && (inputMode() === 1 || inputMode() === 3)) {
    // numpad digits move at the command / direction prompts, type elsewhere
    k = NUMPAD[e.code];
  } else if (e.key === 'Enter') k = '\r';
  else if (e.key === 'Escape') k = '\x1b';
  else if (e.key === 'Backspace') k = '\b';
  else if (e.key === 'Tab') k = '\t';
  else if (e.key.length === 1) k = e.key;
  if (k === null) return;
  e.preventDefault();
  send(k);
});

function unescapeKey(s) {
  return s.replace(/\\x([0-9a-f]{2})/gi, (_, h) => String.fromCharCode(parseInt(h, 16))).replace(/\\r/g, '\r');
}

function wireControls() {
  for (const b of document.querySelectorAll('#controls button[data-k]')) {
    const k = unescapeKey(b.dataset.k);
    b.addEventListener('click', (e) => { e.preventDefault(); isDirKey(k) ? sendDir(k) : send(k); });
  }
  $('run').addEventListener('click', () => { runNext = !runNext; $('run').classList.toggle('on', runNext); });
  // Soft keyboard: an off-screen input catches typed characters.
  const t = $('typer');
  $('kbd').addEventListener('click', () => { t.value = ''; t.focus(); });
  t.addEventListener('input', () => { send(t.value); t.value = ''; });
  t.addEventListener('keydown', (e) => {
    if (e.key === 'Enter') { e.preventDefault(); e.stopPropagation(); send('\r'); }
    else if (e.key === 'Backspace') { e.preventDefault(); e.stopPropagation(); send('\b'); }
    else e.stopPropagation(); // the 'input' event sends printable keys
  });
  // Tap on the map: step towards the tapped square.
  $('map').addEventListener('click', (e) => {
    if (!view || inputMode() !== 1 || i32(L.display_mode) !== 0) return;
    const r = $('map').getBoundingClientRect();
    const tx = view.x0 + Math.floor((e.clientX - r.left) / view.t);
    const ty = view.y0 + Math.floor((e.clientY - r.top) / view.t);
    const dx = Math.sign(tx - i32(L.player_x)), dy = Math.sign(ty - i32(L.player_y));
    const k = { '-1,-1': 'y', '0,-1': 'k', '1,-1': 'u', '-1,0': 'h', '0,0': '.', '1,0': 'l', '-1,1': 'b', '0,1': 'j', '1,1': 'n' }[dx + ',' + dy];
    sendDir(k);
  });
  $('newgame').addEventListener('click', () => start('new'));
  $('continue').addEventListener('click', () => start('continue'));
  $('checkpoint').addEventListener('click', () => start('ckp'));
  $('again').addEventListener('click', () => location.reload());
  window.addEventListener('pagehide', () => { if (running) syncSave(); });
  document.addEventListener('visibilitychange', () => { if (document.hidden && running) syncSave(); });
}

// ---------------------------------------------------------------- drawing
let view = null;

function frame() {
  if (L && running) {
    const seq = i32(L.seq);
    if (seq !== lastSeq) { lastSeq = seq; render(); }
  }
  if (running) requestAnimationFrame(frame);
}

function esc(c) { return c === '<' ? '&lt;' : c === '>' ? '&gt;' : c === '&' ? '&amp;' : c; }

/* Lines of text with their FormatType codes as spans; optional caret cell. */
function textBlock(chrOff, fmtOff, lines, caretX, caretY) {
  const W = L.line, out = [];
  for (let y = 0; y < lines; y++) {
    let s = '', cur = -1, row = '';
    for (let x = 0; x < W; x++) row += String.fromCharCode(u8(chrOff + y * (W + 1) + x) || 32);
    row = row.replace(/\s+$/, '');
    const len = Math.max(row.length, caretY === y ? caretX + 1 : 0);
    for (let x = 0; x < len; x++) {
      let f = x < row.length ? u8(fmtOff + y * W + x) : 0;
      const caret = caretY === y && caretX === x;
      if (caret) f = 'c';
      if (f !== cur) { if (cur !== -1 && cur !== 0) s += '</span>'; if (f !== 0) s += f === 'c' ? '<span class="caret">' : '<span class="f' + f + '">'; cur = f; }
      s += esc(x < row.length ? row[x] : ' ');
    }
    if (cur !== -1 && cur !== 0) s += '</span>';
    out.push(s);
  }
  while (out.length && out[out.length - 1] === '') out.pop();
  return out.join('\n');
}

function render() {
  const textMode = i32(L.display_mode) === 1;
  const caret = i32(L.caret) ? [i32(L.cursor_x) - 1, i32(L.cursor_y) - 1] : [-1, -1];
  $('map').hidden = textMode;
  $('text').hidden = !textMode;
  $('mapwrap').classList.toggle('textmode', textMode);
  if (textMode) {
    $('text').innerHTML = textBlock(L.text_chr, L.text_fmt, L.text_lines, caret[0], caret[1]);
    $('msgs').innerHTML = '';
  } else {
    drawMap();
    $('msgs').innerHTML = textBlock(L.msg_chr, L.msg_fmt, L.msg_lines, caret[0], caret[1]);
  }
  $('status').textContent = (str(L.status1, 96) + '\n' + str(L.status2, 96)).replace(/ +$/gm, '');
  const lvl = str(L.level_name, 8);
  $('hud').innerHTML = lvl
    ? `HP <b>${i32(L.hp)}/${i32(L.hpmax)}</b> · Spells <b>${i32(L.spells)}/${i32(L.spellmax)}</b> · Gold <b>${i32(L.gold)}</b> · Lvl <b>${i32(L.plevel)}</b> · AC <b>${i32(L.ac)}</b> WC <b>${i32(L.wc)}</b> · Dungeon <b>${esc(lvl)}</b>`
    : '';
}

function drawMap() {
  const cv = $('map');
  const MW = L.map_w, MH = L.map_h;
  const app = $('app'), cs = getComputedStyle(app);
  const avail = (app.clientWidth - parseFloat(cs.paddingLeft) - parseFloat(cs.paddingRight) - 2) || window.innerWidth - 34;
  // Whole map when it fits at >= 14px a tile; otherwise follow the player.
  let t = Math.floor(avail / MW);
  if (t < 14) t = Math.max(22, Math.floor(avail / 16));
  t = Math.min(t, 40);
  const dpr = window.devicePixelRatio || 1;
  const cols = Math.min(MW, Math.floor(avail / t)), rows = MH;
  const px = i32(L.player_x), py = i32(L.player_y);
  const x0 = Math.max(0, Math.min(MW - cols, px - (cols >> 1))), y0 = 0;
  view = { x0, y0, t };
  const w = cols * t, h = rows * t;
  if (cv.width !== Math.round(w * dpr) || cv.height !== Math.round(h * dpr)) {
    cv.width = Math.round(w * dpr); cv.height = Math.round(h * dpr);
    cv.style.width = w + 'px'; cv.style.height = h + 'px';
  }
  const g = cv.getContext('2d');
  g.imageSmoothingEnabled = false;
  g.setTransform(dpr, 0, 0, dpr, 0, 0);
  g.fillStyle = '#000'; g.fillRect(0, 0, w, h);
  if (!tiles.complete) return;
  const cursor = i32(L.player_cursor), hat = i32(L.hat_tile);
  const petX = i32(L.pet_x), petY = i32(L.pet_y);
  const tile = (id, x, y) => {
    if (id < 0) return;
    g.drawImage(tiles, (id % ATLAS_COLS) * TILE_SRC, Math.floor(id / ATLAS_COLS) * TILE_SRC, TILE_SRC, TILE_SRC, x, y, t, t);
  };
  for (let ty = y0; ty < y0 + rows; ty++) {
    for (let tx = x0; tx < x0 + cols; tx++) {
      const x = (tx - x0) * t, y = (ty - y0) * t, i = ty * MW + tx;
      tile(i16(L.tiles + 2 * i), x, y);
      const isPlayer = tx === px && ty === py && cursor;
      if (isPlayer && hat > 0) tile(hat, x, y);
      tile(i16(L.overlay + 2 * i), x, y);
      if (petX >= 0 && tx === petX && ty === petY) tile(TILE_PET_HEART, x, y);
      if (isPlayer) tile(TILE_CURSOR, x, y);
    }
  }
}

window.addEventListener('resize', () => { if (running && L) render(); });
tiles.onload = () => { if (running && L) render(); };
tiles.src = 'ularn_tiles.png';
wireControls();
