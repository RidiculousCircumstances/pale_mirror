import { createHash } from 'node:crypto';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { readFileSync } from 'node:fs';
import { dirname, relative, resolve } from 'node:path';
import { CRASH_BOUNDARIES } from './crash-controller.mjs';

const SCHEMA = 1;
const MAX_FAST_FORWARD_TIMEOUT_MS = 180_000;
// One hour is an explicit fail-closed ceiling for a declared full recovery scenario. It covers
// the longest checked-in route-repair proof (3.1m) without truncating any of its action bounds.
const MAX_SCENARIO_WALL_BUDGET_MS = 3_600_000;
const SCENARIO_STARTUP_FLOOR_MS = 120_000;
const SCENARIO_TERMINAL_GRACE_MS = 30_000;
const SCENARIO_FRAME_CAPTURE_BUDGET_MS = 30_000;
// These are client-side protocol bounds, not coordination sleeps. They mirror the
// pilot's existing bounds so an outer watchdog cannot consume a later declared
// action window while an earlier action is still legitimately pending.
const VISIT_TIMEOUT_MS = 120_000;
const INSPECT_TIMEOUT_MS = 30_000;
const PILOT_CATALOG = resolve(dirname(new URL(import.meta.url).pathname), '../../../pale-mirror-frontier/src/testFixtures/resources/io/farfrontier/palemirror/frontier/v3/model/frontier-v3-pilot-profiles.properties');
const { profiles: PILOT_PROFILES, defaultProfile: PILOT_DEFAULT_PROFILE } = loadPilotProfiles(PILOT_CATALOG);
const EVIDENCE_ACTIONS = new Set(['walk', 'look', 'look_nearest_entity', 'break', 'place', 'open_container', 'quick_move_from_inventory', 'quick_move_from_container', 'wait_until_container_item', 'wait', 'wait_until_block', 'wait_until_diagnostic', 'wait_until_harvest_result', 'fast_forward', 'inspect', 'assert_visible_block', 'assert_visible_board', 'assert_visible_entity', 'interact_board', 'interact_nearest_entity', 'attack_nearest_entity', 'visit', 'visit_operation', 'look_operation']);
const SETUP_ACTIONS = new Set(['command', 'observe', 'assert_fixture', 'visit']);

/** Resolves only the unambiguous Xwayland session cookie name; it never reads the secret. */
export function selectMutterXauthority(entries) {
  const candidates = entries.filter((entry) => /^\.mutter-Xwaylandauth\.[A-Za-z0-9]+$/.test(entry));
  return candidates.length === 1 ? candidates[0] : undefined;
}

export function defaultPilotProfile() { return PILOT_DEFAULT_PROFILE; }

/**
 * Resolves an explicitly requested disposable-server JFR capture.  The runner accepts only a
 * bounded duration and a build-local evidence path, so an audit invocation cannot overwrite a
 * source artifact or escape the disposable test workspace.
 */
export function jfrCaptureRequest(environment, project) {
  const requested = environment.FRONTIER_V3_JFR_OUTPUT;
  if (requested === undefined || requested === '') return undefined;
  const duration = environment.FRONTIER_V3_JFR_DURATION ?? '120s';
  if (!/^[1-9][0-9]*[smh]$/.test(duration)) throw new Error('FRONTIER_V3_JFR_DURATION must look like 120s, 5m or 1h');
  const root = resolve(project, 'build/profiles');
  const output = resolve(project, requested);
  const pathFromProfiles = relative(root, output);
  if (pathFromProfiles === '' || pathFromProfiles.startsWith('..') || pathFromProfiles.includes('/..') || !output.endsWith('.jfr')) {
    throw new Error('FRONTIER_V3_JFR_OUTPUT must be a .jfr file under build/profiles');
  }
  return Object.freeze({ duration, output });
}

/** Resolves the JVM identity announced by the exact nonce passed to a disposable server. */
export function pilotServerPid(output, runId) {
  const match = new RegExp(`PMV3_PILOT_SERVER runId=${escapeRegExp(runId)} pid=([1-9][0-9]*)`).exec(output);
  return match === null ? undefined : Number(match[1]);
}

