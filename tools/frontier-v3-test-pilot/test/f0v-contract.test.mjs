import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import { composeF0vScenarioMatrix, REQUIRED_VARIANTS, validateF0vContract } from '../src/f0v-contract.mjs';
import { validateScenario } from '../src/scenario.mjs';

test('checked-in F0.V contract composes every required HOT/COLD/restart variant without a family branch', async () => {
  const source = await readFile(new URL('../contracts/resource-site-harvest-f0v.json', import.meta.url), 'utf8');
  const contract = validateF0vContract(JSON.parse(source));
  assert.deepEqual(Object.keys(contract.variants).sort(), [...REQUIRED_VARIANTS].sort());
  const matrix = composeF0vScenarioMatrix(contract);
  assert.deepEqual(matrix.map((variant) => variant.variant), REQUIRED_VARIANTS);
  for (const variant of matrix) validateScenario(variant.scenario);
  const differential = matrix.find((variant) => variant.variant === 'neutral_observer_differential');
  assert.deepEqual(differential.differential.map((entry) => entry.lane), ['cold', 'hot_cold']);
  assert.notEqual(differential.differential[0].scenario.id, differential.differential[1].scenario.id);
});

test('contract validation fails closed when a reviewer removes a causal variant or restart split', () => {
  const base = validContract('frontier.resource-site-harvest', 'ResourceSiteHarvestJob');
  assert.throws(() => validateF0vContract({ ...base, variants: {} }), /missing or extra/);
  assert.throws(() => validateF0vContract({ ...base, variants: { ...base.variants, graceful_restart: {
    ...base.variants.graceful_restart, restartAfterAction: 2 } } }), /restart F0.V/);
  assert.throws(() => validateF0vContract({ ...base, variants: { ...base.variants, never_loaded: {
    ...base.variants.never_loaded, assertions: [{ after: 2, view: 'summary', id: '', expect: { status: 'ok' } }] } } }), /invalid F0.V variant/);
  assert.throws(() => validateF0vContract({ ...base, variants: { ...base.variants, abrupt_restart: {
    ...base.variants.abrupt_restart, crashWindows: base.variants.abrupt_restart.crashWindows.slice(1) } } }), /every semantic crash window/);
  assert.throws(() => validateF0vContract({ ...base, variants: { ...base.variants, never_loaded: {
    ...base.variants.never_loaded, coldProgress: undefined } } }), /COLD cursor progress/);
  assert.throws(() => validateF0vContract({ ...base, variants: { ...base.variants, neutral_observer_differential: {
    ...base.variants.neutral_observer_differential, differential: base.variants.neutral_observer_differential.differential.map((projection) =>
      projection.invariant === 'schedule' ? { ...projection, paths: ['schedule'] } : projection) } } }), /complete schedule entries/);
  assert.throws(() => validateF0vContract({ ...base, variants: { ...base.variants, arrival_checkpoint_one: {
    ...base.variants.arrival_checkpoint_one, terminalProjections: [] } } }), /terminal projections/);
  assert.throws(() => validateF0vContract({ ...base, variants: { ...base.variants, arrival_checkpoint_one: {
    ...base.variants.arrival_checkpoint_one, terminalProjections: [{ invariant: 'rendered_label', view: 'summary', id: '', paths: ['identity'] }] } } }),
  /semantic projections/);
  assert.throws(() => validateF0vContract({ ...base, variants: { ...base.variants, unload_return: {
    ...base.variants.unload_return, actions: [{ type: 'wait', ms: 12_000 }, ...base.variants.unload_return.actions.slice(1)] } } }),
  /fixed wait lacks a named gameplay-time assertion/);
});

test('the generic matrix accepts a different existing process vocabulary without a concrete-family whitelist', () => {
  const transit = validContract('frontier.population-migration', 'ResidentMigrationJourney');
  const matrix = composeF0vScenarioMatrix(transit);
  assert.equal(matrix[0].family, 'frontier.population-migration');
  assert.equal(matrix[0].canonicalOwner, 'ResidentMigrationJourney');
});

test('a terminal assertion binds after each composed HOT/COLD lane rather than the common action prefix', () => {
  const contract = validContract('frontier.resource-site-harvest', 'ResourceSiteHarvestJob');
  const differential = contract.variants.neutral_observer_differential;
  differential.assertions = differential.assertions.map((assertion) => ({ ...assertion, after: 'terminal' }));
  const entry = composeF0vScenarioMatrix(contract).find((value) => value.variant === 'neutral_observer_differential');
  const cold = entry.differential.find((value) => value.lane === 'cold').scenario;
  const hotCold = entry.differential.find((value) => value.lane === 'hot_cold').scenario;
  assert.equal(cold.assertions[0].after, cold.actions.length);
  assert.equal(hotCold.assertions[0].after, hotCold.actions.length);
  assert.notEqual(cold.assertions[0].after, hotCold.assertions[0].after,
    'the HOT/COLD lane must not reuse the all-COLD terminal action index');
});

