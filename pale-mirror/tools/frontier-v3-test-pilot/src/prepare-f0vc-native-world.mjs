import { createHash } from 'node:crypto';
import { cp, mkdir, rm, stat, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const project = resolve(dirname(fileURLToPath(import.meta.url)), '../../..');
if (process.argv[1] !== undefined && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) await main();

/**
 * The only mutable part of an F0.VC consumer view.  This is the byte-for-byte disposable-lite
 * server/client setup formerly performed by a Gradle task; moving it here prevents a consumer
 * from configuring, compiling, transforming, or packaging candidate code after it accepts the
 * producer manifest.
 */
export async function prepareNativeWorld({ world, seed, port, rconPort, password, username, profile, viewDistance, reset = true }) {
  validate({ world, seed, port, rconPort, password, username, profile, viewDistance, reset });
  const serverRoot = resolve(project, 'pale-mirror-neoforge/build/runs/frontier-v3-pilot-server');
  const worldRoot = resolve(serverRoot, world);
  if (reset) await rm(worldRoot, { recursive: true, force: true });
  await rm(resolve(serverRoot, 'mods'), { recursive: true, force: true });
  await mkdir(worldRoot, { recursive: true });
  await writeFile(resolve(serverRoot, 'eula.txt'), 'eula=true\n');
  await writeFile(resolve(serverRoot, 'server.properties'), `level-name=${world}\nlevel-seed=${seed}\nserver-ip=127.0.0.1\nserver-port=${port}\nenable-rcon=true\nrcon.port=${rconPort}\nrcon.password=${password}\nonline-mode=false\nallow-flight=true\ngamemode=creative\ndifficulty=normal\nspawn-protection=0\nview-distance=${viewDistance}\nsimulation-distance=${viewDistance}\n`);
  const uuid = offlineUuid(username);
  await writeFile(resolve(serverRoot, 'ops.json'), `[\n  {"uuid":"${uuid}","name":"${username}","level":4,"bypassesPlayerLimit":false}\n]\n`);
  const source = resolve(project, '..', 'datapacks/pale-mirror-graybox');
  try { if (!(await stat(resolve(source, 'data/pale_mirror/dimension/frontier_graybox.json'))).isFile()) throw new Error('missing canonical graybox datapack'); }
  catch (error) { throw new Error('F0.VC native world cannot locate canonical graybox datapack', { cause: error }); }
  await cp(source, resolve(worldRoot, 'datapacks/pale-mirror-graybox'), { recursive: true, force: false, errorOnExist: true });
  const clientRoot = resolve(project, 'pale-mirror-neoforge/build/runs/frontier-v3-pilot-client');
  await mkdir(resolve(clientRoot, 'config'), { recursive: true });
  await writeFile(resolve(clientRoot, 'options.txt'), 'onboardAccessibility:b:true\nskipMultiplayerWarning:b:true\nnarrator:0\n');
  await writeFile(resolve(clientRoot, 'config/fml.toml'), 'earlyWindowControl = false\ndisableConfigWatcher = false\nmaxThreads = -1\nversionCheck = true\ndefaultConfigPath = "defaultconfigs"\ndisableOptimizedDFU = true\nearlyWindowProvider = "fmlearlywindow"\nearlyWindowWidth = 854\nearlyWindowHeight = 480\nearlyWindowFBScale = 1\nearlyWindowMaximized = false\nearlyWindowSkipGLVersions = []\nearlyWindowSquir = false\n');
  return Object.freeze({ worldRoot, serverRoot, profile, preparedAt: new Date().toISOString() });
}

async function main() {
  const values = Object.fromEntries(process.argv.slice(2).map((entry) => { const [key, value] = entry.split('=', 2); return [key, value]; }));
  await prepareNativeWorld({ world: values.world, seed: Number(values.seed), port: Number(values.port), rconPort: Number(values.rcon), password: values.password,
    username: values.username, profile: values.profile, viewDistance: Number(values.view), reset: values.reset !== 'false' });
}
function validate(value) {
  if (!/^[a-z0-9][a-z0-9_-]{0,63}$/.test(value.world ?? '') || !Number.isSafeInteger(value.seed) || !validPort(value.port) || !validPort(value.rconPort)
      || value.port === value.rconPort || !/^[0-9a-f-]{36}$/.test(value.password ?? '') || !/^[A-Za-z0-9_]{3,16}$/.test(value.username ?? '')
      || value.profile !== 'world' || !Number.isInteger(value.viewDistance) || value.viewDistance < 2 || value.viewDistance > 32) throw new Error('F0.VC native world inputs are malformed');
}
function validPort(value) { return Number.isInteger(value) && value >= 1024 && value <= 65535; }
function offlineUuid(username) { const hash = createHash('md5').update(`OfflinePlayer:${username}`).digest(); hash[6] = (hash[6] & 0x0f) | 0x30; hash[8] = (hash[8] & 0x3f) | 0x80; const hex = hash.toString('hex'); return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`; }
