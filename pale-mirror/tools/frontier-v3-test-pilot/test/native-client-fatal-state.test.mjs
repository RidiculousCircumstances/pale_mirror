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

test('unrelated output cannot terminate the native consumer', () => {
  const detector = createEarlyDisplayFailureDetector();
  assert.equal(detector.observe('[EARLYDISPLAY/]: rendering progress'), null);
  assert.equal(detector.observe('glfwInit failed in an unrelated tool'), null);
});
