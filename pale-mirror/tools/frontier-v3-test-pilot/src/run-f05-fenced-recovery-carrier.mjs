import { createHash, randomUUID } from 'node:crypto';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { spawn } from 'node:child_process';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { assertF05FencedRecoveryCarrier, assertF05FencedRecoveryDeclaration, assertF05FencedRecoveryIdentity } from './f05-fenced-recovery-carrier.mjs';
import { isolatedScenarioOuterAttemptEnvironment } from './isolated-scenario-attempt.mjs';

const project = resolve(dirname(fileURLToPath(import.meta.url)), '../../..');
const scenario = 'disposable-f05-fenced-route-recovery.json';

export function preflightF05FencedRecoveryCarrier({ declarationSource, declarationSha256, manifestSource, outerAttempt }) {
  if (!Buffer.isBuffer(declarationSource) || !Buffer.isBuffer(manifestSource) || digest(declarationSource) !== declarationSha256) {
    throw new Error('F0.5 fenced recovery declaration envelope is malformed or stale');
  }
  const declaration = parse(declarationSource, 'declaration'); const manifest = parse(manifestSource, 'final manifest');
  return Object.freeze({ declarationSha256, finalManifestSha256: digest(manifestSource),
    declaration: assertF05FencedRecoveryDeclaration(declaration), identity: assertF05FencedRecoveryIdentity({ declaration, declarationSha256, manifest, outerAttempt }),
    recovery: assertF05FencedRecoveryCarrier(manifest) });
}

export function buildF05FencedRecoveryReceipt(validated) {
  if (!validated?.recovery || !sha256(validated.declarationSha256) || !sha256(validated.finalManifestSha256)) {
    throw new Error('F0.5 fenced recovery carrier cannot construct a receipt without validated native facts');
  }
  return Object.freeze({ schema: 1, kind: 'f05-fenced-recovery-native-carrier', status: 'passed',
    declaration: { scenario, sha256: validated.declarationSha256 }, finalManifest: { file: 'fenced-recovery.manifest.json', sha256: validated.finalManifestSha256 },
    identity: validated.identity, recovery: validated.recovery });
}

export function isolatedScenarioSupervisorInvocation({ scenarioPath, manifestPath, root, outerAttempt }) {
  if (![scenarioPath, manifestPath, root, outerAttempt].every(value => typeof value === 'string' && value.length > 0)) {
    throw new Error('F0.5 fenced recovery isolated scenario invocation is malformed');
  }
  const environment = { ...process.env }; delete environment.FRONTIER_V3_TEST_SAVE_OWNER_OBSERVER_ADMISSION_ONLY;
  return Object.freeze({ args: Object.freeze(['tools/frontier-v3-test-pilot/src/run-isolated-scenario.mjs', scenarioPath, manifestPath]),
    options: Object.freeze({ cwd: project, env: Object.freeze({ ...environment, FRONTIER_V3_NATIVE_PROCESS_ROOT: resolve(root, 'process'),
      ...isolatedScenarioOuterAttemptEnvironment(outerAttempt) }), stdio: 'inherit' }) });
}

if (resolve(process.argv[1] ?? '') === fileURLToPath(import.meta.url)) await main();

async function main() {
  const output = resolve(process.argv[2] ?? 'build/f05-native/fenced-recovery-carrier.json');
  if (!output.startsWith(`${project}/`) || !process.env.DISPLAY) throw new Error('F0.5 fenced recovery carrier requires in-checkout output and task-private visible display');
  const root = dirname(output); await mkdir(resolve(root, 'process'), { recursive: true });
  const declarationPath = resolve(project, 'tools/frontier-v3-test-pilot/scenarios', scenario);
  const declarationSource = await readFile(declarationPath); const declarationSha256 = digest(declarationSource);
  assertF05FencedRecoveryDeclaration(parse(declarationSource, 'declaration'));
  const manifestPath = resolve(root, 'fenced-recovery.manifest.json'); const outerAttempt = randomUUID();
  const { args, options } = isolatedScenarioSupervisorInvocation({ scenarioPath: declarationPath, manifestPath, root, outerAttempt });
  const child = spawn(process.execPath, args, options);
  const code = await new Promise((resolveExit, reject) => { child.once('error', reject); child.once('exit', resolveExit); });
  if (code !== 0) throw new Error(`F0.5 fenced recovery native carrier scenario failed (${code})`);
  const validated = preflightF05FencedRecoveryCarrier({ declarationSource, declarationSha256, manifestSource: await readFile(manifestPath), outerAttempt });
  const receipt = buildF05FencedRecoveryReceipt(validated); await writeFile(output, `${JSON.stringify(receipt, null, 2)}\n`, { flag: 'wx' }); console.log(JSON.stringify(receipt));
}
function digest(value) { return createHash('sha256').update(value).digest('hex'); }
function sha256(value) { return typeof value === 'string' && /^[a-f0-9]{64}$/.test(value); }
function parse(value, label) { try { return JSON.parse(value); } catch { throw new Error(`F0.5 fenced recovery ${label} bytes are malformed`); } }
