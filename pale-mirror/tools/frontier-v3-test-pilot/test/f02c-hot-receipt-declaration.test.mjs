import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import { assertF02cHotReceiptCarrier, assertF02cHotReceiptDeclaration } from '../src/f02c-hot-receipt-carrier.mjs';
import { buildF02cHotReceiptCarrierReceipt, preflightF02cHotReceiptCarrier } from '../src/run-f02c-hot-receipt-carrier.mjs';

const outerAttempt = '00000000-0000-0000-0000-000000000031';
const clientRunId = '00000000-0000-0000-0000-000000000032';
const nonce = '00000000-0000-0000-0000-000000000033';
const sessionId = '00000000-0000-0000-0000-000000000034';
const declarationSource = await readFile(new URL('../scenarios/disposable-settlement-assault-restart.json', import.meta.url));
const declarationSha256 = hash(declarationSource); const declaration = JSON.parse(declarationSource); const build = { sourceCommit: '2139f55c4c009cc30d6e569ca5d1ac72a62b4847', sourceDirty: false, profile: 'settlement-assault' };

function envelope() {
  const current = structuredClone(declaration); const identity = { schema: 1, buildIdentitySha256: hash(JSON.stringify(build)), workerId: 'local-hot-receipt', runId: outerAttempt,
    scenarioId: current.id, segmentId: 'native-scenario', nonce, sessionId };
  const barriers = [
    ['server_run_ready', { serverRunId: 'before' }], ['prepared_client_ready', { clientPid: 101, segment: 'before_restart' }],
    ['client_connected_fixture_ready', { clientPid: 101, segment: 'before_restart' }], ['action_checkpoint_acknowledged', { actionStep: 2, segment: 'before_restart' }],
    ['scenario_segment_complete', { segment: 'before_restart' }], ['client_normally_disconnected', { clientPid: 101, segment: 'before_restart' }],
    ['durable_server_save', { serverRunId: 'before' }], ['game_port_closed', { port: 25575 }], ['recovery_server_ready', { serverRunId: 'after' }],
    ['same_client_reconnected_state_cleared', { clientPid: 101 }], ['action_checkpoint_acknowledged', { actionStep: 6, segment: 'after_restart' }],
    ['scenario_segment_complete', { segment: 'after_restart' }], ['terminal_assertion_complete', { assertionCount: 4 }]
  ];
  const lifecycle = barriers.map(([barrier, detail], index) => ({ schema: 1, sequence: index + 1, barrier, identity: structuredClone(identity), detail }));
  const semantic = current.assertions.filter(entry => [2, 3, 5].includes(entry.after));
  const record = (entry) => ({ assertion: structuredClone(entry), observed: { actionStep: entry.after,
    value: { kind: 'scene', id: 'assault:development-settlement-assault', ...structuredClone(entry.expect) } } });
  const manifest = { schema: 2, status: 'ok', scenarioId: current.id, scenarioSha256: declarationSha256, scenarioDeclarationSha256: declarationSha256,
    runId: clientRunId, build: structuredClone(build), recovery: { mode: 'graceful', splitAfterAction: 2, clientSession: { runId: outerAttempt, reusedJvm: true } },
    actions: current.actions.map((action, index) => ({ correlation: `scenario:${clientRunId}:${index + 1}`, action: structuredClone(action) })), lifecycle,
    clientSegments: [{ segment: 'persistent_restart', manifest: 'settlement-assault.manifest.json', runId: clientRunId, timing: {}, reusedJvm: true }],
    diagnostics: semantic.map(record).concat([{ actionStep: 2, value: structuredClone(semantic[0].expect) }]) };
  return { scenario: 'disposable-settlement-assault-restart.json', declarationSource, declarationSha256, outerAttempt, declaration: current, manifest,
    manifestSource: Buffer.from(`${JSON.stringify(manifest)}\n`) };
}

test('HOT declaration begins before manufacture and names three exact persistent receipt slots', () => {
  const slots = assertF02cHotReceiptDeclaration(declaration);
  assert.deepEqual(Object.keys(slots), ['preRestart', 'recovered', 'released']);
  assert.deepEqual([slots.preRestart.after, slots.recovered.after, slots.released.after], [2, 3, 5]);
});

test('HOT consumer reads exactly one declaration-bound pre-restart, recovery, and release record from the final manifest', () => {
  const value = envelope(); const receipt = assertF02cHotReceiptCarrier({ declaration: value.declaration, manifest: value.manifest });
  assert.equal(receipt.scene, 'assault:development-settlement-assault'); assert.equal(receipt.lifecycle.released, 5);
});

test('HOT production-shaped preflight binds declaration bytes, persistent runner, build, lifecycle, and final-manifest digest', () => {
  const value = envelope(); const validated = preflightF02cHotReceiptCarrier(value);
  assert.equal(validated.declarationSha256, declarationSha256); assert.equal(validated.finalManifestSha256, hash(value.manifestSource));
  assert.deepEqual(validated.identity, { outerAttempt, clientRunId, lifecycle: { runId: outerAttempt, sessionId, nonce } });
  assert.equal(buildF02cHotReceiptCarrierReceipt(validated).finalManifest.sha256, hash(value.manifestSource));
});

test('HOT consumer rejects observed receipt mutation, replay, old record, lifecycle misassociation, and duplicate declaration-bound slots', () => {
  for (const mutate of [
    value => { value.manifest.diagnostics[0].observed.value.strikeReceipt = 'observation:intent:foreign'; },
    value => { value.manifest.diagnostics[0].observed.value.kind = 'trace'; },
    value => { value.manifest.diagnostics[0].observed.value.id = 'assault:foreign'; },
    value => { value.manifest.diagnostics[1].observed.actionStep = 2; },
    value => { value.manifest.diagnostics[2].observed.value.strikeIntent = value.manifest.diagnostics[0].observed.value.strikeIntent.replace(/.$/, 'f'); },
    value => { value.manifest.lifecycle[5].identity.runId = clientRunId; },
    value => { value.manifest.diagnostics.splice(1, 0, structuredClone(value.manifest.diagnostics[1])); },
    value => { value.manifest.actions[3].correlation = `scenario:${clientRunId}:3`; },
    value => { value.manifest.recovery.clientSession.runId = clientRunId; },
    value => { value.manifest.lifecycle[3].detail.actionStep = 3; },
    value => { value.manifest.lifecycle[10].detail.segment = 'before_restart'; }
  ]) {
    const value = envelope(); mutate(value); value.manifestSource = Buffer.from(`${JSON.stringify(value.manifest)}\n`);
    assert.throws(() => preflightF02cHotReceiptCarrier(value), /F0\.2C HOT carrier|lifecycle barrier/);
  }
});

function hash(value) { return createHash('sha256').update(value).digest('hex'); }
