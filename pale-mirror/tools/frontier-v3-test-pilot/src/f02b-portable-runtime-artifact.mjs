import { createHash } from 'node:crypto';
import { cp, mkdir, readFile, readdir, stat, writeFile } from 'node:fs/promises';
import { dirname, relative, resolve } from 'node:path';
import { readRuntimeManifest } from './f0vc-prepared-runtime.mjs';

const SHA = /^[a-f0-9]{64}$/;

/** Stages the immutable F0.VC store beside the producer descriptor for artifact transport. */
export async function stagePortableRuntimeArtifact({ runtime, output }) {
  const descriptorPath = resolve(runtime);
  const descriptor = await readDescriptor(descriptorPath);
  const manifestPath = resolve(descriptor.manifest);
  const manifest = await verifyClosure(manifestPath, descriptor.contentSha256);
  const root = dirname(manifestPath);
  const destination = resolve(output, descriptor.contentSha256);
  await absent(destination, 'portable producer runtime');
  await mkdir(dirname(destination), { recursive: true });
  await cp(root, destination, { recursive: true, errorOnExist: true, force: false });
  await verifyClosure(resolve(destination, 'manifest.json'), descriptor.contentSha256);
  return { contentSha256: manifest.contentSha256, root: destination };
}

/** Binds a downloaded producer artifact to the consumer's independently rooted checkout. */
export async function bindPortableRuntimeArtifact({ runtime, artifact, output }) {
  const descriptor = await readDescriptor(resolve(runtime));
  const root = resolve(artifact, descriptor.contentSha256);
  const manifest = resolve(root, 'manifest.json');
  await verifyClosure(manifest, descriptor.contentSha256);
  const bound = { ...descriptor, manifest: relative(dirname(resolve(output)), manifest) };
  await mkdir(dirname(resolve(output)), { recursive: true });
  await writeFile(resolve(output), `${JSON.stringify(bound, null, 2)}\n`, { flag: 'wx' });
  return bound;
}

export async function verifyPortableRuntimeArtifact({ runtime, artifact }) {
  const descriptor = await readDescriptor(resolve(runtime));
  return await verifyClosure(resolve(artifact, descriptor.contentSha256, 'manifest.json'), descriptor.contentSha256);
}

async function readDescriptor(path) {
  let value; try { value = JSON.parse(await readFile(path, 'utf8')); } catch { throw new Error('F0.2B portable runtime descriptor is unreadable'); }
  if (!value || !SHA.test(value.contentSha256 ?? '') || typeof value.manifest !== 'string' || !value.manifest.startsWith('/')) {
    throw new Error('F0.2B portable runtime descriptor is malformed');
  }
  return value;
}

async function verifyClosure(manifestPath, expected) {
  const manifest = await readRuntimeManifest(manifestPath);
  if (manifest.contentSha256 !== expected) throw new Error('F0.2B portable runtime content identity drifted');
  for (const entry of manifest.inputs) {
    const path = resolve(dirname(manifestPath), manifest.entriesRoot, entry.id);
    const digest = await digestPath(path);
    if (digest !== entry.sha256) throw new Error('F0.2B portable runtime closure is incomplete or corrupt');
  }
  return manifest;
}

async function absent(path, label) { try { await stat(path); throw new Error(`F0.2B refuses to overwrite ${label}`); } catch (error) { if (error?.code !== 'ENOENT') throw error; } }
async function digestPath(path) {
  const value = await stat(path);
  if (value.isFile()) return createHash('sha256').update(await readFile(path)).digest('hex');
  if (!value.isDirectory()) throw new Error('F0.2B portable runtime entry is not a file or directory');
  const digest = createHash('sha256'); await digestDirectory(path, path, digest); return digest.digest('hex');
}
async function digestDirectory(root, directory, digest) {
  for (const entry of (await readdir(directory, { withFileTypes: true })).sort((left, right) => left.name.localeCompare(right.name))) {
    const target = resolve(directory, entry.name);
    if (entry.isDirectory()) await digestDirectory(root, target, digest);
    else if (entry.isFile()) { digest.update(relative(root, target)); digest.update('\0'); digest.update(await readFile(target)); }
    else throw new Error('F0.2B portable runtime entry has an unsupported filesystem member');
  }
}

if (process.argv[1] !== undefined && resolve(process.argv[1]) === new URL(import.meta.url).pathname) {
  const values = Object.fromEntries(process.argv.slice(2).map((entry) => { const [key, value] = entry.replace(/^--/, '').split('=', 2); return [key, value]; }));
  if (values.mode === 'stage') await stagePortableRuntimeArtifact(values);
  else if (values.mode === 'bind') await bindPortableRuntimeArtifact(values);
  else if (values.mode === 'verify') await verifyPortableRuntimeArtifact(values);
  else throw new Error('usage: f02b-portable-runtime-artifact --mode=stage|bind|verify ...');
}
