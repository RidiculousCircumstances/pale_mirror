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
// The seeded assault lease is lease:assault-development-settlement-assault-r14.
const fixtureStrikeIntent = 'intent:scene-strike-assault-9cb1e908be6e0c35354f9b92fb020f744407022a00be79b8ecb239254bd862c3';
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
    diagnostics: [{ observed: { actionStep: 1, value: { kind: 'demand_handshake', id: current.actions[0].demandHandshake.request,
      assault: current.actions[0].demandHandshake.assault, destinationDimension: current.actions[0].dimension,
      travelAnchor: structuredClone(current.actions[0].position), candidateHandoff: structuredClone(current.actions[0].demandHandshake.handoff),
      pilotRunId: outerAttempt, pilotActionStep: 1, pilotActionAttempt: '00000000-0000-0000-0000-000000000036',
      playerId: '00000000-0000-0000-0000-000000000035', serverPlayerPosition: { x: -360, y: 65, z: -352 }, destinationObserved: true,
      destinationPlayerTicket: true, destinationHolder: true, providerIdentity: 'projection-snapshot', exactCandidateCount: 1,
      sceneDemandChunkLoaded: true, sceneDemandObserverIds: ['00000000-0000-0000-0000-000000000035'], requestedObserverPresent: true, reason: 'ADMITTED' } } }]
      .concat(semantic.map(record), [{ observed: { actionStep: 1, value: { kind: 'visit_ingress', id: current.actions[0].demandHandshake.request,
        pilotRunId: outerAttempt, pilotActionStep: 1, pilotActionAttempt: '00000000-0000-0000-0000-000000000036', targetDimensionSeen: true, targetChunkSeen: true, finalClientDimension: current.actions[0].dimension,
        finalClientPosition: structuredClone(current.actions[0].position) } } }, { actionStep: 2, value: structuredClone(semantic[0].expect) }]) };
  return { scenario: 'disposable-settlement-assault-restart.json', declarationSource, declarationSha256, outerAttempt, declaration: current, manifest,
    manifestSource: Buffer.from(`${JSON.stringify(manifest)}\n`) };
}

test('HOT declaration begins before manufacture and names three exact persistent receipt slots', () => {
  const slots = assertF02cHotReceiptDeclaration(declaration);
  assert.deepEqual(Object.keys(slots), ['preRestart', 'recovered', 'released']);
  assert.deepEqual([slots.preRestart.after, slots.recovered.after, slots.released.after], [2, 3, 5]);
  assert.deepEqual(declaration.actions[0].demandHandshake, { request: 'settlement-assault-visit', assault: 'assault:development-settlement-assault', handoff: { x: -360, y: 64, z: -340 } });
  assert.equal(declaration.actions[0].settleMs, 0);
});

test('HOT declaration binds the seeded lease receipt rather than a stale foreign strike identity', () => {
  const slots = assertF02cHotReceiptDeclaration(declaration);
  for (const slot of Object.values(slots)) {
    assert.equal(slot.expect.strikeIntent, fixtureStrikeIntent);
    assert.equal(slot.expect.strikeReceipt, `observation:${fixtureStrikeIntent.replace(':', '-')}`);
  }
});

test('HOT declaration rejects the former client-local settle ingress before a carrier can run it', () => {
  const oldOrder = structuredClone(declaration);
  delete oldOrder.actions[0].demandHandshake;
  oldOrder.actions[0].settleMs = 1_000;
  assert.throws(() => assertF02cHotReceiptDeclaration(oldOrder), /server-thread demand handshake ingress/);
});

test('HOT consumer reads exactly one declaration-bound pre-restart, recovery, and release record from the final manifest', () => {
  const value = envelope(); const receipt = assertF02cHotReceiptCarrier({ declaration: value.declaration, manifest: value.manifest });
  assert.equal(receipt.scene, 'assault:development-settlement-assault'); assert.equal(receipt.lifecycle.released, 5);
});

test('HOT client ingress retains final position diagnostically without requiring an exact travel coordinate', () => {
  const value = envelope(); value.manifest.diagnostics[4].observed.value.finalClientPosition = { x: -359, y: 64, z: -351 };
  assert.equal(assertF02cHotReceiptCarrier({ declaration: value.declaration, manifest: value.manifest }).scene, 'assault:development-settlement-assault');
});

