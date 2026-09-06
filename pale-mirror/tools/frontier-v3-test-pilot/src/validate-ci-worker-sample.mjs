import { validateCiCorrectnessAdmission } from './ci-matrix.mjs';

if (process.argv[1] !== undefined && new URL(import.meta.url).pathname === process.argv[1]) await main();

/**
 * This is deliberately a pre-Gradle admission boundary.  It consumes only
 * reusable-workflow inputs and the expanded job tuple; it has no access to a
 * prepared artifact, a server, a client or a world.
 */
export async function main(environment = process.env) {
  const admitted = parseEnvironment(environment);
  console.log(JSON.stringify({ status: 'admitted', measurement: admitted.measurement, worker: admitted.worker, port: admitted.port }));
  return admitted;
}

export function parseEnvironment(environment) {
  const measurement = environment?.FRONTIER_V3_CI_MEASUREMENT;
  const worker = environment?.FRONTIER_V3_PILOT_WORKER_ID;
  const port = environment?.FRONTIER_V3_PILOT_PORT;
  const rawWorkers = environment?.FRONTIER_V3_CI_WORKERS_JSON;
  if (typeof rawWorkers !== 'string' || rawWorkers.length === 0) throw new Error('CI correctness worker matrix input is missing');
  let workers;
  try {
    workers = JSON.parse(rawWorkers);
  } catch {
    throw new Error('CI correctness worker matrix input is not valid JSON');
  }
  return validateCiCorrectnessAdmission({ measurement, workers, worker, port });
}
