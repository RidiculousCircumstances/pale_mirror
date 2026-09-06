import { createHash } from 'node:crypto';
import { mkdir, stat, writeFile } from 'node:fs/promises';
import { relative, resolve } from 'node:path';

/**
 * Creates only disposable server-process configuration for an already allocated fixture clone.
 * It does not enter, decode or edit the world directory: the first allowed writer of that clone
 * remains the normal Minecraft server JVM after launch.
 */
export async function prepareFixtureConsumerRuntime({ project, worldName, port, rconPort, rconPassword, username, viewDistance }) {
  const root = resolve(project); const runtime = resolve(root, 'pale-mirror-neoforge/build/runs/frontier-v3-pilot-server');
  const world = resolve(runtime, worldName);
  const relativeWorld = relative(runtime, world);
  if (!/^[a-z0-9][a-z0-9_-]{0,63}$/.test(worldName) || relativeWorld !== worldName || !await isDirectory(world)) {
    throw new Error('fixture consumer world is unavailable or malformed');
  }
  if (!Number.isInteger(port) || port < 1024 || port > 65534 || !Number.isInteger(rconPort) || rconPort < 1024 || rconPort > 65534
      || port === rconPort || !/^[0-9a-f-]{36}$/i.test(rconPassword) || !/^[A-Za-z0-9_]{3,16}$/.test(username)
      || !Number.isInteger(viewDistance) || viewDistance < 2 || viewDistance > 32) {
    throw new Error('fixture consumer runtime configuration is malformed');
  }
  await mkdir(runtime, { recursive: true });
  await writeFile(resolve(runtime, 'eula.txt'), 'eula=true\n', 'utf8');
  await writeFile(resolve(runtime, 'server.properties'), `level-name=${worldName}\nserver-ip=127.0.0.1\nserver-port=${port}\nenable-rcon=true\nrcon.port=${rconPort}\nrcon.password=${rconPassword}\nonline-mode=false\nallow-flight=true\ngamemode=creative\ndifficulty=normal\nspawn-protection=0\nview-distance=${viewDistance}\nsimulation-distance=${viewDistance}\n`, 'utf8');
  await writeFile(resolve(runtime, 'ops.json'), `[\n  {"uuid":"${offlineUuid(username)}","name":"${username}","level":4,"bypassesPlayerLimit":false}\n]\n`, 'utf8');
  return Object.freeze({ runtimeDirectory: runtime, worldDirectory: world, worldName, port, rconPort, username, viewDistance });
}

function offlineUuid(username) {
  const bytes = createHash('md5').update(`OfflinePlayer:${username}`, 'utf8').digest();
  bytes[6] = (bytes[6] & 0x0f) | 0x30; bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = bytes.toString('hex');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}
async function isDirectory(path) { try { return (await stat(path)).isDirectory(); } catch (error) { if (error?.code === 'ENOENT') return false; throw error; } }
