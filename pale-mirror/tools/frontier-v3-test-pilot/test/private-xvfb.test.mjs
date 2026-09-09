import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { chmod, mkdtemp, readFile, rm, writeFile } from 'node:fs/promises';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import test from 'node:test';

const project = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..', '..');
const wrapper = resolve(project, 'scripts/with-private-xvfb.sh');
const xvfb = process.env.PALE_MIRROR_XVFB ?? '/home/rd/.local/bin/Xvfb';

test('private Xvfb admits the real caller command only after a same-namespace GLX context', async (context) => {
  const fixture = await fixtureRoot(context);
  const result = await invoke(fixture, ['bash', '-ceu', 'xdpyinfo -display "$DISPLAY" >/dev/null; printf caller-reached']);
  assert.equal(result.code, 0, result.stderr);
  assert.match(result.stdout, /caller-reached/);
  const diagnostic = await oneDiagnostic(fixture);
  assert.match(await readFile(join(diagnostic, 'glx-probe.json'), 'utf8'), /"status": "admitted"/);
});

test('private Xvfb rejects a live but graphics-inadequate display before the caller command', async (context) => {
  const fixture = await fixtureRoot(context);
  const marker = join(fixture.root, 'caller-reached');
  const withoutGlx = join(fixture.root, 'without-glx-xvfb');
  await writeFile(withoutGlx, `#!/usr/bin/env bash\nexec ${JSON.stringify(xvfb)} "$@" -extension GLX\n`, { mode: 0o700 });
  await chmod(withoutGlx, 0o700);
  const result = await invoke(fixture, ['bash', '-ceu', `touch ${JSON.stringify(marker)}`], { PALE_MIRROR_XVFB: withoutGlx });
  assert.notEqual(result.code, 0, 'graphics-inadequate Xvfb admitted a caller command');
  assert.match(result.stderr, /GLX admission was rejected/);
  await assert.rejects(readFile(marker), /ENOENT/);
  const diagnostic = await oneDiagnostic(fixture);
  assert.match(await readFile(join(diagnostic, 'glx-probe.json'), 'utf8'), /"status": "rejected"/);
  assert.match(await readFile(join(diagnostic, 'result'), 'utf8'), /status=1/);
});

test('private Xvfb retains bounded diagnostics when the admitted caller command fails', async (context) => {
  const fixture = await fixtureRoot(context);
  const result = await invoke(fixture, ['bash', '-ceu', 'printf command-failure >&2; exit 23']);
  assert.equal(result.code, 23, result.stderr);
  assert.match(result.stderr, /PM_PRIVATE_XVFB_DIAGNOSTIC=/);
  const diagnostic = await oneDiagnostic(fixture);
  assert.match(await readFile(join(diagnostic, 'result'), 'utf8'), /status=23/);
  assert.ok((await readFile(join(diagnostic, 'xvfb.log'))).byteLength <= 65_536);
});

async function fixtureRoot(context) {
  const root = await mkdtemp(join(project, 'build', 'private-xvfb-test-'));
  const processRoot = join(root, 'process');
  await (await import('node:fs/promises')).mkdir(processRoot);
  context.after(() => rm(root, { recursive: true, force: true }));
  return { root, processRoot };
}

async function invoke(fixture, command, extra = {}) {
  return await new Promise((resolveResult, reject) => {
    const child = spawn(wrapper, command, { cwd: project, env: { ...process.env, PALE_MIRROR_XVFB: xvfb,
      FRONTIER_V3_PILOT_PORT: '30123', FRONTIER_V3_NATIVE_PROCESS_ROOT: fixture.processRoot, ...extra }, stdio: ['ignore', 'pipe', 'pipe'] });
    let stdout = ''; let stderr = '';
    child.stdout.setEncoding('utf8').on('data', value => { stdout += value; });
    child.stderr.setEncoding('utf8').on('data', value => { stderr += value; });
    child.once('error', reject); child.once('exit', code => resolveResult({ code, stdout, stderr }));
  });
}

async function oneDiagnostic(fixture) {
  const { readdir } = await import('node:fs/promises');
  const entries = await readdir(fixture.processRoot, { withFileTypes: true });
  const directories = entries.filter(entry => entry.isDirectory() && entry.name.startsWith('frontier-v3-private-xvfb.'));
  assert.equal(directories.length, 1);
  return join(fixture.processRoot, directories[0].name);
}
