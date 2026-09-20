import { createHash, randomUUID } from 'node:crypto';
import { spawn } from 'node:child_process';
import { mkdir, readFile, stat, writeFile } from 'node:fs/promises';
import { dirname, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { assertF06r3ColdTerminalCarrier } from './f06r3-cold-terminal-carrier.mjs';
import { isolatedScenarioOuterAttemptEnvironment } from './isolated-scenario-attempt.mjs';

const project = resolve(dirname(fileURLToPath(import.meta.url)), '../../..');
const scenario = 'disposable-f06r3-cold-terminal.json';
if (resolve(process.argv[1] ?? '') === fileURLToPath(import.meta.url)) await main();

async function main() {
  const verificationOnly = process.argv[2] === '--verify';
  const output = resolve(verificationOnly ? process.argv[4] : process.argv[2] ?? 'build/f06r3-cold-terminal/cold-terminal-carrier.json');
  const retainedManifest = verificationOnly ? resolve(process.argv[3] ?? '') : null;
  if (!output.startsWith(`${project}/`) || (!verificationOnly && !process.env.DISPLAY)
      || (verificationOnly && (!retainedManifest.startsWith(`${project}/`) || retainedManifest === project))) {
    throw new Error('F0.6R3 COLD terminal carrier requires in-checkout receipt inputs and a task-private display for a native execution');
  }
  await absent(output); await mkdir(dirname(output), { recursive: true });
  const declarationPath = resolve(project, 'tools/frontier-v3-test-pilot/scenarios', scenario);
  const declarationSource = await readFile(declarationPath); const declaration = parse(declarationSource, 'declaration');
  const manifestPath = retainedManifest ?? resolve(dirname(output), 'cold-terminal.manifest.json');
  if (!verificationOnly) {
    const code = await run(['tools/frontier-v3-test-pilot/src/run-isolated-scenario.mjs', declarationPath, manifestPath], {
      cwd: project, env: { ...process.env, FRONTIER_V3_NATIVE_PROCESS_ROOT: resolve(dirname(output), 'process'), ...isolatedScenarioOuterAttemptEnvironment(randomUUID()) }, stdio: 'inherit' });
    if (code !== 0) throw new Error(`F0.6R3 COLD terminal native scenario failed (${code})`);
  }
  const manifestSource = await readFile(manifestPath); const manifest = parse(manifestSource, 'manifest');
  const beforePath = resolve(manifest.recovery?.beforeRestartManifest ?? '');
  const beforeSource = await readFile(beforePath);
  const facts = assertF06r3ColdTerminalCarrier({ declaration, beforeRestart: parse(beforeSource, 'before-restart manifest'), manifest,
    topologyRevisionEvidence: { after: terminalReceiptTopologyRevision(manifestSource) } });
  const receipt = Object.freeze({ schema: 1, kind: 'f06r3-cold-terminal-native-carrier', status: 'passed',
    declaration: { scenario, sha256: digest(declarationSource) }, finalManifest: { file: relative(dirname(output), manifestPath), sha256: digest(manifestSource) },
    beforeRestartManifest: { file: relative(dirname(output), beforePath), sha256: digest(beforeSource) }, facts });
  await writeFile(output, `${JSON.stringify(receipt, null, 2)}\n`, { flag: 'wx' }); console.log(JSON.stringify(receipt));
}
function run(args, options) { return new Promise((resolveExit, reject) => { const child = spawn(process.execPath, args, options); child.once('error', reject); child.once('exit', code => resolveExit(code ?? 1)); }); }
async function absent(path) { try { await stat(path); throw new Error('F0.6R3 COLD terminal receipt already exists'); } catch (error) { if (error?.code !== 'ENOENT') throw error; } }
function parse(value, label) { try { return JSON.parse(value); } catch { throw new Error(`F0.6R3 ${label} bytes are malformed`); } }
function digest(value) { return createHash('sha256').update(value).digest('hex'); }
export function terminalReceiptTopologyRevision(source) {
  const matches = [...String(source).matchAll(/"terminalReceipt"\s*:\s*\{[\s\S]*?"topology"\s*:\s*\{[\s\S]*?"revision"\s*:\s*"?(-?\d+)"?/g)];
  const revisions = new Set(matches.map(match => match[1]));
  if (revisions.size !== 1 || !/^-?\d+$/.test(matches[0]?.[1])) throw new Error('F0.6R3 terminal receipt topology revision is not exactly representable in its evidence bytes');
  return matches[0][1];
}
