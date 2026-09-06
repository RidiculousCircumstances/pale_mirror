import { createHash } from 'node:crypto';
import { readdir, readFile, stat } from 'node:fs/promises';
import { relative, resolve } from 'node:path';
import { fingerprintWorkingContentWithMonorepoWorkflows } from './evidence-cache.mjs';

const CLASSPATHES = Object.freeze({
  server: 'pale-mirror-neoforge/build/moddev/frontierV3PilotServerLegacyClasspath.txt',
  client: 'pale-mirror-neoforge/build/moddev/frontierV3PilotClientLegacyClasspath.txt'
});
const LAUNCH_MANIFEST = 'pale-mirror-neoforge/build/moddev/frontierV3PilotPreparedLaunch.json';
const PILOT_CRASH_MIXIN_OPTION = '--mixin.config';
const PILOT_CRASH_MIXIN_CONFIGURATION = 'pale_mirror.frontier_v3.pilot_crash.mixins.json';
// This is deliberately conservative. A native proof is produced by both the prepared JVM
// inputs and the external harness that drives them, so a dirty runner, scenario, contract or
// process source cannot inherit a prepared identity made for older bytes.
export const PREPARED_SOURCE_PREFIXES = Object.freeze([
  '.github/workflows/', 'architecture.yml', 'build.gradle', 'gradle/', 'gradle.properties',
  'pale-mirror-frontier/', 'pale-mirror-neoforge/', 'pale-mirror-visuals/', 'settings.gradle',
  'scripts/with-private-xvfb.sh',
  'tools/frontier-v3-test-pilot/', 'tools/engineering/curate_bunkhouse_rice_bags.mjs',
  'tools/engineering/verify_visual_compile_dependencies.mjs', 'docs/frontier-v3-'
]);

/**
 * Fingerprints the exact launch inputs selected by moddev.  The distributable JAR alone is not
 * enough: the native runner starts development JVMs, so both their resolved classpath manifests
 * and every referenced file/directory contribute to the identity.  Paths are not copied; a
 * restart proves that the same prepared inputs still exist byte-for-byte.
 */
export async function fingerprintPreparedBuild(project, artifact) {
  const selected = resolve(project, artifact);
  const artifactBytes = await readFile(selected);
  const launchPath = resolve(project, LAUNCH_MANIFEST);
  const launchBytes = await readFile(launchPath);
  const launch = JSON.parse(launchBytes.toString('utf8'));
  if (launch?.schema !== 1 || typeof launch.java !== 'string') throw new Error('prepared launch manifest is malformed');
  const classpaths = {};
  const launchInputs = {};
  for (const [role, source] of Object.entries(CLASSPATHES)) {
    const manifest = resolve(project, source);
    const raw = await readFile(manifest, 'utf8');
    const entries = raw.split(/\r?\n/).map((value) => value.trim()).filter(Boolean);
    if (entries.length === 0 || new Set(entries).size !== entries.length) throw new Error(`prepared ${role} classpath is empty or duplicate`);
    const digest = createHash('sha256');
    const portableDigest = createHash('sha256');
    digest.update(raw);
    for (const entry of entries) {
      if (!entry.startsWith('/')) throw new Error(`prepared ${role} classpath entry is not absolute: ${entry}`);
      const content = await pathDigest(entry);
      digest.update(`\n${entry}\0${content}`);
      portableDigest.update(`\n${content}`);
    }
    const launcherEntries = launch?.[role]?.launchClasspath;
    if (!Array.isArray(launcherEntries) || launcherEntries.length === 0 || launcherEntries.some((entry) => typeof entry !== 'string' || !entry.startsWith('/'))) {
      throw new Error(`prepared ${role} direct launch classpath is malformed`);
    }
    const launcherDigest = createHash('sha256');
    const portableLauncherDigest = createHash('sha256');
    for (const entry of launcherEntries) {
      const content = await pathDigest(entry);
      launcherDigest.update(`\n${entry}\0${content}`);
      portableLauncherDigest.update(`\n${content}`);
    }
    const vmArgs = await launchInputDigest(project, launch?.[role]?.vmArgs, `${role} VM arguments`);
    const programArgs = await launchInputDigest(project, launch?.[role]?.programArgs, `${role} program arguments`);
    classpaths[role] = Object.freeze({ manifest: source, entries: entries.length, sha256: digest.digest('hex'),
      portableSha256: portableDigest.digest('hex'), launchEntries: launcherEntries.length,
      launchSha256: launcherDigest.digest('hex'), portableLaunchSha256: portableLauncherDigest.digest('hex') });
    launchInputs[role] = Object.freeze({ vmArgs, programArgs });
  }
  validatePilotCrashMixinLaunchArguments(
    await parsedProgramArguments(project, launch?.server?.programArgs, 'server program arguments'),
    await parsedProgramArguments(project, launch?.client?.programArgs, 'client program arguments')
  );
  return Object.freeze({
    preparedArtifact: Object.freeze({ path: relative(project, selected), sha256: sha256(artifactBytes) }),
    launchManifest: Object.freeze({ path: LAUNCH_MANIFEST, sha256: sha256(launchBytes) }),
    classpaths: Object.freeze(classpaths),
    launchInputs: Object.freeze(launchInputs)
  });
}

