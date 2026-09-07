import assert from 'node:assert/strict';
import test from 'node:test';
import { PhaseTiming, compareNativeTimingMedians } from '../src/timing.mjs';

test('failed native boundaries remain measurable instead of masking the first failure', () => {
  const timing = new PhaseTiming();
  timing.begin('client.jvm_boot_and_connect');
  timing.abortOpen({ status: 'failed' });
  const report = timing.finish({ runner: 'test' });
  assert.equal(report.phases.length, 1);
  assert.equal(report.phases[0].name, 'client.jvm_boot_and_connect');
  assert.equal(report.phases[0].incomplete, true);
  assert.equal(report.phases[0].status, 'failed');
});

test('failure snapshots retain completed phases while cleanup is still open', () => {
  const timing = new PhaseTiming();
  timing.begin('cleanup');
  const snapshot = timing.snapshot({ runner: 'test', status: 'failed' });
  assert.deepEqual(snapshot.open, ['cleanup']);
  assert.equal(snapshot.status, 'failed');
  timing.end('cleanup');
  assert.equal(timing.finish({ runner: 'test' }).phases.length, 1);
});

test('three native baseline and candidate runs report the versioned advisory target', () => {
  const environment = { host: 'same-host', seed: 41, profile: 'world', viewDistance: 10, sourceCommit: 'abc',
    preparedArtifactSha256: 'jar', serverClasspathSha256: 'server', clientClasspathSha256: 'client' };
  const sample = (totalMillis, status = 'ok', extra = {}) => ({ totalMillis, status, environment: { ...environment, ...extra } });
  const report = compareNativeTimingMedians({ baseline: [sample(100), sample(110), sample(120)],
    candidate: [sample(70), sample(75), sample(80)] });
  assert.equal(report.baselineMedianMillis, 110);
  assert.equal(report.candidateMedianMillis, 75);
  assert.ok(report.improvement >= 0.25);
  assert.equal(report.targetMet, true);
  assert.deepEqual(report.comparisonPolicy, { version: 2, thresholdMode: 'advisory-target', targetImprovement: 0.25 });
});

test('a positive 22.52 percent result is valid but reports the advisory target as unmet', () => {
  const environment = { host: 'same-host', seed: 41, profile: 'world', viewDistance: 10, sourceCommit: 'abc',
    preparedArtifactSha256: 'jar', serverClasspathSha256: 'server', clientClasspathSha256: 'client' };
  const sample = (totalMillis) => ({ totalMillis, status: 'ok', environment });
  const report = compareNativeTimingMedians({ baseline: [sample(100), sample(100), sample(100)],
    candidate: [sample(77.48), sample(77.48), sample(77.48)] });
  assert.equal(report.baselineMedianMillis, 100);
  assert.equal(report.candidateMedianMillis, 77.48);
  assert.ok(Math.abs(report.improvement - 0.2252) < Number.EPSILON);
  assert.equal(report.targetMet, false);
  assert.deepEqual(report.comparisonPolicy, { version: 2, thresholdMode: 'advisory-target', targetImprovement: 0.25 });
});

test('native timing comparison rejects identity, malformed samples, invalid targets, and nonpositive benefit', () => {
  const environment = { host: 'same-host', seed: 41, profile: 'world', viewDistance: 10, sourceCommit: 'abc',
    preparedArtifactSha256: 'jar', serverClasspathSha256: 'server', clientClasspathSha256: 'client' };
  const sample = (totalMillis, status = 'ok', extra = {}) => ({ totalMillis, status, environment: { ...environment, ...extra } });
  assert.throws(() => compareNativeTimingMedians({ baseline: [sample(100), sample(110), sample(120)],
    candidate: [sample(70), sample(75), sample(80, 'ok', { seed: 42 })] }), /one host/);
  assert.throws(() => compareNativeTimingMedians({ baseline: [sample(100), sample(110), sample(120)],
    candidate: [sample(70), sample(75), sample(80, 'failed')] }), /failed or malformed/);
  assert.throws(() => compareNativeTimingMedians({ baseline: [sample(100), sample(110), sample(120)],
    candidate: [sample(90), sample(95), sample(100)], targetImprovement: 0 }), /target improvement/);
  assert.throws(() => compareNativeTimingMedians({ baseline: [sample(100), sample(110), sample(120)],
    candidate: [sample(90), sample(95), sample(100)], targetImprovement: 1 }), /target improvement/);
  assert.throws(() => compareNativeTimingMedians({ baseline: [sample(100), sample(110), sample(120)],
    candidate: [sample(90), sample(95), sample(100)], targetImprovement: Number.NaN }), /target improvement/);
  assert.throws(() => compareNativeTimingMedians({ baseline: [sample(100), sample(110), sample(120)],
    candidate: [sample(110), sample(115), sample(120)] }), /no positive improvement/);
  assert.throws(() => compareNativeTimingMedians({ baseline: [sample(100), sample(110), sample(120)],
    candidate: [sample(100), sample(110), sample(120)] }), /no positive improvement/);
  assert.throws(() => compareNativeTimingMedians({ baseline: [sample(100), sample(110)],
    candidate: [sample(70), sample(75), sample(80)] }), /exactly three complete samples/);
  assert.throws(() => compareNativeTimingMedians({ baseline: [sample(100), sample(110), sample(120)],
    candidate: [sample(70), sample(75), sample(undefined)] }), /failed or malformed/);
});
