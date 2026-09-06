import assert from 'node:assert/strict';
import test from 'node:test';
import { mkdtemp, mkdir, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { digestOpaqueDirectory } from '../src/development-fixture-image.mjs';
import { prepareFixtureConsumerRuntime } from '../src/development-fixture-runtime.mjs';

test('fixture consumer runtime writes only server process configuration and never edits the allocated world', async (context) => {
  const project = await mkdtemp(join(tmpdir(), 'pmv3-fixture-runtime-')); context.after(() => rm(project, { recursive: true, force: true }));
  const world = join(project, 'pale-mirror-neoforge/build/runs/frontier-v3-pilot-server/fixture-world');
  await mkdir(join(world, 'frontier-v3'), { recursive: true }); await writeFile(join(world, 'frontier-v3/snapshot.bin'), 'opaque-world');
  const before = await digestOpaqueDirectory(project, 'pale-mirror-neoforge/build/runs/frontier-v3-pilot-server/fixture-world');
  const runtime = await prepareFixtureConsumerRuntime({ project, worldName: 'fixture-world', port: 25577, rconPort: 25578,
    rconPassword: '7a7d1f7a-f73e-4e70-8a5b-c9f4e6bca083', username: 'PMTestPilot', viewDistance: 10 });
  const after = await digestOpaqueDirectory(project, 'pale-mirror-neoforge/build/runs/frontier-v3-pilot-server/fixture-world');
  assert.deepEqual(after, before); assert.equal(runtime.worldName, 'fixture-world');
  const properties = await readFile(join(project, 'pale-mirror-neoforge/build/runs/frontier-v3-pilot-server/server.properties'), 'utf8');
  assert.match(properties, /level-name=fixture-world/); assert.match(properties, /server-port=25577/);
  assert.match(await readFile(join(project, 'pale-mirror-neoforge/build/runs/frontier-v3-pilot-server/ops.json'), 'utf8'), /PMTestPilot/);
  await assert.rejects(prepareFixtureConsumerRuntime({ project, worldName: 'missing-world', port: 25577, rconPort: 25578,
    rconPassword: '7a7d1f7a-f73e-4e70-8a5b-c9f4e6bca083', username: 'PMTestPilot', viewDistance: 10 }), /unavailable/);
});
