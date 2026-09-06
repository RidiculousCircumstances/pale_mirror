import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import test from 'node:test';
import { terminateOwnedProcessGroup } from '../src/owned-process-group.mjs';

test('owned detached cleanup terminates the wrapper and its native descendant', { skip: process.platform !== 'linux' }, async () => {
  const wrapper = spawn('/bin/sh', ['-c', 'sleep 300 & echo $!; wait'], { detached: true, stdio: ['ignore', 'pipe', 'ignore'] });
  const childPid = Number((await onceLine(wrapper.stdout)).trim());
  assert.ok(Number.isSafeInteger(childPid) && childPid > 1);
  const result = await terminateOwnedProcessGroup(wrapper, { label: 'owned-process-group-test', gracefulTimeoutMs: 2_000, forcedTimeoutMs: 2_000 });
  assert.equal(result.forced, false);
  await new Promise((resolve) => setTimeout(resolve, 20));
  assert.throws(() => process.kill(childPid, 0), { code: 'ESRCH' });
});

function onceLine(stream) {
  return new Promise((resolve, reject) => {
    let buffered = '';
    stream.setEncoding('utf8');
    stream.on('data', (chunk) => {
      buffered += chunk;
      const newline = buffered.indexOf('\n');
      if (newline >= 0) resolve(buffered.slice(0, newline));
    });
    stream.once('error', reject);
  });
}
