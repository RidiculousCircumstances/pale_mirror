import { createHash } from 'node:crypto';
import { compareDifferentialEvidence, extractDeclaredProjections, materializeF0vMatrix, requireArrivalCheckpoint, requireDistinctArrivalCheckpoints } from './f0v-matrix.mjs';

export const CI_MATRIX_SCHEMA = 2;
/** Provider queue time is not a matrix-speed sample: all four timed workers must begin together. */
export const CI_MATRIX_MAX_START_SKEW_MILLIS = 30_000;
export const CI_CORRECTNESS_MEASUREMENTS = Object.freeze(['correctness-1', 'correctness-2', 'correctness-3']);
const KIND = 'frontier-v3-four-worker-matrix';
const REQUIRED_INVARIANTS = Object.freeze(['identity', 'claims', 'conservation', 'schedule', 'result']);

/**
 * Validates the complete provider-controlled reusable-workflow input before a
 * worker can invoke Gradle.  GitHub expands the strategy before executing a
 * step, but every expanded job still receives this exact immutable input, so
 * this rejects a bad complete matrix before build or world creation.
 */
export function validateCiCorrectnessAdmission({ measurement, workers, worker, port }) {
  if (!CI_CORRECTNESS_MEASUREMENTS.includes(measurement)) throw new Error('CI correctness measurement is unknown');
  if (!plainObject(workers) || Object.keys(workers).length !== 1 || !Array.isArray(workers.include) || workers.include.length !== 4) {
    throw new Error('CI correctness worker matrix must contain exactly four workers');
  }
  const admitted = workers.include.map((entry) => validateCiWorkerPort(entry));
  const expectedWorkers = ['worker-0', 'worker-1', 'worker-2', 'worker-3'];
  if (admitted.map((entry) => entry.worker).sort().join(',') !== expectedWorkers.join(',')) {
    throw new Error('CI correctness worker matrix has missing, duplicate or foreign workers');
  }
  if (new Set(admitted.map((entry) => entry.port)).size !== admitted.length) {
    throw new Error('CI correctness worker matrix has duplicate private ports');
  }
  const assigned = admitted.find((entry) => entry.worker === worker);
  if (assigned === undefined || assigned.port !== port) {
    throw new Error('CI correctness expanded worker does not match the admitted private worker/port pair');
  }
  return Object.freeze({ measurement, workers: Object.freeze([...admitted].sort((left, right) => left.worker.localeCompare(right.worker))), worker, port });
}

/** Builds an immutable, deterministic four-worker native lane plan from the actual F0.V contract. */
export function createFourWorkerMatrixPlan(contract, { buildIdentitySha256, contractSha256, workers = 4 } = {}) {
  if (!sha(buildIdentitySha256) || !sha(contractSha256) || workers !== 4) throw new Error('four-worker matrix identity is malformed');
  const lanes = materializeF0vMatrix(contract).flatMap((entry) => entry.executions.map((execution) => lane(entry, execution)));
  if (lanes.length < 4 || new Set(lanes.map((value) => value.id)).size !== lanes.length) throw new Error('four-worker matrix has invalid lane coverage');
  const assigned = assign(lanes, workers);
  const core = Object.freeze({ schema: CI_MATRIX_SCHEMA, kind: KIND, buildIdentitySha256, contractSha256,
    workers, lanes: Object.freeze([...lanes].sort(byId)), shards: Object.freeze(assigned) });
  return Object.freeze({ ...core, planSha256: hash(JSON.stringify(core)) });
}

export function validateFourWorkerMatrixPlan(plan) { return validatePlan(plan); }

