import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import { assertF02cAftermathCarrier } from '../src/f02c-aftermath-carrier.mjs';
import { preflightF02cAftermathCarrier } from '../src/run-f02c-aftermath-carrier.mjs';

const selector = 'cause:development-settlement-assault-epoch-4-attacker-bioform-west-19';
const position = { x: -360, y: 64, z: -340 };
const declarationSource = await readFile(new URL('../scenarios/disposable-cold-bomber-aftermath-restart.json', import.meta.url));
const clearDeclaration = JSON.parse(declarationSource);
const sha256 = createHash('sha256').update(declarationSource).digest('hex');
const pending = (revision) => ({ kind: 'aftermath', id: selector, status: 'ok', aftermathId: 'aftermath:development-settlement-assault-bomber-4',
  cause: selector, epoch: 4, expectedOwner: 'structure:1-hall', expectedPart: 'FOUNDATION', expectedMaterial: 'HALL', provenance: 'captive-bomber-strike:assault:development-settlement-assault',
  eventAt: 400, revision, terminal: false, nextStatus: 'PENDING', cellStatus: 'PENDING', authorityRevision: -1, cursor: 0, observedAt: null, position });
const realized = { ...pending(45), terminal: true, nextStatus: 'NONE', cellStatus: 'REALIZED', authorityRevision: 17, cursor: 1, observedAt: 445 };
const lifecycle = ['server_run_ready', 'scenario_segment_complete', 'client_normally_disconnected', 'durable_server_save',
  'game_port_closed', 'recovery_server_ready', 'same_client_reconnected_state_cleared', 'terminal_assertion_complete'].map((barrier) => ({ barrier }));

function runnerEnvelope() {
  const declaration = structuredClone(clearDeclaration);
  const [pre, recovered, terminal] = declaration.assertions;
  const record = (assertion, actionStep, value) => ({ assertion: structuredClone(assertion), observed: { actionStep, value: structuredClone(value) } });
  return { clearDeclaration: declaration, clearManifest: {
    status: 'ok', recovery: { mode: 'graceful', splitAfterAction: 2, clientSession: { reusedJvm: true } },
    actions: declaration.actions.map((action) => ({ action: structuredClone(action) })), lifecycle: structuredClone(lifecycle),
    // These duplicate polling replies intentionally disagree in revision.  They are trace only.
    diagnostics: [record(pre, 1, pending(41)), record(recovered, 3, pending(43)), record(terminal, 6, realized),
      { actionStep: 1, value: pending(1) }, { actionStep: 3, value: pending(2) }, { actionStep: 6, value: { ...realized, revision: 99 } }]
  } };
}
function wrapperEnvelope() {
  const value = runnerEnvelope();
  return { scenario: 'disposable-cold-bomber-aftermath-restart.json', declaration: value.clearDeclaration, declarationSha256: sha256, manifest: value.clearManifest };
}

test('F0.2C carrier consumes three assertion-bound records from the persistent final manifest, ignoring raw polling duplicates', () => {
  const value = runnerEnvelope();
  assert.deepEqual(assertF02cAftermathCarrier(value), { aftermathId: 'aftermath:development-settlement-assault-bomber-4', cause: selector,
    position, owner: 'structure:1-hall', semanticPart: 'FOUNDATION', revision: 45 });
});

test('F0.2C carrier rejects every one-defect false-positive mutation', () => {
  for (const mutate of [
    value => { value.clearManifest.diagnostics.splice(1, 0, structuredClone(value.clearManifest.diagnostics[1])); }, // duplicate bound receipt
    value => { value.clearManifest.diagnostics.splice(1, 1); }, // missing recovered phase despite raw polling trace
    value => { value.clearManifest.diagnostics[1].observed.actionStep = 2; }, // action step mismatch
    value => { value.clearManifest.diagnostics[1].observed.value.aftermathId = 'aftermath:replacement'; },
    value => { value.clearManifest.diagnostics[1].observed.value.expectedOwner = 'structure:other-hall'; },
    value => { value.clearManifest.diagnostics[1].observed.value.expectedPart = 'ROOF'; },
    value => { value.clearManifest.diagnostics[2].observed.value.authorityRevision = -1; },
    value => { value.clearManifest.diagnostics[2].observed.value.revision = 43; },
    value => { [value.clearManifest.lifecycle[4], value.clearManifest.lifecycle[5]] = [value.clearManifest.lifecycle[5], value.clearManifest.lifecycle[4]]; },
    value => { Object.assign(value.clearManifest.diagnostics[2].observed.value, pending(45)); }, // replayed terminal
    value => { value.clearManifest.actions[4].action.position = { x: -359, y: 64, z: -340 }; },
    value => { value.clearDeclaration.setup.push({ type: 'visit' }); },
    value => { value.clearDeclaration.actions[2] = { type: 'visit' }; },
    value => { value.clearManifest.diagnostics.splice(0, 1); } // raw duplicate cannot replace assertion record
  ]) {
    const value = runnerEnvelope(); mutate(value);
    assert.throws(() => assertF02cAftermathCarrier(value), /F0\.2C carrier/);
  }
});

test('F0.2C wrapper preflight accepts only its runner-shaped envelope before Minecraft', () => {
  const value = wrapperEnvelope();
  assert.equal(preflightF02cAftermathCarrier(value).revision, 45);
  delete value.manifest.recovery.clientSession;
  assert.throws(() => preflightF02cAftermathCarrier(value), /F0\.2C carrier/);
});
