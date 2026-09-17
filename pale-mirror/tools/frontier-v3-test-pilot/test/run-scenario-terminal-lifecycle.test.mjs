import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

test('terminal lifecycle close is fenced by the published segment journal rather than a late stdout queue', async () => {
  const runner = await readFile(new URL('../src/run-scenario.mjs', import.meta.url), 'utf8');
  assert.match(runner, /awaitLifecycleSignal\(lifecycle, LifecycleSignal\.SCENARIO_SEGMENT_COMPLETE, segment, 300_000\)/,
    'the final close must first authenticate the exact client segment signal');
  assert.match(runner, /awaitLifecycleBarrier\(lifecycle, LifecycleBarrier\.SCENARIO_SEGMENT_COMPLETE, 300_000,[\s\S]*entry => entry\.detail\.segment === segment\)/,
    'the final close must then require the supervisor-owned monotonic segment barrier');
  assert.match(runner, /awaitLifecycleBarrier\(lifecycle, LifecycleBarrier\.TERMINAL_ASSERTION_COMPLETE, 300_000/,
    'the close token cannot precede terminal assertion publication');
});