/** Refuses a worker result unless it covers exactly its assigned lanes with terminal semantic evidence. */
export function validateCiShardResult(plan, result) {
  const checked = validatePlan(plan);
  if (!result || result.schema !== CI_MATRIX_SCHEMA || result.kind !== 'frontier-v3-ci-shard-result' || result.planSha256 !== checked.planSha256
      || result.buildIdentitySha256 !== checked.buildIdentitySha256 || result.contractSha256 !== checked.contractSha256 || !workerId(result.workerId)
      || !CI_CORRECTNESS_MEASUREMENTS.includes(result.measurementId) || result.status !== 'ok' || !Number.isFinite(result.executionMillis) || result.executionMillis <= 0
      || !Number.isSafeInteger(result.startedEpochMillis) || !Number.isSafeInteger(result.finishedEpochMillis)
      || result.startedEpochMillis <= 0 || result.finishedEpochMillis < result.startedEpochMillis || !Array.isArray(result.lanes)) {
    throw new Error('CI shard result is malformed, foreign, failed or incomplete');
  }
  const shard = checked.shards.find((entry) => entry.workerId === result.workerId);
  if (shard === undefined) throw new Error('CI shard worker is foreign');
  const expected = shard.lanes.map((entry) => entry.id).sort();
  const byExpectedId = new Map(shard.lanes.map((entry) => [entry.id, entry]));
  const actual = result.lanes.map((entry) => validateLaneResult(byExpectedId.get(entry?.id), entry)).sort((left, right) => left.id.localeCompare(right.id));
  if (actual.length !== expected.length || actual.some((entry, index) => entry.id !== expected[index])) {
    throw new Error('CI shard has missing, duplicate or foreign lane coverage');
  }
  return Object.freeze({ workerId: result.workerId, measurementId: result.measurementId, executionMillis: result.executionMillis, startedEpochMillis: result.startedEpochMillis,
    finishedEpochMillis: result.finishedEpochMillis, lanes: Object.freeze(actual) });
}

/** Merges exactly four isolated worker reports; an omitted/failed report is never interpreted as a skip. */
export function mergeFourWorkerMatrix(plan, results) {
  const aggregate = aggregateFourWorkerMatrix(plan, results);
  const started = aggregate.workers.map((shard) => shard.startedEpochMillis);
  const finished = aggregate.workers.map((shard) => shard.finishedEpochMillis);
  const earliestStart = Math.min(...started);
  const latestStart = Math.max(...started);
  const earliestFinish = Math.min(...finished);
  const latestFinish = Math.max(...finished);
  const startSkewMillis = latestStart - earliestStart;
  if (startSkewMillis > CI_MATRIX_MAX_START_SKEW_MILLIS) {
    throw new Error('CI matrix workers did not begin inside the bounded concurrent execution window');
  }
  if (latestStart > earliestFinish) {
    throw new Error('CI matrix workers did not execute concurrently');
  }
  return Object.freeze({ ...aggregate, policy: 'parallel', startSkewMillis, parallelExecutionMillis: latestFinish - earliestStart });
}

/** Serial timing is complete correctness evidence, but must never claim parallel overlap. */
export function mergeSequentialFourWorkerMatrix(plan, results) {
  const aggregate = aggregateFourWorkerMatrix(plan, results);
  const ordered = [...aggregate.workers].sort((left, right) => left.workerId.localeCompare(right.workerId));
  for (let index = 1; index < ordered.length; index++) {
    if (ordered[index].startedEpochMillis < ordered[index - 1].finishedEpochMillis) {
      throw new Error('CI sequential matrix shard intervals overlap or are out of order');
    }
  }
  return Object.freeze({ ...aggregate, policy: 'sequential', sequentialExecutionMillis: ordered.at(-1).finishedEpochMillis - ordered[0].startedEpochMillis });
}

/** Shared validation owns coverage and all cross-lane semantic relations before timing policy. */
export function aggregateFourWorkerMatrix(plan, results) {
  const checked = validatePlan(plan);
  if (!Array.isArray(results) || results.length !== checked.workers) throw new Error('CI matrix merge requires exactly four worker results');
  const shards = results.map((result) => validateCiShardResult(checked, result)).sort((left, right) => left.workerId.localeCompare(right.workerId));
  if (new Set(shards.map((entry) => entry.workerId)).size !== checked.workers) throw new Error('CI matrix merge contains duplicate worker evidence');
  if (new Set(shards.map((entry) => entry.measurementId)).size !== 1) throw new Error('CI matrix merge contains mixed measurement evidence');
  const lanes = shards.flatMap((shard) => shard.lanes).sort((left, right) => left.id.localeCompare(right.id));
  const required = checked.lanes.map((entry) => entry.id).sort();
  if (lanes.length !== required.length || lanes.some((entry, index) => entry.id !== required[index])) {
    throw new Error('CI matrix merge has missing or duplicate lanes');
  }
  const crossLane = requireCrossLaneEvidence(checked, lanes);
  return Object.freeze({ schema: CI_MATRIX_SCHEMA, kind: 'frontier-v3-four-worker-matrix-merge', status: 'ok',
    planSha256: checked.planSha256, buildIdentitySha256: checked.buildIdentitySha256, contractSha256: checked.contractSha256,
    measurementId: shards[0].measurementId, workers: Object.freeze(shards), lanes: Object.freeze(lanes), crossLane,
    totalExecutionMillis: shards.reduce((sum, shard) => sum + shard.executionMillis, 0) });
}

