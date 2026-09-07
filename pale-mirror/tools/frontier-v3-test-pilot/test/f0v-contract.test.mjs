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
  const unloadReturn = matrix.find((variant) => variant.variant === 'unload_return').scenario;
  const hysteresis = unloadReturn.actions.findIndex((action) => action.type === 'wait' && action.gameplayElapsedPurpose === 'hot_cold_release_hysteresis');
  assert.equal(unloadReturn.actions[hysteresis + 1].type, 'inspect', 'return travel must resolve the current COLD cursor after hysteresis');
  assert.equal(unloadReturn.actions[hysteresis + 2].type, 'visit');
  const intervention = matrix.find((variant) => variant.variant === 'player_intervention').scenario;
  const approachCrop = intervention.actions.find((action) => action.type === 'visit' && action.position?.diagnostic?.field === 'lastCrop');
  assert.deepEqual(approachCrop, { type: 'visit', dimension: 'pale_mirror:frontier_graybox',
    position: { diagnostic: { view: 'site', id: 'site:4-wheat-field', field: 'lastCrop' } }, settleMs: 1000 });
  const observedConflict = intervention.actions.find((action) => action.type === 'wait_until_diagnostic' && action.view === 'site'
    && action.expect?.phase === 'CONFLICT');
  assert.deepEqual(observedConflict, { type: 'wait_until_diagnostic', view: 'site', id: 'site:4-wheat-field',
    expect: { status: 'ok', phase: 'CONFLICT' }, timeoutMs: 30_000 });
  assert.deepEqual(intervention.assertions[0], { after: intervention.actions.indexOf(observedConflict) + 1,
    view: 'site', id: 'site:4-wheat-field', expect: { status: 'ok', phase: 'CONFLICT' } },
  'the native receipt discriminator must bind the conflict observation to its read-only confirmation, not the preceding client break request');
  const abrupt = matrix.find((variant) => variant.variant === 'abrupt_restart').scenario;
  const checkpoint = abrupt.actions.find((action) => action.requireIncreaseAt === 'cursor.index');
  assert.deepEqual(checkpoint, { type: 'wait_until_diagnostic', view: 'process', id: 'job:site-harvest-4-wheat-field-1',
    expect: { status: 'ok', claims: { lease: { status: 'HOT', members: 1 } } }, requireIncreaseAt: 'cursor.index', timeoutMs: 90_000 },
  'the hot-checkpoint crash lane must wait for a new durable traversal before release travel');
  assert.equal(abrupt.restart.afterAction, abrupt.actions.indexOf(checkpoint) + 2,
    'the abrupt split retains the ordinary release action after the checkpoint barrier');
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
  assert.throws(() => validateF0vContract({ ...base, variants: { ...base.variants, abrupt_restart: {
    ...base.variants.abrupt_restart, crashWindows: base.variants.abrupt_restart.crashWindows.slice(1),
    deferredCrashWindows: [base.variants.abrupt_restart.crashWindows[0]] } } }), /defer only physical-effect/);
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

