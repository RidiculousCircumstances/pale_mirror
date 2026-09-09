import assert from 'node:assert/strict';
import test from 'node:test';
import { createEarlyDisplayFailureDetector } from '../src/native-client-fatal-state.mjs';

test('the native client consumer rejects the exact NeoForge early-display fatal state immediately', () => {
  const detector = createEarlyDisplayFailureDetector();
  assert.equal(detector.observe('[main/INFO] ordinary launcher line'), null);
  assert.equal(detector.observe('[main/ERROR] [EARLYDISPLAY/]: ERROR DISPLAY'), null);
  assert.equal(detector.observe('Failed to initialize the graphics system.'), null);
  assert.match(detector.observe('glfwInit failed.'), /early display initialization failed/);
});

test('an unrelated GLFW mention cannot terminate the native consumer', () => {
  const detector = createEarlyDisplayFailureDetector();
  assert.equal(detector.observe('glfwInit failed in an unrelated tool'), null);
});
