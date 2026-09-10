import { createHash, randomUUID } from 'node:crypto';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { spawn } from 'node:child_process';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { assertF02cHotReceiptCarrier, assertF02cHotReceiptDeclaration, assertF02cHotReceiptIdentity } from './f02c-hot-receipt-carrier.mjs';
import { isolatedScenarioOuterAttemptEnvironment } from './isolated-scenario-attempt.mjs';

const project = resolve(dirname(fileURLToPath(import.meta.url)), '../../..');

/** Pure preflight of a completed ordinary persistent-run manifest; it never starts Minecraft. */
export function preflightF02cHotReceiptCarrier({ scenario, declarationSource, declarationSha256, manifestSource, outerAttempt }) {
  if (scenario !== 'disposable-settlement-assault-restart.json' || !Buffer.isBuffer(declarationSource) || !Buffer.isBuffer(manifestSource)
      || !/^[a-f0-9]{64}$/.test(declarationSha256 ?? '')) throw new Error('F0.2C HOT carrier wrapper received a foreign declaration envelope');
  if (digest(declarationSource) !== declarationSha256) throw new Error('F0.2C HOT carrier declaration bytes drifted from its envelope');
  const manifestSha256 = digest(manifestSource); const declaration = parse(declarationSource, 'declaration'); const manifest = parse(manifestSource, 'final manifest');
  assertF02cHotReceiptDeclaration(declaration);
  const identity = assertF02cHotReceiptIdentity({ declaration, declarationSha256, manifest, outerAttempt });
  const receipt = assertF02cHotReceiptCarrier({ declaration, manifest });
  return Object.freeze({ declarationSha256, finalManifestSha256: manifestSha256, identity, receipt });
}

export function buildF02cHotReceiptCarrierReceipt(validated, manifestFile = 'settlement-assault.manifest.json') {
  if (!validated?.receipt || !/^[a-f0-9]{64}$/.test(validated.declarationSha256) || !/^[a-f0-9]{64}$/.test(validated.finalManifestSha256)) {
    throw new Error('F0.2C HOT carrier cannot construct a receipt from an unvalidated final manifest');
  }
  return Object.freeze({ schema: 1, kind: 'f02c-hot-settlement-assault-carrier', status: 'passed', declaration: {
    scenario: 'disposable-settlement-assault-restart.json', sha256: validated.declarationSha256 },
  finalManifest: { file: manifestFile, sha256: validated.finalManifestSha256 }, receipt: validated.receipt,
  identity: { precommittedOuterAttempt: validated.identity.outerAttempt, clientRunner: { runId: validated.identity.clientRunId }, lifecycle: validated.identity.lifecycle } });
}

export function isolatedScenarioSupervisorInvocation({ scenarioPath, manifestPath, root, outerAttempt }) {
  if (![scenarioPath, manifestPath, root, outerAttempt].every(value => typeof value === 'string' && value.length > 0)) {
    throw new Error('F0.2C HOT carrier isolated scenario invocation is malformed');
  }
  const environment = { ...process.env }; delete environment.FRONTIER_V3_TEST_SAVE_OWNER_OBSERVER_ADMISSION_ONLY;
  return Object.freeze({ args: Object.freeze(['tools/frontier-v3-test-pilot/src/run-isolated-scenario.mjs', scenarioPath, manifestPath]),
    options: Object.freeze({ cwd: project, env: Object.freeze({ ...environment, FRONTIER_V3_NATIVE_PROCESS_ROOT: resolve(root, 'process'),
      ...isolatedScenarioOuterAttemptEnvironment(outerAttempt) }), stdio: 'inherit' }) });
}

export async function runIsolatedScenario(invocation) {
  const { args, options } = isolatedScenarioSupervisorInvocation(invocation); const child = spawn(process.execPath, args, options);
  const code = await new Promise((resolveExit, reject) => { child.once('error', reject); child.once('exit', resolveExit); });
  if (code !== 0) throw new Error(`F0.2C HOT native carrier scenario failed (${code})`);
}

if (resolve(process.argv[1] ?? '') === fileURLToPath(import.meta.url)) await main();

async function main() {
  const output = resolve(process.argv[2] ?? 'build/f02c-native/hot-receipt-carrier.json');
  if (!output.startsWith(`${project}/`) || !process.env.DISPLAY) throw new Error('F0.2C HOT carrier requires an in-checkout output and task-private visible display');
  const root = dirname(output); await mkdir(root, { recursive: true }); const scenario = 'disposable-settlement-assault-restart.json';
  const declarationPath = resolve(project, 'tools/frontier-v3-test-pilot/scenarios', scenario); const declarationSource = await readFile(declarationPath);
  const declarationSha256 = digest(declarationSource); assertF02cHotReceiptDeclaration(parse(declarationSource, 'declaration'));
  const manifestPath = resolve(root, 'settlement-assault.manifest.json'); const outerAttempt = randomUUID();
  await runIsolatedScenario({ scenarioPath: declarationPath, manifestPath, root, outerAttempt });
  const validated = preflightF02cHotReceiptCarrier({ scenario, declarationSource, declarationSha256, manifestSource: await readFile(manifestPath), outerAttempt });
  const receipt = buildF02cHotReceiptCarrierReceipt(validated); await writeFile(output, `${JSON.stringify(receipt, null, 2)}\n`, { flag: 'wx' }); console.log(JSON.stringify(receipt));
}

function digest(value) { return createHash('sha256').update(value).digest('hex'); }
function parse(value, label) { try { return JSON.parse(value); } catch { throw new Error(`F0.2C HOT carrier ${label} bytes are malformed`); } }
