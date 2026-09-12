import { createHash, randomUUID } from 'node:crypto';
import { spawn } from 'node:child_process';
import { mkdir, readFile, stat, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { assertF06ResourceSiteHarvestProgressCarrier, assertF06ResourceSiteHarvestProgressDeclaration } from './f06-resource-site-harvest-progress-carrier.mjs';
import { isolatedScenarioOuterAttemptEnvironment } from './isolated-scenario-attempt.mjs';

const project = resolve(dirname(fileURLToPath(import.meta.url)), '../../..');
const scenario = 'disposable-f06-resource-site-harvest-progress.json';

/** Validates immutable native bytes before issuing a minimal first-crop evidence receipt. */
export function preflightF06ResourceSiteHarvestProgressCarrier({ declarationSource, declarationSha256, manifestSource }) {
  if (!Buffer.isBuffer(declarationSource) || !Buffer.isBuffer(manifestSource) || digest(declarationSource) !== declarationSha256) {
    throw new Error('F0.6 resource-site harvest declaration envelope is malformed or stale');
  }
  const declaration = parse(declarationSource, 'declaration'); const manifest = parse(manifestSource, 'final manifest');
  return Object.freeze({ declarationSha256, finalManifestSha256: digest(manifestSource),
    facts: assertF06ResourceSiteHarvestProgressCarrier({ declaration, manifest }) });
}

export function buildF06ResourceSiteHarvestProgressReceipt(validated) {
  if (!sha256(validated?.declarationSha256) || !sha256(validated?.finalManifestSha256) || !validated?.facts) {
    throw new Error('F0.6 resource-site harvest carrier cannot construct a receipt without native facts');
  }
  return Object.freeze({ schema: 1, kind: 'f06-resource-site-harvest-progress-native-carrier', status: 'passed',
    declaration: { scenario, sha256: validated.declarationSha256 },
    finalManifest: { file: 'resource-site-harvest-progress.manifest.json', sha256: validated.finalManifestSha256 }, facts: validated.facts });
}

export function isolatedScenarioSupervisorInvocation({ scenarioPath, manifestPath, root, outerAttempt }) {
  if (![scenarioPath, manifestPath, root, outerAttempt].every(value => typeof value === 'string' && value.length > 0)) {
    throw new Error('F0.6 resource-site harvest isolated scenario invocation is malformed');
  }
  return Object.freeze({ args: Object.freeze(['tools/frontier-v3-test-pilot/src/run-isolated-scenario.mjs', scenarioPath, manifestPath]),
    options: Object.freeze({ cwd: project, env: Object.freeze({ ...process.env, FRONTIER_V3_NATIVE_PROCESS_ROOT: resolve(root, 'process'),
      ...isolatedScenarioOuterAttemptEnvironment(outerAttempt) }), stdio: 'inherit' }) });
}

if (resolve(process.argv[1] ?? '') === fileURLToPath(import.meta.url)) await main();

async function main() {
  const output = resolve(process.argv[2] ?? 'build/f06-native/resource-site-harvest-progress-carrier.json');
  if (!output.startsWith(`${project}/`) || !process.env.DISPLAY) {
    throw new Error('F0.6 resource-site harvest carrier requires in-checkout output and task-private visible display');
  }
  await absent(output); const root = dirname(output); await mkdir(root, { recursive: true });
  const declarationPath = resolve(project, 'tools/frontier-v3-test-pilot/scenarios', scenario);
  const declarationSource = await readFile(declarationPath); assertF06ResourceSiteHarvestProgressDeclaration(parse(declarationSource, 'declaration'));
  const declarationSha256 = digest(declarationSource); const manifestPath = resolve(root, 'resource-site-harvest-progress.manifest.json');
  const { args, options } = isolatedScenarioSupervisorInvocation({ scenarioPath: declarationPath, manifestPath, root, outerAttempt: randomUUID() });
  const code = await run(args, options);
  if (code !== 0) throw new Error(`F0.6 resource-site harvest native scenario failed (${code})`);
  const receipt = buildF06ResourceSiteHarvestProgressReceipt(preflightF06ResourceSiteHarvestProgressCarrier({ declarationSource, declarationSha256,
    manifestSource: await readFile(manifestPath) }));
  await writeFile(output, `${JSON.stringify(receipt, null, 2)}\n`, { flag: 'wx' }); console.log(JSON.stringify(receipt));
}

function run(args, options) { return new Promise((resolveExit, reject) => { const child = spawn(process.execPath, args, options);
  child.once('error', reject); child.once('exit', code => resolveExit(code ?? 1)); }); }
async function absent(path) { try { await stat(path); throw new Error('F0.6 resource-site harvest receipt already exists'); }
catch (error) { if (error?.code !== 'ENOENT') throw error; } }
function digest(value) { return createHash('sha256').update(value).digest('hex'); }
function sha256(value) { return typeof value === 'string' && /^[a-f0-9]{64}$/.test(value); }
function parse(value, label) { try { return JSON.parse(value); } catch { throw new Error(`F0.6 resource-site harvest ${label} bytes are malformed`); } }