test('HOT production-shaped preflight binds declaration bytes, persistent runner, build, lifecycle, and final-manifest digest', () => {
  const value = envelope(); const validated = preflightF02cHotReceiptCarrier(value);
  assert.equal(validated.declarationSha256, declarationSha256); assert.equal(validated.finalManifestSha256, hash(value.manifestSource));
  assert.deepEqual(validated.identity, { outerAttempt, clientRunId, lifecycle: { runId: outerAttempt, sessionId, nonce } });
  assert.equal(buildF02cHotReceiptCarrierReceipt(validated).finalManifest.sha256, hash(value.manifestSource));
});

test('HOT consumer rejects observed receipt mutation, replay, old record, lifecycle misassociation, and duplicate declaration-bound slots', () => {
  for (const mutate of [
    value => { value.manifest.diagnostics[1].observed.value.strikeReceipt = 'observation:intent:foreign'; },
    value => { value.manifest.diagnostics[1].observed.value.kind = 'trace'; },
    value => { value.manifest.diagnostics[1].observed.value.id = 'assault:foreign'; },
    value => { value.manifest.diagnostics[2].observed.actionStep = 2; },
    value => { value.manifest.diagnostics[3].observed.value.strikeIntent = value.manifest.diagnostics[1].observed.value.strikeIntent.replace(/.$/, 'f'); },
    value => { value.manifest.lifecycle[5].identity.runId = clientRunId; },
    value => { value.manifest.diagnostics.splice(2, 0, structuredClone(value.manifest.diagnostics[2])); },
    value => { value.manifest.actions[3].correlation = `scenario:${clientRunId}:3`; },
    value => { value.manifest.recovery.clientSession.runId = clientRunId; },
    value => { value.manifest.lifecycle[3].detail.actionStep = 3; },
    value => { value.manifest.lifecycle[10].detail.segment = 'before_restart'; },
    value => { [value.manifest.lifecycle[3].detail, value.manifest.lifecycle[10].detail] = [value.manifest.lifecycle[10].detail, value.manifest.lifecycle[3].detail]; },
    value => { value.manifest.lifecycle[4].detail.segment = 'after_restart'; },
    value => { value.manifest.lifecycle[11].detail.segment = 'before_restart'; },
    value => { value.manifest.lifecycle.splice(12, 0, structuredClone(value.manifest.lifecycle[11])); value.manifest.lifecycle.forEach((entry, index) => { entry.sequence = index + 1; }); },
    value => { value.manifest.diagnostics[0].observed.value.reason = 'NO_DEMAND'; },
    value => { value.manifest.diagnostics[0].observed.value.id = 'other-request'; },
    value => { value.manifest.diagnostics[0].observed.value.destinationDimension = 'minecraft:overworld'; },
    value => { value.manifest.diagnostics[0].observed.value.destinationPlayerTicket = false; },
    value => { value.manifest.diagnostics[0].observed.value.exactCandidateCount = 2; },
    value => { value.manifest.diagnostics[0].observed.value.candidateHandoff.y = 65; },
    value => { value.manifest.diagnostics[0].observed.value.playerId = '00000000-0000-0000-0000-000000000036'; },
    value => { value.manifest.diagnostics[0].observed.value.sceneDemandObserverIds = []; },
    value => { value.manifest.diagnostics[0].observed.value.serverPlayerPosition = null; },
    value => { value.manifest.diagnostics[0].observed.value.pilotRunId = '00000000-0000-0000-0000-000000000037'; },
    value => { value.manifest.diagnostics[0].observed.value.pilotRunId = '00000000-0000-0000-0000-000000000037'; value.manifest.diagnostics[4].observed.value.pilotRunId = '00000000-0000-0000-0000-000000000037'; },
    value => { value.manifest.diagnostics[4].observed.value.targetChunkSeen = false; },
    value => { value.manifest.diagnostics[4].observed.value.finalClientDimension = 'minecraft:overworld'; },
    value => { value.manifest.diagnostics[4].observed.value.pilotActionAttempt = '00000000-0000-0000-0000-000000000037'; },
    value => { value.manifest.diagnostics.splice(1, 0, structuredClone(value.manifest.diagnostics[0])); }
  ]) {
    const value = envelope(); mutate(value); value.manifestSource = Buffer.from(`${JSON.stringify(value.manifest)}\n`);
    assert.throws(() => preflightF02cHotReceiptCarrier(value), /F0\.2C HOT carrier|lifecycle barrier/);
  }
});

function hash(value) { return createHash('sha256').update(value).digest('hex'); }
