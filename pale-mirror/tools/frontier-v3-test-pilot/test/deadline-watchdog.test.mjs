import assert from 'node:assert/strict';
import { EventEmitter } from 'node:events';
import test from 'node:test';
import { abortSignalFailure, awaitChildExit, awaitWithin, childExitCancellation, childExitWatch, deadlineWatchdog } from '../src/deadline-watchdog.mjs';

test('a completed protocol transition closes its watchdog without weakening the deadline', (context) => {
  context.mock.timers.enable({ apis: ['setTimeout'] });
  const watchdog = deadlineWatchdog(20, 'must fail when left open');
  watchdog.close();
  context.mock.timers.tick(35);
  assert.equal(watchdog.closed(), true);
});

test('an unclosed watchdog remains an attributable deadline failure', async (context) => {
  context.mock.timers.enable({ apis: ['setTimeout'] });
  const watchdog = deadlineWatchdog(5, 'named protocol deadline');
  const rejected = assert.rejects(watchdog.wait, /named protocol deadline/);
  context.mock.timers.tick(5);
  await rejected;
  assert.equal(watchdog.closed(), false);
  watchdog.close();
});

test('awaitWithin closes the losing timeout after a successful protocol operation', async (context) => {
  context.mock.timers.enable({ apis: ['setTimeout'] });
  assert.equal(await awaitWithin(Promise.resolve('arrived'), 20, 'unexpected deadline'), 'arrived');
  context.mock.timers.tick(35);
});

test('awaitWithin retains the named deadline when its protocol operation never arrives', async (context) => {
  context.mock.timers.enable({ apis: ['setTimeout'] });
  const rejected = assert.rejects(awaitWithin(new Promise(() => {}), 5, 'exact lifecycle deadline'), /exact lifecycle deadline/);
  context.mock.timers.tick(5);
  await rejected;
});

test('a serial native barrier removes its child-exit watcher once the barrier resolves', () => {
  const child = new EventEmitter();
  child.exitCode = null; child.signalCode = null;
  const cancellation = childExitCancellation(child);
  assert.equal(child.listenerCount('exit'), 1);
  cancellation.close();
  assert.equal(cancellation.closed(), true);
  assert.equal(child.listenerCount('exit'), 0);
  child.emit('exit', 1);
  assert.equal(cancellation.signal.aborted, false);
});

test('a child exit aborts only the currently registered barrier', () => {
  const child = new EventEmitter();
  child.exitCode = null; child.signalCode = null;
  const cancellation = childExitCancellation(child);
  child.emit('exit', 17);
  assert.equal(cancellation.signal.aborted, true);
  assert.equal(cancellation.signal.reason, 17);
  cancellation.close();
  assert.equal(child.listenerCount('exit'), 0);
});

test('a child-exit watch releases the listener when a lifecycle signal wins its race', async () => {
  const child = new EventEmitter();
  child.exitCode = null; child.signalCode = null;
  const exit = childExitWatch(child);
  assert.equal(child.listenerCount('exit'), 1);
  assert.equal(await Promise.race([Promise.resolve('acknowledged'), exit.wait]), 'acknowledged');
  exit.close();
  assert.equal(exit.closed(), true);
  assert.equal(child.listenerCount('exit'), 0);
});

test('a bounded child-exit wait closes both timeout and listener on success or timeout', async (context) => {
  context.mock.timers.enable({ apis: ['setTimeout'] });
  const success = new EventEmitter();
  success.exitCode = null; success.signalCode = null;
  const arrived = awaitChildExit(success, 20, 'unexpected child deadline');
  success.exitCode = 0; success.emit('exit', 0);
  assert.equal(await arrived, 0);
  assert.equal(success.listenerCount('exit'), 0);

  const timeout = new EventEmitter();
  timeout.exitCode = null; timeout.signalCode = null;
  const rejected = assert.rejects(awaitChildExit(timeout, 5, 'exact child deadline'), /exact child deadline/);
  context.mock.timers.tick(5);
  await rejected;
  assert.equal(timeout.listenerCount('exit'), 0);
});

test('an abort-backed terminal wait fails on its exact child exit and closes its listener', async () => {
  const controller = new AbortController();
  const failure = abortSignalFailure(controller.signal, 'terminal client exit');
  controller.abort(23);
  await assert.rejects(failure.wait, /terminal client exit \(23\)/);
  assert.equal(failure.closed(), true);
  failure.close();
});
