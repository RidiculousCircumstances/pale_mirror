import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import { assertF02cAftermathCarrier } from '../src/f02c-aftermath-carrier.mjs';
import { resolveIsolatedScenarioOuterAttempt } from '../src/isolated-scenario-attempt.mjs';
import { buildF02cAftermathCarrierReceipt, preflightF02cAftermathCarrier, runIsolatedScenario } from '../src/run-f02c-aftermath-carrier.mjs';

const selector = 'cause:development-settlement-assault-epoch-4-attacker-bioform-west-19';
const position = { x: -360, y: 64, z: -340 };
const outerAttempt = '00000000-0000-0000-0000-000000000011';
const clientRunId = '00000000-0000-0000-0000-000000000012';
const nonce = '00000000-0000-0000-0000-000000000013';
const sessionId = '00000000-0000-0000-0000-000000000014';
const declarationSource = await readFile(new URL('../scenarios/disposable-cold-bomber-aftermath-restart.json', import.meta.url));
const declarationSha256 = hash(declarationSource);
const clearDeclaration = JSON.parse(declarationSource);
const build = { sourceCommit: '1c8bd3df6cb075f48341c780b9e900d7637905b5', sourceDirty: false, profile: 'disposable_lite' };
const pending = (revision) => ({ kind: 'aftermath', id: selector, status: 'ok', aftermathId: 'aftermath:development-settlement-assault-bomber-4',
  cause: selector, epoch: 4, expectedOwner: 'structure:1-hall', expectedPart: 'FOUNDATION', expectedMaterial: 'HALL', provenance: 'captive-bomber-strike:assault:development-settlement-assault',
  eventAt: 400, revision, terminal: false, nextStatus: 'PENDING', cellStatus: 'PENDING', authorityRevision: -1, cursor: 0, observedAt: null, position });
const realized = { ...pending(45), terminal: true, nextStatus: 'NONE', cellStatus: 'REALIZED', authorityRevision: 17, cursor: 1, observedAt: 445 };

function runnerEnvelope() {
  const declaration = structuredClone(clearDeclaration);
  const [pre, recovered, terminal] = declaration.assertions;
  const record = (assertion, actionStep, value) => ({ assertion: structuredClone(assertion), observed: { actionStep, value: structuredClone(value) } });
  const identity = { schema: 1, buildIdentitySha256: hash(JSON.stringify(build)), workerId: 'local-standalone', runId: outerAttempt,
    scenarioId: declaration.id, segmentId: 'native-scenario', nonce, sessionId };
  const barriers = [
    ['server_run_ready', { serverRunId: 'server-before' }], ['prepared_client_ready', { clientPid: 101, segment: 'before_restart' }],
    ['client_connected_fixture_ready', { clientPid: 101, segment: 'before_restart' }], ['action_checkpoint_acknowledged', { actionStep: 1, segment: 'before_restart' }],
    ['scenario_segment_complete', { segment: 'before_restart' }], ['client_normally_disconnected', { clientPid: 101, segment: 'before_restart' }],
    ['durable_server_save', { serverRunId: 'server-before' }], ['game_port_closed', { port: 25575 }], ['recovery_server_ready', { serverRunId: 'server-after' }],
    ['same_client_reconnected_state_cleared', { clientPid: 101 }], ['action_checkpoint_acknowledged', { actionStep: 6, segment: 'after_restart' }],
    ['scenario_segment_complete', { segment: 'after_restart' }], ['terminal_assertion_complete', { assertionCount: 3 }]
  ];
  const lifecycle = barriers.map(([barrier, detail], index) => ({ schema: 1, sequence: index + 1, barrier, identity: structuredClone(identity), detail }));
  return { declaration, manifest: {
    schema: 2, status: 'ok', scenarioId: declaration.id, scenarioSha256: declarationSha256, scenarioDeclarationSha256: declarationSha256,
    runId: clientRunId, build: structuredClone(build), recovery: { mode: 'graceful', splitAfterAction: 2, clientSession: { runId: outerAttempt, reusedJvm: true } },
    actions: declaration.actions.map((action, index) => ({ correlation: `scenario:${clientRunId}:${index + 1}`, action: structuredClone(action) })), lifecycle,
    clientSegments: [{ segment: 'persistent_restart', manifest: 'clear.manifest.json', runId: clientRunId, timing: {}, reusedJvm: true }],
    diagnostics: [record(pre, 1, pending(41)), record(recovered, 3, pending(43)), record(terminal, 6, realized),
      { actionStep: 1, value: pending(1) }, { actionStep: 3, value: pending(2) }, { actionStep: 6, value: { ...realized, revision: 99 } }]
  } };
}

