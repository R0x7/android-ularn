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
    checkEnd();
    syncSave(() => {
      const end = i32(L.end_state);
      $('endmsg').textContent = end === 2 ? 'You won! Score ' + i32(L.end_score).toLocaleString()
        : end ? 'Game over: ' + str(L.end_reason, 81) + ' (score ' + i32(L.end_score).toLocaleString() + ')'
        : exists(SAVE) ? 'Game saved.' : 'The game has ended.';
      $('game').hidden = true;
      document.body.classList.remove('playing');
      if (document.fullscreenElement) document.exitFullscreen().catch(() => {});
      $('ended').hidden = false;
      window.scrollTo(0, 0);
      const run = end && !unranked ? i32(L.run_id) : 0;
      (scoreSent || Promise.resolve(true)).then((ok) => {
        Leaderboard.show($('lb-end'), run).then(() => {
          if (unranked) $('lb-end').querySelector('.lb-status').textContent =
            'Wizard and cheat games are not put on the leaderboard.';
          else if (run && !ok) $('lb-end').querySelector('.lb-status').textContent =
            "Couldn't reach the leaderboard; your score will be sent next time you open the game.";
        });
      });
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
  Leaderboard.flush().finally(() => Leaderboard.show($('lb-title')));
}

// ---------------------------------------------------------- leaderboard
const CLASSES = ['Ogre', 'Wizard', 'Klingon', 'Elf', 'Rogue', 'Adventurer', 'Dwarf', 'Rambo'];
const OUTCOMES = { 1: 'died', 2: 'won', 3: 'quit' };
let scoreSent = null;   // promise of the current run's submission
let unranked = false;   // wizard / cheat run: not submitted

/* The game sets end_state when it shows the final score (death, win or
 * quit). Submit that once per run, as soon as it appears. */
function checkEnd() {
  if (scoreSent || !L) return;
  const end = i32(L.end_state), run = i32(L.run_id);
  if (!end || !run || !OUTCOMES[end]) return;
  if (Module._ularn_web_unranked()) { scoreSent = Promise.resolve(true); unranked = true; return; }
  scoreSent = Leaderboard.submit({
    run_id: run,
    name: store.get('name', 'Adventurer'),
    score: i32(L.end_score),
    outcome: OUTCOMES[end],
    reason: str(L.end_reason, 81).trim(),
    char_class: CLASSES[i32(L.class_num)] || '',
    char_level: i32(L.plevel),
    dungeon: str(L.level_name, 8).trim(),
    turns: i32(L.game_time),
    kills: i32(L.kills),
    difficulty: i32(L.hardness),
    wild: (i32(L.wild_flags) & 0xffff) !== 0,
  }).catch((e) => { console.warn(e); return false; });
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
  $('lb-title').hidden = true;
  $('home').hidden = true;
  document.body.classList.add('playing');
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
  // Toolbar: on-screen buttons (default: touch screens only), side panel, full screen.
  const touch = window.matchMedia('(pointer: coarse)').matches;
  const setPref = (cls, key, on) => {
    document.body.classList.toggle(cls, on);
    store.set(key, on);
    if (running && L) requestAnimationFrame(render);
  };
  setPref('touchui', 'touchUi', store.get('touchUi', touch));
  setPref('noside', 'noSide', store.get('noSide', false));
  const syncTools = () => {
    $('t-controls').classList.toggle('on', document.body.classList.contains('touchui'));
    $('t-side').classList.toggle('on', !document.body.classList.contains('noside'));
    $('t-full').classList.toggle('on', !!document.fullscreenElement);
  };
  $('t-controls').addEventListener('click', () => { setPref('touchui', 'touchUi', !document.body.classList.contains('touchui')); syncTools(); });
  $('t-side').addEventListener('click', () => { setPref('noside', 'noSide', !document.body.classList.contains('noside')); syncTools(); });
  $('t-full').addEventListener('click', () => {
    if (document.fullscreenElement) document.exitFullscreen().catch(() => {});
    else document.documentElement.requestFullscreen().catch(() => {});
  });
  if (!document.documentElement.requestFullscreen) $('t-full').hidden = true;
  document.addEventListener('fullscreenchange', syncTools);
  syncTools();
  new ResizeObserver(() => { if (running && L) render(); }).observe($('playcol'));

  // Clicking an in-game button must not move keyboard focus to it, or the
  // next Space / Enter would press it again instead of going to the game.
  for (const b of document.querySelectorAll('#game button')) b.addEventListener('mousedown', (e) => e.preventDefault());

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
    if (seq !== lastSeq) { lastSeq = seq; render(); checkEnd(); }
  }
  if (running) requestAnimationFrame(frame);
}

