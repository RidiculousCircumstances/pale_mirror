import { composeF0vScenarioMatrix, validateF0vContract } from './f0v-contract.mjs';

/**
 * Pure half of the F0.V matrix runner.  It deliberately knows no process family, server port,
 * world directory or fault mechanism: contracts compose scenarios, while the native runner owns
 * lifecycle.  Keeping the comparator here makes every lane use the same terminal semantics.
 */
export function materializeF0vMatrix(contract) {
  const matrix = composeF0vScenarioMatrix(validateF0vContract(contract));
  return Object.freeze(matrix.map((entry) => Object.freeze({
    ...entry,
    executions: entry.driver === 'CRASH_WAL'
      ? Object.freeze(entry.crashWindows.map((crash) => Object.freeze({ lane: crash.boundary,
        scenario: Object.freeze({ ...entry.scenario, id: `${entry.scenario.id}_${crash.boundary}`, crash }) })))
      : entry.differential.length === 0
        ? Object.freeze([{ lane: 'single', scenario: entry.scenario }])
        : entry.differential
  })));
}

/** Compares only explicit read-only terminal projections, never physical trajectories or timing. */
export function compareDifferential(contractEntry, coldManifest, hotColdManifest) {
  if (contractEntry.driver !== 'COLD_VS_HOT_COLD') throw new Error('only a declared differential may be compared');
  requireAbsoluteDifferentialAlignment(contractEntry, coldManifest, hotColdManifest);
  return compareDifferentialEvidence(contractEntry, extractDeclaredProjections(coldManifest, contractEntry.scenario.f0vDifferential, 'differential'),
    extractDeclaredProjections(hotColdManifest, contractEntry.scenario.f0vDifferential, 'differential'));
}

/** Projects only contract-declared diagnostic values into portable aggregate evidence. */
export function extractDeclaredProjections(manifest, declarations, label = 'projection') {
  if (!Array.isArray(declarations)) throw new Error(`generated F0.V ${label} declaration is malformed`);
  return Object.freeze(declarations.map((projection) => {
    if (!projection || typeof projection.invariant !== 'string' || typeof projection.view !== 'string' || typeof projection.id !== 'string'
        || !Array.isArray(projection.paths) || projection.paths.length === 0) throw new Error(`generated F0.V ${label} declaration is malformed`);
    const observed = diagnostic(manifest, projection.view, projection.id);
    if (observed === undefined) throw new Error(`${label} lacks terminal diagnostic: ${projection.invariant}`);
    const values = Object.fromEntries(projection.paths.map((path) => {
      const value = atPath(observed, path);
      if (value === undefined) throw new Error(`${label} lacks declared value: ${projection.invariant}/${path}`);
      return [path, structuredClone(value)];
    }));
    return Object.freeze({ invariant: projection.invariant, view: projection.view, id: projection.id,
      paths: Object.freeze([...projection.paths]), values: Object.freeze(values) });
  }));
}

/** One declaration-driven comparator is shared by local complete runs and CI aggregate evidence. */
export function compareDifferentialEvidence(contractEntry, coldEvidence, hotColdEvidence) {
  if (contractEntry.driver !== 'COLD_VS_HOT_COLD') throw new Error('only a declared differential may be compared');
  const declarations = contractEntry.scenario.f0vDifferential;
  const cold = requireDeclaredProjectionEvidence(declarations, coldEvidence, 'cold differential');
  const hotCold = requireDeclaredProjectionEvidence(declarations, hotColdEvidence, 'hot_cold differential');
  return Object.freeze(declarations.map((projection, index) => {
    for (const path of projection.paths) {
      if (!equivalent(cold[index].values[path], hotCold[index].values[path], projection.tolerance ?? 0)) {
        throw new Error(`differential ${projection.invariant} diverged at ${path}`);
      }
    }
    return Object.freeze({ invariant: projection.invariant, view: projection.view, id: projection.id, paths: Object.freeze([...projection.paths]) });
  }));
}

