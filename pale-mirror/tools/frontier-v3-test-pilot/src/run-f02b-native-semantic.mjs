import { createHash } from 'node:crypto';
import { createWriteStream } from 'node:fs';
import { mkdir, readFile, readdir, writeFile } from 'node:fs/promises';
import { spawn } from 'node:child_process';
import { dirname, resolve } from 'node:path';
import { assertSemanticEvidence, F02B_KIND, F02B_SCHEMA, laneFor } from './f02b-native-semantic.mjs';

const values = Object.fromEntries(process.argv.slice(2).map(value => { const [key, entry] = value.slice(2).split('=', 2); return [key, entry]; }));
const namespaces = JSON.parse(await readFile(resolve(values.namespaces ?? ''), 'utf8'));
const identity = JSON.parse(await readFile(resolve(values.identity ?? ''), 'utf8'));
const output = resolve(values.output ?? ''); const log = resolve(values.log ?? '');
const lane = laneFor(values.worker);
if (!output.startsWith(`${process.cwd()}/`) || !log.startsWith(`${process.cwd()}/`) || values.lane !== lane) throw new Error('F0.2B semantic invocation is malformed');
for (const field of ['gradle', 'cache', 'world', 'process']) await mkdir(namespaces[field], { recursive: true });
const startedAtMillis = Date.now();
const child = spawn('./gradlew', [':pale-mirror-neoforge:jar', ':pale-mirror-neoforge:runFrontierV3ReferenceGameTestServer', '--no-daemon'], {
  cwd: process.cwd(), env: { ...process.env, GRADLE_USER_HOME: namespaces.gradle, FRONTIER_V3_REFERENCE_WORLD_ROOT: namespaces.world,
    FRONTIER_V3_REFERENCE_LANE: lane, DISPLAY: namespaces.display }, stdio: ['ignore', 'pipe', 'pipe']
});
const stream = createWriteStream(log, { flags: 'wx' }); child.stdout.pipe(stream); child.stderr.pipe(stream);
const code = await new Promise((resolveExit, rejectExit) => { child.once('error', rejectExit); child.once('exit', resolveExit); });
await new Promise((resolveClose, rejectClose) => stream.once('error', rejectClose).once('close', resolveClose));
const finishedAtMillis = Date.now(); const transcript = await readFile(log, 'utf8');
if (code !== 0 || !transcript.includes("Launching target 'forgeserverdev'") || !transcript.includes(`Running test batch 'pm-frontier-v3-reference-${lane}:0'`)
  || !transcript.includes('All 1 required test passed :)') || !transcript.includes('BUILD SUCCESSFUL')) throw new Error('F0.2B native semantic GameTest did not complete its declared lane');
const jar = (await readdir(resolve('pale-mirror-neoforge/build/libs'))).filter(name => name.endsWith('.jar') && !name.endsWith('-sources.jar')).sort().at(-1);
if (!jar) throw new Error('F0.2B native semantic launch produced no distributable jar');
const jarSha256 = createHash('sha256').update(await readFile(resolve('pale-mirror-neoforge/build/libs', jar))).digest('hex');
const evidence = { schema: F02B_SCHEMA, kind: F02B_KIND, status: 'passed', worker: values.worker, lane, qualificationId: values.qualification,
  repository: values.repository, headSha: values.head, workflowSha: values['workflow-sha'], workflowRef: values.workflow, runId: Number(values.run), runAttempt: Number(values.attempt),
  jobId: identity.jobId, runnerId: identity.runnerId, runnerName: identity.runnerName, startedAtMillis, finishedAtMillis, gradlePid: child.pid,
  launchTarget: 'forgeserverdev', requiredTest: `pm-frontier-v3-reference-${lane}:0`, requiredTestCount: 1, jarSha256, namespaces };
assertSemanticEvidence(evidence); await writeFile(output, `${JSON.stringify(evidence)}\n`, { flag: 'wx' });
