export const CRASH_BOUNDARIES = new Set([
  'lease_recorded_before_physical_materialization',
  'physical_effect_visible_before_typed_observation',
  'typed_observation_durable_before_next_process_checkpoint',
  'hot_checkpoint_durable_before_drain_release',
  'release_durable_before_cold_resumption'
]);

/**
 * Test-only abrupt-stop authority.  It cannot discover processes: an F0.V caller must supply
 * the exact Minecraft PID emitted with its nonce plus the causal owner/revision/epoch it expects
 * to see.  An unrelated server is therefore not a valid target even if it shares a port/name.
 */
export function createCrashController(config) {
  const value = Object.freeze({ ...config });
  if (!/^[0-9a-f-]{36}$/.test(value.runId ?? '')) throw new Error('crash controller requires exact runId');
  if (!CRASH_BOUNDARIES.has(value.boundary)) throw new Error('crash controller boundary is not admitted');
  if (typeof value.owner !== 'string' || value.owner.length < 3) throw new Error('crash controller requires canonical owner');
  if (typeof value.payloadType !== 'string' || !/^[A-Za-z0-9][A-Za-z0-9_.:-]{2,127}$/.test(value.payloadType)) {
    throw new Error('crash controller requires exact payload type');
  }
  const hasRevision = Number.isSafeInteger(value.expectedRevision) && value.expectedRevision >= 0;
  const hasEpoch = Number.isSafeInteger(value.expectedAuthorityEpoch) && value.expectedAuthorityEpoch >= 0;
  if (!hasRevision && !hasEpoch) throw new Error('crash controller requires expected revision or authority epoch');
  if (value.expectedRevision !== undefined && !hasRevision) throw new Error('crash controller requires nonnegative expected revision');
  if (value.expectedAuthorityEpoch !== undefined && !hasEpoch) throw new Error('crash controller requires nonnegative expected authority epoch');
  if (!Number.isSafeInteger(value.serverPid) || value.serverPid <= 1) throw new Error('crash controller requires exact server PID');
  let fired = false;
  return Object.freeze({
    config: value,
    fire(observation, kill = process.kill) {
      if (fired) throw new Error('crash controller may fire exactly once');
      if (!matches(value, observation)) throw new Error('crash observation does not match the armed exact causal boundary');
      kill(value.serverPid, 'SIGKILL'); fired = true;
      return Object.freeze({ runId: value.runId, boundary: value.boundary, owner: value.owner,
        payloadType: value.payloadType, serverPid: value.serverPid, revision: observation.revision,
        ...(value.expectedAuthorityEpoch === undefined ? {} : { authorityEpoch: observation.authorityEpoch }) });
    }
  });
}

function matches(expected, observed) {
  return observed && expected.runId === observed.runId && expected.boundary === observed.boundary
    && expected.owner === observed.owner && expected.payloadType === observed.payloadType
    && (expected.expectedRevision === undefined || expected.expectedRevision === observed.revision)
    && (expected.expectedAuthorityEpoch === undefined || expected.expectedAuthorityEpoch === observed.authorityEpoch);
}
