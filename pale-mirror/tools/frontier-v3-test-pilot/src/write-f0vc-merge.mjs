import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { mergeDirectory } from './f0vc-pipeline.mjs';

if (process.argv[1] !== undefined && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) await main();

async function main() {
  const values = Object.fromEntries(process.argv.slice(2).map((entry) => { const [key, value] = entry.replace(/^--/, '').split('=', 2); return [key, value]; }));
  const planPath = resolve(values.plan ?? ''); const input = resolve(values.input ?? ''); const output = resolve(values.output ?? '');
  try {
    const plan = JSON.parse(await readFile(planPath, 'utf8'));
    await mergeDirectory({ plan, input, output });
  } catch (error) {
    try { await readFile(output, 'utf8'); }
    catch {
      await mkdir(dirname(output), { recursive: true });
      await writeFile(output, `${JSON.stringify({ schema: 1, kind: 'frontier-v3-f0vc-native-merge', status: 'incomplete',
        failure: String(error?.message ?? error), planAvailable: false }, null, 2)}\n`, { flag: 'wx' });
    }
    throw error;
  }
}
