import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import { assertF05HardCrashCarrier, selectF05ResourceWindows } from '../src/f05-hard-crash-carrier.mjs';
import { restartSegments, validateScenario } from '../src/scenario.mjs';

const artifact = 'a'.repeat(64);
const windows = [
  'lease_recorded_before_physical_materialization',
  'hot_checkpoint_durable_before_drain_release',
  'release_durable_before_cold_resumption'
].map((boundary) => ({ status: 'passed', manifest: crash(resourceCrash(boundary)) }));

test('F0.5 hard-crash carrier binds every semantic window and both player-save arrival orders', () => {
  const result = assertF05HardCrashCarrier({ windows,
    physicalFirst: player('disposable_f03_fungible_player_abrupt', 'physical_effect_visible_before_typed_observation', 32),
    canonicalFirst: player('disposable_f05_fenced_player_canonical_first_abrupt', 'typed_observation_durable_before_next_process_checkpoint', 0) });
  assert.equal(result.windows.length, 5);
  assert.equal(result.physicalFirst.playerSaveCount, 32);
  assert.equal(result.canonicalFirst.playerSaveCount, 0);
  assert.deepEqual(result.canonicalFirst.authenticatedPlayer, {
    itemKind: 'minecraft:wheat', slot: 9, count: 32, canonicalQuantity: 32, bindingEpoch: 1
  });
  assert.equal(result.candidateArtifactSha256, artifact);
  assert.deepEqual(result.retainedArtifactSha256s, []);
});

test('retained resource evidence admits only the three still-admitted resource-site boundaries', () => {
  const runs = windows.map((value) => ({ lane: value.manifest.recovery.crash.boundary, status: value.status,
    manifest: `build/${value.manifest.recovery.crash.boundary}.json` }));
  runs.push({ lane: 'physical_effect_visible_before_typed_observation', status: 'failed', manifest: 'build/ignored.json' },
    { lane: 'typed_observation_durable_before_next_process_checkpoint', status: 'failed', manifest: 'build/ignored.json' });
  const report = { variants: [{ variant: 'abrupt_restart', runs }] };
  assert.deepEqual(selectF05ResourceWindows(report).map((run) => run.lane), windows.map((value) => value.manifest.recovery.crash.boundary));
  report.variants[0].runs[0].status = 'failed';
  assert.throws(() => selectF05ResourceWindows(report), /incomplete or foreign/);
});

test('F0.5 hard-crash carrier rejects a substituted window, stale player save, or mixed package', () => {
  const valid = { windows, physicalFirst: player('disposable_f03_fungible_player_abrupt', 'physical_effect_visible_before_typed_observation', 32),
    canonicalFirst: player('disposable_f05_fenced_player_canonical_first_abrupt', 'typed_observation_durable_before_next_process_checkpoint', 0) };
  const missing = structuredClone(valid); missing.windows[0].manifest.recovery.crash.boundary = missing.windows[1].manifest.recovery.crash.boundary;
  assert.throws(() => assertF05HardCrashCarrier(missing), /incomplete or foreign/);
  const saved = structuredClone(valid); saved.canonicalFirst.recovery.crash.playerSave.itemCount = 32;
  assert.throws(() => assertF05HardCrashCarrier(saved), /arrival receipt/);
  const wrongAction = structuredClone(valid); wrongAction.physicalFirst.diagnostics = [
    resource(5, 'custody:container-1-depot'), resource(6, 'custody:player-bf39347d-cb86-3221-b6b7-7b89a1dcb4cf')
  ];
  assert.throws(() => assertF05HardCrashCarrier(wrongAction), /action-bound canonical custody/);
  const mixed = structuredClone(valid); mixed.physicalFirst.build.preparedArtifact.sha256 = 'b'.repeat(64);
  assert.throws(() => assertF05HardCrashCarrier(mixed), /mixes artifact/);
  const retained = assertF05HardCrashCarrier({ ...mixed, retainedArtifactSha256s: ['b'.repeat(64)] });
  assert.deepEqual(retained.retainedArtifactSha256s, ['b'.repeat(64)], 'only an explicit retained identity may reuse unaffected native evidence');
  const emptySlot = structuredClone(valid); emptySlot.canonicalFirst.diagnostics.at(-1).observed.value.actual.count = 0;
  assert.throws(() => assertF05HardCrashCarrier(emptySlot), /authenticated reconnected player custody/);
  const unresolvedCleanup = structuredClone(valid); delete unresolvedCleanup.canonicalFirst.terminalCleanup;
  assert.throws(() => assertF05HardCrashCarrier(unresolvedCleanup), /post-semantic replacement-server disposal/);
});

test('native launcher accepts only its checkout or exact task-private sibling as a process namespace', async () => {
  const launcher = await readFile(new URL('../src/run-f05-hard-crash-carrier.mjs', import.meta.url), 'utf8');
  assert.ok(launcher.includes("const taskPrivateRoot = `${resolve(project, '..')}-tmp/`;"));
  assert.match(launcher, /processRoot\.startsWith\(`\$\{project\}\/`\).*processRoot\.startsWith\(taskPrivateRoot\)/s);
  assert.match(launcher, /--reuse-resource-windows=/);
  assert.match(launcher, /--reuse-physical-first=/);
  assert.match(launcher, /--reuse-canonical-first=/);
  assert.match(launcher, /candidateArtifactSha256/);
  assert.match(launcher, /retainedArtifactSha256s/);
  assert.match(launcher, /prepared === undefined \? \{\} : \{ FRONTIER_V3_PREPARED_BUILD_IDENTITY: prepared \}/);
});