/** Finalizes one runner entry without treating a selected differential half as a complete proof. */
export function finalizeMatrixEntry(entry, runs, { selectedLane = false } = {}) {
  if (entry.driver !== 'COLD_VS_HOT_COLD') return Object.freeze({ differential: Object.freeze([]) });
  const cold = runs.find((run) => run.lane === 'cold');
  const hotCold = runs.find((run) => run.lane === 'hot_cold');
  if (cold === undefined || hotCold === undefined) {
    if (!selectedLane || runs.length !== 1) throw new Error(`F0.V differential is incomplete: ${entry.variant}`);
    return Object.freeze({ differential: Object.freeze({ status: 'pending_aggregate', requiredLanes: Object.freeze(['cold', 'hot_cold']),
      evidence: extractDeclaredProjections(runs[0].result, entry.scenario.f0vDifferential, 'differential') }) });
  }
  return Object.freeze({ differential: compareDifferential(entry, cold.result, hotCold.result) });
}

/**
 * Extends a COLD differential lane by the exact elapsed canonical instants
 * observed in its HOT/COLD peer. This does not normalize or omit any compared
 * value: both final diagnostics are still compared byte-for-byte over the
 * complete declared projection, including full schedule entries. The delta is
 * discovered from a checked-in read-only anchor, never hard-coded as a timing
 * guess in the contract.
 */
export function targetColdDifferentialScenario(entry, coldScenario, hotColdManifest) {
  if (entry?.driver !== 'COLD_VS_HOT_COLD' || !entry.differentialAlignment || coldScenario?.f0vExecutionLane !== 'cold') {
    throw new Error('semantic time alignment requires a declared COLD/HOT differential pair');
  }
  const alignment = entry.differentialAlignment;
  const terminal = diagnostic(hotColdManifest, alignment.view, alignment.id);
  const targetInstant = atPath(terminal, alignment.instantPath);
  if (!Number.isSafeInteger(targetInstant) || targetInstant < 1) {
    throw new Error('F0.V differential absolute target is absent or invalid');
  }
  const terminalInspections = distinctInspectionCount(coldScenario.f0vTerminalProjections);
  if (terminalInspections < 1 || terminalInspections >= coldScenario.actions.length) {
    throw new Error('F0.V differential COLD lane lacks terminal inspections for semantic alignment');
  }
  const terminalStart = coldScenario.actions.length - terminalInspections;
  const actions = [
    ...coldScenario.actions.slice(0, terminalStart),
    { type: 'fast_forward_to_instant', targetInstant, timeoutMs: 180_000 },
    ...coldScenario.actions.slice(terminalStart)
  ];
  return Object.freeze({ ...structuredClone(coldScenario), actions: Object.freeze(actions),
    assertions: Object.freeze(coldScenario.assertions.map((assertion) => assertion.after === terminalStart + terminalInspections
      ? Object.freeze({ ...assertion, after: actions.length }) : structuredClone(assertion))),
    f0vAbsoluteTarget: Object.freeze({ view: alignment.view, id: alignment.id, instantPath: alignment.instantPath, targetInstant }) });
}

function requireAbsoluteDifferentialAlignment(entry, coldManifest, hotColdManifest) {
  const alignment = entry.differentialAlignment;
  if (!alignment) throw new Error('F0.V differential lacks an absolute target declaration');
  const hotInstant = atPath(diagnostic(hotColdManifest, alignment.view, alignment.id), alignment.instantPath);
  const coldInstant = atPath(diagnostic(coldManifest, alignment.view, alignment.id), alignment.instantPath);
  if (!Number.isSafeInteger(hotInstant) || !Number.isSafeInteger(coldInstant) || hotInstant !== coldInstant) {
    throw new Error(`F0.V differential terminal instants diverged: COLD=${coldInstant} HOT/COLD=${hotInstant}`);
  }
}

/** Verifies that the normal client runner retained each declared terminal domain assertion. */
export function requireTerminalEvidence(scenario, manifest) {
  const terminal = scenario.assertions.filter((assertion) => assertion.after === scenario.actions.length);
  if (terminal.length === 0) throw new Error('generated F0.V scenario has no terminal assertion');
  for (const assertion of terminal) {
    const actual = diagnostic(manifest, assertion.view, assertion.id);
    if (actual === undefined || !matches(actual, assertion.expect)) {
      throw new Error(`generated F0.V terminal assertion is absent or false: ${assertion.view} ${assertion.id}`);
    }
  }
  const projections = scenario.f0vTerminalProjections;
  if (!Array.isArray(projections) || projections.length === 0) {
    throw new Error('generated F0.V scenario lacks a declared semantic terminal projection');
  }
  for (const projection of projections) {
    const assertion = terminal.find((candidate) => candidate.view === projection.view && candidate.id === projection.id
      && projection.paths.every((path) => expectedPath(candidate.expect, path) !== undefined));
    const actual = diagnostic(manifest, projection.view, projection.id);
    if (assertion === undefined || actual === undefined || projection.paths.some((path) => atPath(actual, path) === undefined)) {
      throw new Error(`generated F0.V terminal semantic invariant is missing: ${projection.invariant}`);
    }
  }
}

