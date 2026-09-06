import { createHash, randomUUID } from 'node:crypto';
import { chmod, copyFile, mkdir, readFile, readdir, rename, rm, stat, writeFile } from 'node:fs/promises';
import { constants as fsConstants } from 'node:fs';
import { dirname, join, relative, resolve } from 'node:path';

export const DEVELOPMENT_FIXTURE_IMAGE_SCHEMA = 1;
const KIND = 'frontier-v3-development-fixture-image';
const MAX_FILES = 16_384;
const MAX_BYTES = 1_073_741_824;

/**
 * Creates a content-addressed, development-only opaque world image.  The harness never opens
 * NBT/region/WAL data: it only copies raw bytes and verifies a whole-directory digest.  A caller
 * must supply the exact durable server-stop acknowledgement; a stopped launcher or a directory
 * that merely happens to exist is not enough authority to cache a world.
 */
export async function createDevelopmentFixtureImage({ project, sourceDirectory, imageRoot = 'build/frontier-v3-development-fixtures',
  imageId, metadata, durableStop }) {
  const root = resolve(project); const source = safeGeneratedDirectory(root, sourceDirectory, 'fixture source');
  const images = safeRootBuildDirectory(root, imageRoot, 'fixture image root');
  const checkedMetadata = validateMetadata(metadata); validateDurableStop(durableStop);
  const sourceTree = await digestTree(source);
  const manifest = Object.freeze({ schema: DEVELOPMENT_FIXTURE_IMAGE_SCHEMA, kind: KIND, status: 'READY', imageId: token(imageId, 'fixture image id'),
    metadata: checkedMetadata, durableStop: checkedDurableStop(durableStop), world: sourceTree });
  const imageSha256 = hash(stable(manifest));
  const destination = join(images, 'images', `${manifest.imageId}-${imageSha256}`);
  try { await stat(destination); }
  catch (error) { if (!isMissing(error)) throw error; }
  if (await exists(destination)) {
    const existing = await readFixtureImage(root, destination, manifest.imageId);
    if (existing.imageSha256 !== imageSha256) throw new Error('fixture image path collides with different content');
    return existing;
  }
  await mkdir(join(images, 'images'), { recursive: true });
  const staging = join(images, 'images', `.${manifest.imageId}-${randomUUID()}.staging`);
  await mkdir(staging, { recursive: false });
  try {
    await copyTree(source, join(staging, 'world'), { writable: true });
    const copiedTree = await digestTree(join(staging, 'world'));
    if (stable(copiedTree) !== stable(sourceTree)) throw new Error('fixture source changed while its image was being copied');
    await writeExclusive(join(staging, 'manifest.json'), `${JSON.stringify({ ...manifest, imageSha256 }, null, 2)}\n`);
    await makeReadOnly(staging);
    try { await rename(staging, destination); }
    catch (error) {
      if (error?.code !== 'EEXIST' && error?.code !== 'ENOTEMPTY') throw error;
      const existing = await readFixtureImage(root, destination, manifest.imageId);
      if (existing.imageSha256 !== imageSha256) throw new Error('fixture image concurrent publication conflicts with this content');
    }
  } catch (error) {
    await rm(staging, { recursive: true, force: true });
    throw error;
  }
  return await readFixtureImage(root, destination, manifest.imageId);
}

/**
 * Validates an image before a runner considers the fast path.  All invalid states are explicit
 * `UNAVAILABLE` results so the caller can do normal deterministic bootstrap; no damaged image is
 * silently repaired, decoded or used as a partial world.
 */
export async function locateDevelopmentFixtureImage({ project, imageRoot = 'build/frontier-v3-development-fixtures', imageId, metadata }) {
  const root = resolve(project); const images = safeRootBuildDirectory(root, imageRoot, 'fixture image root');
  const checkedMetadata = validateMetadata(metadata); const id = token(imageId, 'fixture image id');
  let names;
  try { names = await readdir(join(images, 'images')); }
  catch (error) { if (isMissing(error)) return unavailable('NO_IMAGE'); throw error; }
  const candidates = names.filter((name) => name.startsWith(`${id}-`) && !name.startsWith('.')).sort();
  if (candidates.length === 0) return unavailable('NO_IMAGE');
  const matches = []; const unavailableReasons = [];
  for (const candidate of candidates) {
    try {
      const image = await readFixtureImage(root, join(images, 'images', candidate), id);
      if (stable(image.manifest.metadata) === stable(checkedMetadata)) matches.push(image);
    } catch (error) {
      if (typeof error?.fixtureUnavailable !== 'string') throw error;
      unavailableReasons.push(error.fixtureUnavailable);
    }
  }
  if (matches.length === 1) return Object.freeze({ status: 'READY', image: matches[0] });
  if (matches.length > 1) return unavailable('AMBIGUOUS_IMAGE');
  if (candidates.length === 1 && unavailableReasons.length === 1) return unavailable(unavailableReasons[0]);
  return unavailable('METADATA_DRIFT');
}