test('managed player-break receipt has one server-side disposition before physical mutation', async () => {
  const mixin = await readFile(new URL('../../../pale-mirror-neoforge/src/main/java/io/farfrontier/palemirror/internal/frontier/v3/mixin/FrontierV3PlayerActionMixin.java', import.meta.url), 'utf8');
  const lifecycle = await readFile(new URL('../../../pale-mirror-neoforge/src/main/java/io/farfrontier/palemirror/internal/frontier/v3/FrontierV3ServerLifecycle.java', import.meta.url), 'utf8');
  const resource = await readFile(new URL('../../../pale-mirror-neoforge/src/main/java/io/farfrontier/palemirror/internal/frontier/v3/FrontierV3ResourceSiteExecutor.java', import.meta.url), 'utf8');
  const events = await readFile(new URL('../../../pale-mirror-neoforge/src/main/java/io/farfrontier/palemirror/PaleMirrorEvents.java', import.meta.url), 'utf8');
  assert.match(mixin, /@Mixin\(ServerPlayerGameMode\.class\)[\s\S]*@Inject\(method\s*=\s*"handleBlockBreakAction",\s*at\s*=\s*@At\("HEAD"\)[\s\S]*START_DESTROY_BLOCK/,
    'the disposition must run in Minecraft\'s authoritative game-mode boundary, not on Netty');
  assert.match(events, /onFrontierPlayerBreakAdmission\(PlayerInteractEvent\.LeftClickBlock event\)[\s\S]*observePlayerBreakPacket[\s\S]*PlayerBreakDisposition\.REJECTED[\s\S]*ClientboundBlockUpdatePacket/,
    'the authoritative server left-click boundary must admit or explicitly reject and resynchronize before mutation');
  assert.match(mixin, /PMV3_PLAYER_BREAK boundary=server-game-mode disposition=\{\}/,
    'the native preflight must distinguish receipt at the game-mode boundary from a missing packet');
  assert.match(events, /PMV3_PLAYER_BREAK boundary=server-left-click disposition=\{\}/,
    'the native preflight must distinguish fallback-event receipt from a missing owner disposition');
  assert.match(resource, /PMV3_PLAYER_BREAK resource-disposition=\{\} detail=\{\}/,
    'a rejected resource owner must retain the canonical rejection category rather than silently losing a physical action');
  assert.match(mixin, /observePlayerBreakPacket\([\s\S]*PlayerBreakDisposition\.REJECTED[\s\S]*ClientboundBlockUpdatePacket[\s\S]*callback\.cancel\(\)/,
    'a rejected managed receipt must resynchronize and stop vanilla mutation');
  const dispatcher = lifecycle.slice(lifecycle.indexOf('observePlayerBreakPacket'), lifecycle.indexOf('rejectBlockBreak'));
  assert.ok(dispatcher.indexOf('InfectionOverlayExecutor.observeBlockBreak') < dispatcher.indexOf('ResourceSiteExecutor.observeBlockBreak'));
  assert.ok(dispatcher.indexOf('ResourceSiteExecutor.observeBlockBreak') < dispatcher.indexOf('GrayboxExecutor.observeBlockBreak'));
  assert.match(dispatcher, /ACCEPTED\) \{[\s\S]*PlayerBreakDisposition\.ACCEPTED/,
    'the first accepting physical owner must terminate dispatch');
  assert.match(events, /consumeAcceptedPlayerBreak\([\s\S]*\)\) return;/,
    'the later physical event must consume an accepted packet rather than reinterpret it');
});

test('persistent restart correlates reconnect to the prepared Minecraft client, never its Node wrapper', async () => {
  const isolated = await readFile(new URL('../src/run-isolated-scenario.mjs', import.meta.url), 'utf8');
  assert.match(isolated, /const preparedClient = await awaitLifecycleBarrierFromPilot\(LifecycleBarrier\.PREPARED_CLIENT_READY,[\s\S]*entry\.detail\.segment === 'before_restart'/,
    'the initial lifecycle boundary must retain the native client identity');
  assert.match(isolated, /SAME_CLIENT_RECONNECTED_STATE_CLEARED,[\s\S]*entry\.detail\.clientPid === preparedClient\.detail\.clientPid/,
    'the reconnect receipt must correlate to the originally prepared Minecraft JVM');
  assert.doesNotMatch(isolated, /entry\.detail\.clientPid === persistentPilot\.child\.pid/,
    'the Node wrapper PID is not the native client identity');
});

test('crash before-halves reserve the terminal lifecycle barrier for recovered evidence', async () => {
  const isolated = await readFile(new URL('../src/run-isolated-scenario.mjs', import.meta.url), 'utf8');
  assert.match(isolated, /FRONTIER_V3_PILOT_LIFECYCLE_TERMINAL: 'false'/);
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
      ...(drivers[name] === 'COLD_VS_HOT_COLD' ? { alignment: { view: 'summary', id: '', instantPath: 'instant' }, differential: [
        { invariant: 'identity', view: 'summary', id: '', paths: ['identity'] },
        { invariant: 'claims', view: 'summary', id: '', paths: ['claims'] },
        { invariant: 'conservation', view: 'summary', id: '', paths: ['conservation'] },
        { invariant: 'schedule', view: 'summary', id: '', paths: ['schedule', 'schedule.entries'] },
        { invariant: 'result', view: 'summary', id: '', paths: ['instant', 'result', 'cursor.index', 'cursor.retainedBody', 'cursor.actorBody'] }
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
