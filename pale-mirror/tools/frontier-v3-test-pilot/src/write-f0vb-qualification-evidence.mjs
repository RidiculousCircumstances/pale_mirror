import { mkdir, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
const values = Object.fromEntries(process.argv.slice(2).map(value => { const [key, entry] = value.slice(2).split('=', 2); return [key, entry]; }));
const worker = values.worker; const run = values.run; const lease = values.lease; const started = Number(values.started); const finished = Number(values.finished);
if (!/^worker-[0-3]$/.test(worker) || !/^\d+$/.test(run) || !lease?.startsWith('/') || !Number.isInteger(started) || !Number.isInteger(finished) || finished <= started) throw new Error('malformed F0.VB worker evidence');
const output = resolve(values.output ?? ''); if (!output.startsWith(`${process.cwd()}/`)) throw new Error('qualification output escapes workspace');
await mkdir(dirname(output), { recursive: true });
await writeFile(output, JSON.stringify({ schema: 1, kind: 'f0vb-native-lease', worker, run, lease, started, finished }) + '\n', { flag: 'wx' });
