import assert from 'node:assert/strict';
import { gzipSync } from 'node:zlib';
import test from 'node:test';
import { inventoryCount } from '../src/player-save-snapshot.mjs';

test('player-save snapshot reads only the exact vanilla Inventory item count', () => {
  const source = root([item('minecraft:wheat', 32), item('minecraft:stick', 4)]);
  assert.equal(inventoryCount(source, 'minecraft:wheat'), 32);
  assert.equal(inventoryCount(gzipSync(source), 'minecraft:stick'), 4);
  assert.equal(inventoryCount(source, 'minecraft:diamond'), 0);
});

test('player-save snapshot recognizes the current lowercase integer stack count', () => {
  assert.equal(inventoryCount(root([modernItem('minecraft:wheat', 32)]), 'minecraft:wheat'), 32);
});

test('player-save snapshot rejects malformed Inventory type and truncated NBT', () => {
  const malformed = Buffer.concat([Buffer.from([10, 0, 0, 8]), string('Inventory'), string('not-a-list'), Buffer.from([0])]);
  assert.throws(() => inventoryCount(malformed, 'minecraft:wheat'), /Inventory list/);
  assert.throws(() => inventoryCount(Buffer.from([10, 0]), 'minecraft:wheat'), /truncated/);
});

function root(items) { return Buffer.concat([Buffer.from([10, 0, 0, 9]), string('Inventory'), Buffer.from([10]), integer(items.length), ...items, Buffer.from([0])]); }
function item(id, count) { return Buffer.concat([Buffer.from([8]), string('id'), string(id), Buffer.from([1]), string('Count'), Buffer.from([count]), Buffer.from([0])]); }
function modernItem(id, count) { return Buffer.concat([Buffer.from([3]), string('count'), integer(count), Buffer.from([1]), string('Slot'), Buffer.from([0]), Buffer.from([8]), string('id'), string(id), Buffer.from([0])]); }
function string(value) { const bytes = Buffer.from(value); return Buffer.concat([Buffer.from([0, bytes.length]), bytes]); }
function integer(value) { const bytes = Buffer.alloc(4); bytes.writeInt32BE(value); return bytes; }
