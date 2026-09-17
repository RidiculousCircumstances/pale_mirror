import assert from 'node:assert/strict';
import test from 'node:test';
import { zeroPlayerPerformance, zeroPlayerRejectedReceipt, zeroPlayerRequestReceipt } from '../src/zero-player-receipt.mjs';

const queued = 'PMV3_DIAG {"kind":"status","status":"ok","fastForwardRequests":[{"requestId":1,"kind":"RELATIVE","requestedTicks":24000,"targetInstant":24001,"admittedCheckpointInstant":1,"reachedCheckpointInstant":null,"status":"QUEUED","reason":null}]}';

test('zero-player timeout preserves a queued server-owned request instead of inventing a terminal marker', () => {
  const receipt = zeroPlayerRequestReceipt(queued);
  assert.equal(receipt.status, 'QUEUED');
  assert.equal(receipt.targetInstant, 24001);
  assert.equal(receipt.reachedCheckpointInstant, null);
  assert.throws(() => zeroPlayerRejectedReceipt(queued), /recoverable rejected request receipt/);
});

test('zero-player receipt accepts only attributable bounded performance diagnostics', () => {
  const performance = zeroPlayerPerformance('PMV3_DIAG {"kind":"performance","status":"ok","fastForwardSlice":{"samples":3,"advancedTicks":8,"totalNanos":30,"maxNanos":20,"safetyNanos":4,"maxSafetyNanos":2,"advanceNanos":26,"maxAdvanceNanos":18}}');
  assert.equal(performance.fastForwardSlice.advancedTicks, 8);
  assert.throws(() => zeroPlayerPerformance('PMV3_DIAG {"kind":"performance","status":"ok","fastForwardSlice":{"samples":0}}'), /bounded fast-forward slice attribution/);
});
