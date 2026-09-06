import { createHash } from 'node:crypto';

export const INDEPENDENT_MATRIX_SCHEMA = 1;

/**
 * The former F0.V feedback path deliberately launches a new ordinary client for
 * every independent scenario.  It is retained only as a measured comparison
 * cohort; it never shares worlds, a client JVM, or a mutable control session.
 */
export function independentMatrixPlan({ build, smokeScenario, gracefulScenario }) {
  const buildSha256 = hash(stable(build));
  const smokeSha256 = hash(stable(smokeScenario)); const gracefulSha256 = hash(stable(gracefulScenario));
  return Object.freeze({ schema: INDEPENDENT_MATRIX_SCHEMA, kind: 'frontier-v3-independent-native-matrix', buildSha256,
    segments: Object.freeze([
      Object.freeze({ id: 'smoke_alpha', scenarioSha256: smokeSha256, restart: false }),
      Object.freeze({ id: 'graceful_restart', scenarioSha256: gracefulSha256, restart: true }),
      Object.freeze({ id: 'smoke_beta', scenarioSha256: smokeSha256, restart: false })
    ]) });
}

export function validateIndependentMatrixEvidence(plan, manifests) {
  if (!plan || plan.schema !== INDEPENDENT_MATRIX_SCHEMA || plan.kind !== 'frontier-v3-independent-native-matrix'
      || !sha(plan.buildSha256) || !Array.isArray(plan.segments) || plan.segments.length !== 3
      || !Array.isArray(manifests) || manifests.length !== plan.segments.length) {
    throw new Error('independent matrix evidence is malformed');
  }
  const seenRuns = new Set(); const seenWorlds = new Set(); const checked = manifests.map((manifest, index) => {
    const segment = plan.segments[index];
    const world = segment.restart ? manifest?.recovery?.world : manifest?.isolation?.world;
    if (!manifest || manifest.status !== 'ok' || !manifest.build || hash(stable(manifest.build)) !== plan.buildSha256
        || !manifest.runId || !manifest.timing?.totalMillis || manifest.timing.totalMillis <= 0 || typeof world !== 'string') {
      throw new Error(`independent matrix segment ${segment?.id ?? index} lacks exact successful identity`);
    }
    if (seenRuns.has(manifest.runId) || seenWorlds.has(world)) throw new Error('independent matrix reused a run or world identity');
    seenRuns.add(manifest.runId); seenWorlds.add(world);
    if (segment.restart) {
      if (manifest.recovery.mode !== 'graceful' || manifest.recovery.clientSession !== undefined || !Array.isArray(manifest.clientSegments)
          || manifest.clientSegments.length !== 2) throw new Error('independent graceful segment reused a client or lacks both ordinary client sessions');
    } else if (manifest.recovery !== null || !Array.isArray(manifest.clientSegments) || manifest.clientSegments.length !== 1) {
      throw new Error('independent smoke segment lacks one ordinary client session');
    }
    return Object.freeze({ id: segment.id, runId: manifest.runId, world,
      totalMillis: manifest.timing.totalMillis });
  });
  if (new Set(checked.map((entry) => entry.runId)).size !== checked.length || new Set(checked.map((entry) => entry.world).filter(Boolean)).size !== checked.length) {
    throw new Error('independent matrix reused a run or world identity');
  }
  return Object.freeze({ segmentCount: checked.length, segments: Object.freeze(checked), totalMillis: checked.reduce((sum, entry) => sum + entry.totalMillis, 0) });
}

function sha(value) { return typeof value === 'string' && /^[a-f0-9]{64}$/.test(value); }
function hash(value) { return createHash('sha256').update(value).digest('hex'); }
function stable(value) { return JSON.stringify(value); }
