import { createHash } from 'node:crypto';
import { CI_MATRIX_SCHEMA, validateFourWorkerMatrixPlan } from './ci-matrix.mjs';
import { restartSegments, validateScenario } from './scenario.mjs';

export const PERSISTENT_WORKER_PLAN_SCHEMA = 1;
export const PERSISTENT_WORKER_PLAN_KIND = 'frontier-v3-persistent-worker-plan';
export const ASSIGNED_PERSISTENT_MATRIX_KIND = 'frontier-v3-assigned-persistent-matrix';
const MAX_SEGMENTS = 32;

/**
 * Compiles one already-admitted immutable CI shard into the later persistent-client input.
 * This is intentionally pure: it creates neither a client session nor lifecycle evidence.
 */
export function compilePersistentWorkerPlan(sourcePlan, workerId, measurementId) {
  const plan = validateFourWorkerMatrixPlan(sourcePlan);
  if (!/^worker-[0-3]$/.test(workerId ?? '')) throw new Error('persistent worker plan has an invalid worker identity');
  if (!/^correctness-[1-3]$/.test(measurementId ?? '')) throw new Error('persistent worker plan has an invalid measurement identity');
  const shard = plan.shards.find((candidate) => candidate.workerId === workerId);
  if (shard === undefined || shard.lanes.length === 0) throw new Error('persistent worker plan has no admitted shard lanes');

  const authoritativeLanes = new Map(plan.lanes.map((lane) => [lane.id, lane]));
  const lanes = shard.lanes.map((lane) => {
    const authoritative = authoritativeLanes.get(lane.id);
    if (authoritative === undefined || JSON.stringify(lane) !== JSON.stringify(authoritative)) {
      throw new Error('persistent worker plan shard lane is foreign or content-drifted');
    }
    return compileLane(authoritative);
  });
  const segments = lanes.flatMap((lane) => lane.segments);
  if (segments.length === 0 || segments.length > MAX_SEGMENTS) throw new Error('persistent worker plan segment count is outside its bounded capacity');
  if (new Set(lanes.map((lane) => lane.worldKey)).size !== lanes.length) throw new Error('persistent worker plan aliases independent lane worlds');

  const core = {
    schema: PERSISTENT_WORKER_PLAN_SCHEMA,
    kind: PERSISTENT_WORKER_PLAN_KIND,
    source: {
      ciMatrixSchema: CI_MATRIX_SCHEMA,
      planSha256: plan.planSha256,
      buildIdentitySha256: plan.buildIdentitySha256,
      contractSha256: plan.contractSha256,
      workerId,
      measurementId
    },
    lanes
  };
  return freeze({ ...core, contentSha256: hash(core) });
}

/** Reconstructs expected content from the immutable CI plan; a replacement hash cannot bless drift. */
export function validatePersistentWorkerPlan(value, sourcePlan, { workerId, measurementId } = {}) {
  const source = value?.source;
  if (!value || value.schema !== PERSISTENT_WORKER_PLAN_SCHEMA || value.kind !== PERSISTENT_WORKER_PLAN_KIND
      || !sha(value.contentSha256) || !source || typeof source !== 'object') {
    throw new Error('persistent worker plan is malformed');
  }
  const expected = compilePersistentWorkerPlan(sourcePlan, workerId, measurementId);
  if (JSON.stringify(value) !== JSON.stringify(expected)) throw new Error('persistent worker plan is foreign or content-drifted');
  return expected;
}

/**
 * The CI worker plan, not a minimum-world boolean, is the only admission source for the
 * assigned runner.  Benchmark plans deliberately keep their separate three-world policy.
 */
export function compileAssignedPersistentMatrix(workerPlan, sourcePlan, { workerId, measurementId } = {}) {
  const checked = validatePersistentWorkerPlan(workerPlan, sourcePlan, { workerId, measurementId });
  const segments = checked.lanes.flatMap((lane) => lane.segments.map((segment) => Object.freeze({
    id: segment.id, laneId: lane.id, scenarioId: segment.scenario.id, scenarioSha256: segment.scenarioSha256,
    originalScenarioId: segment.originalScenarioId, originalScenarioSha256: segment.originalScenarioSha256,
    originalActionOffset: segment.originalActionOffset, worldKey: segment.worldKey, actionCount: segment.actionCount,
    completion: segment.completion, scenario: structuredClone(segment.scenario), ...(segment.crash === undefined ? {} : { expectedCrash: { ...structuredClone(segment.crash), laneId: lane.id } })
  })));
  const core = { schema: 1, kind: ASSIGNED_PERSISTENT_MATRIX_KIND, workerId: checked.source.workerId,
    buildIdentitySha256: checked.source.buildIdentitySha256, source: structuredClone(checked.source),
    workerPlanSha256: checked.contentSha256, workerPlan: structuredClone(checked),
    segments: segments.map((segment, epoch) => ({ ...segment, final: epoch === segments.length - 1 })) };
  return freeze({ ...core, contentSha256: hash(core) });
}

function compileLane(lane) {
  validateScenario(lane.scenario);
  const worldKey = `lane-${hash(lane.id).slice(0, 24)}`;
  const split = restartSegments(lane.scenario);
  if (split === null) return lanePlan(lane, worldKey, [{ completion: 'terminal', scenario: lane.scenario }]);
  if (split.mode === 'graceful') {
    return lanePlan(lane, worldKey, [
      { completion: 'graceful_handoff', scenario: split.before },
      { completion: 'recovered_terminal', scenario: split.after }
    ]);
  }
  if (split.mode !== 'abrupt' || lane.scenario.crash?.phase !== 'before_restart') {
    throw new Error('persistent worker plan does not support this crash recovery boundary');
  }
  const expectedCrash = { ...split.before, crash: undefined };
  const recovered = { ...split.after, crash: undefined };
  return lanePlan(lane, worldKey, [
    { completion: 'expected_crash', scenario: expectedCrash, crash: lane.scenario.crash },
    { completion: 'recovered_terminal', scenario: recovered }
  ]);
}

function lanePlan(lane, worldKey, entries) {
  const split = restartSegments(lane.scenario);
  const segments = entries.map((entry, index) => {
    const scenario = structuredClone(entry.scenario);
    validateScenario(scenario);
    const core = {
      id: `segment-${hash(lane.id).slice(0, 24)}-${index}`,
      laneId: lane.id,
      originalScenarioId: lane.scenario.id,
      originalScenarioSha256: lane.scenarioSha256,
      originalActionOffset: index === 0 ? 0 : lane.scenario.restart?.afterAction,
      order: index,
      worldKey,
      completion: entry.completion,
      scenario,
      scenarioSha256: hash(scenario),
      actionCount: scenario.actions.length
    };
    return entry.crash === undefined ? core : { ...core, crash: structuredClone(entry.crash) };
  });
  return { id: lane.id, originalScenarioSha256: lane.scenarioSha256, worldKey, segments };
}

function hash(value) { return createHash('sha256').update(JSON.stringify(value)).digest('hex'); }
function sha(value) { return typeof value === 'string' && /^[a-f0-9]{64}$/.test(value); }
function freeze(value) {
  if (!value || typeof value !== 'object' || Object.isFrozen(value)) return value;
  for (const child of Object.values(value)) freeze(child);
  return Object.freeze(value);
}
