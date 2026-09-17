import { spawn } from 'node:child_process';
import { mkdir, readFile, rm, writeFile } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { ensurePreparedLaunchWorkingDirectory, preparedLaunch } from './prepared-launch.mjs';
import { requirePreparedF0vBuild } from './prepared-build.mjs';
import { verifiedPrivateDisplayEnvironment, verifiedVisibleDisplayEnvironment } from './visible-display.mjs';

const project = resolve(dirname(fileURLToPath(import.meta.url)), '../../..');
const [identityArgument, outputArgument] = process.argv.slice(2);
if (!identityArgument || !outputArgument) throw new Error('usage: run-pilot-glfw-admission <prepared-identity.json> <build/receipt.json>');
const identityPath = resolve(project, identityArgument); const output = resolve(project, outputArgument);
if (!output.startsWith(resolve(project, 'build') + '/')) throw new Error('GLFW admission output must remain under build/');
const root = dirname(output); const receiptPath = resolve(root, 'client-glfw-receipt.json');
const visible = process.env.FRONTIER_V3_PILOT_VISIBLE_DISPLAY === 'true';
// The wrapper's private-display marker is a capability bit.  Keep accepting a
// numeric legacy selector for direct callers, but never reinterpret `true` as
// an X display name.
const privateMarker = process.env.FRONTIER_V3_PILOT_PRIVATE_DISPLAY;
const display = visible ? ':0' : (privateMarker === 'true' ? process.env.DISPLAY : privateMarker) ?? process.env.DISPLAY ?? ':97';
if (visible ? display !== ':0' : !/^:[1-9][0-9]*$/.test(display)) {
  throw new Error(visible ? 'visible GLFW admission requires DISPLAY=:0' : 'GLFW admission display must be a private numeric X display');
}
const identity = JSON.parse(await readFile(identityPath, 'utf8'));
await mkdir(root, { recursive: true }); await rm(receiptPath, { force: true });
const namespaceOwned = process.env.FRONTIER_V3_PILOT_PRIVATE_X11_NAMESPACE === 'true';
let xvfb; let client;
try {
  if (!visible && !namespaceOwned) {
    xvfb = spawn('Xvfb', [display, '-screen', '0', '1280x720x24', '-nolisten', 'tcp'], { stdio: 'ignore' });
    await waitFor(() => xvfb.exitCode !== null ? Promise.reject(new Error(`task-private Xvfb exited (${xvfb.exitCode})`)) : existsSync(`/tmp/.X11-unix/X${display.slice(1)}`), 10_000, 'task-private Xvfb did not expose its socket');
  }
  const environment = visible
    ? await verifiedVisibleDisplayEnvironment({ ...process.env, DISPLAY: display })
    : await verifiedPrivateDisplayEnvironment({ ...process.env, DISPLAY: display });
  await requirePreparedF0vBuild(project, identity);
  const launch = await preparedLaunch(project, identity, 'client', {
    'pale_mirror.frontier_v3.test_pilot.x11_admission_receipt': receiptPath
  });
  await ensurePreparedLaunchWorkingDirectory(launch, { automatedSemanticClient: true });
  client = spawn(launch.command, launch.args, { cwd: launch.cwd, env: { ...environment, XDG_SESSION_TYPE: 'x11', WAYLAND_DISPLAY: '__pale_mirror_pilot_xwayland_only__' }, stdio: ['ignore', 'pipe', 'pipe'] });
  let outputText = '';
  for (const stream of [client.stdout, client.stderr]) stream.setEncoding('utf8').on('data', chunk => { outputText += chunk; process.stdout.write(chunk); });
  await waitFor(async () => {
    if (existsSync(receiptPath)) return true;
    if (client.exitCode !== null) throw new Error(`actual pilot client exited before GLFW admission (${client.exitCode}): ${outputText.slice(-1200)}`);
    return false;
  }, 180_000, 'actual pilot client did not publish a GLFW admission receipt');
  const receipt = JSON.parse(await readFile(receiptPath, 'utf8'));
  if (receipt?.kind !== 'frontier-v3-pilot-glfw-admission' || receipt.display !== display
      || !Number.isInteger(receipt.pid) || receipt.pid !== client.pid || !Number.isInteger(receipt.window) || receipt.window === 0) {
    throw new Error(`actual pilot GLFW admission receipt is foreign, malformed, or outside the ${visible ? 'visible' : 'private'} display`);
  }
  await writeFile(output, `${JSON.stringify({ schema: 1, kind: `frontier-v3-pilot-${visible ? 'visible' : 'private'}-glfw-admission`, status: 'passed', display, clientPid: client.pid,
    window: receipt.window, preparedIdentity: identityPath }, null, 2)}\n`, { flag: 'wx' });
  console.log(`PMV3_PILOT_GLFW_ADMISSION display=${display} clientPid=${client.pid} window=${receipt.window}`);
} finally {
  if (client?.exitCode === null && client?.signalCode === null) client.kill('SIGINT');
  if (client) await exited(client);
  if (!visible && !namespaceOwned && xvfb?.exitCode === null && xvfb?.signalCode === null) xvfb.kill('SIGTERM');
  if (!visible && !namespaceOwned && xvfb) await exited(xvfb);
}

async function waitFor(predicate, timeoutMs, message) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) { if (await predicate()) return; await new Promise(resolveDelay => setTimeout(resolveDelay, 100)); }
  throw new Error(message);
}
function exited(child) { return child.exitCode !== null || child.signalCode !== null ? Promise.resolve() : new Promise(resolveExit => child.once('exit', resolveExit)); }
