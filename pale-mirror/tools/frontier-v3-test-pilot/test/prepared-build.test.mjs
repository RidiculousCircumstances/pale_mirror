import assert from 'node:assert/strict';
import { execFile } from 'node:child_process';
import test from 'node:test';
import { mkdtemp, mkdir, readFile, rm, stat, writeFile } from 'node:fs/promises';
import { promisify } from 'node:util';
import { tmpdir } from 'node:os';
import { resolve } from 'node:path';
import { fingerprintPreparedBuild, fingerprintPreparedSource, portablePreparedBuildIdentity, requirePreparedBuild, requirePreparedF0vBuild } from '../src/prepared-build.mjs';
import { ensurePreparedLaunchWorkingDirectory, preparedLaunch } from '../src/prepared-launch.mjs';

test('prepared build fingerprints both native classpaths and fails closed on drift', async () => {
  const monorepo = await mkdtemp(resolve(tmpdir(), 'pmv3-prepared-build-'));
  const root = resolve(monorepo, 'pale-mirror');
  try {
    const artifact = resolve(root, 'pale-mirror-neoforge/build/libs/pale_mirror-test.jar');
    const dependency = resolve(root, 'runtime/dependency.bin');
    const launchOnly = resolve(root, 'runtime/launch-only.bin');
    await mkdir(resolve(root, 'pale-mirror-neoforge/build/moddev'), { recursive: true });
    await mkdir(resolve(root, 'pale-mirror-neoforge/build/libs'), { recursive: true });
    await mkdir(resolve(root, 'pale-mirror-neoforge/build/runs/server'), { recursive: true });
    await mkdir(resolve(root, 'pale-mirror-neoforge/build/runs/client'), { recursive: true });
    await mkdir(resolve(root, 'runtime'), { recursive: true });
    await mkdir(resolve(root, 'pale-mirror-frontier/src/main'), { recursive: true });
    await mkdir(resolve(root, 'scripts'), { recursive: true });
    await mkdir(resolve(root, 'tools/frontier-v3-test-pilot/src'), { recursive: true });
    await mkdir(resolve(monorepo, '.github/workflows'), { recursive: true });
    await writeFile(resolve(root, '.gitignore'), 'pale-mirror-neoforge/build/\n');
    await writeFile(resolve(root, 'pale-mirror-frontier/src/main/Owner.java'), 'class Owner {}\n');
    await writeFile(resolve(root, 'tools/frontier-v3-test-pilot/src/runner.mjs'), 'export const version = 1;\n');
    await writeFile(resolve(root, 'scripts/with-private-xvfb.sh'), '#!/usr/bin/env bash\n# Xvfb v1\n');
    await writeFile(resolve(root, 'scripts/private-xvfb-glx-probe.py'), '#!/usr/bin/env python3\n# GLX v1\n');
    await writeFile(resolve(monorepo, '.github/workflows/build.yml'), 'name: build-v1\n');
    await writeFile(resolve(monorepo, '.github/workflows/f0va-native-correctness-sample.yml'), 'name: sample-v1\n');
    const exec = promisify(execFile);
    const environment = { ...process.env };
    delete environment.GIT_DIR; delete environment.GIT_WORK_TREE; delete environment.GIT_INDEX_FILE;
    await exec('git', ['init', '-q'], { cwd: monorepo, env: environment });
    await exec('git', ['add', '.'], { cwd: monorepo, env: environment });
    await exec('git', ['-c', 'user.name=test', '-c', 'user.email=test@example.invalid', 'commit', '-qm', 'prepared-source'], { cwd: monorepo, env: environment });
    await writeFile(artifact, 'artifact-v1'); await writeFile(dependency, 'classpath-v1'); await writeFile(launchOnly, 'launch-v1');
    const serverProgramArgs = resolve(root, 'pale-mirror-neoforge/build/moddev/frontierV3PilotServerRunProgramArgs.txt');
    const clientProgramArgs = resolve(root, 'pale-mirror-neoforge/build/moddev/frontierV3PilotClientRunProgramArgs.txt');
    const crashMixin = 'pale_mirror.frontier_v3.pilot_crash.mixins.json';
    const serverArguments = `--launchTarget\nunit\n--mixin.config\n${crashMixin}\n`;
    const clientArguments = '--launchTarget\nunit\n';
    for (const name of ['frontierV3PilotServerLegacyClasspath.txt', 'frontierV3PilotClientLegacyClasspath.txt']) {
      await writeFile(resolve(root, 'pale-mirror-neoforge/build/moddev', name), `${dependency}\n`);
    }
    for (const role of ['Server', 'Client']) {
      await writeFile(resolve(root, `pale-mirror-neoforge/build/moddev/frontierV3Pilot${role}RunVmArgs.txt`), '-Xmx1G\n');
    }
    await writeFile(serverProgramArgs, serverArguments);
    await writeFile(clientProgramArgs, clientArguments);
    await writeFile(resolve(root, 'pale-mirror-neoforge/build/moddev/frontierV3PilotPreparedLaunch.json'), JSON.stringify({
      schema: 1, java: '/prepared/java', modFolders: 'pale_mirror%%/prepared/classes',
      server: { vmArgs: 'pale-mirror-neoforge/build/moddev/frontierV3PilotServerRunVmArgs.txt',
        programArgs: 'pale-mirror-neoforge/build/moddev/frontierV3PilotServerRunProgramArgs.txt',
        classpath: 'pale-mirror-neoforge/build/moddev/frontierV3PilotServerLegacyClasspath.txt',
        launchClasspath: [dependency, launchOnly],
        gameDirectory: 'pale-mirror-neoforge/build/runs/server' },
      client: { vmArgs: 'pale-mirror-neoforge/build/moddev/frontierV3PilotClientRunVmArgs.txt',
        programArgs: 'pale-mirror-neoforge/build/moddev/frontierV3PilotClientRunProgramArgs.txt',
        classpath: 'pale-mirror-neoforge/build/moddev/frontierV3PilotClientLegacyClasspath.txt',
        launchClasspath: [dependency, launchOnly],
        gameDirectory: 'pale-mirror-neoforge/build/runs/client' }
    }));
    await writeFile(serverProgramArgs, '--launchTarget\nunit\n');
    await assert.rejects(() => fingerprintPreparedBuild(root, artifact), /pilot crash mixin/);
    await writeFile(serverProgramArgs, `${serverArguments}--mixin.config\n${crashMixin}\n`);
    await assert.rejects(() => fingerprintPreparedBuild(root, artifact), /pilot crash mixin/);
    await writeFile(serverProgramArgs, `--launchTarget\nunit\n--mixin.config\n--nogui\n${crashMixin}\n`);
    await assert.rejects(() => fingerprintPreparedBuild(root, artifact), /pilot crash mixin/);
    await writeFile(serverProgramArgs, '--launchTarget\nunit\n--mixin.config\nforeign.mixins.json\n');
    await assert.rejects(() => fingerprintPreparedBuild(root, artifact), /pilot crash mixin/);
    await writeFile(serverProgramArgs, '--launchTarget\nunit\n');
    await writeFile(clientProgramArgs, `${clientArguments}--mixin.config\n${crashMixin}\n`);
    await assert.rejects(() => fingerprintPreparedBuild(root, artifact), /pilot crash mixin/);
    await writeFile(serverProgramArgs, serverArguments);
    await writeFile(clientProgramArgs, clientArguments);
    const identity = Object.freeze({ sourceContent: await fingerprintPreparedSource(root), ...(await fingerprintPreparedBuild(root, artifact)) });
    assert.ok(identity.sourceContent.files.some((entry) => entry.path === 'monorepo/.github/workflows/build.yml'));
    assert.ok(identity.sourceContent.files.some((entry) => entry.path === 'scripts/with-private-xvfb.sh'));
    assert.ok(identity.sourceContent.files.some((entry) => entry.path === 'scripts/private-xvfb-glx-probe.py'));
    await exec('git', ['rm', '--cached', '.github/workflows/f0va-native-correctness-sample.yml'], { cwd: monorepo, env: environment });
    assert.ok((await exec('git', ['ls-files', '--others', '--exclude-standard'], { cwd: monorepo, env: environment })).stdout
      .split(/\r?\n/).includes('.github/workflows/f0va-native-correctness-sample.yml'));
    assert.deepEqual(await fingerprintPreparedSource(root), identity.sourceContent,
      'an untracked root workflow contributes its exact bytes rather than disappearing from identity');
    await requirePreparedBuild(root, identity);
    await requirePreparedF0vBuild(root, identity);
    const portable = portablePreparedBuildIdentity(identity);
    const relocatedLaunchInputs = structuredClone(identity);
    relocatedLaunchInputs.launchInputs.server.vmArgs.sha256 = 'f'.repeat(64);
    relocatedLaunchInputs.launchInputs.client.programArgs.sha256 = 'e'.repeat(64);
    assert.deepEqual(portablePreparedBuildIdentity(relocatedLaunchInputs), portable,
      'portable CI identity uses normalized launch content while the local identity retains raw paths');
    const relocated = structuredClone(identity);
    relocated.preparedArtifact.path = 'different-worker/pale_mirror-test.jar';
    relocated.launchManifest.path = 'different-worker/launch.json';
    for (const role of ['server', 'client']) {
      relocated.classpaths[role].manifest = `different-worker/${role}.txt`;
      relocated.launchInputs[role].vmArgs.path = `different-worker/${role}-vm.txt`;
      relocated.launchInputs[role].programArgs.path = `different-worker/${role}-program.txt`;
    }
    assert.deepEqual(portablePreparedBuildIdentity(relocated), portable,
      'portable CI identity retains launch content, not worker-local absolute locations');
    const launch = await preparedLaunch(root, identity, 'server', { 'pale_mirror.frontier_v3.pilot.run_id': 'nonce' });
    assert.equal(launch.command, '/prepared/java');
    assert.ok(launch.args.includes('-Dfml.modFolders=pale_mirror%%/prepared/classes'));
    assert.ok(launch.args.includes('net.neoforged.devlaunch.Main'));
    assert.ok(launch.args.includes('-Dpale_mirror.frontier_v3.pilot.run_id=nonce'));
    assert.equal(launch.cwd, resolve(root, 'pale-mirror-neoforge/build/runs/server'));
    const ownedClientDirectory = resolve(root, 'pale-mirror-neoforge/build/runs/direct-client');
    await ensurePreparedLaunchWorkingDirectory({ cwd: ownedClientDirectory });
    assert.ok((await stat(ownedClientDirectory)).isDirectory());
    assert.equal(await readFile(resolve(ownedClientDirectory, 'options.txt'), 'utf8'),
      'onboardAccessibility:b:true\nskipMultiplayerWarning:b:true\nnarrator:0\n',
      'a fresh private prepared client can Quick Play without a first-run screen');
    await writeFile(resolve(ownedClientDirectory, 'options.txt'), 'foreign-private-options\n');
    await ensurePreparedLaunchWorkingDirectory({ cwd: ownedClientDirectory });
    assert.equal(await readFile(resolve(ownedClientDirectory, 'options.txt'), 'utf8'), 'foreign-private-options\n',
      'consumer setup never adopts or overwrites an existing private client view');
    await assert.rejects(() => ensurePreparedLaunchWorkingDirectory({ cwd: 'relative-client' }), /malformed/);
    await writeFile(dependency, 'classpath-v2');
    await assert.rejects(() => requirePreparedBuild(root, identity), /hash drifted/);
    await writeFile(dependency, 'classpath-v1');
    await writeFile(launchOnly, 'launch-v2');
    await assert.rejects(() => requirePreparedBuild(root, identity), /hash drifted/);
    await writeFile(launchOnly, 'launch-v1');
    await writeFile(resolve(monorepo, '.github/workflows/build.yml'), 'name: build-v2\n');
    await assert.rejects(() => requirePreparedF0vBuild(root, identity), /source content hash drifted/);
    await writeFile(resolve(monorepo, '.github/workflows/build.yml'), 'name: build-v1\n');
    await requirePreparedF0vBuild(root, identity);
    await writeFile(resolve(root, 'tools/frontier-v3-test-pilot/src/runner.mjs'), 'export const version = 2;\n');
    await assert.rejects(() => requirePreparedF0vBuild(root, identity), /source content hash drifted/);
    await writeFile(resolve(root, 'tools/frontier-v3-test-pilot/src/runner.mjs'), 'export const version = 1;\n');
    await writeFile(resolve(root, 'scripts/with-private-xvfb.sh'), '#!/usr/bin/env bash\n# Xvfb v2\n');
    await assert.rejects(() => requirePreparedF0vBuild(root, identity), /source content hash drifted/);
    await writeFile(resolve(root, 'scripts/with-private-xvfb.sh'), '#!/usr/bin/env bash\n# Xvfb v1\n');
    await writeFile(resolve(root, 'scripts/private-xvfb-glx-probe.py'), '#!/usr/bin/env python3\n# GLX v2\n');
    await assert.rejects(() => requirePreparedF0vBuild(root, identity), /source content hash drifted/);
    await assert.rejects(() => requirePreparedBuild(root, { ...identity,
      preparedArtifact: { ...identity.preparedArtifact, path: '/outside-worker.jar' } }), /escapes project/);
  } finally { await rm(monorepo, { recursive: true, force: true }); }
});
