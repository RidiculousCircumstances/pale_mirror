import { mkdir, rm, writeFile } from 'node:fs/promises';
import { join, resolve } from 'node:path';

/**
 * Serializes only the host-wide durable Minecraft save boundary for otherwise
 * independent native consumers.  The lock lives under the task-owned root;
 * it carries no world, canonical, or prepared-runtime authority.
 */
export async function withGracefulSaveGate({ directory, owner }, operation) {
  if (directory === undefined || directory === '') return operation({ mode: 'independent' });
  if (typeof directory !== 'string' || !directory.startsWith('/') || !owner || typeof owner !== 'object') {
    throw new Error('graceful save gate declaration is malformed');
  }
  const root = resolve(directory);
  const lock = join(root, 'active');
  await mkdir(root, { recursive: true });
  const waitingAtMillis = Date.now();
  for (;;) {
    try {
      await mkdir(lock);
      const acquiredAtMillis = Date.now();
      const receipt = Object.freeze({ mode: 'serialized', directory: root, waitingAtMillis, acquiredAtMillis,
        waitMillis: acquiredAtMillis - waitingAtMillis, owner: Object.freeze({ ...owner }) });
      try {
        await writeFile(join(lock, 'owner.json'), `${JSON.stringify(receipt)}\n`, { flag: 'wx' });
        return await operation(receipt);
      } finally {
        await rm(lock, { recursive: true, force: true });
      }
    } catch (failure) {
      if (failure?.code !== 'EEXIST') throw failure;
      // This is a wake-up cadence, not a save deadline.  The holder still has
      // to produce Minecraft's exact durable-save signal before it can release.
      await new Promise(resolveDelay => setTimeout(resolveDelay, 25));
    }
  }
}
