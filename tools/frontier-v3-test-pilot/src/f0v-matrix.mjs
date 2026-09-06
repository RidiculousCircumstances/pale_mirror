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
  const value = atPath(diagnostic(manifest, projection.view, projection.id), projection.path);
  if (!Number.isSafeInteger(value) || value < 0) {
    throw new Error(`generated F0.V arrival checkpoint is absent or non-integral: ${entry.variant}`);
  }
  return Object.freeze({ view: projection.view, id: projection.id, path: projection.path, value });
}

/** The pair belongs to the aggregate owner when its variants run in separate shards. */
export function requireDistinctArrivalCheckpoints(first, second) {
  if (!first || !second || first.view !== second.view || first.id !== second.id || first.path !== second.path || first.value === second.value) {
    throw new Error('F0.V arrival checkpoints do not prove two distinct retained process stages');
  }
  return Object.freeze({ first: Object.freeze(structuredClone(first)), second: Object.freeze(structuredClone(second)) });
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
