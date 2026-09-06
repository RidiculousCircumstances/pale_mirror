import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { access, mkdir, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
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

test('Visuals compile-only dependencies are resolved from exact verified pins rather than runtime server mods', async () => {
  const [visualsBuild, properties, gecko, villager, create, recurrence] = await Promise.all([
    readFile(resolve(project, 'pale-mirror-visuals', 'build.gradle'), 'utf8'),
    readFile(resolve(project, 'gradle.properties'), 'utf8'),
    readFile(resolve(monorepo, 'mods', 'geckolib.pw.toml'), 'utf8'),
    readFile(resolve(monorepo, 'mods', 'villager-overhaul.pw.toml'), 'utf8'),
    readFile(resolve(monorepo, 'mods', 'create.pw.toml'), 'utf8'),
    readFile(resolve(project, 'tools', 'engineering', 'verify_visual_compile_dependencies.mjs'), 'utf8')
  ]);
  assert.match(visualsBuild, /resolveVisualCompileDependencies/);
  assert.match(visualsBuild, /tasks\.named\('compileJava'\) \{ dependsOn resolveVisualCompileDependencies \}/);
  assert.match(visualsBuild, /prepareVisualsGameTestMods.*?dependsOn resolveVisualCompileDependencies/s);
  assert.match(visualsBuild, /outputs\.upToDateWhen \{ false \}/);
  for (const property of ['geckolib_integration_jar', 'villager_overhaul_integration_jar', 'create_integration_jar']) {
    assert.match(visualsBuild, new RegExp(`visualCompileArtifactsByProperty\\.${property}\\.candidate`));
  }
  assert.doesNotMatch(visualsBuild, /compileOnly files\(rootProject\.findProperty/);
  for (const [name, pack] of [['geckolib', gecko], ['villager_overhaul', villager], ['create', create]]) {
    const url = pack.match(/url = "([^"]+)"/)[1];
    const sha512 = pack.match(/hash = "([a-f0-9]{128})"/)[1];
    assert.match(properties, new RegExp(`^${name}_integration_url=${escapeRegExp(url)}$`, 'm'));
    assert.match(properties, new RegExp(`^${name}_integration_sha512=${sha512}$`, 'm'));
  }
  assert.match(visualsBuild, /SHA-512 mismatch/);
  assert.match(visualsBuild, /Pinned \$\{artifact\.label\} JAR is missing/);
  assertVisualsModDevFrontierSourceSet(visualsBuild);
  assert.throws(() => assertVisualsModDevFrontierSourceSet(visualsBuild.replace(
    "            sourceSet project(':pale-mirror-frontier').sourceSets.main\n", '')),
  /Frontier source set/);
  assert.match(recurrence, /normal invocation without --rerun-tasks/);
  assert.match(recurrence, /for \(const artifact of artifacts\)/);
  assert.match(recurrence, /mutate\(artifact\.overridePath\)/);
  assert.match(recurrence, /unlink\(artifact\.overridePath\)/);
  assert.match(recurrence, /mutate\(artifact\.defaultPath\)/);
});

test('prepared native launch resolves DevLaunch as an explicit isolated Gradle input', async () => {
  const build = await readFile(resolve(project, 'pale-mirror-neoforge', 'build.gradle'), 'utf8');
  assert.match(build, /frontierV3PilotLauncher\s*\{/);
  assert.match(build, /frontierV3PilotLauncher 'net\.neoforged:DevLaunch:1\.0\.2'/);
  assert.match(build, /configurations\.frontierV3PilotLauncher/);
  assert.doesNotMatch(build, /frontierV3PilotDevLaunchDirectory/);
});

test('all curated bunkhouses use the supported property-free rice bag provision without decoded collateral drift', async () => {
  const curator = resolve(project, 'tools', 'engineering', 'curate_bunkhouse_rice_bags.mjs');
  // The retained-before bytes are the accepted publication parent, rather
  // than HEAD: this recurrence must remain valid after the curator is itself
  // committed and after later provider-only commits.
  const curatedBeforeCommit = 'b3d04959b6c0c1d51164f92bd35ad4483e0d825c';
  const before = await mkdirTemp('pale-mirror-r3-bunkhouse-before-');
  try {
    for (const family of ['temperate', 'cold_taiga', 'dry_arid']) {
      const path = `pale-mirror/pale-mirror-visuals/src/main/resources/data/pale_mirror_visuals/structure/${family}/bunkhouse_2.nbt`;
      await writeFile(join(before, `${family}-bunkhouse_2.before.nbt`),
        execFileSync('git', ['show', `${curatedBeforeCommit}:${path}`], { cwd: project }));
    }
    assert.doesNotThrow(() => execFileSync('node', [curator, '--check', '--before-root', before], { cwd: project, encoding: 'utf8' }));
    assert.throws(() => execFileSync('node', [curator, '--check'], { cwd: project, encoding: 'utf8', stdio: 'pipe' }),
      /requires --before-root/);
    const source = execFileSync('node', ['--input-type=module', '--eval',
      `import { readFile } from 'node:fs/promises'; const s=await readFile(${JSON.stringify(curator)},'utf8'); if (!s.includes('verifyPreservedBefore') || !s.includes('alexscaves:dinosaur_chop') || !s.includes('farmersdelight:rice_bag')) process.exit(1);`],
      { cwd: project, encoding: 'utf8' });
    assert.equal(source, '');
  } finally {
    await rm(before, { recursive: true, force: true });
  }
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

function escapeRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function assertVisualsModDevFrontierSourceSet(visualsBuild) {
  const entry = '        pale_mirror {\n';
  const start = visualsBuild.indexOf(entry);
  const end = visualsBuild.indexOf('\n        }', start);
  assert.notEqual(start, -1, 'Visuals ModDev pale_mirror entry is missing');
  assert.notEqual(end, -1, 'Visuals ModDev pale_mirror entry is unterminated');
  const sourceSets = visualsBuild.slice(start, end);
  assert.ok(sourceSets.includes("sourceSet project(':pale-mirror-frontier').sourceSets.main"),
    'Visuals ModDev pale_mirror entry must include the Frontier source set');
}

async function mkdirTemp(prefix) {
  const root = join(tmpdir(), `${prefix}${process.pid}-${Date.now()}`);
  await mkdir(root, { recursive: true });
  return root;
}