/** Parses only the exact durable crash rendezvous emitted by one disposable server nonce. */
export function pilotCrashBoundary(output, runId) {
  const expression = new RegExp(`PMV3_CRASH_BOUNDARY runId=${escapeRegExp(runId)} boundary=([^\\s]+) owner=([^\\s]+) revision=([0-9]+) payload=([^\\s]+)`);
  const match = expression.exec(output);
  return match === null ? undefined : Object.freeze({ runId, boundary: match[1], owner: match[2], revision: Number(match[3]), payloadType: match[4] });
}

/**
 * A disposable server is usable only after its own exact JVM marker is visible.
 * `Done` plus the generic v3 startup line can arrive one stdout chunk earlier.
 */
export function pilotServerReady(output, runId) {
  return output.includes('Done') && output.includes('Frontier v3') && Number.isInteger(pilotServerPid(output, runId));
}

/**
 * Startup quarantine is a terminal canonical condition, not an absence of output to wait out.
 * Keep the diagnostic narrow so normal warnings and a stale unrelated log line cannot abort the
 * nonce-owned server lifecycle.
 */
export function pilotServerQuarantineFailure(output) {
  const match = /Frontier v3 development runtime quarantined(?: at startup)?: ([^\r\n]+)/.exec(output);
  return match === null ? undefined : match[1];
}

export async function loadScenario(path) {
  const source = await readFile(path, 'utf8');
  const scenario = JSON.parse(source);
  validateScenario(scenario);
  return { scenario, sha256: createHash('sha256').update(source).digest('hex') };
}