/**
 * Allocates exactly one new mutable world from an immutable image.  The consumer lease prevents
 * a previous mutable destination from being re-used, while the copied world remains opaque to
 * this module.  Reflink is attempted first, then a byte-verified ordinary copy is used.
 */
export async function acquireDevelopmentFixtureConsumer({ project, imageRoot = 'build/frontier-v3-development-fixtures', image,
  consumerId, targetDirectory, policy = {} }) {
  const root = resolve(project); assertFixturePermitted(policy);
  const images = safeRootBuildDirectory(root, imageRoot, 'fixture image root');
  const checkedImage = await readFixtureImage(root, image?.directory, image?.manifest?.imageId);
  const target = safeGeneratedDirectory(root, targetDirectory, 'fixture consumer target');
  const consumer = token(consumerId, 'fixture consumer id');
  const consumerDirectory = join(images, 'consumers', consumer);
  try { await stat(target); throw new Error(`fixture consumer target already exists: ${relative(root, target)}`); }
  catch (error) { if (!isMissing(error)) throw error; }
  await mkdir(dirname(target), { recursive: true });
  await mkdir(join(images, 'consumers'), { recursive: true });
  try { await mkdir(consumerDirectory, { recursive: false }); }
  catch (error) { if (error?.code === 'EEXIST') throw new Error(`fixture consumer already exists: ${consumer}`); throw error; }
  const lease = Object.freeze({ schema: DEVELOPMENT_FIXTURE_IMAGE_SCHEMA, kind: 'frontier-v3-development-fixture-consumer', status: 'CLAIMED',
    consumerId: consumer, imageSha256: checkedImage.imageSha256, targetDirectory: relative(root, target), policy: checkedPolicy(policy) });
  try {
    await writeExclusive(join(consumerDirectory, 'consumer.json'), `${JSON.stringify(lease, null, 2)}\n`);
    await copyTree(join(checkedImage.directory, 'world'), target, { writable: true, reflink: true });
    const copied = await digestTree(target);
    if (stable(copied) !== stable(checkedImage.manifest.world)) throw new Error('fixture consumer copy does not match immutable image');
    const result = Object.freeze({ ...lease, status: 'READY', copyMode: 'REFLINK_OR_VERIFIED_COPY', world: copied });
    await writeExclusive(join(consumerDirectory, 'ready.json'), `${JSON.stringify(result, null, 2)}\n`);
    return result;
  } catch (error) {
    await writeFailure(consumerDirectory, error);
    await rm(target, { recursive: true, force: true });
    throw error;
  }
}

/** Fresh/final/bootstrap/persistence/timing/package claims must always use normal bootstrap. */
export function assertFixturePermitted(policy = {}) {
  const checked = checkedPolicy(policy);
  if (!['T2', 'T3'].includes(checked.tier) || checked.freshEvidence || checked.bootstrap || checked.initialRecovery
      || checked.persistenceFormat || checked.packagedJar || checked.timing) {
    throw new Error('development fixture image is forbidden for this evidence claim');
  }
  return checked;
}

export async function digestOpaqueDirectory(project, directory) {
  return await digestTree(safeGeneratedDirectory(resolve(project), directory, 'opaque fixture directory'));
}

async function readFixtureImage(root, directory, expectedId) {
  const imageDirectory = safeRootBuildDirectory(root, directory, 'fixture image');
  let raw;
  try { raw = JSON.parse(await readFile(join(imageDirectory, 'manifest.json'), 'utf8')); }
  catch (error) { if (isMissing(error)) throw unavailableError('MISSING_MANIFEST'); throw unavailableError('CORRUPT_MANIFEST'); }
  let metadata;
  try { metadata = validateMetadata(raw?.metadata); }
  catch { throw unavailableError('CORRUPT_MANIFEST'); }
  if (!raw || raw.schema !== DEVELOPMENT_FIXTURE_IMAGE_SCHEMA || raw.kind !== KIND || raw.status !== 'READY' || !sha(raw.imageSha256)
      || raw.imageId !== expectedId || stable(metadata) !== stable(raw.metadata)) throw unavailableError('CORRUPT_MANIFEST');
  try { validateDurableStop(raw.durableStop); }
  catch { throw unavailableError('INCOMPLETE_STOP'); }
  const checkedTree = validateTree(raw.world);
  if (raw.imageSha256 !== hash(stable({ schema: raw.schema, kind: raw.kind, status: raw.status, imageId: raw.imageId,
    metadata: raw.metadata, durableStop: raw.durableStop, world: checkedTree }))) throw unavailableError('MANIFEST_HASH_MISMATCH');
  let actual;
  try { actual = await digestTree(join(imageDirectory, 'world')); }
  catch { throw unavailableError('MISSING_WORLD'); }
  if (stable(actual) !== stable(checkedTree)) throw unavailableError('WORLD_HASH_MISMATCH');
  return Object.freeze({ directory: imageDirectory, imageSha256: raw.imageSha256,
    manifest: Object.freeze({ schema: raw.schema, kind: raw.kind, status: raw.status, imageId: raw.imageId,
      metadata, durableStop: checkedDurableStop(raw.durableStop), world: checkedTree }) });
}

