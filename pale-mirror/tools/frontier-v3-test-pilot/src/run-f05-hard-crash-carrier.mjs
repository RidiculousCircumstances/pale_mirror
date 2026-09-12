import { createHash } from 'node:crypto';
import { spawn } from 'node:child_process';
import { mkdir, readFile, stat, writeFile } from 'node:fs/promises';
import { dirname, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { assertF05HardCrashCarrier, selectF05ResourceWindows } from './f05-hard-crash-carrier.mjs';

const project = resolve(dirname(fileURLToPath(import.meta.url)), '../../..');
const contract = 'tools/frontier-v3-test-pilot/contracts/resource-site-harvest-f0v.json';
const physicalScenario = 'tools/frontier-v3-test-pilot/scenarios/disposable-f03-fungible-player-abrupt.json';
const canonicalScenario = 'tools/frontier-v3-test-pilot/scenarios/disposable-f05-fenced-player-canonical-first-abrupt.json';

if (resolve(process.argv[1] ?? '') === fileURLToPath(import.meta.url)) await main();

/** One bounded native campaign: three admitted resource boundaries plus both real player-save orders. */
async function main() {
  const [outputArgument, ...options] = process.argv.slice(2);
  const reusedReport = options.find((option) => option.startsWith('--reuse-resource-windows='))?.slice('--reuse-resource-windows='.length);
  const reusedPhysical = options.find((option) => option.startsWith('--reuse-physical-first='))?.slice('--reuse-physical-first='.length);
  const reusedCanonical = options.find((option) => option.startsWith('--reuse-canonical-first='))?.slice('--reuse-canonical-first='.length);
  if (options.length !== new Set(options).size || options.some((option) => !option.startsWith('--reuse-resource-windows=')
      && !option.startsWith('--reuse-physical-first=') && !option.startsWith('--reuse-canonical-first='))) {
    throw new Error('F0.5 hard-crash carrier accepts only exact retained resource-window, physical-first, and canonical-first options');
  }
  const output = resolve(outputArgument ?? 'build/f05-native/fenced-hard-crash-carrier.json');
  if (!output.startsWith(`${project}/`) || !process.env.DISPLAY) {
    throw new Error('F0.5 hard-crash carrier requires in-checkout output and a task-private visible display');
  }
  await absent(output, 'F0.5 hard-crash receipt');
  const root = dirname(output); await mkdir(root, { recursive: true });
  const processRoot = resolve(process.env.FRONTIER_V3_NATIVE_PROCESS_ROOT ?? `${root}/process`);
  const taskPrivateRoot = `${resolve(project, '..')}-tmp/`;
  if (!processRoot.startsWith(`${project}/`) && !processRoot.startsWith(taskPrivateRoot)) {
    throw new Error('F0.5 hard-crash process root must remain in checkout or its task-private sibling');
  }
  const resourceEvidence = reusedReport === undefined
    ? await runResourceWindows(root, processRoot)
    : await resourceWindowsFromReport(containedBuildPath(reusedReport, 'F0.5 retained resource-window report'), true);
  // One private display owns one visible client at a time; the two arrival orders are independent
  // fresh worlds and are intentionally serial rather than competing for any client state.
  const physicalEvidence = reusedPhysical === undefined
    ? await freshPlayerEvidence(physicalScenario, resolve(root, 'physical-first.manifest.json'), resourceEvidence.prepared, processRoot, port(2))
    : await retainedPlayerEvidence(containedBuildPath(reusedPhysical, 'F0.5 retained physical-first manifest'), 'physical-first');
  const canonicalEvidence = reusedCanonical === undefined
    ? await freshPlayerEvidence(canonicalScenario, resolve(root, 'canonical-first.manifest.json'), resourceEvidence.prepared, processRoot, port(4))
    : await retainedPlayerEvidence(containedBuildPath(reusedCanonical, 'F0.5 retained canonical-first manifest'), 'canonical-first');
  const physicalFirst = physicalEvidence.manifest; const canonicalFirst = canonicalEvidence.manifest;
  const facts = assertF05HardCrashCarrier({ windows: resourceEvidence.windows, physicalFirst, canonicalFirst });
  const receipt = Object.freeze({ schema: 1, kind: 'f05-fenced-hard-crash-native-carrier', status: 'passed', facts,
    semanticWindows: resourceEvidence.receipt,
    playerOrders: { physicalFirst: await playerReceipt(physicalEvidence), canonicalFirst: await playerReceipt(canonicalEvidence) } });
  await writeFile(output, `${JSON.stringify(receipt, null, 2)}\n`, { flag: 'wx' });
  console.log(JSON.stringify(receipt));
}

async function freshPlayerEvidence(scenario, output, prepared, processRoot, candidatePort) {
  const exit = await runScenario(scenario, output, prepared, processRoot, candidatePort);
  if (exit !== 0) throw new Error(`F0.5 player-save crash campaign failed for ${scenario} (${exit})`);
  return Object.freeze({ path: output, manifest: await readJson(output), reused: false });
}

async function retainedPlayerEvidence(path, order) {
  const manifest = await readJson(path); const artifact = manifest?.build?.preparedArtifact;
  if (typeof artifact?.path !== 'string' || !/^[a-f0-9]{64}$/.test(artifact.sha256)) {
    throw new Error(`F0.5 retained ${order} manifest has no packaged artifact identity`);
  }
  const artifactPath = resolve(project, artifact.path);
  if (!artifactPath.startsWith(`${project}/`) || await sha(artifactPath) !== artifact.sha256) {
    throw new Error(`F0.5 retained ${order} manifest does not match the current packaged artifact`);
  }
  return Object.freeze({ path, manifest, reused: true });
}

async function playerReceipt(evidence) {
  return Object.freeze({ manifest: relative(project, evidence.path), sha256: await sha(evidence.path), reused: evidence.reused });
}

async function runResourceWindows(root, processRoot) {
  const matrixRoot = resolve(root, 'semantic-windows');
  const matrix = await run(process.execPath, ['tools/frontier-v3-test-pilot/src/run-f0v-matrix.mjs', contract,
    '--variant=abrupt_restart', `--output-root=${relative(project, matrixRoot)}`], { ...process.env,
    FRONTIER_V3_NATIVE_PROCESS_ROOT: resolve(processRoot, 'windows'), FRONTIER_V3_PILOT_PORT: String(port(0)) });
  if (matrix !== 0) throw new Error(`F0.5 semantic crash-window campaign failed (${matrix})`);
  return resourceWindowsFromReport(resolve(matrixRoot, 'matrix.json'));
}

async function resourceWindowsFromReport(reportPath, reused = false) {
  const report = await readJson(reportPath); const runs = selectF05ResourceWindows(report);
  const artifact = report?.build?.preparedArtifact;
  if (typeof artifact?.path !== 'string' || !/^[a-f0-9]{64}$/.test(artifact.sha256)) {
    throw new Error('F0.5 retained resource-window report has no packaged artifact identity');
  }
  const artifactPath = resolve(project, artifact.path);
  if (!artifactPath.startsWith(`${project}/`) || await sha(artifactPath) !== artifact.sha256) {
    throw new Error('F0.5 retained resource-window report does not match the current packaged artifact');
  }
  const windows = await Promise.all(runs.map(async (run) => Object.freeze({ ...run,
    manifest: await readJson(resolve(project, run.manifest)) })));
  const preparedPath = resolve(dirname(reportPath), 'prepared-build.json');
  if (!reused) await readJson(preparedPath);
  return Object.freeze({ windows, prepared: reused ? undefined : relative(project, preparedPath),
    receipt: Object.freeze({ report: relative(project, reportPath), sha256: await sha(reportPath), reused }) });
}

function runScenario(scenario, output, prepared, processRoot, candidatePort) {
  return run(process.execPath, ['tools/frontier-v3-test-pilot/src/run-isolated-scenario.mjs', scenario, relative(project, output)], {
    ...process.env, ...(prepared === undefined ? {} : { FRONTIER_V3_PREPARED_BUILD_IDENTITY: prepared }),
    FRONTIER_V3_NATIVE_PROCESS_ROOT: resolve(processRoot, `player-${candidatePort}`),
    FRONTIER_V3_PILOT_PORT: String(candidatePort)
  });
}

function run(command, args, env) {
  return new Promise((resolveExit, reject) => {
    const child = spawn(command, args, { cwd: project, env, stdio: 'inherit' });
    child.once('error', reject); child.once('exit', code => resolveExit(code ?? 1));
  });
}

function port(offset) {
  const base = Number(process.env.FRONTIER_V3_PILOT_PORT ?? 25575);
  if (!Number.isInteger(base) || base < 1024 || base + 5 >= 65535) throw new Error('F0.5 hard-crash carrier requires one bounded private port range');
  return base + offset;
}

async function absent(path, label) { try { await stat(path); throw new Error(`refusing to overwrite ${label}`); } catch (error) { if (error?.code !== 'ENOENT') throw error; } }
async function readJson(path) { return JSON.parse(await readFile(path, 'utf8')); }
async function sha(path) { return createHash('sha256').update(await readFile(path)).digest('hex'); }
function containedBuildPath(value, label) {
  if (typeof value !== 'string' || value.length === 0) throw new Error(`${label} is required`);
  const path = resolve(project, value); if (!path.startsWith(`${project}/build/`)) throw new Error(`${label} must remain in checkout build/`);
  return path;
}
