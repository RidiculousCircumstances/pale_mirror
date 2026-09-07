import { writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';

const values = Object.fromEntries(process.argv.slice(2).map((value) => { const [key, entry] = value.slice(2).split('=', 2); return [key, entry]; }));
const output = resolve(values.output ?? '');
if (!/^worker-[0-3]$/.test(values.worker ?? '') || !/^\d+$/.test(values.run ?? '') || !output.startsWith(`${process.cwd()}/`) || !process.env.GITHUB_TOKEN) {
  throw new Error('F0.VB job identity capture has invalid invocation');
}
const url = `https://api.github.com/repos/${values.repository}/actions/runs/${values.run}/jobs?filter=latest&per_page=100`;
let identity;
for (let attempt = 0; attempt < 20 && !identity; attempt++) {
  const response = await fetch(url, { headers: { authorization: `Bearer ${process.env.GITHUB_TOKEN}`, 'x-github-api-version': '2022-11-28' } });
  if (!response.ok) throw new Error(`F0.VB cannot read current job identity (${response.status})`);
  const jobs = (await response.json()).jobs ?? [];
  const job = jobs.find((value) => value.name === `F0.VB ${values.worker}` && value.status === 'in_progress'
    && Number.isSafeInteger(value.id) && Number.isSafeInteger(value.runner_id) && typeof value.runner_name === 'string' && value.runner_name);
  if (job) identity = { jobId: job.id, runnerId: job.runner_id, runnerName: job.runner_name };
  else await new Promise((resolveDelay) => setTimeout(resolveDelay, 1000));
}
if (!identity) throw new Error('F0.VB cannot bind this worker to an active GitHub job and runner');
await writeFile(output, `${JSON.stringify(identity)}\n`, { flag: 'wx' });
