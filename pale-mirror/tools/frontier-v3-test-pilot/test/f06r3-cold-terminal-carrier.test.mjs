import assert from 'node:assert/strict';
import test from 'node:test';
import { readFile } from 'node:fs/promises';
import { assertF06r3ColdTerminalDeclaration, f06r3AcceleratedPreludeControl, f06r3ColdTerminalIngressResponsive } from '../src/f06r3-cold-terminal-carrier.mjs';
import { terminalReceiptTopologyRevision } from '../src/run-f06r3-cold-terminal-carrier.mjs';

test('COLD terminal carrier requires both ordinary ingress and restart re-entry receipts', () => {
  const receipt = { status: 'ok', ordinaryClientJoined: true, noServerTickStall: true };
  assert.equal(f06r3ColdTerminalIngressResponsive([receipt, receipt]), true);
  assert.equal(f06r3ColdTerminalIngressResponsive([receipt]), false);
  assert.equal(f06r3ColdTerminalIngressResponsive([receipt, { ...receipt, noServerTickStall: false }]), false);
});

test('COLD terminal carrier retains accelerated scheduler-debt telemetry while requiring a bounded completed control receipt', () => {
  const prelude = { status: 'completed', advanceTicks: 24_000, clientSegmentsBeforeCompletion: 0, clientSegmentsBeforeAdmission: 0,
    boundedness: { status: 'STALL', noServerTickStall: false, stallCount: 1, maxBehindMillis: 2003, maxBehindTicks: 40 },
    performance: { fastForwardRequests: [{ kind: 'RELATIVE', requestedTicks: 24_000, targetInstant: 24_001,
      admittedCheckpointInstant: 1, reachedCheckpointInstant: 27_618, status: 'COMPLETED' }],
    fastForwardSlice: { samples: 3_617, advancedTicks: 24_000, totalNanos: 44_698_703_102, maxNanos: 128_880_719,
      safetyNanos: 29_415_713, maxSafetyNanos: 78_767, advanceNanos: 44_659_386_639, maxAdvanceNanos: 128_875_719 } } };
  const manifest = { status: 'ok', terminalCleanup: { portClosed: true }, lifecycle: [{ barrier: 'terminal_assertion_complete' }] };
  assert.equal(f06r3AcceleratedPreludeControl(prelude, manifest), true);
  assert.equal(f06r3AcceleratedPreludeControl({ ...prelude, performance: { ...prelude.performance,
    fastForwardRequests: [{ ...prelude.performance.fastForwardRequests[0], status: 'FAILED' }] } }, manifest), false);
  assert.equal(f06r3AcceleratedPreludeControl(prelude, { ...manifest, terminalCleanup: { portClosed: false } }), false);
});

test('COLD terminal carrier retains the measured 24k execution window without relaxing its five-minute ceiling', async () => {
  const declaration = JSON.parse(await readFile(new URL('../scenarios/disposable-f06r3-cold-terminal.json', import.meta.url), 'utf8'));
  // The retained native server receipt completed the admitted 24k interval in
  // 182,764 ms.  Keep a modest, explicit observation margin for a real
  // terminal receipt; this is not a semantic-duration or product-rate change.
  assert.equal(declaration.zeroPlayerPrelude.timeoutMs, 240_000);
  assert.ok(declaration.zeroPlayerPrelude.timeoutMs <= 300_000);
});

test('COLD terminal carrier binds the field arrival oracle at the declared visit action', async () => {
  const declaration = JSON.parse(await readFile(new URL('../scenarios/disposable-f06r3-cold-terminal.json', import.meta.url), 'utf8'));
  assert.doesNotThrow(() => assertF06r3ColdTerminalDeclaration(declaration));
  const misplacedVisit = structuredClone(declaration);
  misplacedVisit.actions[3].position.diagnostic.field = 'firstCrop';
  assert.throws(() => assertF06r3ColdTerminalDeclaration(misplacedVisit), /exact no-player\/ordinary-ingress declaration/);
});

test('COLD terminal carrier compares the terminal topology revision without JavaScript number rounding', () => {
  const source = '{"terminalReceipt":{"topology":{"id":"topology:test","revision":3287615078285191244,"cursor":94}}}';
  assert.equal(terminalReceiptTopologyRevision(source), '3287615078285191244');
  assert.equal(terminalReceiptTopologyRevision(Buffer.from(source)), '3287615078285191244');
  assert.equal(terminalReceiptTopologyRevision(`${source}${source}`), '3287615078285191244');
  assert.throws(() => terminalReceiptTopologyRevision(`${source}{"terminalReceipt":{"topology":{"revision":3287615078285191245}}}`), /not exactly representable/);
  assert.equal(terminalReceiptTopologyRevision('{"terminalReceipt":{"topology":{"revision":"3287615078285191244"}}}'), '3287615078285191244');
});