export function validateScenario(scenario) {
  if (!scenario || scenario.schema !== SCHEMA || typeof scenario.id !== 'string' || !scenario.id) {
    throw new Error(`scenario must contain schema=${SCHEMA} and nonempty id`);
  }
  if (!scenario.server || typeof scenario.server.host !== 'string' || !Number.isInteger(scenario.server.port)) {
    throw new Error('scenario server must contain host and integer port');
  }
  if (scenario.server.profile !== undefined && !PILOT_PROFILES.includes(scenario.server.profile)) {
    throw new Error(`scenario server profile must be one of the test-only catalog entries: ${PILOT_PROFILES.join(', ')}`);
  }
  if (!scenario.pilot || typeof scenario.pilot.username !== 'string' || !scenario.pilot.username) {
    throw new Error('scenario pilot must contain username');
  }
  if (scenario.isolation !== undefined && (!scenario.isolation || scenario.isolation.mode !== 'disposable_lite'
      || !Number.isInteger(scenario.isolation.seed) || scenario.isolation.seed < -2_147_483_648 || scenario.isolation.seed > 2_147_483_647)) {
    throw new Error('isolation must declare disposable_lite with a signed 32-bit seed');
  }
  if (scenario.server?.viewDistance !== undefined && (!Number.isInteger(scenario.server.viewDistance)
      || scenario.server.viewDistance < 2 || scenario.server.viewDistance > 32)) {
    throw new Error('server.viewDistance must be an integer 2..32');
  }
  if (scenario.restart !== undefined && (!scenario.restart || !Number.isInteger(scenario.restart.afterAction)
      || scenario.restart.afterAction < 1 || scenario.restart.afterAction >= (scenario.actions ?? []).length
      || !['graceful', 'abrupt'].includes(scenario.restart.mode)
      || (scenario.restart.resumeSetup !== undefined && !Array.isArray(scenario.restart.resumeSetup)))) {
    throw new Error('restart needs mode graceful|abrupt and afterAction strictly inside the evidence action range');
  }
  if (scenario.crash !== undefined && (!scenario.crash || scenario.restart?.mode !== 'abrupt'
      || !['before_restart', 'after_restart'].includes(scenario.crash.phase)
      || !CRASH_BOUNDARIES.has(scenario.crash.boundary) || !stableToken(scenario.crash.owner)
      || !stableToken(scenario.crash.payloadType) || !validCrashRevision(scenario.crash.expectedRevision)
      || (scenario.crash.expectedAuthorityEpoch !== undefined && (!Number.isSafeInteger(scenario.crash.expectedAuthorityEpoch)
        || scenario.crash.expectedAuthorityEpoch < 0)))) {
    throw new Error('crash needs an abrupt restart plus one exact phase/boundary/owner/revision/payload');
  }
  for (const [phase, allowed] of [['setup', SETUP_ACTIONS], ['actions', EVIDENCE_ACTIONS]]) {
    const actions = scenario[phase] ?? [];
    if (!Array.isArray(actions)) throw new Error(`scenario ${phase} must be an array`);
    for (const action of actions) {
      if (!action || !allowed.has(action.type)) throw new Error(`unsupported ${phase} action: ${action?.type}`);
      if (action.type === 'command' && !String(action.command).startsWith('/')) throw new Error('setup command must start with /');
      if (action.type === 'visit' && (!validDimension(action.dimension) || !validResolvablePosition(action.position)
          || !Number.isInteger(action.settleMs) || action.settleMs < 0 || action.settleMs > 120_000)) {
        throw new Error('visit needs a namespaced dimension, block position and settleMs 0..120000');
      }
      if (action.type === 'visit_operation' && (!requiredId(action.operationId, 'operation:') || !validDimension(action.dimension)
          || !validPosition(action.offset) || Math.abs(action.offset.x) > 32 || Math.abs(action.offset.y) > 8 || Math.abs(action.offset.z) > 32
          || !Number.isInteger(action.settleMs) || action.settleMs < 0 || action.settleMs > 120_000
          || !Number.isInteger(action.timeoutMs) || action.timeoutMs < 0 || action.timeoutMs > 120_000)) {
        throw new Error('visit_operation needs a bounded operation-relative ordinary visit');
      }
      if (action.type === 'look_operation' && (!requiredId(action.operationId, 'operation:')
          || (action.anchor !== undefined && !['travelCurrent', 'travelCargo'].includes(action.anchor))
          || !Number.isInteger(action.timeoutMs) || action.timeoutMs < 0 || action.timeoutMs > 120_000)) {
        throw new Error('look_operation needs a current read-only operation anchor');
      }
      if (action.type === 'assert_fixture' && (!Array.isArray(action.checks) || action.checks.length < 1 || action.checks.length > 16
          || !Number.isInteger(action.timeoutMs) || action.timeoutMs < 0 || action.timeoutMs > 120_000
          || action.checks.some((check) => !validDiagnosticIdentity(check) || !check.expect || typeof check.expect !== 'object' || Array.isArray(check.expect)))) {
        throw new Error('assert_fixture needs 1..16 read-only diagnostic checks and timeoutMs 0..120000');
      }
      if (action.type === 'walk') validatePosition(action.position ?? action.at);
      if (action.type === 'break' && !validResolvablePosition(action.position)) validatePosition(action.position);
      if (action.type === 'place' && !validPlacePosition(action.position)) validatePosition(action.position);
      if (action.type === 'open_container' && !validResolvablePosition(action.position)) validatePosition(action.position);
      if (action.type === 'look' && !validResolvablePosition(action.at ?? action.position)) validatePosition(action.at ?? action.position);
      if (action.type === 'wait_until_block' && !validResolvablePosition(action.position)) validatePosition(action.position);
      if (action.type === 'place' && (!validItemKind(action.item) || !Number.isInteger(action.timeoutMs) || action.timeoutMs < 0 || action.timeoutMs > 120_000)) {
        throw new Error('place needs a known item, block position and timeoutMs 0..120000');
      }
      if (action.type === 'open_container' && (!Number.isInteger(action.timeoutMs) || action.timeoutMs < 0 || action.timeoutMs > 120_000)) {
        throw new Error('open_container needs timeoutMs 0..120000');
      }
      if (['quick_move_from_inventory', 'quick_move_from_container'].includes(action.type) && (!validItemKind(action.item) || !validStackCount(action.count)
          || !Number.isInteger(action.timeoutMs) || action.timeoutMs < 0 || action.timeoutMs > 120_000)) {
        throw new Error(`${action.type} needs exact item/count and timeoutMs 0..120000`);
      }
      if (action.type === 'wait_until_container_item' && (!requiredId(action.containerId, 'container:') || !validItemKind(action.item)
          || !validStackCount(action.count) || (action.slot !== undefined && (!Number.isInteger(action.slot) || action.slot < 0 || action.slot > 26))
          || !Number.isInteger(action.timeoutMs) || action.timeoutMs < 0 || action.timeoutMs > 300_000)) {
        throw new Error('wait_until_container_item needs a container, exact item/count and timeoutMs 0..300000');
      }
      if (action.type === 'assert_visible_block' && (!validResolvablePosition(action.position) || !Number.isInteger(action.timeoutMs)
          || action.timeoutMs < 0 || action.timeoutMs > 120_000)) {
        throw new Error('assert_visible_block needs position and timeoutMs 0..120000');
      }
      if (action.type === 'assert_visible_board' && (typeof action.text !== 'string' || !action.text
          || !validPosition(action.position) || !Number.isInteger(action.timeoutMs) || action.timeoutMs < 0 || action.timeoutMs > 120_000
          || (action.radius !== undefined && (!Number.isFinite(action.radius) || action.radius < 0 || action.radius > 16))
          || (action.maxDistance !== undefined && (!Number.isFinite(action.maxDistance) || action.maxDistance < 1 || action.maxDistance > 128))
          || (action.maxAngleDeg !== undefined && (!Number.isFinite(action.maxAngleDeg) || action.maxAngleDeg < 1 || action.maxAngleDeg > 90)))) {
        throw new Error('assert_visible_board needs text, position and bounded visibility limits');
      }
      if (action.type === 'assert_visible_entity' && (!validItemKind(action.entityType) || typeof action.nameContains !== 'string' || !action.nameContains
          || !Number.isInteger(action.timeoutMs) || action.timeoutMs < 0 || action.timeoutMs > 120_000
          || (action.maxDistance !== undefined && (!Number.isFinite(action.maxDistance) || action.maxDistance < 1 || action.maxDistance > 128))
          || (action.maxAngleDeg !== undefined && (!Number.isFinite(action.maxAngleDeg) || action.maxAngleDeg < 1 || action.maxAngleDeg > 90)))) {
        throw new Error('assert_visible_entity needs a bounded locally rendered entity presentation');
      }
      if (action.type === 'look_nearest_entity' && (!validItemKind(action.entityType) || typeof action.nameContains !== 'string' || !action.nameContains
          || !Number.isInteger(action.timeoutMs) || action.timeoutMs < 0 || action.timeoutMs > 120_000
          || (action.maxDistance !== undefined && (!Number.isFinite(action.maxDistance) || action.maxDistance < 1 || action.maxDistance > 128)))) {
        throw new Error('look_nearest_entity needs one bounded locally rendered named entity');
      }
      if (action.type === 'interact_board' && (typeof action.text !== 'string' || !action.text || typeof action.title !== 'string' || !action.title
          || action.title.length > 72 || !validPosition(action.position) || !Number.isInteger(action.timeoutMs)
          || action.timeoutMs < 0 || action.timeoutMs > 120_000
          || (action.radius !== undefined && (!Number.isFinite(action.radius) || action.radius < 0 || action.radius > 16))
          || (action.maxDistance !== undefined && (!Number.isFinite(action.maxDistance) || action.maxDistance < 1 || action.maxDistance > 128))
          || (action.maxAngleDeg !== undefined && (!Number.isFinite(action.maxAngleDeg) || action.maxAngleDeg < 1 || action.maxAngleDeg > 90)))) {
        throw new Error('interact_board needs a bounded visible board target and expected contextual title');
      }
      if (action.type === 'interact_nearest_entity' && (!validItemKind(action.entityType) || !Number.isInteger(action.timeoutMs)
          || action.timeoutMs < 0 || action.timeoutMs > 120_000
          || (action.maxDistance !== undefined && (!Number.isFinite(action.maxDistance) || action.maxDistance < 1 || action.maxDistance > 64)))) {
        throw new Error('interact_nearest_entity needs a namespaced entity type and bounded local range');
      }
      if (action.type === 'attack_nearest_entity' && (!validItemKind(action.entityType) || !Number.isInteger(action.timeoutMs)
          || action.timeoutMs < 0 || action.timeoutMs > 120_000 || !Number.isInteger(action.maxAttacks)
          || action.maxAttacks < 1 || action.maxAttacks > 40
          || (action.nameContains !== undefined && (typeof action.nameContains !== 'string' || !action.nameContains || action.nameContains.length > 72))
          || (action.maxDistance !== undefined && (!Number.isFinite(action.maxDistance) || action.maxDistance < 1 || action.maxDistance > 64)))) {
        throw new Error('attack_nearest_entity needs a namespaced entity type, bounded local range and 1..40 attacks');
      }
      if (action.type === 'wait' && (!Number.isInteger(action.ms) || action.ms < 0 || action.ms > 120_000)) {
        throw new Error('wait duration must be 0..120000 milliseconds');
      }
      if (action.type === 'wait_until_block' && (typeof action.block !== 'string' || !action.block || !Number.isInteger(action.timeoutMs)
          || action.timeoutMs < 0 || action.timeoutMs > 120_000)) {
        throw new Error('wait_until_block needs block and timeoutMs 0..120000');
      }
      if (action.type === 'inspect' && !validDiagnosticIdentity(action)) throw new Error('inspect needs a read-only v3 view and id');
      if (action.type === 'fast_forward' && (!Number.isInteger(action.ticks) || action.ticks < 1 || action.ticks > 24_000
          || !Number.isInteger(action.timeoutMs) || action.timeoutMs < 1 || action.timeoutMs > MAX_FAST_FORWARD_TIMEOUT_MS)) {
        throw new Error(`fast_forward needs ticks 1..24000 and timeoutMs 1..${MAX_FAST_FORWARD_TIMEOUT_MS}`);
      }
      if (action.type === 'wait_until_diagnostic' && (!validDiagnosticIdentity(action) || !action.expect || typeof action.expect !== 'object'
          || Array.isArray(action.expect) || !Number.isInteger(action.timeoutMs) || action.timeoutMs < 0 || action.timeoutMs > 300_000)) {
        throw new Error('wait_until_diagnostic needs a read-only view, predicate and timeoutMs 0..300000');
      }
      if (action.type === 'wait_until_harvest_result' && (!requiredId(action.siteId, 'site:') || !requiredId(action.intentId, 'intent:')
          || !requiredId(action.itemId, 'item:') || (action.settlementId !== undefined && !requiredId(action.settlementId, 'settlement:'))
          || (action.workerId !== undefined && !requiredId(action.workerId, 'resident:'))
          || !Number.isInteger(action.timeoutMs) || action.timeoutMs < 0 || action.timeoutMs > 300_000)) {
        throw new Error('wait_until_harvest_result needs exact site, intent, item identities and timeoutMs 0..300000');
      }
    }
  }
  const assertions = scenario.assertions ?? [];
  if (!Array.isArray(assertions)) throw new Error('scenario assertions must be an array');
  for (const assertion of assertions) {
    if (!assertion || !Number.isInteger(assertion.after) || assertion.after < 0 || assertion.after > (scenario.actions ?? []).length
        || !['summary', 'performance', 'process', 'site', 'settlement', 'hive', 'hive_transfer', 'hive_mobilization', 'actor', 'item', 'container', 'market_order', 'operation', 'route_construction', 'route_maintenance', 'route_topology', 'physical_delta', 'medical', 'scene', 'intent', 'trace', 'transit', 'traversal_foundry', 'hive_foundry'].includes(assertion.view)
        || typeof assertion.id !== 'string' || (!['summary', 'performance'].includes(assertion.view) && !assertion.id)
        || !assertion.expect || typeof assertion.expect !== 'object') {
      throw new Error('invalid diagnostic assertion');
    }
  }
  const frames = scenario.frames ?? [];
  if (!Array.isArray(frames)) throw new Error('scenario frames must be an array');
  const frameAfter = new Set(); const frameNames = new Set();
  for (const frame of frames) {
    const presentation = frame?.presentation ?? 'clean';
    if (!frame || !Number.isInteger(frame.after) || frame.after < 1 || frame.after > (scenario.actions ?? []).length
        || typeof frame.name !== 'string' || !/^[a-z0-9][a-z0-9_-]*$/.test(frame.name)
        || !['clean', 'player'].includes(presentation) || frameAfter.has(frame.after) || frameNames.has(frame.name)) {
      throw new Error('invalid frame declaration');
    }
    frameAfter.add(frame.after); frameNames.add(frame.name);
  }
  if (scenario.restart?.resumeSetup) {
    for (const action of scenario.restart.resumeSetup) {
      // Reuse the public setup boundary rather than creating a second, less
      // strict restart-only action language.
      validateScenario({ ...scenario, restart: undefined, setup: [action] });
    }
  }
  scenarioDeadlineMs(scenario);
}

