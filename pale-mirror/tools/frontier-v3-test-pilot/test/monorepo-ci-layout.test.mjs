import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { access, readFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';
import { fingerprintWorkingContent } from '../src/evidence-cache.mjs';

const project = resolve(dirname(fileURLToPath(import.meta.url)), '..', '..', '..');
const monorepo = resolve(project, '..');
const workflowRoot = resolve(monorepo, '.github', 'workflows');

test('monorepo workflows keep Pale Mirror commands and artifact paths under pale-mirror', async () => {
  assert.equal(execFileSync('git', ['rev-parse', '--show-prefix'], { cwd: project, encoding: 'utf8' }).trim(), 'pale-mirror/');
  await assert.rejects(access(resolve(project, '.github', 'workflows', 'build.yml')));
  const [workflow, sample] = await Promise.all([
    readFile(resolve(workflowRoot, 'build.yml'), 'utf8'),
    readFile(resolve(workflowRoot, 'f0va-native-correctness-sample.yml'), 'utf8')
  ]);
  for (const content of [workflow, sample]) {
    assert.match(content, /^defaults:\n  run:\n    working-directory: pale-mirror$/m);
    assert.doesNotMatch(content, /(?:^|\n)\s*path: build\//);
    assert.doesNotMatch(content, /pale-mirror\/\.github\/workflows/);
  }
  assert.match(workflow, /uses: \.\/\.github\/workflows\/f0va-native-correctness-sample\.yml/);
  assert.match(workflow, /path: pale-mirror\/build\/f0va-ci/);
  assert.match(sample, /path: pale-mirror\/build\/f0va-ci/);
});

test('core CI provisions its complete isolated Node and Python test footprint before Gradle', async () => {
  const workflow = await readFile(resolve(workflowRoot, 'build.yml'), 'utf8');
  const requirements = await readFile(resolve(project, 'tools/engineering/requirements-ci.txt'), 'utf8');
  assert.equal(requirements, 'Pillow==12.1.1\nPyYAML==6.0.3\n');
  const core = workflow.slice(workflow.indexOf('  core:\n'), workflow.indexOf('\n  f0va-prepare:\n'));
  assertCoreRuntimeBootstrap(core);

  assert.throws(() => assertCoreRuntimeBootstrap(core.replace('      - name: Setup Node 22\n',
    '      - name: Missing Node setup\n')), /Node 22/);
  assert.throws(() => assertCoreRuntimeBootstrap(core.replace("node-version: '22'", "node-version: '24'")), /Node 22/);
  assert.throws(() => assertCoreRuntimeBootstrap(core.replace('      - name: Initialize isolated Python test environment\n',
    '      - name: Missing isolated Python test environment\n')), /initializer/);
  assert.throws(() => assertCoreRuntimeBootstrap(core.replace('      - name: Initialize isolated Python test environment\n',
    '      - name: Delayed isolated Python test environment\n') + '\n      - name: Initialize isolated Python test environment\n'), /before Gradle/);
  assert.throws(() => assertCoreRuntimeBootstrap(core.replace('tools/engineering/requirements-ci.txt', 'tools/engineering/requirements-local.txt')),
    /requirements/);
  assert.throws(() => assertCoreRuntimeBootstrap(core.replace('>> "$GITHUB_PATH"', '>> "$UNSAFE_PATH"')), /GITHUB_PATH/);
  assert.throws(() => assertCoreRuntimeBootstrap(core.replace('python-version: \'3.11\'', 'python-version: \'3.14\'')), /Python 3\.11/);
});

test('monorepo content inventory stays rooted in Pale Mirror rather than pack inputs', async () => {
  const listed = execFileSync('git', ['ls-files', '--cached', '--others', '--exclude-standard', '-z'], { cwd: project, encoding: 'utf8' })
    .split('\0').filter(Boolean);
  assert(listed.includes('architecture.yml'));
  assert(!listed.includes('pack.toml'));
  assert(listed.every((path) => !path.startsWith('../') && !path.split('/').includes('..')));
  const fingerprint = await fingerprintWorkingContent(project, ['architecture.yml', 'tools/frontier-v3-test-pilot/']);
  assert(fingerprint.files.some((file) => file.path === 'architecture.yml'));
  assert(fingerprint.files.every((file) => !file.path.startsWith('../') && !file.path.split('/').includes('..')));
});

function assertCoreRuntimeBootstrap(core) {
  const node = core.indexOf('      - name: Setup Node 22\n');
  const python = core.indexOf('      - name: Setup Python 3.11\n');
  const initializer = core.indexOf('      - name: Initialize isolated Python test environment\n');
  const gradle = core.indexOf('      - name: Setup Gradle\n');
  const verification = core.indexOf('      - name: Verify core vertical slice\n');
  assert.notEqual(node, -1, 'core CI lacks Node 22 setup');
  assert.notEqual(python, -1, 'core CI lacks Python 3.11 setup');
  assert.notEqual(initializer, -1, 'core CI lacks isolated Python initializer');
  assert.notEqual(gradle, -1, 'core CI lacks Gradle setup');
  assert.notEqual(verification, -1, 'core CI lacks the core Gradle gate');
  assert.ok(node < python && python < initializer && initializer < gradle && gradle < verification, 'Node and Python initializers must run before Gradle');
  const nodeBlock = core.slice(node, python);
  assert.match(nodeBlock, /uses: actions\/setup-node@v4\n        with:\n          node-version: '22'/);
  const pythonBlock = core.slice(python, initializer);
  assert.match(pythonBlock, /uses: actions\/setup-python@v5\n        with:\n          python-version: '3\.11'/);
  const initializerBlock = core.slice(initializer, gradle);
  assert.match(initializerBlock, /test -n "\$\{RUNNER_TEMP:-\}"/);
  assert.match(initializerBlock, /test -n "\$\{GITHUB_PATH:-\}"/);
  assert.match(initializerBlock, /case "\$RUNNER_TEMP" in/);
  assert.match(initializerBlock, /case "\$GITHUB_PATH" in/);
  assert.match(initializerBlock, /frontier_v3_core_python_env="\$RUNNER_TEMP\/frontier-v3-core-python"/);
  assert.match(initializerBlock, /python -m venv "\$frontier_v3_core_python_env"/);
  assert.match(initializerBlock, /\$frontier_v3_core_python_env\/bin\/python" -m pip install --disable-pip-version-check --requirement tools\/engineering\/requirements-ci\.txt/);
  assert.match(initializerBlock, /\$frontier_v3_core_python_env\/bin\/python" -c "import PIL, yaml"/);
  assert.match(initializerBlock, /printf '%s\\n' "\$frontier_v3_core_python_env\/bin" >> "\$GITHUB_PATH"/);
}
