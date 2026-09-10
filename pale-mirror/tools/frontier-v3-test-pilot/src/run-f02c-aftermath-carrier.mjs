import { createHash } from 'node:crypto';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { spawn } from 'node:child_process';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { assertF02cAftermathCarrier, assertF02cAftermathDeclaration } from './f02c-aftermath-carrier.mjs';

const project = resolve(dirname(fileURLToPath(import.meta.url)), '../../..');

/** A pure envelope preflight for the exact persistent runner output shape. */
export function preflightF02cAftermathCarrier({ scenario, declaration, declarationSha256, manifest }) {
  if (scenario !== 'disposable-cold-bomber-aftermath-restart.json' || typeof declarationSha256 !== 'string'
      || !/^[a-f0-9]{64}$/.test(declarationSha256)) throw new Error('F0.2C carrier wrapper received a foreign declaration envelope');
  assertF02cAftermathDeclaration(declaration);
  return assertF02cAftermathCarrier({ clearDeclaration: declaration, clearManifest: manifest });
}

if (resolve(process.argv[1] ?? '') === fileURLToPath(import.meta.url)) await main();

async function main() {
  const output = resolve(process.argv[2] ?? 'build/f02c-native/aftermath-carrier.json');
  if (!output.startsWith(`${project}/`)) throw new Error('F0.2C carrier output must stay inside its source checkout');
  if (!process.env.DISPLAY) throw new Error('F0.2C carrier requires its task-private visible display');
  const root = dirname(output); await mkdir(root, { recursive: true });
  const scenario = 'disposable-cold-bomber-aftermath-restart.json';
  const declarationPath = resolve(project, 'tools/frontier-v3-test-pilot/scenarios', scenario);
  const declarationSource = await readFile(declarationPath); const declaration = JSON.parse(declarationSource);
  // Static validation is deliberately before the native runner can spawn Minecraft.
  assertF02cAftermathDeclaration(declaration);
  const manifestPath = resolve(root, 'clear.manifest.json');
  await run([process.execPath, 'tools/frontier-v3-test-pilot/src/run-isolated-scenario.mjs', declarationPath, manifestPath], root);
  const envelope = { scenario, declaration, declarationSha256: createHash('sha256').update(declarationSource).digest('hex'),
    manifest: JSON.parse(await readFile(manifestPath, 'utf8')) };
  const terminal = preflightF02cAftermathCarrier(envelope);
  const receipt = { schema: 1, kind: 'f02c-deferred-aftermath-native-carrier', status: 'passed', terminal,
    scenarios: { clear: { scenario, declarationSha256: envelope.declarationSha256, manifest: 'clear.manifest.json' } } };
  await writeFile(output, `${JSON.stringify(receipt, null, 2)}\n`, { flag: 'wx' });
  console.log(JSON.stringify(receipt));
}

async function run(args, root) {
  const child = spawn(args[0], args.slice(1), { cwd: project,
    env: { ...process.env, FRONTIER_V3_NATIVE_PROCESS_ROOT: resolve(root, 'process') }, stdio: 'inherit' });
  const code = await new Promise((resolveExit, reject) => { child.once('error', reject); child.once('exit', resolveExit); });
  if (code !== 0) throw new Error(`F0.2C native carrier scenario failed (${code})`);
}
