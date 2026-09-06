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