/** Exact retained checkpoint value used only to prove two declared arrival stages differ. */
export function requireArrivalCheckpoint(entry, manifest) {
  const projection = entry?.scenario?.f0vArrivalCheckpoint;
  if (projection === undefined) return undefined;
  const before = atPath(diagnosticAt(manifest, projection.beforeAction, projection.view, projection.id), projection.path);
  const after = atPath(diagnostic(manifest, projection.view, projection.id), projection.path);
  if (!Number.isSafeInteger(before) || before < 0 || !Number.isSafeInteger(after) || after < before + projection.minimumAdvance) {
    throw new Error(`generated F0.V arrival checkpoint is absent or non-integral: ${entry.variant}`);
  }
  return Object.freeze({ stage: projection.stage, view: projection.view, id: projection.id, path: projection.path,
    beforeAction: projection.beforeAction, before, after, minimumAdvance: projection.minimumAdvance });
}

/** The pair belongs to the aggregate owner when its variants run in separate shards. */
export function requireDistinctArrivalCheckpoints(first, second) {
  if (!first || !second || first.view !== second.view || first.id !== second.id || first.path !== second.path
      || first.stage === second.stage || first.after === second.after) {
    throw new Error('F0.V arrival checkpoints do not prove two distinct retained process stages');
  }
  return Object.freeze({ first: Object.freeze(structuredClone(first)), second: Object.freeze(structuredClone(second)) });
}

/** Retains native evidence for two complete HOT→COLD→HOT cycles of one continuation. */
export function requireHotColdCycles(entry, manifest) {
  const cycles = entry?.scenario?.f0vHotColdCycles;
  if (cycles === undefined) return undefined;
  if (!Array.isArray(cycles) || cycles.length < 2) throw new Error('generated F0.V hand-off cycles are incomplete');
  let retained;
  const evidence = cycles.map((cycle) => {
    const hot = diagnosticAt(manifest, cycle.enteredHotAction, 'process', entry.scenario.f0vTerminalProjections[0].id);
    const cold = diagnosticAt(manifest, cycle.releasedColdAction, 'process', entry.scenario.f0vTerminalProjections[0].id);
    const returned = diagnosticAt(manifest, cycle.returnedHotAction, 'process', entry.scenario.f0vTerminalProjections[0].id);
    if (!hot || !cold || !returned || hot.claims?.lease?.status !== 'HOT' || cold.claims?.lease !== null || returned.claims?.lease?.status !== 'HOT') {
      throw new Error('generated F0.V hand-off cycle lacks HOT/COLD/HOT observations');
    }
    const identity = { job: hot.identity?.job, worker: hot.identity?.worker, schedule: hot.schedule?.entries?.[0]?.id };
    if (!identity.job || !identity.worker || !identity.schedule || JSON.stringify(identity) !== JSON.stringify({ job: cold.identity?.job,
      worker: cold.identity?.worker, schedule: cold.schedule?.entries?.[0]?.id }) || JSON.stringify(identity) !== JSON.stringify({ job: returned.identity?.job,
      worker: returned.identity?.worker, schedule: returned.schedule?.entries?.[0]?.id })) {
      throw new Error('generated F0.V hand-off cycle replaced its retained continuation');
    }
    const cursors = [hot, cold, returned].map(value => value.cursor?.index);
    if (cursors.some(value => !Number.isSafeInteger(value) || value < 0) || cursors[1] < cursors[0] || cursors[2] < cursors[1]) {
      throw new Error('generated F0.V hand-off cycle regressed its retained cursor');
    }
    if (retained !== undefined && JSON.stringify(retained) !== JSON.stringify(identity)) {
      throw new Error('generated F0.V hand-off cycles changed job, worker or continuation');
    }
    retained = identity;
    return Object.freeze({ ...cycle, identity: Object.freeze(identity), cursors: Object.freeze(cursors) });
  });
  return Object.freeze(evidence);
}

