import { createHash, randomUUID } from 'node:crypto';
import { mkdir, readFile, stat, writeFile } from 'node:fs/promises';
import { spawn } from 'node:child_process';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { assertF04HiveReturnCarrier, assertF04HiveReturnDeclaration, assertF04HiveReturnIdentity } from './f04-hive-return-carrier.mjs';
import { isolatedScenarioOuterAttemptEnvironment } from './isolated-scenario-attempt.mjs';

const project = resolve(dirname(fileURLToPath(import.meta.url)), '../../..');
const scenario = 'disposable-hive-return-restart.json';

/** Pure final-manifest preflight. The native wrapper is the only process launcher. */
export function preflightF04HiveReturnCarrier({ declarationSource, declarationSha256, manifestSource, outerAttempt }) {
  if (!Buffer.isBuffer(declarationSource) || !Buffer.isBuffer(manifestSource) || digest(declarationSource) !== declarationSha256) {
    throw new Error('F0.4 return carrier declaration envelope is malformed or stale');
  }
  const declaration = parse(declarationSource, 'declaration'); const manifest = parse(manifestSource, 'final manifest');
  const declarationFacts = assertF04HiveReturnDeclaration(declaration);
  const identity = assertF04HiveReturnIdentity({ declaration, declarationSha256, manifest, outerAttempt });
  const returnFacts = assertF04HiveReturnCarrier(manifest);
  return Object.freeze({ declarationFacts, declarationSha256, finalManifestSha256: digest(manifestSource), identity, returnFacts });
}

export function buildF04HiveReturnReceipt(validated, jfr) {
  if (!validated?.returnFacts || !sha256(validated.declarationSha256) || !sha256(validated.finalManifestSha256)
      || !jfr || !sha256(jfr.sha256) || !Number.isSafeInteger(jfr.bytes) || jfr.bytes <= 0) {
    throw new Error('F0.4 return carrier cannot construct receipt without its native and JFR evidence');
  }
  return Object.freeze({ schema: 1, kind: 'f04-hive-return-native-carrier', status: 'passed', declaration: { scenario, sha256: validated.declarationSha256 },
    finalManifest: { file: 'hive-return.manifest.json', sha256: validated.finalManifestSha256 }, jfr, identity: validated.identity,
    return: validated.returnFacts });
}

export function isolatedScenarioSupervisorInvocation({ scenarioPath, manifestPath, root, outerAttempt }) {
  if (![scenarioPath, manifestPath, root, outerAttempt].every(value => typeof value === 'string' && value.length > 0)) {
    throw new Error('F0.4 return carrier isolated scenario invocation is malformed');
  }
  const environment = { ...process.env }; delete environment.FRONTIER_V3_TEST_SAVE_OWNER_OBSERVER_ADMISSION_ONLY;
  return Object.freeze({ args: Object.freeze(['tools/frontier-v3-test-pilot/src/run-isolated-scenario.mjs', scenarioPath, manifestPath]),
    options: Object.freeze({ cwd: project, env: Object.freeze({ ...environment, FRONTIER_V3_NATIVE_PROCESS_ROOT: resolve(root, 'process'),
      ...isolatedScenarioOuterAttemptEnvironment(outerAttempt) }), stdio: 'inherit' }) });
}

export async function runIsolatedScenario(invocation) {
  const { args, options } = isolatedScenarioSupervisorInvocation(invocation); const child = spawn(process.execPath, args, options);
  const code = await new Promise((resolveExit, reject) => { child.once('error', reject); child.once('exit', resolveExit); });
  if (code !== 0) throw new Error(`F0.4 hive return native carrier scenario failed (${code})`);
}

if (resolve(process.argv[1] ?? '') === fileURLToPath(import.meta.url)) await main();

async function main() {
  const output = resolve(process.argv[2] ?? 'build/f04-native/hive-return-restart-carrier.json');
  if (!output.startsWith(`${project}/`) || !process.env.DISPLAY) {
    throw new Error('F0.4 return carrier requires in-checkout output and task-private visible display');
  }
  const jfrPath = resolve(project, process.env.FRONTIER_V3_JFR_OUTPUT ?? '');
  if (!process.env.FRONTIER_V3_JFR_OUTPUT || !jfrPath.startsWith(`${resolve(project, 'build/profiles')}/`)) {
    throw new Error('F0.4 return carrier requires a build/profiles JFR output');
  }
  const root = dirname(output); await mkdir(resolve(root, 'process'), { recursive: true });
  const declarationPath = resolve(project, 'tools/frontier-v3-test-pilot/scenarios', scenario);
  const declarationSource = await readFile(declarationPath); const declarationSha256 = digest(declarationSource);
  assertF04HiveReturnDeclaration(parse(declarationSource, 'declaration'));
  const manifestPath = resolve(root, 'hive-return.manifest.json'); const outerAttempt = randomUUID();
  await runIsolatedScenario({ scenarioPath: declarationPath, manifestPath, root, outerAttempt });
  const jfrStat = await stat(jfrPath); const jfr = Object.freeze({ file: jfrPath, sha256: digest(await readFile(jfrPath)), bytes: jfrStat.size });
  const validated = preflightF04HiveReturnCarrier({ declarationSource, declarationSha256, manifestSource: await readFile(manifestPath), outerAttempt });
  const receipt = buildF04HiveReturnReceipt(validated, jfr);
  await writeFile(output, `${JSON.stringify(receipt, null, 2)}\n`, { flag: 'wx' }); console.log(JSON.stringify(receipt));
}

function digest(value) { return createHash('sha256').update(value).digest('hex'); }
function sha256(value) { return typeof value === 'string' && /^[a-f0-9]{64}$/.test(value); }
function parse(value, label) { try { return JSON.parse(value); } catch { throw new Error(`F0.4 return carrier ${label} bytes are malformed`); } }