/**
 * Gives every declared action its complete bounded wall-time window. This is deliberately
 * not a best-effort performance cap: an aggregate that exceeds the runner's safe bound is
 * rejected during composition, rather than silently shortening an action's own timeout.
 */
export function scenarioDeadlineMs(scenario) {
  const actions = [...(scenario.setup ?? []), ...(scenario.actions ?? [])];
  const actionBudget = actions.reduce((total, action) => total + actionBudgetMs(action), 0);
  const captureBudget = (scenario.frames ?? []).length * SCENARIO_FRAME_CAPTURE_BUDGET_MS;
  const requested = actionBudget + captureBudget + SCENARIO_TERMINAL_GRACE_MS;
  if (!Number.isSafeInteger(requested) || requested > MAX_SCENARIO_WALL_BUDGET_MS) {
    throw new Error(`scenario declared wall-time budget exceeds ${MAX_SCENARIO_WALL_BUDGET_MS}ms`);
  }
  return Math.max(SCENARIO_STARTUP_FLOOR_MS, requested);
}

function actionBudgetMs(action) {
  if (action.type === 'wait') return action.ms;
  if (action.type === 'fast_forward') return action.timeoutMs;
  if (Number.isInteger(action.timeoutMs)) return action.timeoutMs;
  if (action.type === 'visit') return VISIT_TIMEOUT_MS;
  if (action.type === 'inspect') return INSPECT_TIMEOUT_MS;
  return 0;
}