async function digestTree(directory) {
  const root = resolve(directory); const entries = []; let bytes = 0;
  async function visit(current) {
    const names = await readdir(current, { withFileTypes: true });
    for (const entry of names.sort((left, right) => left.name.localeCompare(right.name))) {
      const target = join(current, entry.name); const path = relative(root, target);
      if (entry.isDirectory()) { entries.push(Object.freeze({ path, type: 'DIRECTORY' })); await visit(target); continue; }
      if (!entry.isFile()) throw new Error(`fixture tree has unsupported entry: ${path}`);
      const value = await readFile(target); bytes += value.length;
      if (entries.length >= MAX_FILES || bytes > MAX_BYTES) throw new Error('fixture tree exceeds bounded development image limits');
      entries.push(Object.freeze({ path, type: 'FILE', bytes: value.length, sha256: hash(value) }));
    }
  }
  await visit(root);
  if (entries.length === 0) throw new Error('fixture tree is empty');
  const tree = Object.freeze({ fileCount: entries.filter((entry) => entry.type === 'FILE').length, bytes, entries: Object.freeze(entries) });
  return Object.freeze({ ...tree, sha256: hash(stable(tree)) });
}

async function copyTree(source, destination, { writable, reflink = false }) {
  await mkdir(destination, { recursive: false });
  const entries = await readdir(source, { withFileTypes: true });
  for (const entry of entries.sort((left, right) => left.name.localeCompare(right.name))) {
    const from = join(source, entry.name); const to = join(destination, entry.name);
    if (entry.isDirectory()) { await copyTree(from, to, { writable, reflink }); continue; }
    if (!entry.isFile()) throw new Error(`fixture tree has unsupported copy entry: ${entry.name}`);
    try { await copyFile(from, to, reflink ? fsConstants.COPYFILE_FICLONE : 0); }
    catch (error) {
      if (!reflink || !['ENOTSUP', 'EOPNOTSUPP', 'EXDEV', 'EINVAL'].includes(error?.code)) throw error;
      await copyFile(from, to);
    }
    if (writable) await chmod(to, 0o644);
  }
  if (writable) await chmod(destination, 0o755);
}

async function makeReadOnly(directory) {
  const entries = await readdir(directory, { withFileTypes: true });
  for (const entry of entries) {
    const target = join(directory, entry.name);
    if (entry.isDirectory()) await makeReadOnly(target);
    else if (entry.isFile()) await chmod(target, 0o444);
    else throw new Error(`fixture image has unsupported immutable entry: ${entry.name}`);
  }
  await chmod(directory, 0o555);
}