function envelope() {
  const value = runnerEnvelope();
  return { scenario: 'disposable-cold-bomber-aftermath-restart.json', declarationSource, declarationSha256,
    manifestSource: Buffer.from(`${JSON.stringify(value.manifest)}\n`), outerAttempt };
}

test('F0.2C carrier consumes three assertion-bound records from the persistent final manifest, ignoring raw polling duplicates', () => {
  const value = runnerEnvelope();
  assert.deepEqual(assertF02cAftermathCarrier({ clearDeclaration: value.declaration, clearManifest: value.manifest }), {
    aftermathId: 'aftermath:development-settlement-assault-bomber-4', cause: selector, position, owner: 'structure:1-hall', semanticPart: 'FOUNDATION', revision: 45
  });
});

test('F0.2C identity preflight binds production declaration bytes, distinct run roles, lifecycle and final-manifest digest', () => {
  const value = envelope(); const receipt = preflightF02cAftermathCarrier(value);
  assert.equal(receipt.declarationSha256, declarationSha256);
  assert.equal(receipt.finalManifestSha256, hash(value.manifestSource));
  assert.deepEqual(receipt.identity, { outerAttempt, clientRunId, lifecycle: { runId: outerAttempt, sessionId, nonce } });
  assert.equal(receipt.terminal.revision, 45);
});

test('F0.2C wrapper crosses the production child environment seam and constructs its terminal receipt', async () => {
  const value = envelope();
  const output = await runIsolatedScenario({ scenarioPath: new URL('../scenarios/disposable-cold-bomber-aftermath-restart.json', import.meta.url).pathname,
    manifestPath: 'unused-admission-manifest.json', root: new URL('../', import.meta.url).pathname, outerAttempt, attemptAdmissionOnly: true });
  const admission = JSON.parse(output.trim());
  assert.deepEqual(admission, { status: 'admitted', outerAttempt, recoveryClientSession: { runId: outerAttempt, reusedJvm: true } });
  const receipt = buildF02cAftermathCarrierReceipt(preflightF02cAftermathCarrier(value));
  assert.equal(receipt.finalManifest.sha256, hash(value.manifestSource));
  assert.equal(receipt.identity.precommittedOuterAttempt, admission.recoveryClientSession.runId);
  assert.deepEqual(receipt.identity, { precommittedOuterAttempt: outerAttempt, clientRunner: { runId: clientRunId },
    lifecycle: { runId: outerAttempt, sessionId, nonce } });
});

test('F0.2C identity preflight rejects each production-shaped one-defect mutation', () => {
  for (const mutate of [
    value => { value.declarationSha256 = 'f'.repeat(64); },
    value => { manifest(value).scenarioDeclarationSha256 = 'f'.repeat(64); },
    value => { manifest(value).scenarioSha256 = 'e'.repeat(64); },
    value => { manifest(value).scenarioId = 'other_scenario'; },
    value => { manifest(value).recovery.clientSession.runId = clientRunId; },
    value => { manifest(value).runId = outerAttempt; },
    value => { manifest(value).clientSegments[0].runId = outerAttempt; },
    value => { manifest(value).actions[3].correlation = `scenario:${clientRunId}:3`; },
    value => { manifest(value).lifecycle[4].identity.runId = clientRunId; },
    value => { manifest(value).lifecycle[5].sequence = 7; },
    value => { manifest(value).lifecycle[0].identity.buildIdentitySha256 = 'd'.repeat(64); },
    value => { manifest(value).diagnostics[1].observed.actionStep = 2; },
    value => { manifest(value).diagnostics[1].observed.value.kind = 'summary'; },
    value => { manifest(value).diagnostics[1].observed.value.id = 'cause:foreign'; },
    value => { manifest(value).diagnostics.splice(1, 0, structuredClone(manifest(value).diagnostics[1])); },
    value => { value.outerAttempt = '00000000-0000-0000-0000-000000000021'; }
  ]) {
    const value = envelope(); mutate(value);
    if (value.manifest !== undefined) value.manifestSource = Buffer.from(`${JSON.stringify(value.manifest)}\n`);
    assert.throws(() => preflightF02cAftermathCarrier(value), /F0\.2C carrier|lifecycle barrier/);
  }
});

test('isolated supervisor accepts only its narrow canonical outer-attempt input', () => {
  assert.equal(resolveIsolatedScenarioOuterAttempt(outerAttempt), outerAttempt);
  for (const malformed of ['', 'not-a-uuid', '0000000A-0000-0000-0000-000000000012']) {
    assert.throws(() => resolveIsolatedScenarioOuterAttempt(malformed), /FRONTIER_V3_ISOLATED_OUTER_ATTEMPT/);
  }
});

function manifest(value) {
  value.manifest ??= JSON.parse(value.manifestSource);
  return value.manifest;
}
function hash(value) { return createHash('sha256').update(value).digest('hex'); }
