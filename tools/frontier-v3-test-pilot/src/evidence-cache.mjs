import { createHash } from 'node:crypto';
import { execFile } from 'node:child_process';
import { mkdir, readFile, readdir, stat, writeFile } from 'node:fs/promises';
import { promisify } from 'node:util';
import { join, relative, resolve } from 'node:path';

const exec = promisify(execFile);
export const EVIDENCE_CACHE_SCHEMA = 1;

/**
 * Hashes actual working-tree bytes, not a commit ID.  `git ls-files` deliberately includes
 * relevant untracked files, while a tracked file missing from disk is represented explicitly so
 * deletion cannot accidentally reuse a prior success.
 */
export async function fingerprintWorkingContent(project, prefixes) {
  const root = resolve(project); const checked = validatePrefixes(prefixes);
  const { stdout } = await exec('git', ['ls-files', '--cached', '--others', '--exclude-standard', '-z'], { cwd: root, maxBuffer: 32 * 1024 * 1024 });
  const paths = [...new Set(stdout.split('\0').filter(Boolean))].filter((path) => checked.some((prefix) => path === prefix || path.startsWith(prefix))).sort();
  if (paths.length === 0) throw new Error('content fingerprint has no relevant working-tree inputs');
  return fingerprintPaths(root, paths);
}

export async function fingerprintPaths(project, paths) {
  const root = resolve(project);
  if (!Array.isArray(paths) || paths.length === 0 || paths.some((path) => !safePath(path))) throw new Error('content fingerprint paths are malformed');
  const files = [];
  for (const path of [...new Set(paths)].sort()) {
    const target = resolve(root, path); if (!inside(root, target)) throw new Error('content fingerprint path escapes project');
    try {
      const value = await stat(target);
      if (!value.isFile()) throw new Error('content fingerprint input is not a file');
      files.push(Object.freeze({ path, state: 'PRESENT', sha256: hash(await readFile(target)) }));
    } catch (error) {
      if (error?.code === 'ENOENT') files.push(Object.freeze({ path, state: 'MISSING', sha256: hash('MISSING\0' + path) }));
      else throw error;
    }
  }
  const value = Object.freeze({ schema: EVIDENCE_CACHE_SCHEMA, kind: 'frontier-v3-working-content', files: Object.freeze(files) });
  return Object.freeze({ ...value, sha256: hash(stable(value)) });
}

/** Creates the complete cache key. Omitted identity fields are rejected rather than defaulted. */
export function evidenceCacheKey(input) { return hash(stable(validateKeyInput(input))); }

export async function storeSuccessfulEvidence(root, input, proofs) {
  const checked = validateKeyInput(input); const key = evidenceCacheKey(checked); const checkedProofs = validateProofs(proofs);
  const directory = join(resolve(root), 'entries', key);
  await mkdir(resolve(root, 'entries'), { recursive: true });
  try { await mkdir(directory); }
  catch (error) { if (error?.code === 'EEXIST') throw new Error('evidence cache entry already exists or is corrupt'); throw error; }
  const entry = Object.freeze({ schema: EVIDENCE_CACHE_SCHEMA, kind: 'frontier-v3-evidence-cache-entry', status: 'SUCCESS', key,
    input: checked, proofs: checkedProofs });
  await writeFile(join(directory, 'entry.json'), `${stable(entry)}\n`, { encoding: 'utf8', flag: 'wx' });
  return Object.freeze({ key, directory, entry });
}

/** Only complete, schema-current success entries matching every requested identity are reusable. */
export async function readReusableEvidence(root, input) {
  const checked = validateKeyInput(input); const key = evidenceCacheKey(checked); const directory = join(resolve(root), 'entries', key);
  let names;
  try { names = await readdir(directory); } catch (error) { if (error?.code === 'ENOENT') return undefined; throw error; }
  if (names.length !== 1 || names[0] !== 'entry.json') throw new Error('evidence cache entry is incomplete or corrupt');
  let entry;
  try { entry = JSON.parse(await readFile(join(directory, 'entry.json'), 'utf8')); }
  catch { throw new Error('evidence cache entry is corrupt'); }
  if (!entry || entry.schema !== EVIDENCE_CACHE_SCHEMA || entry.kind !== 'frontier-v3-evidence-cache-entry' || entry.status !== 'SUCCESS'
      || entry.key !== key || stable(entry.input) !== stable(checked) || evidenceCacheKey(entry.input) !== key) {
    throw new Error('evidence cache entry is foreign, stale or malformed');
  }
  return Object.freeze({ key, directory, entry: Object.freeze({ ...entry, input: checked, proofs: validateProofs(entry.proofs) }) });
}

function validateKeyInput(value) {
  const requiredHashes = ['sourceContentSha256', 'artifactSha256', 'serverClasspathSha256', 'clientClasspathSha256', 'jdkSha256',
    'platformSha256', 'sdkSha256', 'dependencyManifestSha256', 'verticalContractSha256', 'scenarioSha256', 'commandSha256', 'environmentPolicySha256'];
  if (!value || value.schema !== EVIDENCE_CACHE_SCHEMA || value.kind !== 'frontier-v3-evidence-cache-key') throw new Error('evidence cache key input is malformed');
  for (const key of requiredHashes) if (!sha256(value[key])) throw new Error(`evidence cache key lacks ${key}`);
  if (!Number.isSafeInteger(value.seed) || !token(value.profile) || !Number.isInteger(value.viewDistance) || value.viewDistance < 2 || value.viewDistance > 32
      || !['T0', 'T1', 'T2', 'T3', 'T4'].includes(value.tier) || !fixture(value.fixtureImage)) throw new Error('evidence cache execution identity is malformed');
  return Object.freeze(Object.fromEntries(['schema', 'kind', ...requiredHashes, 'seed', 'profile', 'viewDistance', 'tier', 'fixtureImage'].map((key) => [key, value[key]])));
}
function validateProofs(value) {
  if (!Array.isArray(value) || value.length < 1 || value.length > 32 || value.some((proof) => !safePath(proof) || proof.length > 256)) {
    throw new Error('evidence cache proofs are malformed');
  }
  return Object.freeze([...new Set(value)].sort());
}
function validatePrefixes(value) { if (!Array.isArray(value) || value.length === 0 || value.some((entry) => !safePath(entry))) throw new Error('content fingerprint prefixes are malformed'); return [...new Set(value)].sort(); }
function safePath(value) { return typeof value === 'string' && value.length > 0 && !value.startsWith('/') && !value.includes('\\') && !value.split('/').includes('..'); }
function token(value) { return typeof value === 'string' && /^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$/.test(value); }
function fixture(value) { return value === null || sha256(value); }
function sha256(value) { return typeof value === 'string' && /^[a-f0-9]{64}$/.test(value); }
function inside(root, target) { const path = relative(root, target); return path !== '' && !path.startsWith('..') && !path.includes('/..'); }
function hash(value) { return createHash('sha256').update(value).digest('hex'); }
function stable(value) { return JSON.stringify(value); }