function validContract(family, canonicalOwner) {
  const drivers = {
    never_loaded: 'COLD', arrival_checkpoint_one: 'HOT', arrival_checkpoint_two: 'HOT', unload_return: 'HOT_COLD_HOT',
    player_intervention: 'HOT', graceful_restart: 'SNAPSHOT_WAL', abrupt_restart: 'CRASH_WAL', neutral_observer_differential: 'COLD_VS_HOT_COLD'
  };
  return {
    schema: 3, family, canonicalOwner,
    declaration: { identities: ['actor:test'], cursorVocabulary: ['cursor'], legalCheckpoint: {
      view: 'summary', id: '', paths: ['cursor.index', 'cursor.retainedBody', 'cursor.actorBody'] },
      terminalInvariants: ['identity', 'claims', 'conservation', 'schedule', 'result'] },
    scenario: { idPrefix: 'f0v_test', isolation: { mode: 'disposable_lite', seed: 1 },
      server: { host: '127.0.0.1', port: 25575 }, pilot: { username: 'Pilot' }, setup: [], actions: [] },
    variants: Object.fromEntries(REQUIRED_VARIANTS.map((name) => [name, {
      driver: drivers[name], evidence: ['actor', 'cursor'],
      actions: [{ type: 'inspect', view: 'summary', id: '' }, { type: 'inspect', view: 'summary', id: '' }],
      ...(drivers[name] === 'SNAPSHOT_WAL' || drivers[name] === 'CRASH_WAL' ? { restartAfterAction: 1 } : {}),
      ...(drivers[name] === 'CRASH_WAL' ? { crashWindows: crashWindows() } : {}),
      ...((name === 'arrival_checkpoint_one' || name === 'arrival_checkpoint_two') ? { arrivalCheckpoint: {
        view: 'summary', id: '', path: 'cursor' } } : {}),
      assertions: [
        ...(name === 'never_loaded' ? [{ after: 1, view: 'summary', id: '', expect: { status: 'ok', claims: { lease: null } } }] : []),
        { after: 2, view: 'summary', id: '', expect: {
          status: 'ok', identity: 'actor:test', claims: { owner: 'claim:test', lease: null }, conservation: 64, schedule: 'schedule:test', result: 'RUNNING' } }
      ],
      terminalProjections: terminalProjections(),
      ...(name === 'never_loaded' ? { coldProgress: { beforeAction: 1, after: 'terminal', view: 'summary', id: '',
        cursorPath: 'cursor.index', retainedBodyPath: 'cursor.retainedBody', actorBodyPath: 'cursor.actorBody', leasePath: 'claims.lease', minimumAdvance: 1 } } : {}),
      ...(drivers[name] === 'COLD_VS_HOT_COLD' ? { differential: [
        { invariant: 'identity', view: 'summary', id: '', paths: ['identity'] },
        { invariant: 'claims', view: 'summary', id: '', paths: ['claims'] },
        { invariant: 'conservation', view: 'summary', id: '', paths: ['conservation'] },
        { invariant: 'schedule', view: 'summary', id: '', paths: ['schedule', 'schedule.entries'] },
        { invariant: 'result', view: 'summary', id: '', paths: ['result', 'cursor.index', 'cursor.retainedBody', 'cursor.actorBody'] }
      ], lanes: { cold: { setup: [], actions: [] }, hot_cold: { setup: [], actions: [{ type: 'inspect', view: 'summary', id: '' }] } } } : {})
    }]))
  };
}

function terminalProjections() {
  return [
    { invariant: 'identity', view: 'summary', id: '', paths: ['identity'] },
    { invariant: 'claims', view: 'summary', id: '', paths: ['claims'] },
    { invariant: 'conservation', view: 'summary', id: '', paths: ['conservation'] },
    { invariant: 'schedule', view: 'summary', id: '', paths: ['schedule'] },
    { invariant: 'result', view: 'summary', id: '', paths: ['result'] }
  ];
}

function crashWindows() {
  return [
    ['lease_recorded_before_physical_materialization', 'frontier.resource_site_harvest_scene_lease_prepared'],
    ['physical_effect_visible_before_typed_observation', 'frontier.resource_site_harvest_progressed'],
    ['typed_observation_durable_before_next_process_checkpoint', 'frontier.resource_site_harvest_progressed'],
    ['hot_checkpoint_durable_before_drain_release', 'frontier.resource_site_harvest_hot_traversal_advanced'],
    ['release_durable_before_cold_resumption', 'frontier.scene_lease_released_v2']
  ].map(([boundary, payloadType]) => ({ boundary, phase: 'before_restart', owner: 'job:harvest-1', payloadType, expectedRevision: 1 }));
}
