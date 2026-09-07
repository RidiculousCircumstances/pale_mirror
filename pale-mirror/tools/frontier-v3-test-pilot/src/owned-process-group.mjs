import { awaitWithin } from './deadline-watchdog.mjs';

/**
 * A runner-created child remains its cleanup responsibility after a failed
 * graceful stop even when Minecraft has already released the game listener.
 * The listener is an observation, not proof that the JVM has exited: RCON and
 * world-save threads can still be live. Callers must supply their exact child;
 * this function never authorizes process discovery.
 */
export function requiresExactChildTerminationAfterGracefulFailure({ gracefulStopFailed, childExited }) {
  if (typeof gracefulStopFailed !== 'boolean' || typeof childExited !== 'boolean') {
    throw new Error('owned child cleanup disposition is malformed');
  }
  return gracefulStopFailed && !childExited;
}

/**
 * Stops only a runner-created detached process group.  Native launchers can fork a JVM beneath
 * their Node wrapper, so killing the wrapper PID alone is not cleanup ownership.
 */
export async function terminateOwnedProcessGroup(child, { label, gracefulTimeoutMs = 10_000, forcedTimeoutMs = 10_000 } = {}) {
  if (!child || !Number.isSafeInteger(child.pid) || child.pid <= 1 || typeof label !== 'string' || label === '') {
    throw new Error('owned process-group cleanup is malformed');
  }
  if (process.platform !== 'linux') throw new Error('owned process-group cleanup is unavailable on this platform');
  if (child.exitCode !== null || child.signalCode !== null) return Object.freeze({ signal: null, forced: false });
  try { process.kill(-child.pid, 'SIGTERM'); }
  catch (error) { if (error?.code !== 'ESRCH') throw new Error(`${label} process group could not receive SIGTERM`, { cause: error }); }
  try {
    await awaitWithin(exited(child), gracefulTimeoutMs, `${label} process group did not terminate after SIGTERM`);
    return Object.freeze({ signal: 'SIGTERM', forced: false });
  } catch (gracefulFailure) {
    try { process.kill(-child.pid, 'SIGKILL'); }
    catch (error) { if (error?.code !== 'ESRCH') throw new Error(`${label} process group could not receive SIGKILL`, { cause: error }); }
    await awaitWithin(exited(child), forcedTimeoutMs, `${label} process group did not terminate after SIGKILL`);
    return Object.freeze({ signal: 'SIGKILL', forced: true, gracefulFailure: String(gracefulFailure.message) });
  }
}

function exited(child) {
  return child.exitCode !== null || child.signalCode !== null
    ? Promise.resolve(child.exitCode ?? 1)
    : new Promise((resolveExit) => child.once('exit', (code) => resolveExit(code ?? 1)));
}
