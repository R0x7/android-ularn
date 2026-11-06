#!/usr/bin/env node
/* test-radio.js - checks for Dungeon Radio's web port (radio.js and
 * radio-synth.js), the counterpart of the Android unit tests SongTest and
 * SynthTest. Run with `node web/test-radio.js`; web/build.sh runs it when node
 * is available. */
'use strict';
const assert = require('assert');
const Radio = require('./radio.js');
const S = require('./radio-synth.js');
const { Songs, Sfx, Notes, EV, TrackPicker, parsePattern, patternLength, WAVES } = Radio;

let tests = 0;
function test(name, fn) { fn(); tests++; }
const notes = (p) => parsePattern(p).map((s) => s[0]);

test('note names', () => {
  assert.strictEqual(Notes.midi('c4'), 60);
  assert.strictEqual(Notes.midi('A4'), 69);
  assert.strictEqual(Notes.midi('c#4'), 61);
  assert.strictEqual(Notes.midi('bb4'), 70);
  assert.strictEqual(Notes.midi('Bb3'), 58);
  assert.strictEqual(Notes.midi('c0'), 12);
  assert.strictEqual(Notes.midi('cb4'), 59);
  for (const bad of ['h4', 'c', 'c9', 'c#x']) assert.strictEqual(Notes.midi(bad), null, bad);
});

test('note frequencies', () => {
  assert.ok(Math.abs(Notes.freq(69) - 440) < 1e-9);
  assert.ok(Math.abs(Notes.freq(81) - 880) < 1e-9);
  assert.ok(Math.abs(Notes.freq(57) - 220) < 1e-9);
  assert.ok(Math.abs(Notes.freq(60) - 261.626) < 1e-3);
  assert.ok(Math.abs(S.noteFreq(40) - 82.407) < 1e-3);
});

test('parses notes, rests, drums and lengths', () => {
  const p = parsePattern('c4 d4:3 - r:2 l4 e4 k s:1 h | g#3');
  assert.deepStrictEqual(p, [[60, 1], [62, 3], [Notes.REST, 1], [Notes.REST, 2], [64, 4], [Notes.KICK, 4], [Notes.SNARE, 1], [Notes.HAT, 4], [56, 4]]);
  assert.strictEqual(patternLength(p), 1 + 3 + 1 + 2 + 4 + 4 + 1 + 4 + 4);
});

test('repeats and nesting', () => {
  assert.deepStrictEqual(notes('[c4 d4]3'), [60, 62, 60, 62, 60, 62]);
  assert.strictEqual(patternLength(parsePattern('[c4 d4]')), 4);   // "]" alone = twice
  assert.deepStrictEqual(notes('[c4 [d4]2]2'), [60, 62, 62, 60, 62, 62]);
  assert.strictEqual(patternLength(parsePattern('l2 [c3 g3]4')), 16);
  assert.strictEqual(parsePattern('[c4 d4]2').length, 4);   // brackets glued to notes
});

test('rejects bad patterns', () => {
  for (const bad of ['c4 x4', 'c4:0', 'c4:z', '[c4', 'c4]2', 'l0 c4', '[c4]0']) {
    assert.throws(() => parsePattern(bad), Error, 'accepted "' + bad + '"');
  }
});

test('every song is well-formed', () => {
  assert.ok(Songs.ALL.length >= 10);
  for (const s of Songs.ALL) {
    assert.ok(s.lengthSteps > 0, s.name + ' is empty');
    for (const v of s.voices) {
      // Looping voices must line up with the whole song, or the parts drift apart.
      if (s.loop) assert.strictEqual(s.lengthSteps % patternLength(v.steps), 0, s.name + ': voice length ' + patternLength(v.steps) + ' vs ' + s.lengthSteps);
      assert.ok(Object.values(WAVES).includes(v.wave), s.name + ': bad wave');
      assert.strictEqual(v.adsr.length, 4);
    }
    if (s.loop) assert.strictEqual(s.lengthSteps % 16, 0, s.name + ' is not whole bars');
    assert.ok(s.seconds < 60, s.name + ' too long');
  }
  for (const s of [Songs.GAME_OVER, Songs.VICTORY]) { assert.ok(!s.loop); assert.ok(s.seconds < 5); }
  // The bonus order is the C side's track index (compat/wild_radio.c).
  assert.deepStrictEqual(Songs.BONUS.map((s) => s.name), ['Elevator Music', "Surf's Up", 'Boss Fight']);
  // Songs survive the trip to the AudioWorklet (structured clone).
  for (const s of Songs.ALL) assert.deepStrictEqual(JSON.parse(JSON.stringify(s)), s);
});

