/**
 * Runs every bounded cleanup action before writing terminal native-failure
 * evidence. A failed cleanup is evidence too, but it must never skip the
 * failure bundle for the causal failure already in flight.
 */
export async function finalizeFailurePath({ originalFailure = null, cleanup, emitFailureBundle }) {
  if (typeof cleanup !== 'function' || typeof emitFailureBundle !== 'function') {
    throw new Error('terminal failure finalization is malformed');
  }
  const cleanupFailures = [];
  const attempt = async (label, operation) => {
    if (typeof label !== 'string' || label === '' || typeof operation !== 'function') {
      throw new Error('terminal cleanup attempt is malformed');
    }
    try {
      await operation();
      return true;
    } catch (error) {
      cleanupFailures.push(Object.freeze({ label, error }));
      return false;
    }
  };
  try {
    await cleanup(attempt);
  } catch (error) {
    cleanupFailures.push(Object.freeze({ label: 'cleanup_orchestration', error }));
  }
  const terminalFailure = originalFailure ?? cleanupFailures[0]?.error ?? null;
  let bundle = null;
  let bundleFailure = null;
  if (terminalFailure !== null) {
    try {
      bundle = await emitFailureBundle(Object.freeze({ terminalFailure, cleanupFailures: Object.freeze([...cleanupFailures]) }));
    } catch (error) {
      // The caller still receives its original causal failure. Logging the bundle-writer failure
      // preserves that causal ordering instead of letting a diagnostic write hide it.
      bundleFailure = error;
      console.error(`PMV3_ISOLATED failure_bundle_write_failed=${String(error?.stack ?? error)}`);
    }
  }
  return Object.freeze({ terminalFailure, cleanupFailures: Object.freeze([...cleanupFailures]), bundle, bundleFailure });
}

export function boundedCleanupFailures(cleanupFailures) {
  if (!Array.isArray(cleanupFailures)) throw new Error('terminal cleanup failures must be an array');
  return cleanupFailures.slice(0, 8).map(({ label, error }) => Object.freeze({
    label,
    message: String(error?.message ?? error)
  }));
}
