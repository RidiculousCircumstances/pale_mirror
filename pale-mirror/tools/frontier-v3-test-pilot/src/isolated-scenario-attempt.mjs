import { randomUUID } from 'node:crypto';

/**
 * Narrow supervisor-only correlation input. It is never forwarded to the
 * scenario declaration, fixture selection, or Minecraft launch arguments.
 */
export const ISOLATED_SCENARIO_OUTER_ATTEMPT_ENV = 'FRONTIER_V3_ISOLATED_OUTER_ATTEMPT';

export function resolveIsolatedScenarioOuterAttempt(value) {
  if (value === undefined) return randomUUID();
  if (typeof value !== 'string' || !uuid(value)) {
    throw new Error(`${ISOLATED_SCENARIO_OUTER_ATTEMPT_ENV} must be one canonical UUID when declared`);
  }
  return value;
}

/** The persistent restart receipt retains the supervisor's outer attempt, never the client action run. */
export function persistentRecoveryClientSession(runId) {
  return Object.freeze({ runId: resolveIsolatedScenarioOuterAttempt(runId), reusedJvm: true });
}

/** The ordinary wrapper may pass only the validated correlation attempt to its supervisor. */
export function isolatedScenarioOuterAttemptEnvironment(runId) {
  return Object.freeze({ [ISOLATED_SCENARIO_OUTER_ATTEMPT_ENV]: resolveIsolatedScenarioOuterAttempt(runId) });
}

/** The ordinary persistent-restart path retains the same supervisor attempt in its final manifest metadata. */
export function persistentRecoveryMetadata({ mode, world, splitAfterAction, beforeRestartManifest, runId, controlDirectory }) {
  return Object.freeze({ mode, world, splitAfterAction,
    ...(beforeRestartManifest === undefined ? {} : { beforeRestartManifest }),
    clientSession: Object.freeze({ ...persistentRecoveryClientSession(runId), controlDirectory }) });
}

function uuid(value) {
  return /^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/.test(value);
}
