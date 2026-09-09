import { createHash, randomUUID } from 'node:crypto';
import { spawn } from 'node:child_process';
import { chmod, cp, mkdir, readFile, readdir, rename, stat, writeFile } from 'node:fs/promises';
import { arch, platform } from 'node:os';
import { dirname, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { fingerprintPreparedBuild, fingerprintPreparedSource, portablePreparedBuildIdentity } from './prepared-build.mjs';

const project = resolve(dirname(fileURLToPath(import.meta.url)), '../../..');
const SHA = /^[a-f0-9]{64}$/;
const WORKER = /^worker-[0-3]$/;
const RUNTIME_SCHEMA = 1;
const RUNTIME_KIND = 'frontier-v3-f0vc-prepared-runtime';

/**
 * Materializes the exact files consumed by the direct native launcher once.  The store is
 * deliberately outside a checkout: consumers can only derive private reflink views from this
 * immutable content-addressed root and never invoke Gradle to recreate candidate bytes.
 */
export async function prepareRuntime({ store, output, prepared, projectRoot = project, environment = process.env }) {
  const checkedProject = projectRootPath(projectRoot); const checkedStore = taskRoot(store);
  const gradleRoot = await declaredProducerGradleRoot(environment);
  const identity = prepared ?? await loadPrepared(resolve(checkedProject, 'build/f0vc/prepared-build.json'));
  const portable = portablePreparedBuildIdentity(identity);
  const manifest = JSON.parse(await readFile(resolve(checkedProject, identity.launchManifest.path), 'utf8'));
  const inputs = await collectInputs(manifest, identity, { projectRoot: checkedProject, gradleRoot });
  const runtime = await runtimeEnvironment(checkedProject);
  const core = { schema: RUNTIME_SCHEMA, kind: RUNTIME_KIND, producerProject: checkedProject, source: identity.sourceContent,
    portablePreparedIdentity: portable, environment: runtime, inputs };
  const contentSha256 = hash(JSON.stringify(core));
  const root = resolve(checkedStore, 'prepared', contentSha256);
  try { await stat(resolve(root, 'manifest.json')); }
  catch (error) {
    if (error?.code !== 'ENOENT') throw error;
    const temporary = `${root}.partial-${randomUUID()}`;
    await mkdir(resolve(temporary, 'entries'), { recursive: true });
    try {
      for (const entry of inputs) {
        const destination = resolve(temporary, 'entries', entry.id);
        await copyStage(entry.source, destination);
        if (await digestPath(destination) !== entry.sha256) throw new Error('F0.VC producer copied corrupt runtime input');
      }
      const value = { ...core, contentSha256, entriesRoot: 'entries' };
      await writeFile(resolve(temporary, 'manifest.json'), `${JSON.stringify(value, null, 2)}\n`, { flag: 'wx' });
      await makeReadOnly(temporary);
      await rename(temporary, root);
    } catch (failure) {
      await removeExact(temporary); throw failure;
    }
  }
  const checked = await readRuntimeManifest(resolve(root, 'manifest.json'));
  if (checked.contentSha256 !== contentSha256) throw new Error('F0.VC prepared runtime cache identity drifted');
  await mkdir(dirname(resolve(checkedProject, output)), { recursive: true });
  await writeFile(resolve(checkedProject, output), `${JSON.stringify({ manifest: resolve(root, 'manifest.json'), contentSha256, portablePreparedIdentity: portable }, null, 2)}\n`, { flag: 'wx' });
  return checked;
}

/** Reconstructs a private COW checkout view and derives a worker-local direct-launch identity. */
export async function consumeRuntime({ manifest, worker, output, workspace = project }) {
  if (!WORKER.test(worker ?? '')) throw new Error('F0.VC consumer worker is malformed');
  const root = resolve(workspace); const source = await readRuntimeManifest(manifest);
  const environment = await runtimeEnvironment();
  if (JSON.stringify(source.environment) !== JSON.stringify(environment)) throw new Error('F0.VC consumer runtime environment is foreign');
  const actualSource = await requireRuntimeSourceIdentity(source.source, root);
  const map = new Map();
  for (const entry of source.inputs) {
    const origin = resolve(dirname(manifest), source.entriesRoot, entry.id);
    if (await digestPath(origin) !== entry.sha256) throw new Error('F0.VC consumer runtime entry is corrupt');
    const target = resolve(root, entry.target);
    if (!inside(root, target)) throw new Error('F0.VC consumer target escapes its private workspace');
    await absent(target, `consumer runtime target ${entry.target}`);
    await copyConsumerView(origin, target, entry.text);
    if (await digestPath(target) !== entry.sha256) throw new Error('F0.VC consumer COW view hash drifted');
    map.set(entry.source, target);
  }
  for (const entry of source.inputs.filter((value) => value.text)) {
    const target = map.get(entry.source); const text = rewriteConsumerText(await readFile(target, 'utf8'), map, source.producerProject, root);
    if (text.includes(source.producerProject)) throw new Error('F0.VC consumer launch input still references producer workspace');
    await writeFile(target, text, { flag: 'w' });
  }
  const launchPath = [...source.inputs].find((entry) => entry.role === 'launch-manifest');
  if (!launchPath) throw new Error('F0.VC runtime lacks launch manifest');
  const localLaunch = resolve(root, launchPath.target); const launch = JSON.parse(await readFile(localLaunch, 'utf8'));
  launch.java = await javaExecutable();
  await writeFile(localLaunch, `${JSON.stringify(launch, null, 2)}\n`, { flag: 'w' });
  // The private view has been completely rewritten before it becomes launch input. Its ordinary
  // same-user mode is now read-only; more importantly, it never shares an inode with producer
  // or sibling consumers, so a compromised consumer cannot mutate their launch inputs.
  for (const entry of source.inputs) await makeReadOnly(resolve(root, entry.target));
  const artifact = source.inputs.find((entry) => entry.role === 'artifact');
  if (!artifact) throw new Error('F0.VC runtime lacks packaged artifact');
  const identity = Object.freeze({ sourceContent: actualSource, ...(await fingerprintPreparedBuild(root, resolve(root, artifact.target))) });
  if (JSON.stringify(portablePreparedBuildIdentity(identity)) !== JSON.stringify(source.portablePreparedIdentity)) {
    throw new Error('F0.VC consumer prepared artifact/classpath identity drifted');
  }
  const receipt = { schema: RUNTIME_SCHEMA, kind: 'frontier-v3-f0vc-runtime-consumer', status: 'ok', worker,
    runtimeContentSha256: source.contentSha256, copyStrategy: 'PRIVATE_REFLINK_OR_COPY', workspace: root,
    immutableStore: resolve(dirname(manifest)), localPreparedIdentity: relative(root, output), mutableRoots: [resolve(root, 'pale-mirror-neoforge/build/runs'), resolve(root, 'build/f0vc')] };
  await mkdir(dirname(resolve(root, output)), { recursive: true });
  await writeFile(resolve(root, output), `${JSON.stringify(identity, null, 2)}\n`, { flag: 'wx' });
  await writeFile(resolve(root, `${dirname(output)}/consumer-${worker}.json`), `${JSON.stringify(receipt, null, 2)}\n`, { flag: 'wx' });
  return { identity, receipt };
}

export async function readRuntimeManifest(path) {
  let value; try { value = JSON.parse(await readFile(path, 'utf8')); } catch { throw new Error('F0.VC prepared runtime manifest is unreadable'); }
  if (!value || value.schema !== RUNTIME_SCHEMA || value.kind !== RUNTIME_KIND || !SHA.test(value.contentSha256 ?? '')
      || !value.source?.sha256 || !value.portablePreparedIdentity || !Array.isArray(value.inputs) || value.inputs.length < 8
      || typeof value.entriesRoot !== 'string' || !Array.isArray(value.environment)) throw new Error('F0.VC prepared runtime manifest is malformed');
  const core = { schema: value.schema, kind: value.kind, producerProject: value.producerProject, source: value.source, portablePreparedIdentity: value.portablePreparedIdentity,
    environment: value.environment, inputs: value.inputs };
  if (hash(JSON.stringify(core)) !== value.contentSha256) throw new Error('F0.VC prepared runtime manifest hash is stale or foreign');
  const ids = new Set(); const targets = new Set();
  for (const input of value.inputs) {
    if (typeof value.producerProject !== 'string' || !value.producerProject.startsWith('/') || !input || !SHA.test(input.id ?? '') || !SHA.test(input.sha256 ?? '') || typeof input.source !== 'string' || !input.source.startsWith('/')
        || typeof input.target !== 'string' || !safeRelative(input.target) || typeof input.text !== 'boolean' || typeof input.role !== 'string'
        || ids.has(input.id) || targets.has(input.target)) throw new Error('F0.VC prepared runtime entry is malformed');
    ids.add(input.id); targets.add(input.target);
  }
  return Object.freeze(value);
}

/** A changed selected source/test/workflow byte invalidates a prepared runtime before launch. */
export async function requireRuntimeSourceIdentity(expected, workspace) {
  const actual = await fingerprintPreparedSource(resolve(workspace));
  if (JSON.stringify(actual) !== JSON.stringify(expected)) throw new Error('F0.VC consumer source/test/workflow identity drifted');
  return actual;
}

async function collectInputs(launch, identity, { projectRoot, gradleRoot }) {
  if (launch?.schema !== 1 || !launch.server || !launch.client || typeof launch.modFolders !== 'string') throw new Error('F0.VC producer launch manifest is malformed');
  const sources = new Map();
  const add = async (path, role, text = false) => {
    if (typeof path !== 'string' || !path.startsWith('/')) throw new Error('F0.VC producer launch input is not absolute');
    const source = resolve(path); const existing = sources.get(source);
    if (existing) { existing.text ||= text; return; }
    // A content address names the enclosing immutable manifest, never a JVM launch member.
    // NeoForge derives module identity from a JAR's basename and some generated arguments rely
    // on stable sibling paths, so retain the complete project/Gradle relative topology verbatim.
    const target = runtimeTargetFor(source, { projectRoot, gradleRoot });
    sources.set(source, { source, target, role, text, sha256: await digestPath(source) });
  };
  await add(resolve(projectRoot, identity.preparedArtifact.path), 'artifact');
  await add(resolve(projectRoot, identity.launchManifest.path), 'launch-manifest', true);
  for (const role of ['server', 'client']) {
    const view = launch[role];
    for (const field of ['vmArgs', 'programArgs', 'classpath']) await add(resolve(projectRoot, view[field]), `${role}-${field}`, true);
    for (const item of view.launchClasspath) await add(item, `${role}-classpath`);
  }
  for (const part of launch.modFolders.split(':')) {
    const [, location] = part.split('%%'); await add(location, 'mod-folder');
  }
  // Generated VM/program/classpath files may refer to a log configuration or another launch
  // file not named by the JSON manifest.  Stage every existing absolute token transitively;
  // otherwise a consumer could silently retain the producer checkout through an argument file.
  for (let index = 0; index < [...sources.values()].length; index++) {
    const entry = [...sources.values()][index]; if (!entry.text) continue;
    const text = await readFile(entry.source, 'utf8');
    for (const candidate of text.match(/\/[A-Za-z0-9_./@+%=-]+/g) ?? []) {
      if (candidate.endsWith('/bin/java')) continue; // consumer verifies and selects its own pinned JDK
      try { await stat(candidate); await add(candidate, 'transitive-launch-input'); } catch (error) { if (error?.code !== 'ENOENT') throw error; }
    }
  }
  const entries = [...sources.values()].sort((a, b) => a.source.localeCompare(b.source));
  return Object.freeze(entries.map((entry) => Object.freeze({ ...entry, id: hash(`${entry.source}\0${entry.sha256}`) })));
}

/**
 * Maps only declared direct-launch roots into the consumer view without changing a launcher
 * member's basename or its relationship to sibling Gradle-cache entries.
 */
export function runtimeTargetFor(sourcePath, { projectRoot = project, gradleRoot } = {}) {
  const source = resolve(sourcePath); const checkedProject = projectRootPath(projectRoot); const checkedGradle = declaredRootPath(gradleRoot, 'producer Gradle user home');
  if (inside(checkedProject, source)) return relative(checkedProject, source);
  if (inside(checkedGradle, source)) return `.f0vc-runtime/gradle/${relative(checkedGradle, source)}`;
  throw new Error(`F0.VC producer launch input escapes the declared project or Gradle runtime roots: ${source}`);
}

export function rewriteConsumerText(text, paths, producerProject, consumerProject) {
  let rewritten = text;
  for (const [from, to] of [...paths.entries()].sort(([a], [b]) => b.length - a.length)) rewritten = rewritten.split(from).join(to);
  return rewritten.split(producerProject).join(consumerProject);
}

async function runtimeEnvironment(projectRoot = project) {
  const java = await javaExecutable(); const version = await childOutput(java, ['--version']);
  const files = ['gradle/wrapper/gradle-wrapper.properties', 'gradle.properties', 'settings.gradle', 'build.gradle'];
  const checkedProject = projectRootPath(projectRoot); const locks = await lockFiles(checkedProject);
  const values = await Promise.all([...files, ...locks].map(async (path) => ({ path, sha256: hash(await readFile(resolve(checkedProject, path))) })));
  return Object.freeze([{ key: 'node', value: process.version }, { key: 'os', value: `${platform()}-${arch()}` },
    { key: 'java', value: hash(version) }, ...values.map((value) => ({ key: `file:${value.path}`, value: value.sha256 }))].sort((a, b) => a.key.localeCompare(b.key)));
}
async function lockFiles(root) { const names = []; async function visit(directory) { for (const entry of await readdir(directory, { withFileTypes: true })) { const target = resolve(directory, entry.name); if (entry.isDirectory() && !['build', '.gradle', '.git'].includes(entry.name)) await visit(target); else if (entry.isFile() && (entry.name.endsWith('.lockfile') || entry.name === 'gradle.lockfile')) names.push(relative(root, target)); } } await visit(root); return names.sort(); }
async function loadPrepared(path) { return Object.freeze(JSON.parse(await readFile(path, 'utf8'))); }
async function digestPath(path) { const value = await stat(path); if (value.isFile()) return hash(await readFile(path)); if (!value.isDirectory()) throw new Error('F0.VC runtime input is not a file or directory'); const digest = createHash('sha256'); await digestDirectory(path, path, digest); return digest.digest('hex'); }
async function digestDirectory(root, directory, digest) { for (const entry of (await readdir(directory, { withFileTypes: true })).sort((a, b) => a.name.localeCompare(b.name))) { const target = resolve(directory, entry.name); if (entry.isDirectory()) await digestDirectory(root, target, digest); else if (entry.isFile()) { digest.update(relative(root, target)); digest.update('\0'); digest.update(await readFile(target)); } else throw new Error('F0.VC runtime has unsupported filesystem entry'); } }
async function copyStage(source, destination) { await copy(source, destination, ['--archive', '--no-preserve=mode']); }
export async function copyConsumerView(source, destination, mutable) {
  // Reflinks preserve the one immutable source while giving every consumer a distinct inode;
  // GNU cp falls back to a real private copy where the filesystem has no CoW support.  Never
  // use hard links here: mode 0444 is not an isolation proof for simultaneous same-owner jobs.
  await copy(source, destination, ['--archive', '--reflink=auto', '--no-preserve=mode']);
}
async function copy(source, destination, flags) { await mkdir(dirname(destination), { recursive: true }); await new Promise((resolveCopy, rejectCopy) => { const task = spawn('cp', [...flags, source, destination], { stdio: 'ignore' }); task.once('error', rejectCopy); task.once('exit', (code) => code === 0 ? resolveCopy() : rejectCopy(new Error('F0.VC immutable runtime copy failed'))); }); }
async function makeReadOnly(path) {
  const value = await stat(path);
  if (value.isFile()) { await chmod(path, 0o444); return; }
  if (!value.isDirectory()) throw new Error('F0.VC runtime has unsupported filesystem entry');
  await chmod(path, 0o555);
  for (const entry of await readdir(path, { withFileTypes: true })) {
    const target = resolve(path, entry.name);
    if (entry.isDirectory()) await makeReadOnly(target);
    else if (entry.isFile()) await chmod(target, 0o444);
    else throw new Error('F0.VC runtime has unsupported filesystem entry');
  }
}
async function removeExact(path) { await new Promise((resolveRemove) => { const task = spawn('rm', ['-rf', '--', path], { stdio: 'ignore' }); task.once('exit', () => resolveRemove()); task.once('error', () => resolveRemove()); }); }
async function absent(path, label) { try { await stat(path); throw new Error(`F0.VC refuses to overwrite ${label}`); } catch (error) { if (error?.code !== 'ENOENT') throw error; } }
function taskRoot(value) { const root = resolve(value ?? ''); if (!root.startsWith('/home/rd/proj/pm-f0vc-')) throw new Error('F0.VC prepared store must be a dedicated task root'); return root; }
async function declaredProducerGradleRoot(environment) {
  const root = declaredRootPath(environment?.GRADLE_USER_HOME, 'producer Gradle user home');
  try {
    if (!(await stat(root)).isDirectory()) throw new Error('not a directory');
  } catch (error) {
    throw new Error(`F0.VC declared producer Gradle user home is missing or not a directory: ${root}`, { cause: error });
  }
  return root;
}
function projectRootPath(value) { return declaredRootPath(value, 'producer project root'); }
function declaredRootPath(value, label) {
  if (typeof value !== 'string' || !value.startsWith('/')) throw new Error(`F0.VC ${label} must be an absolute declared path`);
  const root = resolve(value); if (root === '/') throw new Error(`F0.VC ${label} must not be the filesystem root`);
  return root;
}
async function javaExecutable() {
  const home = process.env.JAVA_HOME;
  if (typeof home === 'string' && home.startsWith('/')) return resolve(home, 'bin/java');
  const discovered = (await childOutput('sh', ['-lc', 'command -v java'])).trim();
  if (!discovered.startsWith('/')) throw new Error('F0.VC requires an absolute Java executable');
  return discovered;
}
function childOutput(command, args) { return new Promise((resolveOutput, rejectOutput) => { let output = ''; const task = spawn(command, args, { stdio: ['ignore', 'pipe', 'pipe'] }); task.stdout.on('data', (value) => { output += value; }); task.stderr.on('data', (value) => { output += value; }); task.once('error', rejectOutput); task.once('exit', (code) => code === 0 ? resolveOutput(output) : rejectOutput(new Error('F0.VC cannot fingerprint Java runtime'))); }); }
function safeRelative(value) { return typeof value === 'string' && value.length > 0 && !value.startsWith('/') && !value.includes('\\') && !value.split('/').includes('..'); }
function inside(root, path) { const value = relative(root, path); return value !== '' && !value.startsWith('..') && !value.includes('/..'); }
function hash(value) { return createHash('sha256').update(value).digest('hex'); }

if (process.argv[1] !== undefined && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const values = Object.fromEntries(process.argv.slice(2).map((entry) => { const [key, value] = entry.replace(/^--/, '').split('=', 2); return [key, value]; }));
  if (values.mode === 'prepare') await prepareRuntime({ store: values.store, output: values.output, prepared: values.prepared ? await loadPrepared(resolve(project, values.prepared)) : undefined });
  else if (values.mode === 'consume') await consumeRuntime({ manifest: values.manifest, worker: values.worker, output: values.output });
  else throw new Error('usage: f0vc-prepared-runtime --mode=prepare|consume ...');
}
