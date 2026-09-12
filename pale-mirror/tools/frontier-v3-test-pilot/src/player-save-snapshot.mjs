import { createHash } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import { gunzipSync } from 'node:zlib';
import { join, resolve } from 'node:path';

const MAX_PLAYER_DATA_BYTES = 4 * 1024 * 1024;

/**
 * Reads the one ordinary playerdata file after the exact owned JVM is dead and before its
 * replacement is started. This is evidence only: it neither opens a Minecraft save nor changes
 * a player, chunk, ticket, canonical record, or lifecycle transition.
 */
export async function snapshotPlayerSave(worldDirectory, { player, item, expectedCount }) {
  if (!safeUuid(player) || !itemKind(item) || !Number.isInteger(expectedCount) || expectedCount < 0 || expectedCount > 64) {
    throw new Error('player-save snapshot declaration is malformed');
  }
  const world = resolve(worldDirectory); const file = resolve(world, 'playerdata', `${player}.dat`);
  if (!file.startsWith(`${world}/`)) throw new Error('player-save snapshot escapes its disposable world');
  let source;
  try { source = await readFile(file); }
  catch (error) {
    if (error?.code !== 'ENOENT') throw error;
    if (expectedCount !== 0) throw new Error('player-save snapshot is absent before the declared physical save');
    return Object.freeze({ player, item, expectedCount, exists: false, itemCount: 0, sha256: null });
  }
  if (source.length > MAX_PLAYER_DATA_BYTES) throw new Error('player-save snapshot exceeds its bounded playerdata size');
  const itemCount = inventoryCount(source, item);
  if (itemCount !== expectedCount) throw new Error(`player-save snapshot quantity differs from its declared arrival order: ${itemCount}`);
  return Object.freeze({ player, item, expectedCount, exists: true, itemCount,
    sha256: createHash('sha256').update(source).digest('hex') });
}

/** Parses just the vanilla Inventory list; all other NBT fields are skipped with bounded reads. */
export function inventoryCount(source, wantedItem) {
  const bytes = Buffer.isBuffer(source) ? source : Buffer.from(source);
  const payload = bytes.subarray(0, 2).equals(Buffer.from([0x1f, 0x8b])) ? gunzipSync(bytes) : bytes;
  if (payload.length > MAX_PLAYER_DATA_BYTES) throw new Error('expanded player-save snapshot exceeds its bounded size');
  const reader = new Reader(payload); const rootType = reader.byte();
  if (rootType !== 10) throw new Error('player-save snapshot root is not a compound');
  reader.string();
  return compoundInventory(reader, wantedItem, 0);
}

function compoundInventory(reader, wantedItem, depth) {
  let count = 0;
  while (true) {
    const type = reader.byte(); if (type === 0) return count;
    const name = reader.string();
    if (name === 'Inventory') {
      if (type !== 9) throw new Error('player-save Inventory list is malformed');
      count += inventoryList(reader, wantedItem, depth + 1);
    } else reader.skip(type, depth + 1);
  }
}

function inventoryList(reader, wantedItem, depth) {
  const type = reader.byte(); const length = reader.int();
  if (type !== 10 || length < 0 || length > 256) throw new Error('player-save Inventory list is malformed');
  let count = 0;
  for (let index = 0; index < length; index++) count += itemCompound(reader, wantedItem, depth + 1);
  return count;
}

function itemCompound(reader, wantedItem, depth) {
  let id; let count = 0;
  while (true) {
    const type = reader.byte(); if (type === 0) return id === wantedItem ? count : 0;
    const name = reader.string();
    if (name === 'id' && type === 8) id = reader.string();
    else if (name === 'Count' && type === 1) count = reader.signedByte();
    // Current 1.21 playerdata uses the component-era lowercase int form, while
    // older vanilla playerdata retains Count as a byte. Both are physical save
    // encodings of the same bounded inventory stack and neither changes custody.
    else if (name === 'count' && type === 3) count = reader.int();
    else reader.skip(type, depth + 1);
  }
}

class Reader {
  constructor(bytes) { this.bytes = bytes; this.offset = 0; }
  require(size) { if (size < 0 || this.offset + size > this.bytes.length) throw new Error('player-save NBT is truncated'); }
  byte() { this.require(1); return this.bytes[this.offset++]; }
  signedByte() { const value = this.byte(); return value > 127 ? value - 256 : value; }
  short() { this.require(2); const value = this.bytes.readUInt16BE(this.offset); this.offset += 2; return value; }
  int() { this.require(4); const value = this.bytes.readInt32BE(this.offset); this.offset += 4; return value; }
  string() { const length = this.short(); this.require(length); const value = this.bytes.toString('utf8', this.offset, this.offset + length); this.offset += length; return value; }
  skip(type, depth) {
    if (depth > 32) throw new Error('player-save NBT exceeds its nesting bound');
    switch (type) {
      case 1: this.require(1); this.offset++; return;
      case 2: this.require(2); this.offset += 2; return;
      case 3: case 5: this.require(4); this.offset += 4; return;
      case 4: case 6: this.require(8); this.offset += 8; return;
      case 7: return this.skipArray(1);
      case 8: this.string(); return;
      case 9: { const element = this.byte(); const length = this.int(); if (length < 0 || length > 1_000_000) throw new Error('player-save NBT list is malformed'); for (let i = 0; i < length; i++) this.skip(element, depth + 1); return; }
      case 10: while (true) { const child = this.byte(); if (child === 0) return; this.string(); this.skip(child, depth + 1); }
      case 11: return this.skipArray(4);
      case 12: return this.skipArray(8);
      default: throw new Error('player-save NBT has an unknown tag');
    }
  }
  skipArray(width) { const length = this.int(); if (length < 0 || length > 1_000_000) throw new Error('player-save NBT array is malformed'); this.require(length * width); this.offset += length * width; }
}

function safeUuid(value) { return typeof value === 'string' && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(value); }
function itemKind(value) { return typeof value === 'string' && /^[a-z0-9_.-]+:[a-z0-9_./-]+$/.test(value); }
