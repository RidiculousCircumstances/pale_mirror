import { createHash, randomUUID } from 'node:crypto';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { spawn } from 'node:child_process';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { assertF02cAftermathCarrier, assertF02cAftermathDeclaration, assertF02cAftermathIdentity } from './f02c-aftermath-carrier.mjs';
import { isolatedScenarioOuterAttemptEnvironment } from './isolated-scenario-attempt.mjs';

const project = resolve(dirname(fileURLToPath(import.meta.url)), '../../..');

/** A pure envelope preflight for the exact persistent runner output shape. */
export function preflightF02cAftermathCarrier({ scenario, declarationSource, declarationSha256, manifestSource, outerAttempt }) {
  if (scenario !== 'disposable-cold-bomber-aftermath-restart.json' || !Buffer.isBuffer(declarationSource)
      || !Buffer.isBuffer(manifestSource) || typeof declarationSha256 !== 'string'
      || !/^[a-f0-9]{64}$/.test(declarationSha256)) throw new Error('F0.2C carrier wrapper received a foreign declaration envelope');
  const actualDeclarationSha256 = digest(declarationSource);
  // Hash the completed final-manifest bytes before parsing. The digest is only
  // released in a receipt after every production-shape validation succeeds.
  const finalManifestSha256 = digest(manifestSource);
  if (actualDeclarationSha256 !== declarationSha256) throw new Error('F0.2C carrier wrapper declaration bytes drifted from its envelope');
  const declaration = parse(declarationSource, 'declaration'); const manifest = parse(manifestSource, 'final manifest');
  assertF02cAftermathDeclaration(declaration);
  const identity = assertF02cAftermathIdentity({ declaration, declarationSha256, manifest, outerAttempt });
  const terminal = assertF02cAftermathCarrier({ clearDeclaration: declaration, clearManifest: manifest });
  return Object.freeze({ terminal, declarationSha256, finalManifestSha256, identity });
}

/** Production terminal receipt construction, retained separately from native execution. */
export function buildF02cAftermathCarrierReceipt(validated, manifestFile = 'clear.manifest.json') {
  if (!validated?.terminal || !/^[a-f0-9]{64}$/.test(validated.declarationSha256)
      || !/^[a-f0-9]{64}$/.test(validated.finalManifestSha256)
      || typeof manifestFile !== 'string' || manifestFile.length === 0) {
    throw new Error('F0.2C carrier cannot construct a terminal receipt from an unvalidated manifest');
  }
  return Object.freeze({ schema: 2, kind: 'f02c-deferred-aftermath-native-carrier', status: 'passed', terminal: validated.terminal,
    declaration: { scenario: 'disposable-cold-bomber-aftermath-restart.json', sha256: validated.declarationSha256 },
    finalManifest: { file: manifestFile, sha256: validated.finalManifestSha256 },
    identity: { precommittedOuterAttempt: validated.identity.outerAttempt, clientRunner: { runId: validated.identity.clientRunId },
      lifecycle: validated.identity.lifecycle },
    scenarios: { clear: { scenario: 'disposable-cold-bomber-aftermath-restart.json', declarationSha256: validated.declarationSha256, manifest: manifestFile } } });
}

/** The production wrapper's one ordinary child invocation; the child owns its lifecycle and final manifest. */
export function isolatedScenarioSupervisorInvocation({ scenarioPath, manifestPath, root, outerAttempt }) {
  if (typeof scenarioPath !== 'string' || typeof manifestPath !== 'string' || typeof root !== 'string' || typeof outerAttempt !== 'string'
  ) throw new Error('F0.2C carrier isolated scenario invocation is malformed');
  // F0.2B's scoped Node harness may return before lifecycle/manifest work.
  // Its ambient control is never authority for this dedicated COLD carrier.
  const environment = { ...process.env };
  delete environment.FRONTIER_V3_TEST_SAVE_OWNER_OBSERVER_ADMISSION_ONLY;
  return Object.freeze({ args: Object.freeze(['tools/frontier-v3-test-pilot/src/run-isolated-scenario.mjs', scenarioPath, manifestPath]),
    options: Object.freeze({ cwd: project, env: Object.freeze({ ...environment, FRONTIER_V3_NATIVE_PROCESS_ROOT: resolve(root, 'process'),
      ...isolatedScenarioOuterAttemptEnvironment(outerAttempt) }), stdio: 'inherit' }) });
}

export async function runIsolatedScenario(invocation) {
  const { args, options } = isolatedScenarioSupervisorInvocation(invocation);
  const child = spawn(process.execPath, args, options);
  const code = await new Promise((resolveExit, reject) => { child.once('error', reject); child.once('exit', resolveExit); });
  if (code !== 0) throw new Error(`F0.2C native carrier scenario failed (${code})`);
}

if (resolve(process.argv[1] ?? '') === fileURLToPath(import.meta.url)) await main();

async function main() {
  const output = resolve(process.argv[2] ?? 'build/f02c-native/aftermath-carrier.json');
  if (!output.startsWith(`${project}/`)) throw new Error('F0.2C carrier output must stay inside its source checkout');
  if (!process.env.DISPLAY) throw new Error('F0.2C carrier requires its task-private visible display');
  const root = dirname(output); await mkdir(root, { recursive: true });
  const scenario = 'disposable-cold-bomber-aftermath-restart.json';
  const declarationPath = resolve(project, 'tools/frontier-v3-test-pilot/scenarios', scenario);
  const declarationSource = await readFile(declarationPath); const declarationSha256 = digest(declarationSource); const declaration = parse(declarationSource, 'declaration');
  // Static validation is deliberately before the native runner can spawn Minecraft.
  assertF02cAftermathDeclaration(declaration);
  const manifestPath = resolve(root, 'clear.manifest.json');
  const outerAttempt = randomUUID();
  await runIsolatedScenario({ scenarioPath: declarationPath, manifestPath, root, outerAttempt });
  const envelope = { scenario, declarationSource, declarationSha256, manifestSource: await readFile(manifestPath), outerAttempt };
  const validated = preflightF02cAftermathCarrier(envelope);
  const receipt = buildF02cAftermathCarrierReceipt(validated);
  await writeFile(output, `${JSON.stringify(receipt, null, 2)}\n`, { flag: 'wx' });
  console.log(JSON.stringify(receipt));
}

function digest(value) { return createHash('sha256').update(value).digest('hex'); }
function parse(value, label) { try { return JSON.parse(value); } catch { throw new Error(`F0.2C carrier wrapper ${label} bytes are malformed`); } }
