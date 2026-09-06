#!/usr/bin/env node
/**
 * Narrow, reproducible curator for the three imported bunkhouse_2 assets.
 * It deliberately changes only the two former dinosaur_chop palette entries
 * in each template; every block state index, coordinate and unrelated tag is
 * retained byte-for-byte in the decoded NBT tree.
 */
import { createHash } from 'node:crypto';
import { readFile, writeFile } from 'node:fs/promises';
import { gzipSync, gunzipSync } from 'node:zlib';
import path from 'node:path';
import { isDeepStrictEqual } from 'node:util';

const root = path.resolve(import.meta.dirname, '../..');
const families = ['temperate', 'cold_taiga', 'dry_arid'];
const target = 'alexscaves:dinosaur_chop';
const replacement = 'farmersdelight:rice_bag';
const expected = new Map([['4,2,6', 0], ['4,3,6', 2]]);
const beforeRootIndex = process.argv.indexOf('--before-root');
const beforeRoot = beforeRootIndex < 0 ? null : path.resolve(process.argv[beforeRootIndex + 1]
  ?? (() => { throw new Error('--before-root requires a directory'); })());

class Reader {
  constructor(buf) { this.b = buf; this.i = 0; }
  u8() { return this.b.readUInt8(this.i++); }
  i8() { return this.b.readInt8(this.i++); }
  i16() { const v = this.b.readInt16BE(this.i); this.i += 2; return v; }
  i32() { const v = this.b.readInt32BE(this.i); this.i += 4; return v; }
  i64() { const v = this.b.readBigInt64BE(this.i); this.i += 8; return v; }
  f32() { const v = this.b.readFloatBE(this.i); this.i += 4; return v; }
  f64() { const v = this.b.readDoubleBE(this.i); this.i += 8; return v; }
  str() { const n = this.b.readUInt16BE(this.i); this.i += 2; const s = this.b.subarray(this.i, this.i + n).toString('utf8'); this.i += n; return s; }
  value(t) {
    if (t === 1) return this.i8(); if (t === 2) return this.i16(); if (t === 3) return this.i32();
    if (t === 4) return this.i64(); if (t === 5) return this.f32(); if (t === 6) return this.f64();
    if (t === 8) return this.str();
    if (t === 7) { const n = this.i32(); const v = this.b.subarray(this.i, this.i + n); this.i += n; return v; }
    if (t === 11) { const n = this.i32(); return Array.from({ length: n }, () => this.i32()); }
    if (t === 12) { const n = this.i32(); return Array.from({ length: n }, () => this.i64()); }
    if (t === 9) { const e = this.u8(); const n = this.i32(); return { e, v: Array.from({ length: n }, () => this.value(e)) }; }
    if (t === 10) { const v = []; for (;;) { const ct = this.u8(); if (!ct) return v; v.push([ct, this.str(), this.value(ct)]); } }
    throw new Error(`unsupported NBT tag ${t}`);
  }
  root() { const t = this.u8(); if (t !== 10) throw new Error('root is not compound'); this.str(); return this.value(t); }
}
function entry(compound, name) { return compound.find(([, n]) => n === name)?.[2]; }
function state(compound) { return Object.fromEntries(compound.map(([, n, v]) => [n, v])); }
function digest(bytes) { return createHash('sha256').update(bytes).digest('hex'); }
function u16(n) { const b = Buffer.alloc(2); b.writeUInt16BE(n); return b; }
function i32(n) { const b = Buffer.alloc(4); b.writeInt32BE(n); return b; }
function text(s) { const b = Buffer.from(s); return Buffer.concat([u16(b.length), b]); }
function encode(t, v) {
  if (t === 1) return Buffer.from([v & 0xff]);
  if (t === 2) { const b = Buffer.alloc(2); b.writeInt16BE(v); return b; }
  if (t === 3) return i32(v);
  if (t === 4) { const b = Buffer.alloc(8); b.writeBigInt64BE(v); return b; }
  if (t === 5) { const b = Buffer.alloc(4); b.writeFloatBE(v); return b; }
  if (t === 6) { const b = Buffer.alloc(8); b.writeDoubleBE(v); return b; }
  if (t === 7) return Buffer.concat([i32(v.length), v]);
  if (t === 8) return text(v);
  if (t === 9) return Buffer.concat([Buffer.from([v.e]), i32(v.v.length), ...v.v.map(x => encode(v.e, x))]);
  if (t === 10) return Buffer.concat([...v.flatMap(([ct, n, x]) => [Buffer.from([ct]), text(n), encode(ct, x)]), Buffer.from([0])]);
  if (t === 11) return Buffer.concat([i32(v.length), ...v.map(i32)]);
  if (t === 12) { const xs = v.map(x => { const b = Buffer.alloc(8); b.writeBigInt64BE(x); return b; }); return Buffer.concat([i32(v.length), ...xs]); }
  throw new Error(`cannot encode NBT tag ${t}`);
}
function verifyTree(tree, family, requireReplacement) {
  const palette = entry(tree, 'palette').v;
  const blocks = entry(tree, 'blocks').v;
  const size = entry(tree, 'size').v;
  if (size.length !== 3 || size[0] !== 15 || size[1] !== 14 || size[2] !== 19) throw new Error(`${family}: size drift`);
  const positions = new Map();
  for (const block of blocks) {
    const s = state(block); const p = s.pos.v.join(','); const ps = state(palette[s.state]);
    if (expected.has(p)) {
      if ('nbt' in s) throw new Error(`${family}:${p}: affected cell unexpectedly has block entity data`);
      positions.set(p, ps);
    }
  }
  if (positions.size !== 2) throw new Error(`${family}: missing exact affected positions`);
  for (const [pos, bites] of expected) {
    const p = positions.get(pos); if (!p || p.Name !== (requireReplacement ? replacement : target)) throw new Error(`${family}:${pos}: wrong block`);
    if (requireReplacement && 'Properties' in p) throw new Error(`${family}:${pos}: rice_bag has unexpected properties`);
    if (!requireReplacement && (!p.Properties || state(p.Properties).bites !== String(bites))) throw new Error(`${family}:${pos}: lost source bite state`);
  }
}

