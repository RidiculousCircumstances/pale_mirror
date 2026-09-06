import { spawn } from 'node:child_process';
import { access, readdir } from 'node:fs/promises';
import { isAbsolute, join } from 'node:path';
import { awaitWithin } from './deadline-watchdog.mjs';

const XWAYLAND_AUTHORITY = /^\.mutter-Xwaylandauth\.[A-Za-z0-9]+$/;

/**
 * Selects only the exact current Xwayland authority.  A visible native test must either prove
 * it can create its requested window or fail before it starts a server/client matrix; hanging
 * in GLFW is neither actionable evidence nor a legitimate readiness condition.
 */
export function resolveVisibleDisplayAuthority({ display, xauthority = '', runtimeDirectory, entries }) {
  if (display !== ':0') throw new Error('native visible pilot requires DISPLAY=:0');
  if (typeof xauthority === 'string' && xauthority !== '') {
    if (!isAbsolute(xauthority)) throw new Error('visible pilot XAUTHORITY is not absolute');
    return xauthority;
  }
  if (typeof runtimeDirectory !== 'string' || !isAbsolute(runtimeDirectory) || !Array.isArray(entries)) {
    throw new Error('visible pilot Xwayland runtime directory is unavailable');
  }
  const candidates = entries.filter((entry) => XWAYLAND_AUTHORITY.test(entry));
  if (candidates.length !== 1) throw new Error('visible pilot Xwayland authority is ambiguous or absent');
  return join(runtimeDirectory, candidates[0]);
}

/** Returns an authenticated environment only after a bounded real Xwayland preflight. */
export async function verifiedVisibleDisplayEnvironment(environment = process.env) {
  const runtimeDirectory = environment.XDG_RUNTIME_DIR || `/run/user/${process.getuid()}`;
  // A caller with an explicit authority must not depend on discovery of another desktop's
  // runtime directory. Discovery is only the bounded fallback for an unconfigured runner.
  const entries = environment.XAUTHORITY ? undefined : await readdir(runtimeDirectory);
  const authority = resolveVisibleDisplayAuthority({ display: environment.DISPLAY, xauthority: environment.XAUTHORITY,
    runtimeDirectory, entries });
  await access(authority);
  const probe = spawn('xdpyinfo', ['-display', ':0'], { env: { ...environment, DISPLAY: ':0', XAUTHORITY: authority }, stdio: 'ignore' });
  const code = await awaitWithin(exited(probe), 10_000, 'visible pilot Xwayland preflight did not finish');
  if (code !== 0) throw new Error('visible pilot Xwayland preflight was rejected');
  return Object.freeze({ ...environment, DISPLAY: ':0', XAUTHORITY: authority });
}

function exited(child) {
  return child.exitCode !== null || child.signalCode !== null
    ? Promise.resolve(child.exitCode ?? 1)
    : new Promise((resolveExit) => child.once('exit', (code) => resolveExit(code ?? 1)));
}
