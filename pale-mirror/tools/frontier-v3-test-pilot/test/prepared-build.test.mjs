import assert from 'node:assert/strict';
import { execFile } from 'node:child_process';
import test from 'node:test';
import { mkdtemp, mkdir, rm, stat, writeFile } from 'node:fs/promises';
import { promisify } from 'node:util';
import { tmpdir } from 'node:os';
import { resolve } from 'node:path';
import { fingerprintPreparedBuild, fingerprintPreparedSource, portablePreparedBuildIdentity, requirePreparedBuild, requirePreparedF0vBuild } from '../src/prepared-build.mjs';
import { ensurePreparedLaunchWorkingDirectory, preparedLaunch } from '../src/prepared-launch.mjs';

test('prepared build fingerprints both native classpaths and fails closed on drift', async () => {
  const root = await mkdtemp(resolve(tmpdir(), 'pmv3-prepared-build-'));
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
    await mkdir(resolve(root, 'tools/frontier-v3-test-pilot/src'), { recursive: true });
    await writeFile(resolve(root, '.gitignore'), 'pale-mirror-neoforge/build/\n');
    await writeFile(resolve(root, 'pale-mirror-frontier/src/main/Owner.java'), 'class Owner {}\n');
    await writeFile(resolve(root, 'tools/frontier-v3-test-pilot/src/runner.mjs'), 'export const version = 1;\n');
    const exec = promisify(execFile);
    const environment = { ...process.env };
    delete environment.GIT_DIR; delete environment.GIT_WORK_TREE; delete environment.GIT_INDEX_FILE;
    await exec('git', ['init', '-q'], { cwd: root, env: environment });
    await exec('git', ['add', '.'], { cwd: root, env: environment });
    await exec('git', ['-c', 'user.name=test', '-c', 'user.email=test@example.invalid', 'commit', '-qm', 'prepared-source'], { cwd: root, env: environment });
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
    await requirePreparedBuild(root, identity);
    await requirePreparedF0vBuild(root, identity);
    const portable = portablePreparedBuildIdentity(identity);
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
    await assert.rejects(() => ensurePreparedLaunchWorkingDirectory({ cwd: 'relative-client' }), /malformed/);
    await writeFile(dependency, 'classpath-v2');
    await assert.rejects(() => requirePreparedBuild(root, identity), /hash drifted/);
    await writeFile(dependency, 'classpath-v1');
    await writeFile(launchOnly, 'launch-v2');
    await assert.rejects(() => requirePreparedBuild(root, identity), /hash drifted/);
    await writeFile(launchOnly, 'launch-v1');
    await writeFile(resolve(root, 'tools/frontier-v3-test-pilot/src/runner.mjs'), 'export const version = 2;\n');
    await assert.rejects(() => requirePreparedF0vBuild(root, identity), /source content hash drifted/);
    await assert.rejects(() => requirePreparedBuild(root, { ...identity,
      preparedArtifact: { ...identity.preparedArtifact, path: '/outside-worker.jar' } }), /escapes project/);
  } finally { await rm(root, { recursive: true, force: true }); }
});
