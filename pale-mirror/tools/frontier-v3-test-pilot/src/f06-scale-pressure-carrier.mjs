import { isDeepStrictEqual } from 'node:util';

const STEPS = Object.freeze([3, 5, 7, 9, 11]);
const FRONTIER = Object.freeze({ hotSceneLeases: 2, sceneActorBindings: 29, managedActorBindings: 42 });

/**
 * The pressure interval is a state-series proof, not a single successful admission snapshot.
 * Every retained sample must still name the same HOT physical union; bounded retained state
 * cannot grow monotonically through the four 30-second observation intervals.
 */
export function assertF06ScalePressureCarrier({ declaration, manifest }) {
  if (declaration?.id !== 'disposable_settlement_assault_scale_jfr' || manifest?.status !== 'ok'
      || !Array.isArray(declaration.actions) || !Array.isArray(manifest.actions)
      || declaration.actions.length !== 11 || manifest.actions.length !== 11) {
    throw new Error('F0.6 scale pressure declaration/manifest is incomplete');
  }
  declaration.actions.forEach((action, index) => {
    if (!isDeepStrictEqual(manifest.actions[index]?.action, action)) throw new Error('F0.6 scale pressure action substitution');
  });
  const samples = STEPS.map(step => exactly(manifest, step));
  for (const sample of samples) {
    if (!isDeepStrictEqual(pick(sample.frontier), FRONTIER) || sample.queues?.length !== 1
        || sample.queues[0].maxDepth > 1 || sample.queues[0].maxLagTicks !== 0 || sample.droppedAttributions !== 0) {
      throw new Error('F0.6 scale pressure lost HOT custody or bounded queue state');
    }
  }
  const bounded = samples.map(sample => ({ recovery: sample.frontier.recoveryCurrent, tombstones: sample.frontier.recoveryTombstones,
    bytes: sample.frontier.checkpointBytes, deferred: sample.frontier.deferredAftermath }));
  if (bounded.every((value, index) => index === 0 || value.recovery > bounded[index - 1].recovery)
      || bounded.every((value, index) => index === 0 || value.bytes > bounded[index - 1].bytes)
      || bounded.some(value => value.tombstones > 1 || value.deferred !== 0)) {
    throw new Error('F0.6 scale pressure retained state grew monotonically or exceeded its bounded frontier');
  }
  return Object.freeze({ samples: samples.length, first: bounded[0], last: bounded.at(-1), hotUnion: FRONTIER });
}

function exactly(manifest, step) {
  const values = (manifest.diagnostics ?? []).filter(entry => entry?.assertion?.after === step
    && entry.assertion.view === 'performance' && entry.observed?.value?.status === 'ok').map(entry => entry.observed.value);
  if (values.length !== 1) throw new Error(`F0.6 scale pressure lacks one performance receipt at action ${step}`);
  return values[0];
}
function pick(frontier) {
  return { hotSceneLeases: frontier?.hotSceneLeases, sceneActorBindings: frontier?.sceneActorBindings,
    managedActorBindings: frontier?.managedActorBindings };
}
