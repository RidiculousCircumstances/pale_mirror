import assert from 'node:assert/strict';
import { mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import test from 'node:test';
import { DISPOSABLE_SEMANTIC_CLIENT_FML_CONFIG, ensurePreparedLaunchWorkingDirectory } from '../src/prepared-launch.mjs';

test('the disposable semantic client disables only NeoForge early splash before its final graphics launch', async () => {
  const root = await mkdtemp(join(process.env.PALE_MIRROR_TEST_TMP ?? tmpdir(), 'pm-semantic-client-'));
  try {
    const launch = { cwd: root, args: ['--fullscreen', '--quickPlayMultiplayer', '127.0.0.1:26204'] };
    await ensurePreparedLaunchWorkingDirectory(launch, { automatedSemanticClient: true });
    assert.equal(await readFile(join(root, 'config/fml.toml'), 'utf8'), DISPOSABLE_SEMANTIC_CLIENT_FML_CONFIG);
    assert.ok(launch.args.includes('--fullscreen'), 'the actual final Minecraft graphics window remains required');
    const runner = await readFile(resolve(import.meta.dirname, '../src/run-scenario.mjs'), 'utf8');
    assert.match(runner, /ensurePreparedLaunchWorkingDirectory\(launch, \{ automatedSemanticClient: true \}\)/,
      'the actual prepared semantic-client launcher applies this test-only configuration');
    await writeFile(join(root, 'config/fml.toml'), 'earlyWindowControl = true\n');
    await ensurePreparedLaunchWorkingDirectory(launch, { automatedSemanticClient: true });
    assert.equal(await readFile(join(root, 'config/fml.toml'), 'utf8'), DISPOSABLE_SEMANTIC_CLIENT_FML_CONFIG,
      'a stale generated FML default cannot re-enable the disposable splash');
  } finally { await rm(root, { recursive: true, force: true }); }
});
