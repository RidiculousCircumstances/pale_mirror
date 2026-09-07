import { mkdir, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { declaredNamespaces } from './f0vb-qualification.mjs';

const values = Object.fromEntries(process.argv.slice(2).map((value) => { const [key, entry] = value.slice(2).split('=', 2); return [key, entry]; }));
const namespaces = declaredNamespaces({ workspace: values.workspace, temp: values.temp, runId: Number(values.run), runAttempt: Number(values.attempt), worker: values.worker });
const output = resolve(values.output ?? '');
if (!output.startsWith(`${process.cwd()}/`)) throw new Error('F0.VB namespace output escapes workspace');
await Promise.all(['workspace', 'temp', 'gradle', 'cache', 'world', 'process'].map((field) => mkdir(namespaces[field], { recursive: true })));
await writeFile(output, `${JSON.stringify(namespaces)}\n`, { flag: 'wx' });
console.log(JSON.stringify({ worker: values.worker, namespaces }));