function validateMetadata(value) {
  if (!value || typeof value !== 'object' || Array.isArray(value) || !sha(value.sourceContentSha256) || !sha(value.artifactSha256) || !sha(value.serverClasspathSha256)
      || !sha(value.clientClasspathSha256) || !sha(value.launchManifestSha256) || !token(value.rulesetId, 'fixture ruleset id')
      || !sha(value.rulesetSha256) || !Number.isSafeInteger(value.snapshotSchema) || value.snapshotSchema < 1
      || !Number.isSafeInteger(value.walEnvelope) || value.walEnvelope < 1 || !Number.isSafeInteger(value.seed)
      || !token(value.profile, 'fixture profile') || !Number.isInteger(value.viewDistance) || value.viewDistance < 2 || value.viewDistance > 32
      || !sha(value.fixtureDeclarationSha256)) throw new Error('fixture image metadata is malformed');
  return Object.freeze({ sourceContentSha256: value.sourceContentSha256, artifactSha256: value.artifactSha256, serverClasspathSha256: value.serverClasspathSha256,
    clientClasspathSha256: value.clientClasspathSha256, launchManifestSha256: value.launchManifestSha256,
    snapshotSchema: value.snapshotSchema, walEnvelope: value.walEnvelope, rulesetId: value.rulesetId,
    rulesetSha256: value.rulesetSha256, seed: value.seed, profile: value.profile, viewDistance: value.viewDistance,
    fixtureDeclarationSha256: value.fixtureDeclarationSha256 });
}
function validateDurableStop(value) {
  if (!value || typeof value !== 'object' || Array.isArray(value) || !uuid(value.serverRunId)
      || value.durableSave !== true || value.portClosed !== true || !sha(value.lifecycleSha256)) throw new Error('fixture image needs an exact durable clean-stop acknowledgement');
}
function checkedDurableStop(value) { validateDurableStop(value); return Object.freeze({ serverRunId: value.serverRunId, durableSave: true, portClosed: true, lifecycleSha256: value.lifecycleSha256 }); }
function validateTree(value) {
  if (!value || !Number.isInteger(value.fileCount) || value.fileCount < 1 || value.fileCount > MAX_FILES || !Number.isSafeInteger(value.bytes)
      || value.bytes < 1 || value.bytes > MAX_BYTES || !Array.isArray(value.entries) || !sha(value.sha256)) throw unavailableError('CORRUPT_WORLD_DIGEST');
  const seen = new Set(); let counted = 0; let bytes = 0;
  for (const entry of value.entries) {
    if (!entry || !safeRelative(entry.path) || seen.has(entry.path) || !['FILE', 'DIRECTORY'].includes(entry.type)) throw unavailableError('CORRUPT_WORLD_DIGEST');
    seen.add(entry.path);
    if (entry.type === 'FILE') {
      if (!Number.isSafeInteger(entry.bytes) || entry.bytes < 0 || !sha(entry.sha256)) throw unavailableError('CORRUPT_WORLD_DIGEST');
      counted++; bytes += entry.bytes;
    }
  }
  const bare = Object.freeze({ fileCount: counted, bytes, entries: Object.freeze(value.entries.map((entry) => Object.freeze({ ...entry }))) });
  if (counted !== value.fileCount || bytes !== value.bytes || value.sha256 !== hash(stable(bare))) throw unavailableError('CORRUPT_WORLD_DIGEST');
  return Object.freeze({ ...bare, sha256: value.sha256 });
}
function checkedPolicy(value) {
  const policy = { tier: value.tier ?? 'T3', freshEvidence: value.freshEvidence ?? false, bootstrap: value.bootstrap ?? false,
    initialRecovery: value.initialRecovery ?? false, persistenceFormat: value.persistenceFormat ?? false,
    packagedJar: value.packagedJar ?? false, timing: value.timing ?? false };
  if (!['T0', 'T1', 'T2', 'T3', 'T4'].includes(policy.tier) || Object.values(policy).some((entry, index) => index > 0 && typeof entry !== 'boolean')) {
    throw new Error('fixture image policy is malformed');
  }
  return Object.freeze(policy);
}
function safeRootBuildDirectory(project, candidate, label) {
  if (typeof candidate !== 'string' || candidate.length === 0) throw new Error(`${label} is missing`);
  const root = resolve(project, 'build'); const target = resolve(project, candidate); const path = relative(root, target);
  if (path === '' || path.startsWith('..') || path.includes('/..')) throw new Error(`${label} must remain under build/`);
  return target;
}
function safeGeneratedDirectory(project, candidate, label) {
  if (typeof candidate !== 'string' || candidate.length === 0) throw new Error(`${label} is missing`);
  const target = resolve(project, candidate);
  const roots = [resolve(project, 'build'), resolve(project, 'pale-mirror-neoforge/build')];
  if (!roots.some((root) => inside(root, target))) throw new Error(`${label} must remain under a known generated build root`);
  return target;
}
function unavailable(reason) { return Object.freeze({ status: 'UNAVAILABLE', reason }); }
function unavailableError(reason) { const error = new Error(`fixture image is unavailable: ${reason}`); error.fixtureUnavailable = reason; return error; }
function isMissing(error) { return error?.code === 'ENOENT'; }
async function exists(path) { try { await stat(path); return true; } catch (error) { if (isMissing(error)) return false; throw error; } }
function safeRelative(value) { return typeof value === 'string' && value.length > 0 && !value.startsWith('/') && !value.includes('\\') && !value.split('/').includes('..'); }
function inside(root, target) { const path = relative(root, target); return path !== '' && !path.startsWith('..') && !path.includes('/..'); }
function token(value, label) { if (typeof value !== 'string' || !/^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$/.test(value)) throw new Error(`${label} is malformed`); return value; }
function uuid(value) { return typeof value === 'string' && /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value); }
function sha(value) { return typeof value === 'string' && /^[a-f0-9]{64}$/.test(value); }
function hash(value) { return createHash('sha256').update(value).digest('hex'); }
function stable(value) { return JSON.stringify(value); }
async function writeExclusive(path, contents) { await mkdir(dirname(path), { recursive: true }); await writeFile(path, contents, { encoding: 'utf8', flag: 'wx' }); }
async function writeFailure(directory, error) { try { await writeExclusive(join(directory, 'failure.json'), `${JSON.stringify({ status: 'FAILED', failure: String(error?.message ?? error) })}\n`); } catch { /* Original acquisition failure is authoritative. */ } }
