import assert from 'node:assert/strict';
import { EventEmitter } from 'node:events';
import test from 'node:test';
import { awaitRequiredEvidenceOrExit, createStampedDiagnosticRouter, MAX_DIAGNOSTIC_LINE_BYTES, MAX_DIAGNOSTIC_SEGMENT_BYTES, MAX_DIAGNOSTIC_VALUES_PER_SEGMENT, MAX_DIAGNOSTIC_WORKER_BYTES } from '../src/run-persistent-matrix-client.mjs';

const identity = Object.freeze({ runId: '11111111-1111-4111-8111-111111111111', sessionId: '22222222-2222-4222-8222-222222222222' });
const entries = Object.freeze([{ id: 'before', epoch: 0 }, { id: 'after', epoch: 1 }]);
const line = (segment, epoch, step = 1) => `PMV3_PILOT_DIAGNOSTIC ${JSON.stringify({ kind: 'job', id: segment, pilotRunId: identity.runId, pilotSessionId: identity.sessionId, pilotSegment: segment, pilotEpoch: epoch, pilotActionStep: step })}`;

test('stamped diagnostic router retains delayed old evidence as ancillary trace without changing sealed values', () => {
  const router = createStampedDiagnosticRouter(entries, identity); router.acceptLine(line('before', 0)); router.seal('before'); router.acceptLine(line('before', 0, 2)); router.acceptLine(line('after', 1));
  assert.equal(router.values('before').length, 1); assert.equal(router.values('after').length, 1);
  assert.equal(router.lateValues('before')[0].actionStep, 2);
});

test('stamped diagnostic router rejects foreign session and bounded unfinished lines', () => {
  const router = createStampedDiagnosticRouter(entries, identity);
  assert.throws(() => router.acceptLine(line('before', 1)), /foreign|stale/);
  assert.throws(() => router.acceptChunk('stdout', 'x'.repeat(MAX_DIAGNOSTIC_LINE_BYTES + 1)), /unfinished/);
  assert.throws(() => router.acceptChunk('stderr', `\n${'x'.repeat(MAX_DIAGNOSTIC_LINE_BYTES + 1)}`), /unfinished/);
});

test('stamped diagnostic router keeps unannotated setup separate and rejects partial action stamps', () => {
  const router = createStampedDiagnosticRouter(entries, identity);
  router.acceptLine(`PMV3_PILOT_DIAGNOSTIC ${JSON.stringify({ kind: 'setup', id: 'fixture' })}`);
  assert.equal(router.setupTrace().length, 1);
  assert.equal(router.values('before').length, 0);
  assert.throws(() => router.acceptLine(`PMV3_PILOT_DIAGNOSTIC ${JSON.stringify({ kind: 'job', id: 'before', pilotActionStep: 1 })}`), /incomplete/);
});

test('completed-line callback sees a fatal marker split across pipe chunks', () => {
  const lines = []; const router = createStampedDiagnosticRouter(entries, identity, (line) => lines.push(line));
  router.acceptChunk('stdout', 'PMV3_PILOT_FATAL reconnect'); router.acceptChunk('stdout', '_failed\n');
  assert.deepEqual(lines, ['PMV3_PILOT_FATAL reconnect_failed']);
});

test('stamped diagnostic router fails closed at declared count, segment-byte and worker-byte capacities', () => {
  const counted = createStampedDiagnosticRouter(entries, identity);
  for (let index = 0; index < MAX_DIAGNOSTIC_VALUES_PER_SEGMENT; index++) counted.acceptLine(line('before', 0, index + 1));
  assert.throws(() => counted.acceptLine(line('before', 0, MAX_DIAGNOSTIC_VALUES_PER_SEGMENT + 1)), /capacity/);
  const padded = (segment, epoch, step) => `PMV3_PILOT_DIAGNOSTIC ${JSON.stringify({ kind: 'job', id: segment, payload: 'x'.repeat(60 * 1024), pilotRunId: identity.runId, pilotSessionId: identity.sessionId, pilotSegment: segment, pilotEpoch: epoch, pilotActionStep: step })}`;
  const byteEntries = Array.from({ length: 35 }, (_, epoch) => ({ id: `byte-${epoch}`, epoch })); const bounded = createStampedDiagnosticRouter(byteEntries, identity);
  for (let index = 0; index < 17; index++) bounded.acceptLine(padded('byte-0', 0, index + 1));
  assert.ok(17 * 60 * 1024 < MAX_DIAGNOSTIC_SEGMENT_BYTES);
  assert.throws(() => bounded.acceptLine(padded('byte-0', 0, 18)), /capacity/);
  const worker = createStampedDiagnosticRouter(byteEntries, identity);
  let rejected = false;
  for (const entry of byteEntries) for (let step = 1; step <= 16; step++) {
    try { worker.acceptLine(padded(entry.id, entry.epoch, step)); }
    catch (error) { assert.match(String(error), /capacity/); rejected = true; break; }
  }
  assert.equal(rejected, true); assert.ok(35 * 16 * 60 * 1024 > MAX_DIAGNOSTIC_WORKER_BYTES);
});

test('required stamped evidence can arrive after its lifecycle signal but cannot survive fatal or cancellation', async () => {
  const router = createStampedDiagnosticRouter(entries, identity); const cancelled = new AbortController();
  const awaited = router.awaitRequired('before', (values) => values.some((value) => value.actionStep === 1), cancelled.signal, 'before evidence', 1000);
  // The caller has already observed its filesystem lifecycle signal; stdout arrives later.
  router.acceptChunk('stdout', `${line('before', 0)}\n`); await awaited;
  router.seal('before'); assert.equal(router.values('before').length, 1);
  const missing = router.awaitRequired('after', (values) => values.length > 0, cancelled.signal, 'after evidence', 1000);
  cancelled.abort(new Error('pilot fatal')); await assert.rejects(missing, /pilot fatal/);
});

test('required evidence accepts prefix zero, retains reordered nonterminal values, and closes on exact client exit', async () => {
  const router = createStampedDiagnosticRouter(entries, identity); const alive = new AbortController();
  await router.awaitRequired('before', (values) => values.every((value) => value.actionStep <= 0), alive.signal, 'zero prefix', 1000);
  const reordered = router.awaitRequired('before', (values) => values.some((value) => value.actionStep === 1) && values.some((value) => value.actionStep === 2), alive.signal, 'all assertions', 1000);
  router.acceptLine(line('before', 0, 2)); router.acceptChunk('stderr', `${line('before', 0, 1)}\n`); await reordered;
  const child = Object.assign(new EventEmitter(), { exitCode: null, signalCode: null });
  const pending = awaitRequiredEvidenceOrExit(router, entries[1], (values) => values.length > 0, child, alive.signal, 'required after evidence');
  child.emit('exit', 17); await assert.rejects(pending, /client exited/); assert.equal(child.listenerCount('exit'), 0);
});
