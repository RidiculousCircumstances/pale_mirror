/** Read-only finite-trace monitor over versioned owner declarations, never a production reducer. */
export class SemanticProgressMonitor {
  #subjects = new Map();
  observe(value) {
    const o = value?.progressObligation;
    if (o === undefined) return;
    if (o.schema !== 1 || o.subject !== value.id || typeof o.rule !== 'string' || !o.rule
        || typeof o.worker !== 'string' || !o.worker || typeof o.fingerprint !== 'string' || !o.fingerprint
        || !Number.isSafeInteger(o.generation) || o.generation < 1
        || !Number.isSafeInteger(o.revision) || o.revision < 1 || !Number.isSafeInteger(value.instant) || value.instant < 0
        || !Number.isSafeInteger(o.budgetTicks) || o.budgetTicks < 1 || o.budgetTicks > 1_000_000
        || !['ELIGIBLE', 'HIGHER_PRIORITY_ACTIVITY', 'RECEIVER_CAPACITY', 'SERVICE_ACCESS', 'COURIER_CASUALTY', 'RECOVERY_UNKNOWN', 'TERMINAL'].includes(o.disposition))
      throw new Error('invalid versioned semantic progress obligation');
    const old = this.#subjects.get(o.subject);
    if (old && (old.rule !== o.rule || old.worker !== o.worker || o.generation < old.generation
        || o.revision < old.revision || value.instant < old.instant))
      throw new Error(`semantic identity/time regressed: ${o.subject}`);
    if (!old && this.#subjects.size >= 1024) throw new Error('semantic monitor active set exceeds1024');
    const advanced = !old || old.fingerprint !== o.fingerprint;
    const baseline = advanced ? value.instant : old.baseline;
    const eligibleTicks = advanced ? 0 : old.eligibleTicks + (old.disposition === 'ELIGIBLE' ? value.instant - old.instant : 0);
    if (o.disposition === 'ELIGIBLE' && eligibleTicks > o.budgetTicks)
      throw new Error(`semantic progress deadline violated: ${o.subject} next=${o.next} baseline=${baseline} instant=${value.instant}`);
    if (o.disposition === 'TERMINAL' && o.next !== 'CLOSED') throw new Error(`incomplete terminal story: ${o.subject}`);
    this.#subjects.set(o.subject, { ...o, instant: value.instant, baseline, eligibleTicks });
  }
  receipts() {
    return [...this.#subjects.values()].map(o => ({ ...o,
      verdict: o.disposition === 'TERMINAL' ? 'SATISFIED' : o.disposition === 'RECOVERY_UNKNOWN' ? 'INCONCLUSIVE' : 'PENDING' }));
  }
}
