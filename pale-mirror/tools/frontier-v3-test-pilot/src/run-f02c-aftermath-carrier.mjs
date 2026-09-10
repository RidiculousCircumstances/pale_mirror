import { createHash } from 'node:crypto';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { spawn } from 'node:child_process';
import { dirname, resolve } from 'node:path';
import { assertF02cAftermathCarrier } from './f02c-aftermath-carrier.mjs';

const output = resolve(process.argv[2] ?? 'build/f02c-native/aftermath-carrier.json');
const project = process.cwd();
if (!output.startsWith(`${project}/`)) throw new Error('F0.2C carrier output must stay inside its source checkout');
if (!process.env.DISPLAY) throw new Error('F0.2C carrier requires its task-private visible display');
const root = dirname(output); await mkdir(root, { recursive: true });
const scenarios = [
  ['clear', 'disposable-cold-bomber-aftermath-restart.json']
];
const receipts = {};
for (const [name, scenario] of scenarios) {
  const declarationPath = resolve(project, 'tools/frontier-v3-test-pilot/scenarios', scenario);
  const declarationSource = await readFile(declarationPath);
  const manifestPath = resolve(root, `${name}.manifest.json`);
  await run([process.execPath, 'tools/frontier-v3-test-pilot/src/run-isolated-scenario.mjs', declarationPath, manifestPath]);
  const value = JSON.parse(await readFile(manifestPath, 'utf8'));
  const beforePath = value.recovery?.beforeRestartManifest;
  receipts[name] = { scenario, declaration: JSON.parse(declarationSource), declarationSha256: createHash('sha256').update(declarationSource).digest('hex'),
    manifest: value, ...(beforePath ? { before: JSON.parse(await readFile(resolve(beforePath), 'utf8')) } : {}) };
}
const terminal = assertF02cAftermathCarrier({
  clearDeclaration: receipts.clear.declaration,
  clearBefore: receipts.clear.before,
  clearAfter: receipts.clear.manifest
});
const receipt = { schema: 1, kind: 'f02c-deferred-aftermath-native-carrier', status: 'passed', terminal, scenarios: Object.fromEntries(Object.entries(receipts)
  .map(([name, value]) => [name, { scenario: value.scenario, declarationSha256: value.declarationSha256, manifest: `${name}.manifest.json`,
    ...(value.before ? { beforeRestartManifest: `${name}.manifest.before-restart.json` } : {}) }])) };
await writeFile(output, `${JSON.stringify(receipt, null, 2)}\n`, { flag: 'wx' });
console.log(JSON.stringify(receipt));

async function run(args) {
  const child = spawn(args[0], args.slice(1), { cwd: project, env: { ...process.env, FRONTIER_V3_NATIVE_PROCESS_ROOT: resolve(root, 'process') }, stdio: 'inherit' });
  const code = await new Promise((resolveExit, reject) => { child.once('error', reject); child.once('exit', resolveExit); });
  if (code !== 0) throw new Error(`F0.2C native carrier scenario failed (${code})`);
}