/**
 * Fingerprints the owned source inputs that determine a native F0.V/F0.VA proof.  This is not
 * a replacement for the artifact/classpath digest: it records the dirty and untracked harness
 * bytes which can otherwise change the semantics of a run without changing the packaged JAR.
 */
export async function fingerprintPreparedSource(project) {
  return await fingerprintWorkingContentWithMonorepoWorkflows(project, PREPARED_SOURCE_PREFIXES);
}

/**
 * The exact launch identity contains worker-local absolute paths and fences server/client JVMs
 * inside one prepared checkout. CI workers instead compare this content-only projection: it
 * retains classpath ordering and every byte digest, but not a checkout or Gradle-cache location.
 */
export function portablePreparedBuildIdentity(identity) {
  if (!identity?.preparedArtifact?.sha256 || !identity?.classpaths?.server || !identity?.classpaths?.client
      || !identity?.launchInputs?.server || !identity?.launchInputs?.client || !sha256(identity?.sourceContent?.sha256)) {
    throw new Error('portable prepared build identity is incomplete');
  }
  const classpath = (role) => {
    const value = identity.classpaths[role];
    if (!Number.isSafeInteger(value.entries) || value.entries < 1 || !sha256(value.portableSha256)
        || !Number.isSafeInteger(value.launchEntries) || value.launchEntries < 1 || !sha256(value.portableLaunchSha256)) {
      throw new Error(`portable ${role} classpath identity is malformed`);
    }
    return Object.freeze({ entries: value.entries, portableSha256: value.portableSha256,
      launchEntries: value.launchEntries, portableLaunchSha256: value.portableLaunchSha256 });
  };
  const launch = (role) => {
    const value = identity.launchInputs[role];
    if (!sha256(value?.vmArgs?.portableSha256) || !sha256(value?.programArgs?.portableSha256)) {
      throw new Error(`portable ${role} launch identity is malformed`);
    }
    return Object.freeze({ vmArgsSha256: value.vmArgs.portableSha256, programArgsSha256: value.programArgs.portableSha256 });
  };
  if (!sha256(identity.preparedArtifact.sha256)) throw new Error('portable prepared artifact identity is malformed');
  return Object.freeze({ schema: 1, preparedArtifactSha256: identity.preparedArtifact.sha256,
    sourceContentSha256: identity.sourceContent.sha256,
    classpaths: Object.freeze({ server: classpath('server'), client: classpath('client') }),
    launchInputs: Object.freeze({ server: launch('server'), client: launch('client') }) });
}

/** Fails before a new server/client JVM can start when anything selected at prepare time drifted. */
export async function requirePreparedBuild(project, identity) {
  if (!identity?.preparedArtifact?.path || !identity?.preparedArtifact?.sha256 || !identity?.launchManifest?.path
      || !identity?.launchManifest?.sha256 || !identity?.classpaths?.server || !identity?.classpaths?.client
      || !identity?.launchInputs?.server || !identity?.launchInputs?.client) {
    throw new Error('prepared build identity is incomplete');
  }
  const actual = await fingerprintPreparedBuild(project, safeProjectPath(project, identity.preparedArtifact.path, 'artifact'));
  if (JSON.stringify(actual) !== JSON.stringify({ preparedArtifact: identity.preparedArtifact,
    launchManifest: identity.launchManifest, classpaths: identity.classpaths, launchInputs: identity.launchInputs })) {
    throw new Error('prepared artifact/classpath hash drifted during native scenario');
  }
  return actual;
}

