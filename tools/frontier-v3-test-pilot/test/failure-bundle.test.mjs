import assert from 'node:assert/strict';
import { mkdtemp, mkdir, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import test from 'node:test';
import { writeFailureBundle } from '../src/failure-bundle.mjs';

test('failure bundle retains bounded causal evidence and names the preserved world', async () => {
  const project = await mkdtemp(join(tmpdir(), 'pmv3-failure-bundle-'));
  try {
    await mkdir(join(project, 'build/profiles'), { recursive: true });
    await mkdir(join(project, 'world'), { recursive: true });
    await mkdir(join(project, 'lifecycle/events'), { recursive: true });
    await mkdir(join(project, 'lifecycle/signals'), { recursive: true });
    await writeFile(join(project, 'lifecycle/identity.json'), '{"schema":1}\n');
    await writeFile(join(project, 'lifecycle/events/0001-server_run_ready.json'), '{"barrier":"server_run_ready"}\n');
    await writeFile(join(project, 'scenario.json'), '{"schema":1}\n');
    await writeFile(join(project, 'server.log'), 'x'.repeat(40_000));
    await writeFile(join(project, 'trace.jsonl'), '{"source":"PMV3"}\n');
    await writeFile(join(project, 'client.log'), 'client tail\n');
    const directory = await writeFailureBundle({ project, output: 'build/profiles/run.json', scenarioPath: join(project, 'scenario.json'),
      runId: '00000000-0000-0000-0000-000000000001', timing: { totalMillis: 1 }, failure: new Error('expected'),
      serverLog: join(project, 'server.log'), tracePath: join(project, 'trace.jsonl'), worldDirectory: join(project, 'world'),
      process: { serverPid: 42 }, clientLog: join(project, 'client.log'), build: { sourceCommit: 'abc' },
      crash: { boundary: 'hot_checkpoint_durable_before_release' }, decodedWalTail: [{ sequence: 7, revision: 9 }],
      termination: { portClosed: true }, diagnosticSnapshots: [{ value: { kind: 'process', id: 'job:test', revision: 9, status: 'ok',
        family: 'frontier.test', identity: { job: 'job:test', worker: 'resident:test' }, claims: { intent: 'intent:test', lease: { id: 'lease:test' } },
        conservation: { outputItem: 'item:test', completedCropSlots: 3 }, schedule: { count: 1 }, cursor: { index: 3 },
        result: { sitePhase: 'HARVESTING', intentStatus: 'PREPARED' } } }], lifecycleDirectory: join(project, 'lifecycle') });
    const bundle = JSON.parse(await readFile(join(directory, 'bundle.json'), 'utf8'));
    assert.equal(bundle.kind, 'frontier-v3-failure-bundle');
    assert.equal(bundle.retainedWorld, 'world');
    assert.equal(bundle.process.serverPid, 42);
    assert.equal(bundle.build.sourceCommit, 'abc');
    assert.equal(bundle.process.crash.boundary, 'hot_checkpoint_durable_before_release');
    assert.deepEqual(bundle.artifacts.decodedWalTail, [{ sequence: 7, revision: 9 }]);
    assert.equal(bundle.artifacts.clientLog.tail, 'client tail\n');
    assert.equal(bundle.artifacts.serverLog.truncated, true);
    assert.ok(bundle.artifacts.serverLog.tail.length <= 32 * 1024);
    assert.equal(bundle.semantic.snapshots[0].identity.job, 'job:test');
    assert.equal(bundle.semantic.snapshots[0].claims.lease.id, 'lease:test');
    assert.equal(bundle.semantic.snapshots[0].conservation.completedCropSlots, 3);
    assert.equal(bundle.artifacts.lifecycle.events[0].value.barrier, 'server_run_ready');

    const inlineDirectory = await writeFailureBundle({ project, output: 'build/profiles/inline-run.json', scenarioPath: join(project, 'scenario.json'),
      runId: '00000000-0000-0000-0000-000000000002', timing: { totalMillis: 1 }, failure: new Error('inline expected'),
      serverLogText: 'server inline tail\n', clientLogText: 'client inline tail\n' });
    const inline = JSON.parse(await readFile(join(inlineDirectory, 'bundle.json'), 'utf8'));
    assert.deepEqual(inline.artifacts.serverLog, { inline: true, tail: 'server inline tail\n', truncated: false });
    assert.deepEqual(inline.artifacts.clientLog, { inline: true, tail: 'client inline tail\n', truncated: false });
  } finally { await rm(project, { recursive: true, force: true }); }
});
