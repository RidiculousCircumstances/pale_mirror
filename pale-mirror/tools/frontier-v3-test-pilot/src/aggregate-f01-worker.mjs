import { readFile, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';

const values = Object.fromEntries(process.argv.slice(2).map(value => {
  const [key, entry] = value.slice(2).split('=', 2); return [key, entry];
}));
const root = resolve(values.input ?? '');
const output = resolve(values.output ?? '');
const variants = (values.variants ?? '').split(',').filter(Boolean);
if (variants.length === 0 || new Set(variants).size !== variants.length) throw new Error('F0.1 worker aggregate requires distinct assigned variants');

const aggregate = { schema: 1, kind: 'frontier-v3-f01-paired-harvest-native-matrix', build: null, variants: [] };
for (const variant of variants) {
  try {
    const report = JSON.parse(await readFile(resolve(root, variant, 'matrix.json'), 'utf8'));
    if (aggregate.build === null && report.build) aggregate.build = report.build;
    const entry = report.variants?.find(candidate => candidate.variant === variant);
    if (!entry) throw new Error('missing declared variant result');
    aggregate.variants.push(entry);
    if (report.error) aggregate.error = `${aggregate.error ?? ''}${aggregate.error ? '; ' : ''}${variant}: ${report.error}`;
  } catch (failure) {
    aggregate.variants.push({ variant, runs: [{ status: 'failed', failure: String(failure?.message ?? failure) }] });
    aggregate.error = `${aggregate.error ?? ''}${aggregate.error ? '; ' : ''}${variant}: report unavailable`;
  }
}
await writeFile(output, `${JSON.stringify(aggregate)}\n`, { flag: 'wx' });