function loadPilotProfiles(path) {
  const entries = new Map();
  for (const line of readFileSync(path, 'utf8').split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) continue;
    const separator = trimmed.indexOf('=');
    if (separator < 1) throw new Error(`invalid Frontier v3 pilot catalog line: ${trimmed}`);
    entries.set(trimmed.slice(0, separator).trim(), trimmed.slice(separator + 1).trim());
  }
  const profiles = (entries.get('profiles') ?? '').split(',').map((value) => value.trim()).filter(Boolean);
  if (profiles.length === 0 || new Set(profiles).size !== profiles.length) throw new Error('Frontier v3 pilot catalog must contain unique profile ids');
  for (const profile of profiles) {
    for (const field of ['provider', 'source', 'runner', 'assertion']) {
      if (!entries.get(`${profile}.${field}`)) throw new Error(`Frontier v3 pilot catalog lacks ${profile}.${field}`);
    }
  }
  const defaultProfile = entries.get('default');
  if (!defaultProfile || !profiles.includes(defaultProfile)) throw new Error('Frontier v3 pilot catalog default must be one declared profile');
  return Object.freeze({ profiles: Object.freeze(profiles), defaultProfile });
}

/** Splits one declared scenario around its durable recovery boundary. */
export function restartSegments(scenario) {
  if (!scenario.restart) return null;
  const split = scenario.restart.afterAction;
  return {
    mode: scenario.restart.mode,
    before: segment(scenario, 0, split, scenario.setup ?? [], true),
    after: segment(scenario, split, scenario.actions.length, scenario.restart.resumeSetup ?? [], false)
  };
}

