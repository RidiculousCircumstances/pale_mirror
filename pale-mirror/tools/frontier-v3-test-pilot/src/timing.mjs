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

  /**
   * Failure bundles are emitted while their outer cleanup phase is still open.
   * Capture completed timing facts without pretending that cleanup completed or
   * letting diagnostic serialization replace the original causal failure.
   */
  snapshot(detail = {}) {
    return Object.freeze({ totalMillis: elapsedMillis(this.#started, process.hrtime.bigint()), phases: Object.freeze([...this.#entries]),
      open: Object.freeze([...this.#open.keys()]), ...checkedDetail(detail) });
  }
}

export function elapsedMillis(started, finished = process.hrtime.bigint()) {
  return Number(finished - started) / 1_000_000;
}

function requireName(name) {
  if (typeof name !== 'string' || !/^[a-z][a-z0-9_.-]{0,95}$/.test(name)) throw new Error(`invalid timing phase: ${name}`);
}

function checkedDetail(detail) {
  if (!detail || typeof detail !== 'object' || Array.isArray(detail)) throw new Error('timing detail must be an object');
  return Object.fromEntries(Object.entries(detail).filter(([key, value]) => /^[a-z][a-zA-Z0-9]*$/.test(key)
    && (value === null || ['string', 'number', 'boolean'].includes(typeof value))));
}
