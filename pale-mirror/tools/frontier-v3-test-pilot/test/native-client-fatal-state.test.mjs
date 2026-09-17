import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import test from 'node:test';
import { createEarlyDisplayFailureDetector } from '../src/native-client-fatal-state.mjs';

const project = resolve(import.meta.dirname, '../../..');

test('the actual native client output wait rejects NeoForge early-display termination immediately', async () => {
  const detector = createEarlyDisplayFailureDetector();
  assert.equal(detector.observe('[main/INFO] ordinary launcher line'), null);
  assert.match(detector.observe('[pool-2-thread-1/ERROR] [EARLYDISPLAY/]: ERROR DISPLAY'), /early display initialization failed/);
  const runner = await readFile(resolve(project, 'tools/frontier-v3-test-pilot/src/run-scenario.mjs'), 'utf8');
  assert.match(runner, /const fatalDisplay = earlyDisplayFailure\.observe\(line\);/);
  assert.match(runner, /if \(fatalDisplay !== null\) failure \?\?= fatalDisplay;/);
});

test('a timed-out native scenario fences its exact client and recovers the user XWayland authority', async () => {
  const runner = await readFile(resolve(project, 'tools/frontier-v3-test-pilot/src/run-scenario.mjs'), 'utf8');
  assert.match(runner, /await stopOwnedClient\(child\);/);
  assert.match(runner, /\{ signal: 'SIGINT', timeoutMs: 15_000 \}/);
  assert.match(runner, /\{ signal: 'SIGKILL', timeoutMs: 5_000 \}/);
  assert.match(runner, /import \{ verifiedPrivateDisplayEnvironment, verifiedVisibleDisplayEnvironment \} from '\.\/visible-display\.mjs';/);
  assert.match(runner, /const privateDisplay = process\.env\.FRONTIER_V3_PILOT_PRIVATE_DISPLAY === 'true';/);
  assert.match(runner, /\? await verifiedPrivateDisplayEnvironment\(process\.env\)\s+: await verifiedVisibleDisplayEnvironment\(process\.env\)/,
    'private carriers retain their verified Xvfb namespace while visible pilots retain exact Xwayland admission');
  assert.match(runner, /auditEnvironment\.XDG_SESSION_TYPE = 'x11';/);
  assert.match(runner, /auditEnvironment\.WAYLAND_DISPLAY = '__pale_mirror_pilot_xwayland_only__';/);
});

test('the GLFW admission probe can isolate a visible client window without starting a server', async () => {
  const admission = await readFile(resolve(project, 'tools/frontier-v3-test-pilot/src/run-pilot-glfw-admission.mjs'), 'utf8');
  assert.match(admission, /FRONTIER_V3_PILOT_VISIBLE_DISPLAY === 'true'/);
  assert.match(admission, /if \(!visible && !namespaceOwned\)/,
    'a visible probe must never attempt to allocate a competing Xvfb display');
  assert.match(admission, /await verifiedVisibleDisplayEnvironment\(\{ \.\.\.process\.env, DISPLAY: display \}\)/);
  assert.match(admission, /frontier-v3-pilot-\$\{visible \? 'visible' : 'private'\}-glfw-admission/,
    'the visible probe retains an explicit receipt category instead of borrowing private-Xvfb evidence');
});

test('unrelated output cannot terminate the native consumer', () => {
  const detector = createEarlyDisplayFailureDetector();
  assert.equal(detector.observe('[EARLYDISPLAY/]: rendering progress'), null);
  assert.equal(detector.observe('glfwInit failed in an unrelated tool'), null);
});