/**
 * A COLD-only lane is evidence only when it records two retained checkpoints.  This is kept
 * process-neutral: the vertical contract supplies its own diagnostic paths and the runner only
 * verifies monotonic cursor advancement, body coherence and the absence of HOT authority at both
 * observations.
 */
export function requireColdProgress(scenario, manifest) {
  const projection = scenario?.f0vColdProgress;
  if (projection === undefined) return undefined;
  const before = diagnosticAt(manifest, projection.beforeAction, projection.view, projection.id);
  const after = diagnosticAt(manifest, projection.after, projection.view, projection.id);
  if (before === undefined || after === undefined) {
    throw new Error('generated F0.V COLD progress proof lacks its before/after diagnostics');
  }
  const beforeCursor = atPath(before, projection.cursorPath); const afterCursor = atPath(after, projection.cursorPath);
  if (!Number.isSafeInteger(beforeCursor) || !Number.isSafeInteger(afterCursor)
      || afterCursor < beforeCursor + projection.minimumAdvance) {
    throw new Error('generated F0.V COLD progress cursor did not advance');
  }
  for (const value of [before, after]) {
    const retained = atPath(value, projection.retainedBodyPath); const actor = atPath(value, projection.actorBodyPath);
    if (atPath(value, projection.leasePath) !== null || !equivalent(retained, actor, 0)) {
      throw new Error('generated F0.V COLD progress has HOT authority or divergent body checkpoint');
    }
  }
  return Object.freeze({ view: projection.view, id: projection.id, beforeAction: projection.beforeAction, afterAction: projection.after,
    cursorPath: projection.cursorPath, beforeCursor, afterCursor, minimumAdvance: projection.minimumAdvance });
}

function diagnostic(manifest, view, id) {
  const values = manifest?.diagnostics;
  if (!Array.isArray(values)) return undefined;
  for (let index = values.length - 1; index >= 0; index--) {
    const entry = values[index]; const value = entry?.observed?.value ?? entry?.value;
    if (value?.kind === view && value?.id === id) return value;
  }
  return undefined;
}

function diagnosticAt(manifest, actionStep, view, id) {
  const values = manifest?.diagnostics;
  if (!Array.isArray(values)) return undefined;
  for (let index = values.length - 1; index >= 0; index--) {
    const entry = values[index]; const observed = entry?.observed ?? entry; const value = observed?.value ?? entry?.value;
    if (observed?.actionStep === actionStep && value?.kind === view && value?.id === id) return value;
  }
  return undefined;
}

function atPath(value, path) {
  return path.split('.').reduce((current, key) => current != null && Object.prototype.hasOwnProperty.call(current, key) ? current[key] : undefined, value);
}

function equivalent(left, right, tolerance) {
  if (typeof left === 'number' && typeof right === 'number') return Math.abs(left - right) <= tolerance;
  return JSON.stringify(left) === JSON.stringify(right);
}

function matches(actual, expected) {
  return Object.entries(expected).every(([key, value]) => value && typeof value === 'object' && !Array.isArray(value)
    ? actual[key] && matches(actual[key], value) : actual[key] === value);
}

function expectedPath(value, path) {
  return atPath(value, path);
}

function distinctInspectionCount(projections) {
  if (!Array.isArray(projections)) return 0;
  return new Set(projections.map((projection) => `${projection?.view}\u0000${projection?.id}`)).size;
}

function requireDeclaredProjectionEvidence(declarations, evidence, label) {
  if (!Array.isArray(declarations) || !Array.isArray(evidence) || evidence.length !== declarations.length) {
    throw new Error(`${label} evidence is incomplete`);
  }
  return evidence.map((actual, index) => {
    const expected = declarations[index];
    if (!actual || actual.invariant !== expected.invariant || actual.view !== expected.view || actual.id !== expected.id
        || JSON.stringify(actual.paths) !== JSON.stringify(expected.paths) || !actual.values || typeof actual.values !== 'object'
        || Array.isArray(actual.values) || Object.keys(actual.values).sort().join(',') !== [...expected.paths].sort().join(',')
        || expected.paths.some((path) => actual.values[path] === undefined)) {
      throw new Error(`${label} evidence does not match declaration: ${expected?.invariant ?? index}`);
    }
    return actual;
  });
}
