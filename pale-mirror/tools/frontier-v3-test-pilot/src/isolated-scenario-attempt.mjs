import { randomUUID } from 'node:crypto';

/**
 * Narrow supervisor-only correlation input. It is never forwarded to the
 * scenario declaration, fixture selection, or Minecraft launch arguments.
 */
export const ISOLATED_SCENARIO_OUTER_ATTEMPT_ENV = 'FRONTIER_V3_ISOLATED_OUTER_ATTEMPT';
export const ISOLATED_SCENARIO_OUTER_ATTEMPT_ADMISSION_ONLY_ENV = 'FRONTIER_V3_ISOLATED_OUTER_ATTEMPT_ADMISSION_ONLY';

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

function uuid(value) {
  return /^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/.test(value);
}
