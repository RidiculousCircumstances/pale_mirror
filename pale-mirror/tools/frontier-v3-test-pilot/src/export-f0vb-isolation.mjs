import { appendFile, readFile } from 'node:fs/promises';
import { resolve } from 'node:path';

const values = Object.fromEntries(process.argv.slice(2).map((value) => { const [key, entry] = value.slice(2).split('=', 2); return [key, entry]; }));
const input = resolve(values.input ?? '');
if (!input.startsWith(`${process.cwd()}/`) || !process.env.GITHUB_ENV) throw new Error('F0.VB namespace export has invalid invocation');
const namespaces = JSON.parse(await readFile(input, 'utf8'));
for (const key of ['workspace', 'temp', 'gradle', 'cache', 'world', 'process', 'display', 'port']) {
  if (typeof namespaces[key] !== 'string' && !Number.isInteger(namespaces[key])) throw new Error('F0.VB namespace export is malformed');
}
const entries = {
  GRADLE_USER_HOME: namespaces.gradle,
  FRONTIER_V3_NATIVE_WORK_ROOT: namespaces.workspace,
  FRONTIER_V3_NATIVE_TEMP_ROOT: namespaces.temp,
  FRONTIER_V3_NATIVE_CACHE_ROOT: namespaces.cache,
  FRONTIER_V3_PILOT_WORLD_ROOT: namespaces.world,
  FRONTIER_V3_PILOT_PORT: String(namespaces.port),
  FRONTIER_V3_NATIVE_PROCESS_ROOT: namespaces.process,
  DISPLAY: namespaces.display
};
await appendFile(process.env.GITHUB_ENV, `${Object.entries(entries).map(([key, value]) => `${key}=${value}`).join('\n')}\n`, 'utf8');