/** The timing gate is separate from merge completeness: no queue time or cached run may enter this ratio. */
export function requireCiMatrixSpeedup({ sequentialMillis, parallelMillis, required = 2.5 }) {
  if (!Number.isFinite(sequentialMillis) || sequentialMillis <= 0 || !Number.isFinite(parallelMillis) || parallelMillis <= 0
      || !Number.isFinite(required) || required <= 1) throw new Error('CI matrix timing input is malformed');
  const speedup = sequentialMillis / parallelMillis;
  if (speedup < required) throw new Error(`CI matrix speedup ${speedup.toFixed(3)}x is below required ${required.toFixed(3)}x`);
  return Object.freeze({ sequentialMillis, parallelMillis, speedup, required });
}

/** F0.VA timing acceptance compares three complete matrix samples, never one lucky run. */
export function requireCiMatrixMedianSpeedup({ sequentialSamples, parallelSamples, required = 2.5 }) {
  const sequential = timingSamples(sequentialSamples, 'sequential');
  const parallel = timingSamples(parallelSamples, 'parallel');
  const sequentialMedianMillis = median(sequential);
  const parallelMedianMillis = median(parallel);
  const comparison = requireCiMatrixSpeedup({ sequentialMillis: sequentialMedianMillis, parallelMillis: parallelMedianMillis, required });
  return Object.freeze({ ...comparison, sequentialSamples: Object.freeze(sequential), parallelSamples: Object.freeze(parallel),
    sequentialMedianMillis, parallelMedianMillis });
}

/** Extracts the five declared terminal semantic projections from one real native lane manifest. */
export function terminalSemanticsFromManifest(laneValue, manifest) {
  const lane = validateLane(laneValue);
  if (!manifest || manifest.status !== 'ok' || !Array.isArray(manifest.diagnostics)) {
    throw new Error('CI lane manifest is absent, failed or malformed');
  }
  const projections = lane.scenario.f0vTerminalProjections;
  if (!Array.isArray(projections) || projections.length < REQUIRED_INVARIANTS.length) throw new Error('CI lane scenario lacks terminal semantic projections');
  const extracted = extractDeclaredProjections(manifest, projections, 'terminal');
  const terminal = Object.fromEntries(extracted.map((projection) => [projection.invariant, projection]));
  if (Object.keys(terminal).sort().join(',') !== [...REQUIRED_INVARIANTS].sort().join(',')) throw new Error('CI lane terminal projections are incomplete');
  return Object.freeze(terminal);
}

/** Extracts all plan-declared portable evidence from one native manifest for worker serialization. */
export function evidenceFromManifest(laneValue, manifest) {
  const lane = validateLane(laneValue);
  const terminal = terminalSemanticsFromManifest(lane, manifest);
  const differential = extractDeclaredProjections(manifest, lane.scenario.f0vDifferential ?? [], 'differential');
  const arrival = requireArrivalCheckpoint({ variant: lane.variant, scenario: lane.scenario }, manifest);
  return Object.freeze({ terminal, differential, ...(arrival === undefined ? {} : { arrival }) });
}