function segment(scenario, first, end, setup, includeFirstBoundary) {
  const inRange = (after) => includeFirstBoundary ? after >= first && after <= end : after > first && after <= end;
  const rebase = (entry) => ({ ...entry, after: entry.after - first });
  return {
    ...scenario,
    restart: undefined,
    setup,
    actions: scenario.actions.slice(first, end),
    assertions: (scenario.assertions ?? []).filter(({ after }) => inRange(after)).map(rebase),
    frames: (scenario.frames ?? []).filter(({ after }) => inRange(after)).map(rebase)
  };
}

function validDiagnosticIdentity(value) {
  return ['summary', 'performance', 'process', 'site', 'settlement', 'hive', 'hive_transfer', 'hive_mobilization', 'actor', 'item', 'container', 'market_order', 'operation', 'route_construction', 'route_maintenance', 'route_topology', 'physical_delta', 'medical', 'scene', 'intent', 'trace', 'transit', 'traversal_foundry', 'hive_foundry'].includes(value.view)
    && typeof value.id === 'string' && (['summary', 'performance'].includes(value.view) || Boolean(value.id));
}

function requiredId(value, prefix) { return typeof value === 'string' && value.startsWith(prefix) && value.length > prefix.length; }

function validItemKind(value) { return typeof value === 'string' && /^[a-z0-9_.-]+:[a-z0-9_./-]+$/.test(value); }

