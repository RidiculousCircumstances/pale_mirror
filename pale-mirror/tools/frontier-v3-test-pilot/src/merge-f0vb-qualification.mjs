import { readdir, readFile, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
const values = Object.fromEntries(process.argv.slice(2).map(value => { const [key, entry] = value.slice(2).split('=', 2); return [key, entry]; }));
const input = resolve(values.input ?? ''); const output = resolve(values.output ?? ''); const run = values.run;
const evidence = await Promise.all((await readdir(input)).filter(name => name.endsWith('.json')).map(async name => JSON.parse(await readFile(resolve(input, name), 'utf8'))));
if (!/^\d+$/.test(run) || evidence.length !== 4 || new Set(evidence.map(value => value.worker)).size !== 4 || evidence.some(value => value.schema !== 1 || value.kind !== 'f0vb-native-lease' || value.run !== run || !/^worker-[0-3]$/.test(value.worker) || value.finished <= value.started)) throw new Error('F0.VB merge rejects incomplete, duplicate, foreign or malformed worker evidence');
const latestStart = Math.max(...evidence.map(value => value.started)); const earliestFinish = Math.min(...evidence.map(value => value.finished));
if (latestStart >= earliestFinish || new Set(evidence.map(value => value.lease)).size !== 4) throw new Error('F0.VB merge rejects serial acquisition or shared worker namespace');
await writeFile(output, JSON.stringify({ schema: 1, kind: 'f0vb-native-qualification-merge', status: 'ok', run, workers: evidence.map(value => value.worker).sort(), overlapMillis: earliestFinish - latestStart }) + '\n', { flag: 'wx' });
