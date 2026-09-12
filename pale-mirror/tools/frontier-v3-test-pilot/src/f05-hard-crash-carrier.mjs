import { isDeepStrictEqual } from 'node:util';

const PLAYER = 'bf39347d-cb86-3221-b6b7-7b89a1dcb4cf';
const SOURCE = 'custody:container-1-depot';
const CANONICAL_HANDOFF_OWNER = 'settlement:1';
const PLAYER_ACCOUNT = `custody:player-${PLAYER}`;
const WINDOWS = Object.freeze([
  'lease_recorded_before_physical_materialization',
  'physical_effect_visible_before_typed_observation',
  'typed_observation_durable_before_next_process_checkpoint',
  'hot_checkpoint_durable_before_drain_release',
  'release_durable_before_cold_resumption'
]);
const RESOURCE_WINDOWS = Object.freeze([
  ['lease_recorded_before_physical_materialization', 'job:site-harvest-4-wheat-field-1', 'frontier.resource_site_harvest_scene_lease_prepared'],
  ['hot_checkpoint_durable_before_drain_release', 'site:4-wheat-field', 'frontier.resource_site_harvest_hot_traversal_advanced'],
  ['release_durable_before_cold_resumption', 'job:site-harvest-4-wheat-field-1', 'frontier.scene_lease_released_v2']
]);

/**
 * F0.5's native crash receipt joins the three admitted resource-site boundaries to the two
 * actual playerdata/canonical arrival orders.  Crop writes remain deliberately inadmissible
 * in the F0.V reference; fungible player handoff is therefore the physical-capable family
 * that truthfully owns the effect and durable-observation boundaries. The oracle consumes only
 * immutable runner facts; it cannot start a server, save a player, or alter recovery decisions.
 */
export function assertF05HardCrashCarrier({ windows, physicalFirst, canonicalFirst }) {
  const windowFacts = crashWindows(windows);
  // The restart slicer rebases after-restart assertions into their resumed segment; native
  // manifests therefore carry these as 3/4 and 1/2, not their declaration-wide labels.
  const first = playerOrder(physicalFirst, 'physical_effect_visible_before_typed_observation', SOURCE, 32,
    'disposable_f03_fungible_player_abrupt', { sourceAction: 3, playerAction: 4 });
  const second = playerOrder(canonicalFirst, 'typed_observation_durable_before_next_process_checkpoint', CANONICAL_HANDOFF_OWNER, 0,
    'disposable_f05_fenced_player_canonical_first_abrupt', { sourceAction: 1, playerAction: 2 });
  const facts = [...windowFacts, first, second].sort((left, right) => left.boundary.localeCompare(right.boundary));
  if (!isDeepStrictEqual(facts.map(value => value.boundary), [...WINDOWS].sort())) {
    throw new Error('F0.5 hard-crash receipt has missing, duplicate, or substituted semantic windows');
  }
  if (!sameArtifact(facts)) throw new Error('F0.5 hard-crash evidence mixes artifact identities');
  return Object.freeze({ windows: facts.map(value => value.boundary), physicalFirst: first, canonicalFirst: second,
    artifactSha256: first.artifactSha256 });
}

/** Selects only the already-admitted resource-site windows from a retained matrix report. */
export function selectF05ResourceWindows(report) {
  const runs = report?.variants?.find((variant) => variant?.variant === 'abrupt_restart')?.runs;
  if (!Array.isArray(runs)) throw new Error('F0.5 retained resource-window report is incomplete or foreign');
  return Object.freeze(RESOURCE_WINDOWS.map(([boundary]) => {
    const matching = runs.filter((run) => run?.lane === boundary && run.status === 'passed' && typeof run.manifest === 'string');
    if (matching.length !== 1) throw new Error('F0.5 retained resource-window report is incomplete or foreign');
    return Object.freeze(structuredClone(matching[0]));
  }));
}

function crashWindows(values) {
  if (!Array.isArray(values) || values.length !== RESOURCE_WINDOWS.length) {
    throw new Error('F0.5 hard-crash receipt lacks its admitted resource boundaries');
  }
  const facts = values.map((value, index) => {
    const manifest = value?.manifest; const crash = manifest?.recovery?.crash;
    const [boundary, owner, payloadType] = RESOURCE_WINDOWS[index];
    if (value?.status !== 'passed' || manifest?.status !== 'ok' || manifest.recovery?.mode !== 'abrupt'
        || crash?.boundary !== boundary || crash.owner !== owner || crash.payloadType !== payloadType
        || !Number.isSafeInteger(crash.revision) || crash.revision < 0 || !completedCrashProtocol(manifest)) {
      throw new Error('F0.5 hard-crash semantic window is incomplete or foreign');
    }
    return Object.freeze({ boundary: crash.boundary, owner: crash.owner, payloadType: crash.payloadType, revision: crash.revision,
      artifactSha256: artifactSha(manifest) });
  });
  return facts;
}

function playerOrder(manifest, boundary, owner, expectedSave, scenarioId, actions) {
  const crash = manifest?.recovery?.crash; const save = crash?.playerSave;
  if (manifest?.status !== 'ok' || manifest.scenarioId !== scenarioId || manifest.recovery?.mode !== 'abrupt'
      || crash?.boundary !== boundary || crash.owner !== owner || crash.payloadType !== 'frontier.fungible_resource_handoff_observed'
      || !completedCrashProtocol(manifest) || save?.player !== PLAYER || save.item !== 'minecraft:wheat'
      || save.expectedCount !== expectedSave || save.itemCount !== expectedSave || typeof save.exists !== 'boolean') {
    throw new Error('F0.5 player-save arrival receipt is incomplete or foreign');
  }
  const source = resource(manifest, actions.sourceAction, SOURCE); const player = resource(manifest, actions.playerAction, PLAYER_ACCOUNT);
  if (source.quantity !== 32 || player.quantity !== 32 || source.quantity + player.quantity !== 64) {
    throw new Error('F0.5 player-save arrival receipt is not conserved after recovery');
  }
  return Object.freeze({ boundary, playerSaveCount: save.itemCount, sourceQuantity: source.quantity, playerQuantity: player.quantity,
    artifactSha256: artifactSha(manifest) });
}

function completedCrashProtocol(manifest) {
  const lifecycle = manifest?.lifecycle;
  return Array.isArray(lifecycle) && ['expected_loss_armed', 'crash_controller_fired', 'owned_server_exit', 'client_expected_loss', 'game_port_closed']
    .every(barrier => lifecycle.some(entry => entry?.barrier === barrier));
}

function resource(manifest, actionStep, id) {
  const value = manifest?.diagnostics?.find(entry => entry?.assertion?.after === actionStep && entry.assertion.view === 'resource'
    && entry.assertion.id === id)?.observed?.value;
  if (value?.status !== 'ok' || value.id !== id || !Number.isSafeInteger(value.quantity)) {
    throw new Error(`F0.5 player-save receipt lacks action-bound canonical custody ${id}@${actionStep}`);
  }
  return value;
}

function artifactSha(manifest) {
  const value = manifest?.build?.preparedArtifact?.sha256;
  if (typeof value !== 'string' || !/^[a-f0-9]{64}$/.test(value)) throw new Error('F0.5 hard-crash manifest lacks a packaged artifact identity');
  return value;
}

function sameArtifact(values) { return values.every(value => value.artifactSha256 === values[0].artifactSha256); }
