#!/usr/bin/env node
/**
 * Exercises the Visuals compile-only resolver through ordinary Gradle task
 * invocations. A warm task followed by a same-path byte change must still be
 * rejected, so this deliberately never uses --rerun-tasks.
 */
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { appendFile, copyFile, mkdtemp, mkdir, unlink, writeFile } from 'node:fs/promises';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const project = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..');
const recurrenceRoot = join(project, 'build', 'pm-ci-compile-deps-01');
await mkdir(recurrenceRoot, { recursive: true });
const outputRoot = await mkdtemp(join(recurrenceRoot, 'warm-'));
const transcript = join(outputRoot, 'transcript.log');
const artifacts = [
  ['geckolib_integration_jar', 'geckolib-neoforge-1.21.1-4.9.2.jar', 'GeckoLib'],
  ['villager_overhaul_integration_jar', 'villageroverhaul-neoforge-1.21.1-3.10.17.16.jar', 'Villager Overhaul'],
  ['create_integration_jar', 'create-1.21.1-6.0.10.jar', 'Create']
].map(([property, filename, label]) => ({
  property,
  filename,
  label,
  defaultPath: join(project, 'pale-mirror-visuals', 'build', 'integration', filename)
}));

await mkdir(join(outputRoot, 'overrides'), { recursive: true });

async function run(overrides = []) {
  const args = [
    ':pale-mirror-visuals:resolveVisualCompileDependencies',
    '--no-daemon',
    ...overrides.flatMap(({ property, path }) => [`-P${property}=${path}`])
  ];
  const result = spawnSync('./gradlew', args, {
    cwd: project,
    encoding: 'utf8',
    env: {
      ...process.env,
      PALE_MIRROR_RUNTIME_MOD_DIR: join(outputRoot, 'missing-runtime-mods'),
      GRADLE_USER_HOME: join(outputRoot, 'gradle-user-home')
    }
  });
  const rendered = `$ ./gradlew ${args.join(' ')}\n${result.stdout}${result.stderr}`;
  await appendFile(transcript, `${rendered}\n`, 'utf8');
  return { ...result, rendered };
}

function requireSuccess(result, phase) {
  assert.equal(result.status, 0, `${phase} failed:\n${result.rendered}`);
  assert.doesNotMatch(result.rendered, /UP-TO-DATE/, `${phase} skipped verification`);
}

function requireRejected(result, artifact, phase) {
  assert.notEqual(result.status, 0, `${phase} accepted changed ${artifact.label} bytes`);
  assert.match(result.rendered, new RegExp(`Pinned ${artifact.label} (SHA-512 mismatch|JAR is missing)`));
}

async function mutate(path) {
  await appendFile(path, Buffer.from([0]));
}

try {
  requireSuccess(await run(), 'initial default resolution');
  for (const artifact of artifacts) {
    artifact.backupPath = join(outputRoot, `${artifact.filename}.backup`);
    artifact.overridePath = join(outputRoot, 'overrides', artifact.filename);
    await copyFile(artifact.defaultPath, artifact.backupPath);
    await copyFile(artifact.defaultPath, artifact.overridePath);
  }

  const overrides = artifacts.map(({ property, overridePath: path }) => ({ property, path }));
  requireSuccess(await run(overrides), 'warm override resolution');
  for (const artifact of artifacts) {
    await mutate(artifact.overridePath);
    requireRejected(await run(overrides), artifact, `same-path override mutation for ${artifact.label}`);
    await copyFile(artifact.backupPath, artifact.overridePath);
    requireSuccess(await run(overrides), `restored override resolution for ${artifact.label}`);
    await unlink(artifact.overridePath);
    requireRejected(await run(overrides), artifact, `same-path override removal for ${artifact.label}`);
    await copyFile(artifact.backupPath, artifact.overridePath);
    requireSuccess(await run(overrides), `restored override after removal for ${artifact.label}`);
  }

  for (const artifact of artifacts) {
    await mutate(artifact.defaultPath);
    requireRejected(await run(), artifact, `default cache mutation for ${artifact.label}`);
    await copyFile(artifact.backupPath, artifact.defaultPath);
    requireSuccess(await run(), `restored default resolution for ${artifact.label}`);
  }

  await writeFile(join(outputRoot, 'result.json'), `${JSON.stringify({
    outputRoot,
    artifacts: artifacts.map(({ property, filename }) => ({ property, filename })),
    assertions: 'all three override mutations/removals and default byte mutations were rejected by normal invocation without --rerun-tasks'
  }, null, 2)}\n`);
  process.stdout.write(`Visual compile dependency warm-cache recurrence passed; evidence: ${outputRoot}\n`);
} catch (error) {
  await appendFile(transcript, `HARNESS FAILURE\n${error.stack ?? error}\n`, 'utf8');
  throw error;
}
