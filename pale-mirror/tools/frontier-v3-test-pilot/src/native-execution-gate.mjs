import { mkdir, rm, writeFile } from 'node:fs/promises';
import { join, resolve } from 'node:path';

/**
 * Serializes the expensive live portion of otherwise simultaneous disposable
 * worlds.  Every worker still boots an independently named normal server; the
 * lease merely keeps a host-wide save/fast-forward storm from turning a real
 * durable-stop observation into a scheduler artifact.
 */
export async function acquireNativeExecutionGate({ directory, owner }) {
  if (directory === undefined || directory === '') return { receipt: null, release: async () => {} };
  if (typeof directory !== 'string' || !directory.startsWith('/') || !owner || typeof owner !== 'object') {
    throw new Error('native execution gate declaration is malformed');
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
      } catch (failure) {
        await rm(lock, { recursive: true, force: true });
        throw failure;
      }
      let released = false;
      return Object.freeze({ receipt, release: async () => {
        if (released) return;
        released = true;
        await rm(lock, { recursive: true, force: true });
      } });
    } catch (failure) {
      if (failure?.code !== 'EEXIST') throw failure;
      await new Promise(resolveDelay => setTimeout(resolveDelay, 25));
    }
  }
}