function validStackCount(value) { return Number.isInteger(value) && value >= 1 && value <= 64; }

function validatePosition(value) {
  if (!value || !Number.isInteger(value.x) || !Number.isInteger(value.y) || !Number.isInteger(value.z)) {
    throw new Error('block position must contain integer x, y and z');
  }
}

function validPosition(value) {
  return value && Number.isInteger(value.x) && Number.isInteger(value.y) && Number.isInteger(value.z);
}

/**
 * A materialization scenario may follow one retained anchor published by its
 * exact named diagnostic.  A process cursor is permitted only as an ordinary
 * observer-travel target; it does not select a server entity or grant mutation
 * authority. Scene references disclose only a retained current/next edge; a
 * normal player packet may still place or break there, which is evidence rather
 * than test authority.
 */
function validResolvablePosition(value) {
  if (validPosition(value)) return true;
  const reference = value?.diagnostic;
  return value && typeof value === 'object' && Object.keys(value).length === 1
    && reference && typeof reference === 'object' && Object.keys(reference).length === 3
    && ((reference.view === 'site' && requiredId(reference.id, 'site:') && reference.field === 'firstCrop')
      || (reference.view === 'container' && requiredId(reference.id, 'container:') && reference.field === 'position')
      || (reference.view === 'process' && requiredId(reference.id, 'job:') && reference.field === 'cursor.retainedBody')
      || (reference.view === 'scene' && requiredId(reference.id, 'job:')
        && ['productionCurrent', 'productionNext', 'productionNextBody', 'productionFutureBody'].includes(reference.field))
      || (reference.view === 'scene' && requiredId(reference.id, 'service:') && reference.field === 'serviceCurrent'));
}

function validPlacePosition(value) {
  if (validPosition(value)) return true;
  const reference = value?.diagnostic;
  return value && typeof value === 'object' && Object.keys(value).length === 1
    && reference && typeof reference === 'object' && Object.keys(reference).length === 3
    && reference.view === 'scene' && requiredId(reference.id, 'job:') && reference.field === 'productionFutureBody';
}

function validDimension(value) { return typeof value === 'string' && /^[a-z0-9_.-]+:[a-z0-9_./-]+$/.test(value); }

function stableToken(value) { return typeof value === 'string' && /^[A-Za-z0-9][A-Za-z0-9_.:-]{2,127}$/.test(value); }