test('every sound effect is short', () => {
  assert.ok(Sfx.ALL.length >= 15);
  for (const f of Sfx.ALL) {
    assert.ok(f.ms > 0 && f.ms <= 150, f.name + ': ' + f.ms + ' ms');
    for (const g of f.segs) assert.ok(g.f0 > 0 && g.f1 > 0 && g.ms > 0 && g.vol > 0 && g.vol <= 1, f.name);
  }
  assert.strictEqual(Sfx.forEvent(EV.KILL, 0), Sfx.KILL);
  assert.strictEqual(Sfx.forEvent(EV.WILD_COIN, 0), null);   // the silent price tick
  assert.strictEqual(Sfx.forEvent(EV.WILD_COIN, 1), Sfx.GOLD);
  assert.strictEqual(Sfx.forEvent(EV.NEW_LEVEL, 3), null);   // the caller picks up / down
  assert.strictEqual(Sfx.forEvent(EV.WILD_WEATHER, 1), null);
});

const rate = 8000;

test('pulse duty cycle', () => {
  for (const [wave, duty] of [[S.WAVE_PULSE12, 0.125], [S.WAVE_PULSE25, 0.25], [S.WAVE_PULSE50, 0.5]]) {
    const o = new S.Osc(rate); o.wave = wave; o.freq = 100;
    let high = 0;
    for (let i = 0; i < rate; i++) if (o.next() > 0) high++;
    assert.ok(Math.abs(high / rate - duty) < 0.02, 'wave ' + wave);
  }
});

test('triangle stays in range and noise is noisy', () => {
  const o = new S.Osc(rate); o.wave = S.WAVE_TRIANGLE; o.freq = 220;
  const xs = new Set();
  for (let i = 0; i < rate; i++) { const x = o.next(); assert.ok(x >= -1 && x <= 1); xs.add(x); }
  assert.ok(xs.size >= 8 && xs.size <= 17, 'staircase has ' + xs.size + ' levels');
  const n = new S.Osc(rate); n.wave = S.WAVE_NOISE; n.freq = 4000;
  let flips = 0, prev = n.next();
  for (let i = 0; i < rate; i++) { const x = n.next(); if (x !== prev) flips++; prev = x; }
  assert.ok(flips > 1000, 'noise flips ' + flips);
});

test('envelope rises and releases', () => {
  const e = new S.Envelope(rate);
  assert.ok(e.idle);
  e.on([0.01, 0.01, 0.5, 0.01]);
  let peak = 0;
  for (let i = 0; i < rate / 10; i++) peak = Math.max(peak, e.next());
  assert.ok(Math.abs(peak - 1) < 1e-3);
  assert.ok(Math.abs(e.next() - 0.5) < 1e-3);   // sustaining
  e.off();
  for (let i = 0; i < rate / 10; i++) e.next();
  assert.ok(e.idle);
});

function energy(buf) { let m = 0; for (const x of buf) m = Math.max(m, Math.abs(x)); return m; }

test('sequencer makes sound and stingers end', () => {
  const seq = new S.Sequencer(Songs.GAME_OVER, rate);
  const buf = new Float32Array(rate);
  seq.render(buf, buf.length, 1);
  assert.ok(energy(buf) > 0.1);
  assert.ok(!seq.finished);
  for (let i = 0; i < Songs.GAME_OVER.seconds + 2; i++) seq.render(buf, buf.length, 1);
  assert.ok(seq.finished);
  buf.fill(0);
  seq.render(buf, buf.length, 1);
  assert.strictEqual(energy(buf), 0);
});

test('looping songs keep playing', () => {
  const buf = new Float32Array(rate);
  for (const s of Songs.REGULAR.concat(Songs.BONUS)) {
    const seq = new S.Sequencer(s, rate);
    for (let i = 0; i < s.seconds + 1; i++) { buf.fill(0); seq.render(buf, buf.length, 1); }
    assert.ok(!seq.finished, s.name);
    assert.ok(energy(buf) > 0.1, s.name + ' went quiet after a loop');
  }
});

test('mixer cross-fades, plays effects and clips to -1..1', () => {
  const m = new S.Mixer(rate);
  const out = new Float32Array(rate / 4);
  assert.ok(m.silent);
  m.handle({ cmd: 'play', song: JSON.parse(JSON.stringify(Songs.TOWN)) });   // as the worklet gets it
  assert.ok(!m.silent);
  m.render(out, out.length);   // the first buffer fades in from 0 (one gain per buffer)
  m.render(out, out.length);
  assert.ok(energy(out) > 0.02 && energy(out) <= 1);
  // The same song again (another copy) does not restart it.
  const pos = m.current.pos;
  m.handle({ cmd: 'play', song: JSON.parse(JSON.stringify(Songs.TOWN)) });
  assert.strictEqual(m.current.pos, pos);
  m.handle({ cmd: 'play', song: Songs.SHOP });
  assert.strictEqual(m.song.name, 'Shop');
  assert.ok(m.fading && m.fading.song.name === 'Town');
  for (let i = 0; i < 3; i++) m.render(out, out.length);
  assert.strictEqual(m.fading, null);   // 0.3 s fade is over
  m.handle({ cmd: 'play', song: null });
  m.handle({ cmd: 'volume', music: 0, sfx: 1 });
  for (let i = 0; i < 3; i++) m.render(out, out.length);
  assert.strictEqual(energy(out), 0);
  for (let i = 0; i < 6; i++) m.handle({ cmd: 'sfx', def: Sfx.GOLD });   // more than POLYPHONY: steals voices
  assert.ok(!m.silent);
  m.render(out, out.length);
  assert.ok(energy(out) > 0.05 && energy(out) <= 1);
  for (let i = 0; i < 4; i++) m.render(out, out.length);
  assert.ok(m.silent);
});