function lane(entry, execution) {
  const scenario = execution.scenario;
  const id = `${entry.family}:${entry.variant}:${execution.lane}`;
  const crash = scenario.crash !== undefined;
  const restart = scenario.restart !== undefined;
  const weight = scenario.actions.length + (scenario.frames?.length ?? 0) * 2 + (restart ? 8 : 0) + (crash ? 12 : 0) + 1;
  if (!laneId(id) || !Number.isInteger(weight) || weight < 1) throw new Error('CI matrix lane is malformed');
  return Object.freeze({ id, family: entry.family, variant: entry.variant, executionLane: execution.lane,
    scenario: structuredClone(scenario), scenarioSha256: hash(JSON.stringify(scenario)), weight, crash, restart,
    requiredInvariants: REQUIRED_INVARIANTS });
}
function assign(lanes, workers) {
  const buckets = Array.from({ length: workers }, (_, index) => ({ workerId: `worker-${index}`, declaredWeight: 0, lanes: [] }));
  // The sorted lane identity is the stable primary ordering.  Weight is used only to choose the
  // least-loaded deterministic bucket; completion time has no influence on assignment.
  for (const candidate of [...lanes].sort(byId)) {
    const bucket = [...buckets].sort((left, right) => left.declaredWeight - right.declaredWeight || left.workerId.localeCompare(right.workerId))[0];
    bucket.lanes.push(candidate); bucket.declaredWeight += candidate.weight;
  }
  return buckets.map((bucket) => Object.freeze({ workerId: bucket.workerId, declaredWeight: bucket.declaredWeight,
    lanes: Object.freeze([...bucket.lanes].sort(byId)) }));
}
function validatePlan(value) {
  if (!value || value.schema !== CI_MATRIX_SCHEMA || value.kind !== KIND || !sha(value.planSha256) || !sha(value.buildIdentitySha256)
      || !sha(value.contractSha256) || value.workers !== 4 || !Array.isArray(value.lanes) || !Array.isArray(value.shards) || value.shards.length !== 4) {
    throw new Error('CI matrix plan is malformed');
  }
  const core = { schema: value.schema, kind: value.kind, buildIdentitySha256: value.buildIdentitySha256, contractSha256: value.contractSha256,
    workers: value.workers, lanes: value.lanes, shards: value.shards };
  if (hash(JSON.stringify(core)) !== value.planSha256) throw new Error('CI matrix plan hash is stale or foreign');
  const laneIds = value.lanes.map((entry) => validateLane(entry).id).sort();
  if (new Set(laneIds).size !== laneIds.length) throw new Error('CI matrix plan has duplicate lanes');
  const seen = [];
  for (const [index, shard] of value.shards.entries()) {
    if (!shard || shard.workerId !== `worker-${index}` || !Number.isInteger(shard.declaredWeight) || shard.declaredWeight < 1 || !Array.isArray(shard.lanes)) {
      throw new Error('CI matrix shard is malformed');
    }
    const members = shard.lanes.map((entry) => validateLane(entry));
    if (members.reduce((sum, entry) => sum + entry.weight, 0) !== shard.declaredWeight) throw new Error('CI matrix shard weight is inconsistent');
    seen.push(...members.map((entry) => entry.id));
  }
  if (seen.length !== laneIds.length || seen.sort().some((id, index) => id !== laneIds[index])) throw new Error('CI matrix shard coverage is incomplete or duplicated');
  return Object.freeze(value);
}
function validateLane(value) {
  if (!value || !laneId(value.id) || typeof value.family !== 'string' || typeof value.variant !== 'string' || typeof value.executionLane !== 'string'
      || !value.scenario || !sha(value.scenarioSha256) || hash(JSON.stringify(value.scenario)) !== value.scenarioSha256
      || !Number.isInteger(value.weight) || value.weight < 1 || typeof value.crash !== 'boolean' || typeof value.restart !== 'boolean'
      || JSON.stringify(value.requiredInvariants) !== JSON.stringify(REQUIRED_INVARIANTS)) throw new Error('CI matrix lane is malformed');
  return Object.freeze(value);
}
function validateLaneResult(expected, value) {
  if (expected === undefined || !value || value.id !== expected.id) throw new Error('CI shard lane is foreign');
  const terminalDeclarations = expected.scenario.f0vTerminalProjections;
  const terminal = validateTerminalEvidence(terminalDeclarations, value.terminal);
  const differential = validateProjectionEvidence(expected.scenario.f0vDifferential ?? [], value.differential, 'differential');
  const arrivalDeclaration = expected.scenario.f0vArrivalCheckpoint;
  const arrival = arrivalDeclaration === undefined
    ? requireAbsent(value.arrival, 'CI shard lane has unexpected arrival evidence')
    : validateArrivalEvidence(arrivalDeclaration, value.arrival);
  return Object.freeze({ id: value.id, terminal, differential, ...(arrival === undefined ? {} : { arrival }) });
}
function validateTerminalEvidence(declarations, terminal) {
  if (!terminal || typeof terminal !== 'object' || Array.isArray(terminal) || !Array.isArray(declarations)
      || Object.keys(terminal).sort().join(',') !== declarations.map((entry) => entry.invariant).sort().join(',')) {
    throw new Error('CI shard lane lacks terminal semantic invariants');
  }
  const projected = declarations.map((declaration) => ({ invariant: declaration.invariant, ...terminal[declaration.invariant] }));
  const checked = validateProjectionEvidence(declarations, projected, 'terminal');
  return Object.freeze(Object.fromEntries(checked.map((entry) => [entry.invariant, entry])));
}
function validateProjectionEvidence(declarations, evidence, label) {
  if (!Array.isArray(declarations) || !Array.isArray(evidence) || evidence.length !== declarations.length) {
    throw new Error(`CI shard ${label} evidence is incomplete`);
  }
  return Object.freeze(evidence.map((actual, index) => {
    const expected = declarations[index];
    if (!actual || actual.invariant !== expected.invariant || actual.view !== expected.view || actual.id !== expected.id
        || JSON.stringify(actual.paths) !== JSON.stringify(expected.paths) || !plainObject(actual.values)
        || Object.keys(actual.values).sort().join(',') !== [...expected.paths].sort().join(',')
        || expected.paths.some((path) => actual.values[path] === undefined)) {
      throw new Error(`CI shard ${label} evidence does not match immutable declaration: ${expected?.invariant ?? index}`);
    }
    return Object.freeze({ invariant: actual.invariant, view: actual.view, id: actual.id, paths: Object.freeze([...actual.paths]), values: Object.freeze(structuredClone(actual.values)) });
  }));
}
function validateArrivalEvidence(expected, actual) {
  if (!actual || actual.view !== expected.view || actual.id !== expected.id || actual.path !== expected.path
      || !Number.isSafeInteger(actual.value) || actual.value < 0) {
    throw new Error('CI shard arrival evidence does not match immutable declaration');
  }
  return Object.freeze({ view: actual.view, id: actual.id, path: actual.path, value: actual.value });
}
function requireAbsent(value, error) { if (value !== undefined) throw new Error(error); return undefined; }
function requireCrossLaneEvidence(plan, lanes) {
  const byId = new Map(lanes.map((entry) => [entry.id, entry]));
  const comparisons = [];
  for (const lane of plan.lanes.filter((entry) => entry.executionLane === 'cold' && Array.isArray(entry.scenario.f0vDifferential))) {
    const hotColdPlan = plan.lanes.find((entry) => entry.family === lane.family && entry.variant === lane.variant && entry.executionLane === 'hot_cold');
    const cold = byId.get(lane.id); const hotCold = hotColdPlan === undefined ? undefined : byId.get(hotColdPlan.id);
    if (hotColdPlan === undefined || cold === undefined || hotCold === undefined) throw new Error(`CI matrix differential pair is incomplete: ${lane.variant}`);
    comparisons.push(...compareDifferentialEvidence({ driver: 'COLD_VS_HOT_COLD', scenario: lane.scenario }, cold.differential, hotCold.differential));
  }
  const firstPlan = plan.lanes.find((entry) => entry.variant === 'arrival_checkpoint_one');
  const secondPlan = plan.lanes.find((entry) => entry.variant === 'arrival_checkpoint_two');
  if (firstPlan === undefined || secondPlan === undefined) throw new Error('CI matrix plan lacks declared arrival checkpoints');
  const first = byId.get(firstPlan.id)?.arrival; const second = byId.get(secondPlan.id)?.arrival;
  const arrivalCheckpoints = requireDistinctArrivalCheckpoints(first, second);
  return Object.freeze({ differential: Object.freeze(comparisons), arrivalCheckpoints });
}
function byId(left, right) { return left.id.localeCompare(right.id); }
function laneId(value) { return typeof value === 'string' && /^[A-Za-z0-9_.:-]{3,256}$/.test(value); }
function workerId(value) { return typeof value === 'string' && /^worker-[0-3]$/.test(value); }
function validateCiWorkerPort(value) {
  if (!plainObject(value) || Object.keys(value).sort().join(',') !== 'port,worker' || !workerId(value.worker)
      || typeof value.port !== 'string' || !/^[1-9][0-9]{3,4}$/.test(value.port)) {
    throw new Error('CI correctness worker/port pair is malformed');
  }
  const numericPort = Number(value.port);
  if (!Number.isSafeInteger(numericPort) || numericPort < 1024 || numericPort > 65535) {
    throw new Error('CI correctness private port is outside the permitted range');
  }
  return Object.freeze({ worker: value.worker, port: value.port });
}
function plainObject(value) { return value !== null && typeof value === 'object' && !Array.isArray(value) && Object.getPrototypeOf(value) === Object.prototype; }
function sha(value) { return typeof value === 'string' && /^[a-f0-9]{64}$/.test(value); }
function hash(value) { return createHash('sha256').update(value).digest('hex'); }
function timingSamples(value, label) {
  if (!Array.isArray(value) || value.length !== 3 || value.some((entry) => !Number.isFinite(entry) || entry <= 0)) {
    throw new Error(`CI ${label} timing needs exactly three positive complete samples`);
  }
  return [...value];
}
function median(values) { const ordered = [...values].sort((left, right) => left - right); return ordered[1]; }
function atPath(value, path) {
  return path.split('.').reduce((current, key) => current != null && Object.prototype.hasOwnProperty.call(current, key) ? current[key] : undefined, value);
}
