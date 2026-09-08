import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { delimiter, join, relative, resolve } from 'node:path';

// A direct prepared launch deliberately bypasses Gradle's RunGame setup.  Its
// private client root therefore needs the same first-run acknowledgements that
// RunGame ordinarily writes before Quick Play can act.  This is disposable
// consumer state, never part of the immutable prepared artifact.
const DISPOSABLE_CLIENT_OPTIONS = 'onboardAccessibility:b:true\nskipMultiplayerWarning:b:true\nnarrator:0\n';

/**
 * Builds one direct Minecraft JVM command from the already-fingerprinted moddev launch files.
 * No Gradle RunGame task is permitted after this point: a restart either uses these exact bytes
 * or `requirePreparedBuild` fails before the JVM exists.
 */
export async function preparedLaunch(project, identity, role, properties = {}, programArguments = []) {
  if (!['server', 'client'].includes(role)) throw new Error('prepared launch role must be server or client');
  const manifestPath = safeProjectPath(project, identity?.launchManifest?.path, 'prepared launch manifest');
  const manifest = JSON.parse(await readFile(manifestPath, 'utf8'));
  const launch = manifest?.[role];
  if (manifest?.schema !== 1 || typeof manifest.java !== 'string' || typeof manifest.modFolders !== 'string'
      || manifest.modFolders.length === 0 || manifest.modFolders.includes('\n') || manifest.modFolders.includes('\r')
      || !launch || typeof launch !== 'object') {
    throw new Error('prepared launch manifest is malformed');
  }
  const vmArgs = safeProjectPath(project, launch.vmArgs, `${role} VM arguments`);
  const programArgs = safeProjectPath(project, launch.programArgs, `${role} program arguments`);
  const classpathFile = safeProjectPath(project, launch.classpath, `${role} legacy classpath`);
  const gameDirectory = safeProjectPath(project, launch.gameDirectory, `${role} game directory`);
  // Read the legacy list as a consistency check: ModLauncher receives it through the generated
  // VM property, while the direct launcher uses the complete explicit bootstrap classpath.
  const legacy = (await readFile(classpathFile, 'utf8')).split(/\r?\n/).map((value) => value.trim()).filter(Boolean);
  const entries = Array.isArray(launch.launchClasspath) ? launch.launchClasspath : [];
  if (legacy.length === 0 || entries.length === 0 || entries.some((entry) => typeof entry !== 'string' || !entry.startsWith('/'))) {
    throw new Error(`prepared ${role} classpath is malformed`);
  }
  const dynamic = Object.entries(properties).sort(([left], [right]) => left.localeCompare(right)).map(([key, value]) => {
    if (!/^pale_mirror\.[A-Za-z0-9_.-]+$/.test(key) || String(value).includes('\n') || String(value).includes('\r')) {
      throw new Error('prepared launch property is malformed');
    }
    return `-D${key}=${value}`;
  });
  if (!Array.isArray(programArguments) || programArguments.some((value) => typeof value !== 'string' || value.includes('\n') || value.includes('\r'))) {
    throw new Error('prepared launch program arguments are malformed');
  }
  return Object.freeze({ command: manifest.java, cwd: gameDirectory,
    args: Object.freeze([`@${vmArgs}`, `-Dfml.modFolders=${manifest.modFolders}`, ...dynamic, '-cp', entries.join(delimiter),
      // DevLaunch, not BootstrapLauncher, owns the generated run-program argument file.  It
      // understands the comment/run-type sections and expands this one file before ModLauncher
      // sees its launch target.
      'net.neoforged.devlaunch.Main', `@${programArgs}`, ...programArguments]) });
}

/**
 * Gradle normally creates a RunGame working directory immediately before forking Java.  The
 * prepared F0.V launcher intentionally replaces that fork, so it must create only its own
 * already-fingerprinted disposable game directory before spawn.  This never changes a prepared
 * artifact, launch input or world file.
 */
export async function ensurePreparedLaunchWorkingDirectory(launch) {
  if (!launch || typeof launch.cwd !== 'string' || launch.cwd.length === 0 || !launch.cwd.startsWith('/')) {
    throw new Error('prepared launch working directory is malformed');
  }
  await mkdir(launch.cwd, { recursive: true });
  try {
    // `wx` is the custody fence for this mutable consumer view: seed an empty
    // root once, but never adopt, normalize, or overwrite existing state.
    await writeFile(join(launch.cwd, 'options.txt'), DISPOSABLE_CLIENT_OPTIONS, { encoding: 'utf8', flag: 'wx' });
  } catch (error) {
    if (error?.code !== 'EEXIST') throw error;
  }
  return launch;
}

function safeProjectPath(project, candidate, name) {
  if (typeof candidate !== 'string' || candidate.length === 0) throw new Error(`${name} is missing`);
  const root = resolve(project); const path = resolve(root, candidate); const rel = relative(root, path);
  if (rel === '' || rel.startsWith('..') || rel.includes('/..')) throw new Error(`${name} escapes project`);
  return path;
}
