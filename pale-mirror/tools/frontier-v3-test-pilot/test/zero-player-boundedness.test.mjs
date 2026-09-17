import assert from 'node:assert/strict';
import test from 'node:test';
import { zeroPlayerBoundedness } from '../src/zero-player-boundedness.mjs';

test('zero-player boundedness retains the exact server-thread stall rather than relabelling later ingress', () => {
  assert.deepEqual(zeroPlayerBoundedness("[Server thread/WARN]: Can't keep up! Is the server overloaded? Running 2263ms or 45 ticks behind"), {
    status: 'STALL', noServerTickStall: false, stallCount: 1, maxBehindMillis: 2263, maxBehindTicks: 45
  });
});

test('zero-player boundedness distinguishes a clean canonical interval', () => {
  assert.deepEqual(zeroPlayerBoundedness('Frontier v3 completed operator fast-forward'), {
    status: 'NO_STALL', noServerTickStall: true, stallCount: 0, maxBehindMillis: 0, maxBehindTicks: 0
  });
});