// A process event owns the authoritative revision.  The test-only probe may publish that
// revision only while it is parked at an otherwise exact run/boundary/owner/payload rendezvous.
function validCrashRevision(value) {
  return (Number.isSafeInteger(value) && value >= 0) || value === 'observed_at_boundary';
}

function escapeRegExp(value) { return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'); }

export function correlation(runId, step) {
  return `scenario:${runId}:${step}`;
}

/**
 * A native client receives command replies on later render/network ticks.
 *
 * An assertion belongs to its declared evidence action, rather than to a
 * matching diagnostic which happened to arrive earlier. Otherwise a completed
 * final inspection can race the runner: an old process response satisfies the
 * coarse readiness predicate, then the terminal assertion correctly finds no
 * response at its own action step. Callers outside a validated scenario retain
 * the former kind/id-only behavior.
 */
export function hasDiagnosticResponses(diagnostics, assertions) {
  return assertions.every((assertion) => Number.isInteger(assertion.after)
    ? diagnosticForAssertion(diagnostics, assertion) !== undefined
    : diagnostics.some((entry) => entry.value?.kind === assertion.view && entry.value?.id === assertion.id));
}

/** A local pilot annotation is authoritative over the runner's interleaved stdout/stderr view. */
export function pilotDiagnosticActionStep(value, fallbackStep) {
  return Number.isInteger(value?.pilotActionStep) && value.pilotActionStep > 0 ? value.pilotActionStep : fallbackStep;
}

/**
 * Selects evidence emitted while the action named by an assertion was executing.
 * A later read of the same object may legitimately observe a different world state;
 * it cannot retroactively invalidate the earlier milestone.
 */
export function diagnosticForAssertion(diagnostics, assertion) {
  return diagnostics.findLast((entry) => entry.actionStep === assertion.after
    && entry.value?.kind === assertion.view && entry.value?.id === assertion.id);
}

/**
 * Pilot diagnostics are emitted into the structured client log instead of
 * rendering in Minecraft's system-chat surface. The legacy marker remains
 * readable for historical manifests and offline-pilot compatibility.
 */
export function diagnosticFromPilotLine(line) {
  for (const marker of ['PMV3_PILOT_DIAGNOSTIC ', 'PMV3_DIAG ']) {
    const index = line.indexOf(marker);
    if (index < 0) continue;
    try { return { line: line.slice(index), value: JSON.parse(line.slice(index + marker.length)) }; }
    catch { return { line: line.slice(index), error: 'malformed diagnostic' }; }
  }
  return null;
}

/** A pilot lifecycle failure is control-plane evidence, never harmless renderer noise. */
export function pilotFailureFromLine(line) {
  const marker = 'PMV3_PILOT_FATAL ';
  const index = line.indexOf(marker);
  return index < 0 ? null : line.slice(index + marker.length).trim() || 'unstructured fatal lifecycle error';
}

/** The exact run marker, not a stale byte count, bounds one server lifecycle's log. */
export function logOffsetAfterMarker(output, marker) {
  const index = output.lastIndexOf(marker);
  if (index < 0) return undefined;
  const end = output.indexOf('\n', index);
  return end < 0 ? output.length : end + 1;
}

export function newManifest({ scenario, sha256, runId }) {
  return {
    schema: 2,
    scenarioId: scenario.id,
    scenarioSha256: sha256,
    runId,
    pilot: scenario.pilot.username,
    startedAt: new Date().toISOString(),
    setup: [],
    actions: [],
    diagnostics: [],
    frames: [],
    trace: null,
    timing: null,
    build: null
  };
}

/** A line is self-contained so a failed/terminated scenario keeps usable causal evidence. */
export function traceRecord({ runId, kind, correlation: traceCorrelation = null, ...data }) {
  return { schema: 1, source: 'PMV3', runId, kind, correlation: traceCorrelation, at: new Date().toISOString(), ...data };
}

export async function saveManifest(path, manifest) {
  await mkdir(dirname(path), { recursive: true });
  await writeFile(path, `${JSON.stringify(manifest, null, 2)}\n`, 'utf8');
}