function esc(c) { return c === '<' ? '&lt;' : c === '>' ? '&gt;' : c === '&' ? '&amp;' : c; }

/* Lines of text with their FormatType codes as spans; optional caret cell.
 * With asLines, returns [{text, html}] per line instead of one string. */
function textBlock(chrOff, fmtOff, lines, caretX, caretY, asLines) {
  const W = L.line, out = [], plain = [];
  for (let y = 0; y < lines; y++) {
    let s = '', cur = -1, row = '';
    for (let x = 0; x < W; x++) row += String.fromCharCode(u8(chrOff + y * (W + 1) + x) || 32);
    row = row.replace(/\s+$/, '');
    plain.push(row);
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
  if (asLines) return out.map((html, i) => ({ text: plain[i], html }));
  while (out.length && out[out.length - 1] === '') out.pop();
  return out.join('\n');
}

/* Message history. The game shows a 5-line window that scrolls (or is
 * cleared and refilled); lines that leave it are kept here so the log can
 * show older messages above the live window. */
const MSG_KEEP = 300;
let msgHistory = [];     // html of lines that have left the window
let msgPrev = [];        // {text, html} of the window's non-empty lines last time

function updateMessages() {
  const cur = textBlock(L.msg_chr, L.msg_fmt, L.msg_lines, ...caretPos(), true).filter((l) => l.text !== '' || l.html !== '');
  const same = (a, b) => a.text === b.text;
  const n = msgPrev.length;
  // A line being typed into / edited in place: nothing scrolled out.
  const edited = cur.length === n && n > 0 && cur.slice(0, -1).every((l, i) => same(l, msgPrev[i]));
  if (!edited) {
    let k = Math.min(n, cur.length);
    while (k > 0 && !msgPrev.slice(n - k).every((l, i) => same(l, cur[i]))) k--;
    const gone = msgPrev.slice(0, n - k);
    if (gone.length) {
      msgHistory.push(...gone.map((l) => l.html));
      if (msgHistory.length > MSG_KEEP) msgHistory = msgHistory.slice(-MSG_KEEP);
    }
  }
  msgPrev = cur;
  const box = $('msgs');
  const atBottom = box.scrollHeight - box.scrollTop - box.clientHeight < 24;
  box.innerHTML = msgHistory.map((h) => '<span class="old">' + h + '</span>').join('\n')
    + (msgHistory.length && cur.length ? '\n' : '') + cur.map((l) => l.html).join('\n');
  if (atBottom) box.scrollTop = box.scrollHeight;
}

function caretPos() { return i32(L.caret) ? [i32(L.cursor_x) - 1, i32(L.cursor_y) - 1] : [-1, -1]; }

const EFFECTS = ['Stealth', 'Undead Pro', 'Spirit Pro', 'Charm', 'Time Stop', 'Hold Monst', 'Giant Str',
  'Fire Resist', 'Dexterity', 'Strength', 'Scare', 'Haste Self', 'Cancel', 'Invisible', 'Protect 3',
  'Protect 2', 'Wall-Walk'];
let charW = 0;   // monospace advance at 100px, measured once

function render() {
  const textMode = i32(L.display_mode) === 1;
  const caret = caretPos();
  $('map').hidden = textMode;
  $('text').hidden = !textMode;
  $('playcol').classList.toggle('textmode', textMode);
  if (textMode) {
    $('stage').style.height = '';
    $('text').innerHTML = textBlock(L.text_chr, L.text_fmt, L.text_lines, caret[0], caret[1]);
    fitText();
  } else {
    drawMap();
    updateMessages();
  }
  $('status').textContent = (str(L.status1, 96) + '\n' + str(L.status2, 96)).replace(/ +$/gm, '');
  renderHud();
  if (!document.body.classList.contains('noside') && $('side').offsetParent !== null) renderSide();
}

/* Text pages are 80x25: size the font so the page fills the play area. */
function fitText() {
  const el = $('text'), st = $('stage');
  if (!charW) {
    const g = document.createElement('canvas').getContext('2d');
    g.font = '100px ' + getComputedStyle(el).fontFamily;
    charW = g.measureText('M'.repeat(10)).width / 10 || 60;
  }
  const w = st.clientWidth - 28, h = st.clientHeight - 20;
  const fs = Math.max(10, Math.min(30, Math.floor(Math.min(w / (L.line * charW / 100), h / (L.text_lines * 1.2)))));
  el.style.fontSize = fs + 'px';
}

function bar(cls, label, cur, max) {
  const pct = max > 0 ? Math.max(0, Math.min(100, (100 * cur) / max)) : 0;
  const low = cls === 'hp' && pct <= 25 ? ' low' : '';
  return `<span class="bar ${cls}${low}">${label} <i style="--pct:${pct}%"></i> <b>${cur}/${max}</b></span>`;
}

function renderHud() {
  const lvl = str(L.level_name, 8).trim();
  if (!lvl) { $('hud').innerHTML = ''; return; }
  $('hud').innerHTML = bar('hp', 'HP', i32(L.hp), i32(L.hpmax)) + bar('mp', 'Spells', i32(L.spells), i32(L.spellmax))
    + `<span>Gold <b>${i32(L.gold).toLocaleString()}</b></span><span>Lvl <b>${i32(L.plevel)}</b></span>`
    + `<span>AC <b>${i32(L.ac)}</b> WC <b>${i32(L.wc)}</b></span><span>Dungeon <b>${esc(lvl)}</b></span>`
    + `<span>Time <b>${i32(L.game_time)}</b></span>`;
}

const sideCache = {};
function setHtml(id, html) { if (sideCache[id] !== html) { sideCache[id] = html; $(id).innerHTML = html; } }
function escAll(t) { return t.replace(/[<>&]/g, esc); }

function renderSide() {
  const cls = CLASSES[i32(L.class_num)];
  const st = ['STR', 'INT', 'WIS', 'CON', 'DEX', 'CHA'].map((n, i) => `<div><span>${n}</span>${i32(L.stats + 4 * i)}</div>`).join('');
  setHtml('char', cls === undefined ? '<h3>Character</h3><div class="what">Choose a class to begin.</div>'
    : `<h3>Character</h3><div class="who">${escAll(store.get('name', 'Adventurer'))}</div>`
    + `<div class="what">Level ${i32(L.plevel)} ${cls} · ${escAll(str(L.level_title, 40).trim())}<br>Exp ${i32(L.exp).toLocaleString()}</div>`
    + `<div class="stats">${st}<div><span>AC</span>${i32(L.ac)}</div><div><span>WC</span>${i32(L.wc)}</div>`
    + `<div><span>Gold</span>${i32(L.gold)}</div></div>`);
  const fx = i32(L.effects), on = EFFECTS.filter((_, i) => fx & (1 << i));
  $('effects-card').hidden = on.length === 0;
  setHtml('effects', on.map((n) => `<span>${n}</span>`).join(''));
  let inv = '';
  for (let i = 0; i < 26; i++) {
    const t = str(L.inv_text + i * 80, 80).trim();
    if (!t) continue;
    const worn = /\((being worn|weapon in hand|wielded)/.test(t) ? ' class="worn"' : '';
    inv += `<li${worn}><b>${String.fromCharCode(97 + i)}</b><span>${escAll(t)}</span></li>`;
  }
  setHtml('inv', inv || '<li class="empty">Empty</li>');
}

function drawMap() {
  const cv = $('map');
  const MW = L.map_w, MH = L.map_h;
  const st = $('stage'), col = $('playcol');
  // Room for the map: the column minus a 5-line message box (and the status
  // lines when they show). The stage is then sized to the map, and the
  // message log takes whatever height is left.
  const reserved = 5 * 18 + 26 + ($('status').offsetHeight ? $('status').offsetHeight + 6 : 0);
  const aw = Math.max(100, col.clientWidth - 2), ah = Math.max(100, col.clientHeight - reserved - 2);
  // The whole map, as big as that allows (whole pixels per tile). On small
  // screens, where that would be under 14px, zoom in and follow the player.
  let t = Math.min(Math.floor(aw / MW), Math.floor(ah / MH), 72);
  if (t < 14) t = Math.max(20, Math.min(32, Math.floor(aw / 15)));
  const dpr = window.devicePixelRatio || 1;
  const cols = Math.min(MW, Math.floor(aw / t)), rows = Math.min(MH, Math.max(1, Math.floor(ah / t)));
  const px = i32(L.player_x), py = i32(L.player_y);
  const x0 = Math.max(0, Math.min(MW - cols, px - (cols >> 1)));
  const y0 = Math.max(0, Math.min(MH - rows, py - (rows >> 1)));
  view = { x0, y0, t };
  const w = cols * t, h = rows * t;
  st.style.height = (h + 2) + 'px';
  $('playcol').style.setProperty('--mapw', (w + 2) + 'px');
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

tiles.onload = () => { if (running && L) render(); };
tiles.src = 'ularn_tiles.png';
wireControls();
