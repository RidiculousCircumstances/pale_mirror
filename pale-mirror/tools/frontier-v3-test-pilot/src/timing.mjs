/**
 * Small monotonic timing boundary shared by the disposable runner and the
 * native-client runner.  It deliberately records elapsed process time rather
 * than wall-clock deltas, so an NTP correction cannot make a run look faster
 * or slower.  The ISO timestamps remain only operator correlation evidence.
 */
export class PhaseTiming {
  #started = process.hrtime.bigint();
  #open = new Map();
  #entries = [];

  begin(name, detail = {}) {
    requireName(name);
    if (this.#open.has(name)) throw new Error(`timing phase already open: ${name}`);
    this.#open.set(name, { started: process.hrtime.bigint(), startedAt: new Date().toISOString(), detail: checkedDetail(detail) });
  }

  end(name, detail = {}) {
    requireName(name);
    const active = this.#open.get(name);
    if (!active) throw new Error(`timing phase is not open: ${name}`);
    this.#open.delete(name);
    const finished = process.hrtime.bigint();
    this.#entries.push(Object.freeze({ name, startedAt: active.startedAt, finishedAt: new Date().toISOString(),
      durationMillis: elapsedMillis(active.started, finished), ...active.detail, ...checkedDetail(detail) }));
  }

  instant(name, detail = {}) {
    requireName(name);
    this.#entries.push(Object.freeze({ name, startedAt: new Date().toISOString(), finishedAt: new Date().toISOString(),
      durationMillis: 0, ...checkedDetail(detail) }));
  }

  /** Records a bounded failed boundary instead of masking its root cause with an open timer. */
  abortOpen(detail = {}) {
    for (const name of [...this.#open.keys()]) this.end(name, { incomplete: true, ...detail });
  }

  finish(detail = {}) {
    if (this.#open.size !== 0) throw new Error(`timing phases left open: ${[...this.#open.keys()].join(', ')}`);
    return Object.freeze({ totalMillis: elapsedMillis(this.#started, process.hrtime.bigint()), phases: Object.freeze([...this.#entries]), ...checkedDetail(detail) });
  }
}

export function elapsedMillis(started, finished = process.hrtime.bigint()) {
  return Number(finished - started) / 1_000_000;
}

/**
 * F0.V's timing gate deliberately compares complete native runs, not an isolated client phase.
 * The caller supplies exactly three successful baseline and candidate records made from the
 * same immutable prepared artifact/profile.  A missing sample, a failed native run or a changed
 * experiment identity is a gate failure rather than an opportunity to retry selectively.
 */
export function compareNativeTimingMedians({ baseline, candidate, targetImprovement = 0.25 }) {
  validateSeries('baseline', baseline); validateSeries('candidate', candidate);
  if (!Number.isFinite(targetImprovement) || targetImprovement <= 0 || targetImprovement >= 1) {
    throw new Error('timing target improvement must be a fraction between zero and one');
  }
  const baselineEnvironment = stableEnvironment(baseline[0].environment);
  for (const sample of [...baseline, ...candidate]) {
    if (stableEnvironment(sample.environment) !== baselineEnvironment) {
      throw new Error('native timing samples do not share one host/seed/profile/view-distance/build identity');
    }
  }
  const baselineMedianMillis = median(baseline.map((sample) => sample.totalMillis));
  const candidateMedianMillis = median(candidate.map((sample) => sample.totalMillis));
  const improvement = (baselineMedianMillis - candidateMedianMillis) / baselineMedianMillis;
  if (improvement <= 0) {
    throw new Error(`native timing median has no positive improvement: ${(improvement * 100).toFixed(2)}%`);
  }
  return Object.freeze({
    baselineMedianMillis,
    candidateMedianMillis,
    improvement,
    targetMet: improvement >= targetImprovement,
    comparisonPolicy: Object.freeze({ version: 2, thresholdMode: 'advisory-target', targetImprovement }),
    environment: baseline[0].environment
  });
}

export function median(values) {
  if (!Array.isArray(values) || values.length === 0 || values.some((value) => !Number.isFinite(value) || value <= 0)) {
    throw new Error('median needs positive finite timing values');
  }
  const sorted = [...values].sort((left, right) => left - right);
  const middle = Math.floor(sorted.length / 2);
  return sorted.length % 2 === 1 ? sorted[middle] : (sorted[middle - 1] + sorted[middle]) / 2;
}

function validateSeries(name, series) {
  if (!Array.isArray(series) || series.length !== 3) throw new Error(`native timing ${name} must contain exactly three complete samples`);
  for (const sample of series) {
    if (!sample || sample.status !== 'ok' || !Number.isFinite(sample.totalMillis) || sample.totalMillis <= 0
        || !sample.environment || typeof sample.environment !== 'object') {
      throw new Error(`native timing ${name} contains failed or malformed sample`);
    }
  }
}

function stableEnvironment(value) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('native timing sample lacks environment identity');
  const required = ['host', 'seed', 'profile', 'viewDistance', 'sourceCommit', 'preparedArtifactSha256', 'serverClasspathSha256', 'clientClasspathSha256'];
  for (const key of required) if (value[key] === undefined || value[key] === null || value[key] === '') {
    throw new Error(`native timing environment lacks ${key}`);
  }
  return JSON.stringify(Object.fromEntries(required.map((key) => [key, value[key]])));
}

function requireName(name) {
  if (typeof name !== 'string' || !/^[a-z][a-z0-9_.-]{0,95}$/.test(name)) throw new Error(`invalid timing phase: ${name}`);
}

function checkedDetail(detail) {
  if (!detail || typeof detail !== 'object' || Array.isArray(detail)) throw new Error('timing detail must be an object');
  return Object.fromEntries(Object.entries(detail).filter(([key, value]) => /^[a-z][a-zA-Z0-9]*$/.test(key)
    && (value === null || ['string', 'number', 'boolean'].includes(typeof value))));
}
