import { spawn, execFile } from 'node:child_process';
import { mkdir, readdir, writeFile } from 'node:fs/promises';
import { promisify } from 'node:util';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { fingerprintPreparedBuild, fingerprintPreparedSource } from './prepared-build.mjs';

const project = resolve(dirname(fileURLToPath(import.meta.url)), '../../..');
if (process.argv[1] !== undefined && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) await main();

/** Prepares one native artifact/classpath identity for a later matrix; no server or client starts here. */
export async function main(argumentsValue = process.argv.slice(2)) {
  const output = underBuild(parse(argumentsValue));
  const gradle = process.env.FRONTIER_V3_GRADLE ?? resolve(project, 'gradlew');
  const code = await child(gradle, [':pale-mirror-neoforge:prepareFrontierV3PilotNativeEnvironment', '--offline', '--no-daemon']);
  if (code !== 0) throw new Error(`native prepared build failed (${code})`);
  const libraries = resolve(project, 'pale-mirror-neoforge/build/libs');
  const candidates = (await readdir(libraries)).filter((name) => /^[a-z0-9_-]+-.*\.jar$/i.test(name)
    && !name.includes('-sources') && !name.includes('-javadoc')).sort();
  if (candidates.length !== 1) throw new Error(`native prepared build expected exactly one artifact, found ${candidates.join(', ') || 'none'}`);
  const exec = promisify(execFile);
  const [commit, status] = await Promise.all([
    exec('git', ['rev-parse', 'HEAD'], { cwd: project }).then((value) => value.stdout.trim()),
    exec('git', ['status', '--porcelain'], { cwd: project }).then((value) => value.stdout.trim())
  ]);
  const identity = Object.freeze({ sourceCommit: commit, sourceDirty: status.length > 0, profile: 'disposable_lite',
    sourceContent: await fingerprintPreparedSource(project),
    ...(await fingerprintPreparedBuild(project, resolve(libraries, candidates[0]))) });
  await mkdir(dirname(output), { recursive: true });
  await writeFile(output, `${JSON.stringify(identity, null, 2)}\n`, { encoding: 'utf8', flag: 'wx' });
  console.log(JSON.stringify({ status: 'ok', output, sourceDirty: identity.sourceDirty, artifact: identity.preparedArtifact.sha256 }));
  return identity;
}

export function parse(argumentsValue) {
  if (argumentsValue.length !== 1 || !argumentsValue[0].startsWith('--output=')) {
    throw new Error('usage: prepare-native-build.mjs --output=<build/prepared-build.json>');
  }
  return safeRelative(argumentsValue[0].slice('--output='.length));
}
function underBuild(value) { const target = resolve(project, value); const build = resolve(project, 'build'); if (!target.startsWith(build + '/')) throw new Error('prepared build output must remain under build/'); return target; }
function safeRelative(value) { if (typeof value !== 'string' || value.length === 0 || value.startsWith('/') || value.includes('\\') || value.split('/').includes('..')) throw new Error('prepared build output must be a safe relative path'); return value; }
function child(command, args) { return new Promise((resolveExit, rejectExit) => { const childProcess = spawn(command, args, { cwd: project, env: process.env, stdio: 'inherit', shell: false }); childProcess.once('error', rejectExit); childProcess.once('exit', (code, signal) => resolveExit(code ?? (signal == null ? 1 : 128))); }); }
