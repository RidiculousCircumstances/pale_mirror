import { createHash, randomUUID } from 'node:crypto';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { spawn } from 'node:child_process';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { assertF02cAftermathCarrier, assertF02cAftermathDeclaration, assertF02cAftermathIdentity } from './f02c-aftermath-carrier.mjs';
import { ISOLATED_SCENARIO_OUTER_ATTEMPT_ENV } from './isolated-scenario-attempt.mjs';

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
  await run([process.execPath, 'tools/frontier-v3-test-pilot/src/run-isolated-scenario.mjs', declarationPath, manifestPath], root, outerAttempt);
  const envelope = { scenario, declarationSource, declarationSha256, manifestSource: await readFile(manifestPath), outerAttempt };
  const validated = preflightF02cAftermathCarrier(envelope);
  const receipt = { schema: 2, kind: 'f02c-deferred-aftermath-native-carrier', status: 'passed', terminal: validated.terminal,
    declaration: { scenario, sha256: validated.declarationSha256 }, finalManifest: { file: 'clear.manifest.json', sha256: validated.finalManifestSha256 },
    identity: { precommittedOuterAttempt: validated.identity.outerAttempt, clientRunner: { runId: validated.identity.clientRunId }, lifecycle: validated.identity.lifecycle },
    scenarios: { clear: { scenario, declarationSha256: validated.declarationSha256, manifest: 'clear.manifest.json' } } };
  await writeFile(output, `${JSON.stringify(receipt, null, 2)}\n`, { flag: 'wx' });
  console.log(JSON.stringify(receipt));
}

async function run(args, root, outerAttempt) {
  const child = spawn(args[0], args.slice(1), { cwd: project,
    env: { ...process.env, FRONTIER_V3_NATIVE_PROCESS_ROOT: resolve(root, 'process'), [ISOLATED_SCENARIO_OUTER_ATTEMPT_ENV]: outerAttempt }, stdio: 'inherit' });
  const code = await new Promise((resolveExit, reject) => { child.once('error', reject); child.once('exit', resolveExit); });
  if (code !== 0) throw new Error(`F0.2C native carrier scenario failed (${code})`);
}

function digest(value) { return createHash('sha256').update(value).digest('hex'); }
function parse(value, label) { try { return JSON.parse(value); } catch { throw new Error(`F0.2C carrier wrapper ${label} bytes are malformed`); } }
