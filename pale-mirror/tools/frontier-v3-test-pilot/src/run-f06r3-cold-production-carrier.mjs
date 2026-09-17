import { createHash, randomUUID } from 'node:crypto';
import { spawn } from 'node:child_process';
import { mkdir, readFile, stat, writeFile } from 'node:fs/promises';
import { dirname, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { assertF06r3ColdProductionCarrier } from './f06r3-cold-production-carrier.mjs';
import { isolatedScenarioOuterAttemptEnvironment } from './isolated-scenario-attempt.mjs';

const project = resolve(dirname(fileURLToPath(import.meta.url)), '../../..');
const scenario = 'disposable-f06r3-cold-production.json';
if (resolve(process.argv[1] ?? '') === fileURLToPath(import.meta.url)) await main();

async function main() {
  const output = resolve(process.argv[2] ?? 'build/f06r3-cold-production/cold-production-carrier.json');
  if (!output.startsWith(`${project}/`) || !process.env.DISPLAY) throw new Error('F0.6R3 COLD production carrier requires an in-checkout output and task-private display');
  await absent(output); await mkdir(dirname(output), { recursive: true });
  const declarationPath = resolve(project, 'tools/frontier-v3-test-pilot/scenarios', scenario);
  const declarationSource = await readFile(declarationPath); const declaration = parse(declarationSource, 'declaration');
  const manifestPath = resolve(dirname(output), 'cold-production.manifest.json');
  const code = await run(['tools/frontier-v3-test-pilot/src/run-isolated-scenario.mjs', declarationPath, manifestPath], {
    cwd: project, env: { ...process.env, FRONTIER_V3_NATIVE_PROCESS_ROOT: resolve(dirname(output), 'process'), ...isolatedScenarioOuterAttemptEnvironment(randomUUID()) }, stdio: 'inherit' });
  if (code !== 0) throw new Error(`F0.6R3 COLD production native scenario failed (${code})`);
  const manifestSource = await readFile(manifestPath); const manifest = parse(manifestSource, 'manifest');
  const facts = assertF06r3ColdProductionCarrier({ declaration, manifest });
  const receipt = Object.freeze({ schema: 1, kind: 'f06r3-cold-production-native-carrier', status: 'passed',
    declaration: { scenario, sha256: digest(declarationSource) }, finalManifest: { file: relative(dirname(output), manifestPath), sha256: digest(manifestSource) }, facts });
  await writeFile(output, `${JSON.stringify(receipt, null, 2)}\n`, { flag: 'wx' }); console.log(JSON.stringify(receipt));
}
function run(args, options) { return new Promise((resolveExit, reject) => { const child = spawn(process.execPath, args, options); child.once('error', reject); child.once('exit', code => resolveExit(code ?? 1)); }); }
async function absent(path) { try { await stat(path); throw new Error('F0.6R3 COLD production receipt already exists'); } catch (error) { if (error?.code !== 'ENOENT') throw error; } }
function parse(value, label) { try { return JSON.parse(value); } catch { throw new Error(`F0.6R3 ${label} bytes are malformed`); } }
function digest(value) { return createHash('sha256').update(value).digest('hex'); }
