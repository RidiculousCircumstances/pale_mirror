import { execFile } from 'node:child_process';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { availableParallelism, hostname } from 'node:os';
import { dirname, resolve } from 'node:path';
import { promisify } from 'node:util';

const exec = promisify(execFile);
if (import.meta.url === `file://${process.argv[1]}`) await main();

export function evaluateCapacity({ workers, cpu, memAvailableMiB, diskAvailableMiB }) {
  const reasons = [];
  if (workers !== 4) reasons.push('exactly four workers are required');
  if (!Number.isInteger(cpu) || cpu < workers) reasons.push(`need ${workers} CPU slots`);
  if (!Number.isFinite(memAvailableMiB) || memAvailableMiB < workers * 2048) reasons.push(`need ${workers * 2048} MiB available memory`);
  if (!Number.isFinite(diskAvailableMiB) || diskAvailableMiB < workers * 5120) reasons.push(`need ${workers * 5120} MiB available disk`);
  return { admitted: reasons.length === 0, workers, cpu, memAvailableMiB, diskAvailableMiB, reasons };
}

async function main() {
  const values = Object.fromEntries(process.argv.slice(2).map((value) => {
    const [key, entry] = value.slice(2).split('=', 2); return [key, entry];
  }));
  const root = resolve(values.root ?? '');
  const output = resolve(values.output ?? '');
  const workers = Number(values.workers ?? '');
  if (!root.startsWith('/') || !output.startsWith(`${root}/`) || workers !== 4) throw new Error('usage: f0vb-capacity-preflight --root=/absolute/task-root --workers=4 --output=/absolute/task-root/receipt.json');
  const result = evaluateCapacity({ workers, cpu: availableParallelism(), memAvailableMiB: await availableMemoryMiB(), diskAvailableMiB: await availableDiskMiB(root) });
  const receipt = { schema: 1, kind: 'f0vb-same-host-capacity-preflight', root, host: hostname(), ...result };
  await mkdir(dirname(output), { recursive: true });
  await writeFile(output, `${JSON.stringify(receipt)}\n`, { flag: 'wx' });
  if (!result.admitted) throw new Error(`F0.VB same-host capacity preflight rejected: ${result.reasons.join('; ')}`);
  console.log(JSON.stringify(receipt));
}

async function availableMemoryMiB() {
  const content = await readFile('/proc/meminfo', 'utf8');
  const match = content.match(/^MemAvailable:\s+(\d+) kB$/m);
  if (!match) throw new Error('F0.VB cannot read MemAvailable');
  return Math.floor(Number(match[1]) / 1024);
}

async function availableDiskMiB(path) {
  const { stdout } = await exec('df', ['-Pk', path]);
  const line = stdout.trim().split('\n').at(-1).trim().split(/\s+/);
  if (line.length < 4 || !/^\d+$/.test(line[3])) throw new Error('F0.VB cannot read available disk');
  return Math.floor(Number(line[3]) / 1024);
}