/** A native F0.V/F0.VA runner requires both immutable launch bytes and exact source evidence. */
export async function requirePreparedF0vBuild(project, identity) {
  await requirePreparedBuild(project, identity);
  if (!identity?.sourceContent || !sha256(identity.sourceContent.sha256) || !Array.isArray(identity.sourceContent.files)) {
    throw new Error('prepared F0.V source content identity is incomplete');
  }
  const actual = await fingerprintPreparedSource(project);
  if (JSON.stringify(actual) !== JSON.stringify(identity.sourceContent)) {
    throw new Error('prepared F0.V source content hash drifted during native scenario');
  }
  return actual;
}

async function fileDigest(project, candidate, name) {
  if (typeof candidate !== 'string' || candidate.length === 0) throw new Error(`prepared ${name} is missing`);
  const root = resolve(project); const path = resolve(root, candidate); const rel = relative(root, path);
  if (rel === '' || rel.startsWith('..') || rel.includes('/..')) throw new Error(`prepared ${name} escapes project`);
  return Object.freeze({ path: rel, sha256: sha256(await readFile(path)) });
}

// ModDev writes each worker's checkout, cache, and temporary-home locations
// into its launch arguments. Retain their raw digest for local drift checks,
// but compare only location-neutral launch content across isolated workers.
async function launchInputDigest(project, candidate, name) {
  const raw = await fileDigest(project, candidate, name);
  const bytes = await readFile(resolve(project, candidate), 'utf8');
  return Object.freeze({ ...raw, portableSha256: sha256(bytes.replace(/(^|[=:])\/[^\s:]*/gm, '$1<private-absolute-path>')) });
}

async function parsedProgramArguments(project, candidate, name) {
  const path = safeProjectPath(project, candidate, `prepared ${name}`);
  return (await readFile(path, 'utf8')).split(/\r?\n/)
    .map((value) => value.trim()).filter((value) => value.length > 0 && !value.startsWith('#'));
}

function validatePilotCrashMixinLaunchArguments(serverArguments, clientArguments) {
  const countPairs = (values) => values.reduce((count, value, index) => count
    + (value === PILOT_CRASH_MIXIN_OPTION && values[index + 1] === PILOT_CRASH_MIXIN_CONFIGURATION ? 1 : 0), 0);
  if (countPairs(clientArguments) !== 0) {
    throw new Error('prepared client program arguments select the pilot crash mixin');
  }
  if (countPairs(serverArguments) !== 1) {
    throw new Error('prepared server program arguments require exactly one adjacent pilot crash mixin pair');
  }
}

function safeProjectPath(project, candidate, name) {
  if (typeof candidate !== 'string' || candidate.length === 0) throw new Error(`prepared ${name} is missing`);
  const root = resolve(project); const path = resolve(root, candidate); const rel = relative(root, path);
  if (rel === '' || rel.startsWith('..') || rel.includes('/..')) throw new Error(`prepared ${name} escapes project`);
  return path;
}

async function pathDigest(path) {
  const value = await stat(path);
  if (value.isFile()) return sha256(await readFile(path));
  if (!value.isDirectory()) throw new Error(`prepared classpath entry is neither file nor directory: ${path}`);
  const digest = createHash('sha256');
  await digestDirectory(path, path, digest);
  return digest.digest('hex');
}

async function digestDirectory(root, directory, digest) {
  const entries = await readdir(directory, { withFileTypes: true });
  for (const entry of entries.sort((left, right) => left.name.localeCompare(right.name))) {
    const path = resolve(directory, entry.name);
    if (entry.isDirectory()) await digestDirectory(root, path, digest);
    else if (entry.isFile()) {
      digest.update(relative(root, path));
      digest.update('\0');
      digest.update(await readFile(path));
    } else throw new Error(`prepared classpath contains unsupported entry: ${path}`);
  }
}

function sha256(bytes) { return createHash('sha256').update(bytes).digest('hex'); }