test('track picker follows the dungeon', () => {
  const p = new TrackPicker(), none = new Set(), all = new Set([0, 1, 2]);
  const sc = (o) => Object.assign({ dlevel: 0, textPage: false, shopText: false, bankText: false, bossVisible: false, endState: 0 }, o);
  assert.strictEqual(p.pick(sc({ dlevel: 0 }), none, 0), Songs.TOWN);
  assert.strictEqual(p.pick(sc({ dlevel: 1 }), none, 0), Songs.SHALLOWS);
  assert.strictEqual(p.pick(sc({ dlevel: 4 }), none, 0), Songs.SHALLOWS);
  assert.strictEqual(p.pick(sc({ dlevel: 5 }), none, 0), Songs.DEPTHS);
  assert.strictEqual(p.pick(sc({ dlevel: 15 }), none, 0), Songs.DEPTHS);
  assert.strictEqual(p.pick(sc({ dlevel: 16 }), none, 0), Songs.VOLCANO);
  // A shop page, then one of its pages without the name: still the shop.
  assert.strictEqual(p.pick(sc({ textPage: true, shopText: true }), none, 0), Songs.SHOP);
  assert.strictEqual(p.pick(sc({ textPage: true }), none, 0), Songs.SHOP);
  assert.strictEqual(p.pick(sc({ dlevel: 0 }), none, 0), Songs.TOWN);   // back on the map
  // A text page that is no store (inventory, help): the level's music.
  assert.strictEqual(p.pick(sc({ dlevel: 3, textPage: true }), none, 0), Songs.SHALLOWS);
  // The bank: Shop without the tape, Elevator Music with it.
  assert.strictEqual(p.pick(sc({ textPage: true, bankText: true }), none, 0), Songs.SHOP);
  assert.strictEqual(p.pick(sc({ textPage: true, bankText: true }), all, 0), Songs.ELEVATOR);
  p.pick(sc({}), none, 0);
  // Boss and fishing only with their tapes; the boss wins.
  assert.strictEqual(p.pick(sc({ dlevel: 8, bossVisible: true }), none, 0), Songs.DEPTHS);
  assert.strictEqual(p.pick(sc({ dlevel: 8, bossVisible: true }), all, 0), Songs.BOSS);
  p.fishing(1000);
  assert.strictEqual(p.pick(sc({ dlevel: 0 }), none, 1000), Songs.TOWN);
  assert.strictEqual(p.pick(sc({ dlevel: 0 }), all, 1000), Songs.SURF);
  assert.strictEqual(p.pick(sc({ dlevel: 0, bossVisible: true }), all, 1000), Songs.BOSS);
  assert.strictEqual(p.pick(sc({ dlevel: 0 }), all, 1000 + TrackPicker.FISHING_MS), Songs.TOWN);
  // The end: stingers, then silence after a quit.
  assert.strictEqual(p.pick(sc({ endState: 1 }), none, 0), Songs.GAME_OVER);
  assert.strictEqual(p.pick(sc({ endState: 2 }), none, 0), Songs.VICTORY);
  assert.strictEqual(p.pick(sc({ endState: 3 }), none, 0), null);
});

test('controller state without a page', () => {
  // The parts of Radio that don't touch the DOM or audio.
  const mem = {};
  Radio._store = { get: (k, d) => (k in mem ? mem[k] : d), set: (k, v) => { mem[k] = v; } };
  assert.ok(Radio.enabled);
  assert.strictEqual(Radio.musicVolume, 60);
  assert.strictEqual(Radio.sfxVolume, 80);
  assert.deepStrictEqual([...Radio.unlocked], []);
  Radio.unlock(2); Radio.unlock(0); Radio.unlock(7);
  assert.deepStrictEqual([...Radio.unlocked].sort(), [0, 2]);
  Radio.sfxVolume = 150;
  assert.strictEqual(Radio.sfxVolume, 100);
});

console.log('test-radio: ' + tests + ' checks passed');
