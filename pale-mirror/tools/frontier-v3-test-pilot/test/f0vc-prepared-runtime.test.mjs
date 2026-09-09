import assert from 'node:assert/strict';
import test from 'node:test';
import { createHash } from 'node:crypto';
import { chmod, mkdir, mkdtemp, readFile, readdir, rm, stat, writeFile } from 'node:fs/promises';
import { dirname, join, relative } from 'node:path';
import { copyConsumerView, prepareRuntime, requireRuntimeSourceIdentity } from '../src/f0vc-prepared-runtime.mjs';

const TASK_TEMP_PREFIX = '/home/rd/proj/pm-f0vc-runtime-test-';

test('F0.VC consumer views are private reflink-or-copy files: a consumer mutation cannot reach the store or a sibling', async (context) => {
  const root = await mkdtemp(TASK_TEMP_PREFIX); context.after(() => removeTaskRoot(root));
  const producer = join(root, 'producer.jar'); const first = join(root, 'first.jar'); const second = join(root, 'second.jar');
  await writeFile(producer, 'immutable-store');
  await copyConsumerView(producer, first, false); await copyConsumerView(producer, second, false);
  assert.notEqual((await stat(producer)).ino, (await stat(first)).ino, 'consumer must not hard-link the producer');
  assert.notEqual((await stat(first)).ino, (await stat(second)).ino, 'consumer views must not share an inode');
  await chmod(first, 0o644); await writeFile(first, 'first-consumer-mutation');
  assert.equal(await readFile(producer, 'utf8'), 'immutable-store');
  assert.equal(await readFile(second, 'utf8'), 'immutable-store');
});

test('F0.VC rejects a prepared runtime when selected source identity is invalidated before launch', async () => {
  const root = new URL('../../../', import.meta.url).pathname;
  const actual = await requireRuntimeSourceIdentity(await (await import('../src/prepared-build.mjs')).fingerprintPreparedSource(root), root);
  await assert.rejects(requireRuntimeSourceIdentity({ ...actual, sha256: '0'.repeat(64) }, root), /source\/test\/workflow identity drifted/);
});

test('F0.VC production preparation collects the declared task-private Gradle root and rejects unsafe roots or external launch members', async (context) => {
  const positive = await runtimeFixture(context);
  const runtime = await prepareRuntime({ store: positive.store, output: 'build/runtime.json', prepared: positive.identity,
    projectRoot: positive.project, environment: { GRADLE_USER_HOME: positive.gradle } });
  const manifest = JSON.parse(await readFile(join(positive.store, 'prepared', runtime.contentSha256, 'manifest.json'), 'utf8'));
  assert.ok(manifest.inputs.some((entry) => entry.target === '.f0vc-runtime/gradle/caches/modules-2/example.jar'));

  const missing = await runtimeFixture(context, { createGradle: false });
  await assert.rejects(prepareRuntime({ store: missing.store, output: 'build/runtime.json', prepared: missing.identity,
    projectRoot: missing.project, environment: { GRADLE_USER_HOME: missing.gradle } }), /missing or not a directory/);
  const relativeRoot = await runtimeFixture(context);
  await assert.rejects(prepareRuntime({ store: relativeRoot.store, output: 'build/runtime.json', prepared: relativeRoot.identity,
    projectRoot: relativeRoot.project, environment: { GRADLE_USER_HOME: 'producer-gradle' } }), /must be an absolute declared path/);
  const filesystemRoot = await runtimeFixture(context);
  await assert.rejects(prepareRuntime({ store: filesystemRoot.store, output: 'build/runtime.json', prepared: filesystemRoot.identity,
    projectRoot: filesystemRoot.project, environment: { GRADLE_USER_HOME: '/' } }), /must not be the filesystem root/);
  const outside = await runtimeFixture(context, { externalLaunchMember: true });
  await assert.rejects(prepareRuntime({ store: outside.store, output: 'build/runtime.json', prepared: outside.identity,
    projectRoot: outside.project, environment: { GRADLE_USER_HOME: outside.gradle } }), /escapes the declared project or Gradle runtime roots/);
});

async function runtimeFixture(context, { createGradle = true, externalLaunchMember = false } = {}) {
  const root = await mkdtemp('/home/rd/proj/pm-f0vc-runtime-root-');
  context.after(() => removeTaskRoot(root));
  const project = join(root, 'producer', 'pale-mirror'); const store = join(root, 'store'); const gradle = join(root, 'producer-gradle');
  const write = async (path, value = path) => { await mkdir(dirname(path), { recursive: true }); await writeFile(path, value); };
  for (const path of ['gradle/wrapper/gradle-wrapper.properties', 'gradle.properties', 'settings.gradle', 'build.gradle']) await write(join(project, path), path);
  const artifact = join(project, 'pale-mirror-neoforge/build/libs/pale_mirror.jar'); const mod = join(project, 'mods/pilot.jar');
  const argumentsRoot = join(project, 'args'); const serverVm = join(argumentsRoot, 'server-vm.txt'); const serverProgram = join(argumentsRoot, 'server-program.txt');
  const serverClasspath = join(argumentsRoot, 'server-classpath.txt'); const clientVm = join(argumentsRoot, 'client-vm.txt');
  const clientProgram = join(argumentsRoot, 'client-program.txt'); const clientClasspath = join(argumentsRoot, 'client-classpath.txt');
  for (const path of [artifact, mod, serverVm, serverProgram, serverClasspath, clientVm, clientProgram, clientClasspath]) await write(path);
  const gradleMember = join(gradle, 'caches/modules-2/example.jar'); if (createGradle) await write(gradleMember, 'gradle-member');
  const external = join(root, 'producer-gradle-sibling/external.jar'); if (externalLaunchMember) await write(external, 'external-member');
  const launch = { schema: 1, modFolders: `mods%%${mod}`,
    server: { vmArgs: relative(project, serverVm), programArgs: relative(project, serverProgram), classpath: relative(project, serverClasspath), launchClasspath: [externalLaunchMember ? external : gradleMember] },
    client: { vmArgs: relative(project, clientVm), programArgs: relative(project, clientProgram), classpath: relative(project, clientClasspath), launchClasspath: [externalLaunchMember ? external : gradleMember] } };
  const launchPath = join(project, 'pale-mirror-neoforge/build/moddev/frontierV3PilotPreparedLaunch.json'); await write(launchPath, JSON.stringify(launch));
  const digest = (value) => createHash('sha256').update(value).digest('hex'); const hash = (value) => digest(Buffer.from(value));
  const portableClasspaths = { entries: 1, portableSha256: 'a'.repeat(64), launchEntries: 1, portableLaunchSha256: 'b'.repeat(64) };
  const launchInputs = { vmArgs: { portableSha256: 'c'.repeat(64) }, programArgs: { portableSha256: 'd'.repeat(64) } };
  return { project, store, gradle, identity: { sourceContent: { sha256: 'e'.repeat(64) }, preparedArtifact: { path: relative(project, artifact), sha256: hash('artifact') },
    launchManifest: { path: relative(project, launchPath), sha256: hash(JSON.stringify(launch)) }, classpaths: { server: portableClasspaths, client: portableClasspaths },
    launchInputs: { server: launchInputs, client: launchInputs } } };
}

async function removeTaskRoot(root) {
  const writable = async (path) => {
    const value = await stat(path);
    if (value.isDirectory()) {
      for (const entry of await readdir(path)) await writable(join(path, entry));
      await chmod(path, 0o700);
    } else await chmod(path, 0o600);
  };
  await writable(root); await rm(root, { recursive: true, force: true });
}