function requireEntry(tree, name, family) {
  const value = entry(tree, name);
  if (value === undefined) throw new Error(`${family}: missing ${name}`);
  return value;
}

function verifyPreservedBefore(before, after, family) {
  verifyTree(before, family, false);
  verifyTree(after, family, true);
  if (before.length !== after.length) throw new Error(`${family}: root entry count drift`);
  for (let index = 0; index < before.length; index++) {
    const [beforeType, beforeName, beforeValue] = before[index];
    const [afterType, afterName, afterValue] = after[index];
    if (beforeType !== afterType || beforeName !== afterName) throw new Error(`${family}: root entry identity drift at ${index}`);
    if (beforeName === 'palette') continue;
    if (!isDeepStrictEqual(beforeValue, afterValue)) throw new Error(`${family}: unrelated root tag changed: ${beforeName}`);
  }
  const oldPalette = requireEntry(before, 'palette', family).v;
  const newPalette = requireEntry(after, 'palette', family).v;
  if (oldPalette.length !== newPalette.length) throw new Error(`${family}: palette length drift`);
  const changedIndexes = new Set();
  for (let index = 0; index < oldPalette.length; index++) {
    const oldState = state(oldPalette[index]); const newState = state(newPalette[index]);
    if (oldState.Name !== target) {
      if (!isDeepStrictEqual(oldPalette[index], newPalette[index])) throw new Error(`${family}: unrelated palette entry changed: ${index}`);
      continue;
    }
    changedIndexes.add(index);
    if (newState.Name !== replacement || 'Properties' in newState) {
      throw new Error(`${family}: palette entry ${index} was not the exact approved replacement`);
    }
    const oldUnchanged = oldPalette[index].filter(([, name]) => name !== 'Name' && name !== 'Properties');
    const newUnchanged = newPalette[index].filter(([, name]) => name !== 'Name' && name !== 'Properties');
    if (!isDeepStrictEqual(oldUnchanged, newUnchanged)) throw new Error(`${family}: palette entry ${index} changed outside Name/Properties`);
  }
  if (changedIndexes.size !== 2) throw new Error(`${family}: expected exactly two changed palette entries`);
  const references = new Map([...changedIndexes].map(index => [index, []]));
  const oldBlocks = requireEntry(before, 'blocks', family).v;
  const newBlocks = requireEntry(after, 'blocks', family).v;
  if (!isDeepStrictEqual(oldBlocks, newBlocks)) throw new Error(`${family}: block references or unrelated tags changed`);
  for (const block of oldBlocks) {
    const blockState = state(block);
    if (references.has(blockState.state)) references.get(blockState.state).push(blockState.pos.v.join(','));
  }
  const affected = [...references.values()].flat().sort();
  if (!isDeepStrictEqual(affected, [...expected.keys()].sort())) {
    throw new Error(`${family}: changed palette entries are referenced outside the approved placements`);
  }
}

async function beforeTree(family) {
  if (!beforeRoot) return null;
  const file = path.join(beforeRoot, `${family}-bunkhouse_2.before.nbt`);
  return new Reader(gunzipSync(await readFile(file))).root();
}

function verifyBeforeIfProvided(before, tree, family) {
  if (before !== null) verifyPreservedBefore(before, tree, family);
}
for (const family of families) {
  const file = path.join(root, 'pale-mirror-visuals/src/main/resources/data/pale_mirror_visuals/structure', family, 'bunkhouse_2.nbt');
  const before = await readFile(file); const tree = new Reader(gunzipSync(before)).root();
  const preservedBefore = await beforeTree(family);
  const already = entry(tree, 'palette').v.filter(p => state(p).Name === replacement).length;
  if (process.argv.includes('--check')) {
    if (preservedBefore === null) throw new Error('--check requires --before-root with retained decoded baseline inputs');
    verifyTree(tree, family, true); verifyBeforeIfProvided(preservedBefore, tree, family);
    console.log(`${family} ${digest(before)}`); continue;
  }
  if (!already) verifyTree(tree, family, false);
  let changed = 0;
  for (const p of entry(tree, 'palette').v) {
    const name = p.find(([, n]) => n === 'Name');
    if (name?.[2] !== target) continue;
    name[2] = replacement;
    const property = p.findIndex(([, n]) => n === 'Properties'); if (property >= 0) p.splice(property, 1);
    changed++;
  }
  if (changed !== 0 && changed !== 2) throw new Error(`${family}: expected two dinosaur palette entries, got ${changed}`);
  verifyTree(tree, family, true);
  if (changed) {
    const encoded = Buffer.concat([Buffer.from([10]), text(''), encode(10, tree)]);
    await writeFile(file, gzipSync(encoded));
  }
  const current = await readFile(file);
  verifyBeforeIfProvided(preservedBefore, new Reader(gunzipSync(current)).root(), family);
  console.log(`${family} ${digest(current)}`);
}