test('canonical-first player order retains ordinary demand until its post-WAL observation boundary', async () => {
  const declaration = JSON.parse(await readFile(new URL('../scenarios/disposable-f05-fenced-player-canonical-first-abrupt.json', import.meta.url), 'utf8'));
  assert.doesNotThrow(() => validateScenario(declaration));
  assert.equal(declaration.restart.afterAction, 5);
  assert.equal(declaration.crash.owner, 'settlement:1',
    'the durable handoff is fenced by its production settlement event subject, not its source-account diagnostic id');
  assert.deepEqual(declaration.actions.slice(3, 5).map((action) => action.type),
    ['split_move_from_container', 'wait_until_diagnostic']);
  assert.deepEqual(declaration.actions[4].expect, { status: 'ok', quantity: 32,
    custody: { kind: 'CONTAINER', container: 'container:1-depot' } });
  const segments = restartSegments(declaration);
  assert.equal(segments.before.actions.length, 5);
  assert.deepEqual(segments.after.assertions.map((value) => value.after), [1, 2, 3]);
  assert.deepEqual(segments.after.assertions.map((value) => value.id),
    ['custody:container-1-depot', 'custody:player-bf39347d-cb86-3221-b6b7-7b89a1dcb4cf', 'custody:player-bf39347d-cb86-3221-b6b7-7b89a1dcb4cf']);
  assert.equal(segments.after.assertions.at(-1).view, 'player_resource',
    'the recovery half reads the server-authenticated player slot, not the canonical ledger twice');
});

function crash({ boundary, owner, payloadType }) {
  return { status: 'ok', recovery: { mode: 'abrupt', crash: { boundary, owner, payloadType, revision: 17 } },
    lifecycle: lifecycle(), build: { preparedArtifact: { sha256: artifact } } };
}

function resourceCrash(boundary) {
  return ({
    lease_recorded_before_physical_materialization: { boundary, owner: 'job:site-harvest-4-wheat-field-1', payloadType: 'frontier.resource_site_harvest_scene_lease_prepared' },
    hot_checkpoint_durable_before_drain_release: { boundary, owner: 'site:4-wheat-field', payloadType: 'frontier.resource_site_harvest_hot_traversal_advanced' },
    release_durable_before_cold_resumption: { boundary, owner: 'job:site-harvest-4-wheat-field-1', payloadType: 'frontier.scene_lease_released_v2' }
  })[boundary];
}

function player(scenarioId, boundary, expectedCount) {
  const owner = scenarioId === 'disposable_f03_fungible_player_abrupt' ? 'custody:container-1-depot' : 'settlement:1';
  const value = crash({ boundary, owner, payloadType: 'frontier.fungible_resource_handoff_observed' });
  value.scenarioId = scenarioId;
  value.recovery.crash.playerSave = { player: 'bf39347d-cb86-3221-b6b7-7b89a1dcb4cf', item: 'minecraft:wheat', expectedCount, itemCount: expectedCount, exists: expectedCount > 0, sha256: expectedCount > 0 ? 'c'.repeat(64) : null };
  const [sourceAction, playerAction] = scenarioId === 'disposable_f03_fungible_player_abrupt' ? [3, 4] : [1, 2];
  value.diagnostics = [resource(sourceAction, 'custody:container-1-depot'), resource(playerAction, 'custody:player-bf39347d-cb86-3221-b6b7-7b89a1dcb4cf')];
  if (scenarioId === 'disposable_f05_fenced_player_canonical_first_abrupt') value.diagnostics.push(playerResource(3));
  if (scenarioId === 'disposable_f05_fenced_player_canonical_first_abrupt') {
    value.terminalCleanup = { mode: 'exact_owned_post_semantic_disposal', serverPid: 4321, port: 25579, portClosed: true };
  }
  return value;
}

function resource(after, id) { return { assertion: { after, view: 'resource', id }, observed: { value: { status: 'ok', id, quantity: 32 } } }; }
function playerResource(after) {
  const id = 'custody:player-bf39347d-cb86-3221-b6b7-7b89a1dcb4cf';
  return { assertion: { after, view: 'player_resource', id }, observed: { value: {
    status: 'ok', id, player: 'bf39347d-cb86-3221-b6b7-7b89a1dcb4cf', slot: 9, canonicalQuantity: 32,
    binding: { epoch: 1, itemKind: 'minecraft:wheat', quantity: 32 }, actual: { itemKind: 'minecraft:wheat', count: 32 }, matchesCanonical: true
  } } };
}
function lifecycle() { return ['expected_loss_armed', 'crash_controller_fired', 'owned_server_exit', 'client_expected_loss', 'game_port_closed'].map(barrier => ({ barrier })); }
