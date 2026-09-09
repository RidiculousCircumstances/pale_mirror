import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { spawn } from 'node:child_process';
import { access, chmod, mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { constants } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import test from 'node:test';
import { requireSaveOwnerObserverEvidence, writeSaveOwnerObserverContract } from '../src/f02b-save-owner-observer-contract.mjs';
import { assertRequiredSaveOwnerObservation } from '../src/f02b-native-semantic.mjs';

const project = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..', '..');
const scenarioName = 'disposable-f02b-normal-product-recovery.json';
const scenarioPath = resolve(project, 'tools/frontier-v3-test-pilot/scenarios', scenarioName);
const isolated = resolve(project, 'tools/frontier-v3-test-pilot/src/run-isolated-scenario.mjs');

test('F0.2B dispatcher-to-isolated-child transport admits an exact required observer contract before server work', async () => {
  await withTransport(async fixture => {
    try {
      const result = await invokeDispatcher(fixture);
      assert.equal(result.code, 0, result.stderr);
      const receipt = JSON.parse(await readFile(resolve(project, 'build/f02b-native/910055-1-worker-2/owner-observation/disposable-f02b-normal-product-recovery',
        `admission-worker-2-910055-1-${fixture.scenario.id}.json`), 'utf8'));
      assert.equal(receipt.status, 'admitted');
      assert.equal(receipt.identity.scenarioDeclarationSha256, fixture.declarationSha256);
      assert.deepEqual(receipt.scheduledSlotOffsetsMs, [0, 20_000, 60_000, 80_000]);
      await assertNoServerCommand(fixture);
    } finally { await removeDispatcherRoot(910055); }
  });
});

test('F0.2B production post-child consumer accepts exactly one valid return and rejects every invalid returned requirement', async () => {
  await withTransport(async fixture => {
    const cases = [['valid', undefined], ['absent', 'absent'], ['wrong_status', 'wrong-status'],
      ['wrong_identity', 'wrong-identity'], ['incomplete', 'incomplete']];
    for (const [index, [name, mutation]] of cases.entries()) {
      const run = 910060 + index;
      try {
        const result = await invokeDispatcher(fixture, { run, mutation });
        if (mutation === undefined) assert.equal(result.code, 0, result.stderr);
        else {
          assert.notEqual(result.code, 0, `${name} unexpectedly reached the post-child consumer`);
          assert.match(result.stderr, /required save-owner observer return is absent, foreign, or incomplete/);
        }
        await assertNoServerCommand(fixture);
      } finally { await removeDispatcherRoot(run); }
    }
  });
});

test('F0.2B required observer transport mutations fail before a server command can start', async () => {
  await withTransport(async fixture => {
    const dispatched = await dispatch(fixture);
    for (const [name, mutate] of Object.entries({
      missing_contract: env => { delete env.FRONTIER_V3_SAVE_OWNER_OBSERVER_CONTRACT; },
      empty_root: env => { env.FRONTIER_V3_SAVE_OWNER_OBSERVER_ROOT = ''; },
      drifted_lane: env => { env.FRONTIER_V3_F02B_DIAGNOSTIC_LANE = 'normal-never-visited'; }
    })) {
      const env = { ...dispatched.env }; mutate(env);
      const result = await invokeChild(fixture, env);
      assert.notEqual(result.code, 0, `${name} unexpectedly admitted`);
      assert.match(result.stderr, /required save-owner observer transport|transport drifted/i, `${name}: ${result.stderr}`);
      await assertNoServerCommand(fixture);
    }
  });
});

test('required observer result validation rejects null, missing, and unavailable slots without explicit receipts', async () => {
  const admission = { status: 'admitted', receipt: '/tmp/admission.json', scheduledSlotOffsetsMs: [0, 20_000, 60_000, 80_000] };
  const slot = (index, offsetMs, status = 'captured') => ({ index, offsetMs, status,
    receipt: { path: `/tmp/slot-${index}.json`, sha256: 'a'.repeat(64) }, ...(status === 'unavailable' ? { reason: { kind: 'durable_server_save' } } : {}) });
  const valid = { status: 'completed', slots: admission.scheduledSlotOffsetsMs.map((offsetMs, index) => slot(index, offsetMs)) };
  assert.equal(requireSaveOwnerObserverEvidence(admission, valid), valid);
  assert.throws(() => requireSaveOwnerObserverEvidence(admission, null), /observation or scheduled-slot receipt is absent/);
  const missing = structuredClone(valid); missing.slots.pop();
  assert.throws(() => requireSaveOwnerObserverEvidence(admission, missing), /observation or scheduled-slot receipt is absent/);
  const unavailable = structuredClone(valid); unavailable.slots[2] = { index: 2, offsetMs: 60_000, status: 'unavailable' };
  assert.throws(() => requireSaveOwnerObserverEvidence(admission, unavailable), /observation or scheduled-slot receipt is absent/);
});

test('F0.2B primary validation rejects a required diagnostic with null or incomplete retained observation', () => {
  const hash = 'a'.repeat(64);
  const primary = { worker: 'worker-2', lane: 'normal-zero-player-recovery', identity: { runId: 600055, runAttempt: 1 } };
  const receipt = { scenario: scenarioName, declarationSha256: hash };
  const slots = [0, 20_000, 60_000, 80_000].map((offsetMs, index) => ({ index, offsetMs, status: 'captured', receipt: `build/slot-${index}.json`, sha256: hash }));
  const valid = { ownerObservationRequirement: { status: 'accepted', contract: { sha256: hash }, identity: { worker: 'worker-2', lane: 'normal-zero-player-recovery', runId: 600055, runAttempt: 1, scenario: scenarioName, scenarioDeclarationSha256: hash },
    scheduledSlotOffsetsMs: [0, 20_000, 60_000, 80_000] }, ownerObservation: { status: 'completed', slots } };
  assert.equal(assertRequiredSaveOwnerObservation(valid, primary, receipt), valid);
  const nullObservation = structuredClone(valid); nullObservation.ownerObservation = null;
  assert.throws(() => assertRequiredSaveOwnerObservation(nullObservation, primary, receipt), /required save-owner observation/);
  const absentSlot = structuredClone(valid); absentSlot.ownerObservation.slots.pop();
  assert.throws(() => assertRequiredSaveOwnerObservation(absentSlot, primary, receipt), /required save-owner observation/);
  const unavailableWithoutReceipt = structuredClone(valid); unavailableWithoutReceipt.ownerObservation.slots[3] = { index: 3, offsetMs: 80_000, status: 'unavailable' };
  assert.throws(() => assertRequiredSaveOwnerObservation(unavailableWithoutReceipt, primary, receipt), /required save-owner observation/);
});

async function withTransport(action) {
  const root = await mkdtemp(join(project, 'build', 'f02b-observer-transport-'));
  const marker = join(root, 'server-command-started');
  const gradle = join(root, 'forbidden-server-command');
  try {
    await writeFile(gradle, `#!/bin/sh\ntouch ${marker}\nexit 97\n`, { mode: 0o700 });
    await chmod(gradle, 0o700);
    const source = await readFile(scenarioPath);
    await action({ root, marker, gradle, scenario: JSON.parse(source), declarationSha256: createHash('sha256').update(source).digest('hex') });
  } finally { await rm(root, { recursive: true, force: true }); }
}

async function dispatch(fixture) {
  const contract = await writeSaveOwnerObserverContract({ project, output: join(fixture.root, 'contracts', scenarioName),
    worker: 'worker-2', lane: 'normal-zero-player-recovery', runId: 600055, runAttempt: 1,
    scenario: scenarioName, scenarioId: fixture.scenario.id, scenarioDeclarationSha256: fixture.declarationSha256,
    evidenceRoot: join(fixture.root, 'owner-observation') });
  return { contract, env: { DISPLAY: ':f02b-transport-test', FRONTIER_V3_GRADLE: fixture.gradle,
    FRONTIER_V3_TEST_SAVE_OWNER_OBSERVER_ADMISSION_ONLY: 'true', FRONTIER_V3_F02B_SAVE_OWNER_OBSERVER_REQUIREMENT: 'required',
    FRONTIER_V3_F02B_DIAGNOSTIC_LANE: 'normal-zero-player-recovery', FRONTIER_V3_F02B_DIAGNOSTIC_RUN: '600055',
    FRONTIER_V3_F02B_DIAGNOSTIC_ATTEMPT: '1', FRONTIER_V3_PILOT_WORKER_ID: 'worker-2',
    FRONTIER_V3_SAVE_OWNER_OBSERVER_CONTRACT: contract.path, FRONTIER_V3_SAVE_OWNER_OBSERVER_ROOT: contract.contract.evidenceRoot } };
}

async function invokeChild(fixture, supplied) {
  return await new Promise((resolveResult, reject) => {
    const child = spawn(process.execPath, [isolated, scenarioPath], { cwd: project, env: { ...process.env, ...supplied }, stdio: ['ignore', 'pipe', 'pipe'] });
    let stdout = ''; let stderr = '';
    child.stdout.setEncoding('utf8').on('data', value => { stdout += value; });
    child.stderr.setEncoding('utf8').on('data', value => { stderr += value; });
    child.once('error', reject); child.once('exit', code => resolveResult({ code, stdout, stderr }));
  });
}

async function invokeDispatcher(fixture, { run = 910055, mutation = undefined } = {}) {
  const namespaces = { gradle: join(fixture.root, 'gradle'), cache: join(fixture.root, 'cache'), world: join(fixture.root, 'world'),
    process: join(fixture.root, 'process'), port: 26204, display: ':f02b-transport-test' };
  const namespacePath = join(fixture.root, 'namespaces.json'); const identityPath = join(fixture.root, 'identity.json');
  await writeFile(namespacePath, JSON.stringify(namespaces)); await writeFile(identityPath, JSON.stringify({ jobId: 1, runnerId: 2, runnerName: 'observer-transport' }));
  const runner = resolve(project, 'tools/frontier-v3-test-pilot/src/run-f02b-native-semantic.mjs');
  const args = [runner, `--namespaces=${namespacePath}`, `--identity=${identityPath}`, `--output=${join(fixture.root, `semantic-${run}.json`)}`,
    `--log=${join(fixture.root, `semantic-${run}.log`)}`, `--runtime=${join(fixture.root, `runtime-${run}.json`)}`, '--worker=worker-2', '--lane=normal-zero-player-recovery',
    `--run=${run}`, '--attempt=1', '--qualification=f02b-r21', '--repository=RidiculousCircumstances/pale_mirror', '--head=' + 'a'.repeat(40),
    '--workflow-sha=' + 'a'.repeat(40), '--workflow=RidiculousCircumstances/pale_mirror/.github/workflows/f02b-reference-container-semantic.yml@refs/heads/main'];
  return await new Promise((resolveResult, reject) => {
    const child = spawn(process.execPath, args, { cwd: project, env: { ...process.env, DISPLAY: namespaces.display, JAVA_TOOL_OPTIONS: '-Xmx3G',
      F02B_GRACEFUL_SAVE_GATE: join(fixture.root, `f02b-graceful-save-${run}-1`), FRONTIER_V3_GRADLE: fixture.gradle,
      FRONTIER_V3_TEST_F02B_SAVE_OWNER_DISPATCH_ONLY: 'true', FRONTIER_V3_TEST_SAVE_OWNER_OBSERVER_ADMISSION_ONLY: 'true',
      ...(mutation === undefined ? {} : { FRONTIER_V3_TEST_F02B_SAVE_OWNER_RETURN_MUTATION: mutation }) }, stdio: ['ignore', 'pipe', 'pipe'] });
    let stdout = ''; let stderr = '';
    child.stdout.setEncoding('utf8').on('data', value => { stdout += value; }); child.stderr.setEncoding('utf8').on('data', value => { stderr += value; });
    child.once('error', reject); child.once('exit', code => resolveResult({ code, stdout, stderr }));
  });
}

async function assertNoServerCommand(fixture) {
  await assert.rejects(access(fixture.marker, constants.F_OK), /ENOENT/);
}
async function removeDispatcherRoot(run) {
  await rm(resolve(project, `build/f02b-native/${run}-1-worker-2`), { recursive: true, force: true });
}
