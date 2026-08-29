import { randomUUID } from 'node:crypto';
import { spawn } from 'node:child_process';
import { mkdir, readFile, rm, stat, writeFile } from 'node:fs/promises';
import { basename, dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { loadScenario } from './scenario.mjs';

const [scenarioPath, outputPath = `build/frontier-v3-scenarios/${basename(process.argv[2] ?? 'scenario.json', '.json')}-${Date.now()}.json`] = process.argv.slice(2);
if (!scenarioPath) throw new Error('usage: npm run scenario:isolated -- <scenario.json> [manifest.json]');
if (!process.env.DISPLAY) throw new Error('a native visible pilot requires DISPLAY=:0');

const sourcePath = resolve(scenarioPath);
const { scenario } = await loadScenario(sourcePath);
if (scenario.isolation?.mode !== 'disposable_lite') throw new Error('isolated runner requires isolation.mode=disposable_lite');
const project = resolve(dirname(fileURLToPath(import.meta.url)), '../../..');
const gradle = process.env.FRONTIER_V3_GRADLE ?? resolve(project, 'gradlew');
const runId = randomUUID();
const port = Number(process.env.FRONTIER_V3_PILOT_PORT ?? 25575);
if (!Number.isInteger(port) || port < 1024 || port > 65535) throw new Error('FRONTIER_V3_PILOT_PORT must be 1024..65535');
const world = `v3-${scenario.id.replace(/[^a-z0-9_-]/g, '-').slice(0, 36)}-${runId.slice(0, 8)}`;
const output = resolve(project, outputPath);
const ephemeralScenario = resolve(project, `build/frontier-v3-scenarios/${runId}-scenario.json`);
const disposableWorld = resolve(project, `pale-mirror-neoforge/build/runs/frontier-v3-pilot-server/${world}`);
const serverLog = resolve(project, 'pale-mirror-neoforge/build/runs/frontier-v3-pilot-server/logs/latest.log');
const serverLogOffset = await fileSize(serverLog);
await mkdir(dirname(ephemeralScenario), { recursive: true });
await writeFile(ephemeralScenario, `${JSON.stringify({ ...scenario, server: { host: '127.0.0.1', port } }, null, 2)}\n`, 'utf8');

const serverArgs = [':pale-mirror-neoforge:runFrontierV3PilotServer', '--no-daemon',
  `-PfrontierV3PilotWorld=${world}`, `-PfrontierV3PilotSeed=${scenario.isolation.seed}`,
  `-PfrontierV3PilotPort=${port}`, `-PfrontierV3PilotUsername=${scenario.pilot.username}`, '-PfrontierV3PilotReset=true'];
const server = spawn(gradle, serverArgs, { cwd: project, env: process.env, stdio: ['pipe', 'pipe', 'pipe'] });
let serverOutput = '';
for (const stream of [server.stdout, server.stderr]) stream.setEncoding('utf8').on('data', (chunk) => { process.stdout.write(chunk); serverOutput += chunk; });

try {
  await waitForServer(server, () => serverOutput.includes('Done') && serverOutput.includes('Frontier v3'), 180_000);
  const pilot = spawn(process.execPath, [resolve(dirname(fileURLToPath(import.meta.url)), 'run-scenario.mjs'), ephemeralScenario, output], {
    cwd: project, env: { ...process.env, FRONTIER_V3_PILOT_PROFILE: 'lite', FRONTIER_V3_GRADLE: gradle }, stdio: 'inherit'
  });
  const code = await exited(pilot);
  if (code !== 0) throw new Error(`isolated native pilot exited with ${code}`);
} finally {
  await stopServerSafely(server, serverLog, serverLogOffset);
  await rm(ephemeralScenario, { force: true });
  if (process.env.FRONTIER_V3_KEEP_DISPOSABLE !== 'true') await rm(disposableWorld, { recursive: true, force: true });
}

console.log(JSON.stringify({ status: 'ok', profile: 'disposable_lite', world, port, manifest: output }));

function waitForServer(child, ready, timeoutMs) {
  return new Promise((resolveReady, reject) => {
    let settled = false;
    const settle = (callback, value) => { if (!settled) { settled = true; clearInterval(timer); clearTimeout(alarm); callback(value); } };
    const timer = setInterval(() => { if (ready()) settle(resolveReady); }, 100);
    const alarm = setTimeout(() => settle(reject, new Error(`disposable v3 server did not become ready within ${timeoutMs}ms`)), timeoutMs);
    child.once('exit', (code) => settle(reject, new Error(`disposable v3 server exited before ready (${code})`)));
  });
}
function exited(child) {
  // A short native pilot can finish before the parent reaches cleanup.  Node
  // records that terminal state, so subscribe only while it is genuinely
  // running; otherwise a late `once('exit')` would wait forever.
  if (child.exitCode !== null || child.signalCode !== null) return Promise.resolve(child.exitCode ?? 1);
  return new Promise((resolveExit) => child.once('exit', (code) => resolveExit(code ?? 1)));
}
async function stopServerSafely(server, logPath, offset) {
  // Gradle's JavaExec console does not reliably forward `stop` from a pipe.
  // Its SIGINT does begin the Minecraft shutdown, but Gradle can return before
  // its forked server has flushed every level.  Therefore SIGINT is only a
  // stop request; deletion waits for Minecraft's own durable acknowledgement.
  if (server.exitCode === null && server.signalCode === null) server.kill('SIGINT');
  const stopped = await waitForLog(logPath, offset, 'ThreadedAnvilChunkStorage: All dimensions are saved', 45_000);
  if (!stopped) throw new Error('disposable v3 server did not confirm a flushed world; preserving it for diagnosis');
  await Promise.race([exited(server), timeout(5_000)]);
}
async function fileSize(path) {
  try { return (await stat(path)).size; }
  catch { return 0; }
}
async function waitForLog(path, offset, marker, timeoutMs) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    try {
      const output = await readFile(path, 'utf8');
      // Minecraft truncates/rotates `latest.log` at a fresh launch.  An old
      // byte offset must then mean "the whole new file", not an impossible
      // suffix past EOF.
      if (output.slice(output.length < offset ? 0 : offset).includes(marker)) return true;
    } catch { /* The server has not created its log yet. */ }
    await timeout(100);
  }
  return false;
}
function timeout(ms) { return new Promise((resolveTimeout) => setTimeout(() => resolveTimeout(null), ms)); }
