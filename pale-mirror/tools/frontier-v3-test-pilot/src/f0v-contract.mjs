const REQUIRED_VARIANTS = Object.freeze([
  'never_loaded', 'arrival_checkpoint_one', 'arrival_checkpoint_two', 'unload_return',
  'player_intervention', 'graceful_restart', 'abrupt_restart', 'neutral_observer_differential'
]);

const ALLOWED_DRIVERS = new Set(['COLD', 'HOT', 'HOT_COLD_HOT', 'SNAPSHOT_WAL', 'CRASH_WAL', 'COLD_VS_HOT_COLD']);
const RESTART_MODE = Object.freeze({ SNAPSHOT_WAL: 'graceful', CRASH_WAL: 'abrupt' });
const DIFFERENTIAL_INVARIANTS = Object.freeze(['identity', 'claims', 'conservation', 'schedule', 'result']);
const SEMANTIC_CRASH_BOUNDARIES = Object.freeze([
  'lease_recorded_before_physical_materialization',
  'physical_effect_visible_before_typed_observation',
  'typed_observation_durable_before_next_process_checkpoint',
  'hot_checkpoint_durable_before_drain_release',
  'release_durable_before_cold_resumption'
]);
const PHYSICAL_EFFECT_CRASH_BOUNDARIES = Object.freeze([
  'physical_effect_visible_before_typed_observation',
  'typed_observation_durable_before_next_process_checkpoint'
]);

/**
 * Process-neutral declarative F0.V vertical contract. A family owns its identities, ordinary
 * player actions and terminal domain assertions. This module owns the standard causal matrix
 * composition. It deliberately contains no concrete process-family names or policy branches.
 */
export function validateF0vContract(contract) {
  if (!contract || contract.schema !== 3 || !stableId(contract.family) || !stableId(contract.canonicalOwner)
      || !validBaseScenario(contract.scenario) || !validDeclaration(contract.declaration)
      || !contract.variants || typeof contract.variants !== 'object') {
    throw new Error('invalid F0.V vertical contract root');
  }
  const names = Object.keys(contract.variants).sort();
  if (names.join(',') !== [...REQUIRED_VARIANTS].sort().join(',')) throw new Error('F0.V vertical contract has missing or extra variants');
  for (const name of REQUIRED_VARIANTS) {
    validateVariant(name, contract.variants[name]);
    // A vertical result cannot be promoted from a single convenient status field.  Each
    // generated lane must leave terminal evidence for the complete semantic outcome:
    // identity, claims, conservation, schedule and result.  The comparator then reuses the
    // same vocabulary for the neutral-observer differential.
    validateSemanticProjections(contract.variants[name].terminalProjections, `F0.V ${name} terminal projections`);
    requireTerminalCoverage(contract.variants[name].terminalProjections, contract.variants[name], name);
    validateArrivalCheckpoint(name, contract.variants[name]);
    validateColdProgress(name, contract.variants[name]);
    if (contract.variants[name].driver === 'COLD_VS_HOT_COLD') {
      validateDifferentialRequirements(contract.declaration, contract.variants[name].differential);
      requireMatchingProjectionVocabulary(contract.declaration, contract.variants[name].terminalProjections, contract.variants[name].differential,
        contract.variants[name].alignment);
      validateDifferentialAlignment(contract.scenario, contract.variants[name]);
    }
  }
  return deepFreeze(structuredClone(contract));
}

/**
 * Generates reviewable ordinary-player scenarios from one vertical contract. The values use the
 * existing scenario language; this SDK adds no canonical mutation operation and does not execute
 * them. A matrix runner can persist these values under build/ for one native invocation, so a
 * restart never needs a copied handwritten scenario.
 */
export function composeF0vScenarioMatrix(contract) {
  const checked = validateF0vContract(contract);
  return Object.freeze(REQUIRED_VARIANTS.map((name) => {
    const variant = checked.variants[name];
    const scenario = composeVariantScenario(checked, name, variant);
    const differential = variant.driver === 'COLD_VS_HOT_COLD'
      ? Object.freeze(['cold', 'hot_cold'].map((lane) => deepFreeze({
        lane,
        scenario: { ...composeVariantScenario(checked, name, variant, variant.lanes[lane]), id: `${scenario.id}_${lane}`, f0vExecutionLane: lane }
      })))
      : Object.freeze([]);
    return Object.freeze({
      id: `${checked.family}.${name}`,
      family: checked.family,
      canonicalOwner: checked.canonicalOwner,
      declaration: checked.declaration,
      variant: name,
      driver: variant.driver,
      evidence: Object.freeze([...variant.evidence]),
      crashWindows: variant.crashWindows === undefined ? Object.freeze([]) : Object.freeze(structuredClone(variant.crashWindows)),
      deferredCrashWindows: variant.deferredCrashWindows === undefined ? Object.freeze([]) : Object.freeze(structuredClone(variant.deferredCrashWindows)),
      ...(variant.alignment === undefined ? {} : { differentialAlignment: Object.freeze(structuredClone(variant.alignment)) }),
      scenario: deepFreeze(scenario),
      differential
    });
  }));
}

function composeVariantScenario(contract, name, variant, lane = undefined, crash = undefined) {
  const base = contract.scenario;
  const sourceActions = [...base.actions, ...variant.actions, ...(lane?.actions ?? [])];
  // Diagnostics are read-only ordinary player commands.  Generating these terminal inspections
  // from the semantic contract prevents a reviewer from accidentally removing the observation
  // that makes a declared invariant checkable while keeping the gameplay actions themselves
  // process-owned and explicit in the contract.
  const terminalInspections = uniqueTerminalInspections(variant.terminalProjections);
  const terminalAfter = sourceActions.length + terminalInspections.length;
  const scenario = {
    schema: 1,
    id: `${base.idPrefix}_${name}`,
    isolation: structuredClone(base.isolation),
    server: structuredClone(base.server),
    pilot: structuredClone(base.pilot),
    setup: [...base.setup, ...(variant.setup ?? []), ...(lane?.setup ?? [])],
    actions: [...sourceActions, ...terminalInspections],
    assertions: variant.assertions.map((assertion) => ({ ...structuredClone(assertion),
      // A HOT/COLD lane may append ordinary evidence actions after the common process actions.
      // Keep the declaration stable by resolving `terminal` only after that lane is composed;
      // otherwise the HOT lane would accidentally assert an earlier COLD diagnostic.
      after: assertion.after === 'terminal' ? terminalAfter : assertion.after })),
    frames: structuredClone(variant.frames ?? []),
    // The scenario language remains generic; this retained declaration tells the native matrix
    // which terminal *semantic* claims must be demonstrated, instead of counting an arbitrary
    // status reply as a terminal proof.
    f0vTerminalProjections: structuredClone(variant.terminalProjections),
    ...(variant.arrivalCheckpoint === undefined ? {} : { f0vArrivalCheckpoint: structuredClone(variant.arrivalCheckpoint) }),
    ...(variant.coldProgress === undefined ? {} : { f0vColdProgress: composeColdProgress(variant.coldProgress, terminalAfter) }),
    ...(variant.driver === 'COLD_VS_HOT_COLD' ? { f0vDifferential: structuredClone(variant.differential) } : {}),
    ...(variant.driver === 'COLD_VS_HOT_COLD' ? { f0vDifferentialAlignment: structuredClone(variant.alignment) } : {}),
    ...(crash === undefined ? {} : { crash: structuredClone(crash) })
  };
  const restartMode = RESTART_MODE[variant.driver];
  if (restartMode !== undefined) {
    scenario.restart = {
      mode: restartMode,
      afterAction: variant.restartAfterAction,
      resumeSetup: structuredClone(variant.resumeSetup ?? [])
    };
  }
  return scenario;
}

function composeColdProgress(progress, terminalAfter) {
  return Object.freeze({ ...structuredClone(progress), after: progress.after === 'terminal' ? terminalAfter : progress.after });
}

function uniqueTerminalInspections(projections) {
  const seen = new Set();
  return projections.filter((projection) => {
    const key = `${projection.view}\u0000${projection.id}`;
    if (seen.has(key)) return false;
    seen.add(key); return true;
  }).map((projection) => Object.freeze({ type: 'inspect', view: projection.view, id: projection.id }));
}

function validateVariant(name, variant) {
  if (!variant || !ALLOWED_DRIVERS.has(variant.driver) || !Array.isArray(variant.evidence) || variant.evidence.length === 0
      || variant.evidence.some((value) => !stableToken(value)) || !Array.isArray(variant.actions) || variant.actions.length === 0
      || !Array.isArray(variant.assertions) || variant.assertions.length === 0 || !variant.assertions.every(validAssertion)
      || !variant.assertions.some((assertion) => (assertion.after === 'terminal' || assertion.after === variant.actions.length)
        && meaningfulExpectation(assertion.expect))) {
    throw new Error(`invalid F0.V variant: ${name}`);
  }
  validateGameplayElapsedWaits(name, variant.actions);
  validateTerminalProjections(variant.terminalProjections, `F0.V ${name} terminal projections`);
  if (RESTART_MODE[variant.driver] !== undefined
      && (!Number.isInteger(variant.restartAfterAction) || variant.restartAfterAction < 1 || variant.restartAfterAction >= variant.actions.length)) {
    throw new Error(`restart F0.V variant must split inside its ordinary action sequence: ${name}`);
  }
  if (RESTART_MODE[variant.driver] === undefined && variant.restartAfterAction !== undefined) {
    throw new Error(`non-restart F0.V variant cannot declare a restart split: ${name}`);
  }
  if (!Array.isArray(variant.frames ?? []) || !Array.isArray(variant.setup ?? []) || !Array.isArray(variant.resumeSetup ?? [])) {
    throw new Error(`invalid F0.V ordinary evidence declaration: ${name}`);
  }
  if (variant.driver === 'COLD_VS_HOT_COLD') validateDifferential(variant.differential);
  else if (variant.differential !== undefined || variant.lanes !== undefined) throw new Error(`non-differential F0.V variant cannot declare differential lanes: ${name}`);
  if (variant.driver === 'COLD_VS_HOT_COLD') validateDifferentialLanes(variant.lanes);
  if (variant.driver === 'CRASH_WAL') validateCrashWindows(variant.crashWindows, variant.deferredCrashWindows);
  else if (variant.crashWindows !== undefined || variant.deferredCrashWindows !== undefined) {
    throw new Error(`non-crash F0.V variant cannot declare crash windows: ${name}`);
  }
}

/**
 * The two arrival cases are intentionally separate native worlds.  Naming them is not proof
 * that the player arrived mid-process, so the contract must nominate one retained semantic
 * checkpoint whose terminal values the matrix can compare across the two runs.
 */
function validateArrivalCheckpoint(name, variant) {
  const required = name === 'arrival_checkpoint_one' || name === 'arrival_checkpoint_two';
  if (!required && variant.arrivalCheckpoint !== undefined) {
    throw new Error(`only arrival variants may declare an F0.V retained checkpoint: ${name}`);
  }
  if (!required) return;
  const checkpoint = variant.arrivalCheckpoint;
  if (!checkpoint || typeof checkpoint.view !== 'string' || typeof checkpoint.id !== 'string' || !validPath(checkpoint.path)) {
    throw new Error(`arrival F0.V variant lacks a retained checkpoint projection: ${name}`);
  }
  // The semantic categories have fixed expected values, while an exact cursor is deliberately
  // compared between two clean worlds instead of guessed by the contract.  It must nevertheless
  // be read by the same terminal inspection as one declared semantic projection.
  const terminal = variant.terminalProjections.find((projection) => projection.view === checkpoint.view && projection.id === checkpoint.id);
  if (terminal === undefined) throw new Error(`arrival F0.V checkpoint is not terminally inspected: ${name}`);
}

/**
 * Differential fields are a declarative read-only projection, never a mutation API.  Requiring
 * all five semantic classes prevents a merely visual COLD/HOT comparison from being counted as
 * equivalence.
 */
function validateDifferential(value) {
  validateSemanticProjections(value, 'F0.V differential');
  for (const entry of value) {
    if (entry.tolerance !== undefined && (!Number.isFinite(entry.tolerance) || entry.tolerance < 0)) {
      throw new Error(`invalid F0.V differential projection: ${entry?.invariant}`);
    }
  }
}

function validateColdProgress(name, variant) {
  const required = name === 'never_loaded';
  if (!required && variant.coldProgress !== undefined) {
    throw new Error(`only never_loaded may declare F0.V COLD cursor progress: ${name}`);
  }
  if (!required) return;
  const progress = variant.coldProgress;
  if (!progress || !Number.isInteger(progress.beforeAction) || progress.beforeAction < 1 || progress.beforeAction >= variant.actions.length
      || progress.after !== 'terminal' || typeof progress.view !== 'string' || typeof progress.id !== 'string'
      || !validPath(progress.cursorPath) || !validPath(progress.retainedBodyPath) || !validPath(progress.actorBodyPath)
      || !validPath(progress.leasePath) || !Number.isSafeInteger(progress.minimumAdvance) || progress.minimumAdvance < 1) {
    throw new Error('never_loaded F0.V variant lacks a valid COLD cursor progress proof');
  }
  const before = variant.assertions.find((assertion) => assertion.after === progress.beforeAction
    && assertion.view === progress.view && assertion.id === progress.id);
  const terminal = variant.assertions.find((assertion) => (assertion.after === 'terminal' || assertion.after === variant.actions.length)
    && assertion.view === progress.view && assertion.id === progress.id);
  if (before === undefined || terminal === undefined || expectedPath(before.expect, progress.leasePath) !== null
      || expectedPath(terminal.expect, progress.leasePath) !== null) {
    throw new Error('never_loaded F0.V COLD proof must retain a no-lease before/after assertion');
  }
}

function validateDifferentialRequirements(declaration, projections) {
  const schedule = projections.find((projection) => projection.invariant === 'schedule');
  if (schedule === undefined || !schedule.paths.includes('schedule.entries')) {
    throw new Error('F0.V differential must compare complete schedule entries, not just a count');
  }
  const checkpoint = declaration.legalCheckpoint;
  const matching = projections.find((projection) => projection.view === checkpoint.view && projection.id === checkpoint.id
    && checkpoint.paths.every((path) => projection.paths.includes(path)));
  if (matching === undefined) {
    throw new Error('F0.V differential must compare the declared legal checkpoint');
  }
}

function validateDifferentialLanes(value) {
  if (!value || typeof value !== 'object' || Object.keys(value).sort().join(',') !== 'cold,hot_cold') {
    throw new Error('F0.V differential must declare exactly cold and hot_cold execution lanes');
  }
  for (const [name, lane] of Object.entries(value)) {
    if (!lane || typeof lane !== 'object' || !Array.isArray(lane.setup ?? []) || !Array.isArray(lane.actions ?? [])) {
      throw new Error('F0.V differential execution lane is malformed');
    }
    validateGameplayElapsedWaits(`differential ${name}`, lane.actions);
  }
  // A comparison of two no-op lane descriptions cannot prove observer neutrality.  The generic
  // composer does not know how a family materializes, but the process-owned contract must name
  // at least one ordinary physical evidence action for the HOT/COLD lane.
  if (value.hot_cold.setup.length + value.hot_cold.actions.length === 0) {
    throw new Error('F0.V hot_cold differential lane lacks ordinary physical evidence');
  }
}

/**
 * HOT observation consumes real simulation instants while COLD continues. The
 * differential therefore names one terminal canonical instant. The runner asks
 * the server-thread mutation lane to admit that exact absolute target; it never
 * derives a client-relative delta or writes a process cursor/schedule.
 */
function validateDifferentialAlignment(baseScenario, variant) {
  const alignment = variant.alignment;
  if (!alignment || typeof alignment.view !== 'string' || typeof alignment.id !== 'string'
      || !validPath(alignment.instantPath)) {
    throw new Error('F0.V differential lacks a semantic time alignment declaration');
  }
  if (!variant.differential.some((projection) => projection.view === alignment.view && projection.id === alignment.id
      && projection.paths.includes(alignment.instantPath))) {
    throw new Error('F0.V differential absolute target must be a declared comparison projection');
  }
}

/**
 * A fixed wait is allowed only when elapsed gameplay time itself is the asserted product rule.
 * Lifecycle, event delivery and materialization readiness must use their typed barriers instead.
 */
function validateGameplayElapsedWaits(scope, actions) {
  for (const action of actions) {
    if (action?.type !== 'wait') continue;
    if (!Number.isSafeInteger(action.ms) || action.ms < 1 || action.ms > 120_000
        || !stableToken(action.gameplayElapsedPurpose)
        || typeof action.gameplayElapsedJustification !== 'string'
        || action.gameplayElapsedJustification.length < 16
        || action.gameplayElapsedJustification.length > 240) {
      throw new Error(`F0.V fixed wait lacks a named gameplay-time assertion: ${scope}`);
    }
  }
}

function validateCrashWindows(value, deferred = undefined) {
  const active = Array.isArray(value) ? value : [];
  const parked = deferred === undefined ? [] : deferred;
  if (!Array.isArray(value) || !Array.isArray(parked)
      || [...active, ...parked].map((entry) => entry?.boundary).sort().join(',') !== [...SEMANTIC_CRASH_BOUNDARIES].sort().join(',')) {
    throw new Error('F0.V abrupt restart must cover every semantic crash window exactly once');
  }
  if (parked.some((window) => !PHYSICAL_EFFECT_CRASH_BOUNDARIES.includes(window?.boundary))) {
    throw new Error('F0.V may defer only physical-effect crash windows to F0.2');
  }
  for (const window of [...active, ...parked]) {
    if (!window || window.phase !== 'before_restart' || !SEMANTIC_CRASH_BOUNDARIES.includes(window.boundary)
        || !stableToken(window.owner) || !stableToken(window.payloadType)
        || !validCrashRevision(window.expectedRevision)
        || (window.expectedAuthorityEpoch !== undefined && (!Number.isSafeInteger(window.expectedAuthorityEpoch)
          || window.expectedAuthorityEpoch < 0))) {
      throw new Error(`invalid F0.V semantic crash window: ${window?.boundary}`);
    }
  }
}

function validBaseScenario(base) {
  return base && stableToken(base.idPrefix) && base.isolation?.mode === 'disposable_lite'
    && Number.isInteger(base.isolation.seed) && typeof base.server?.host === 'string' && Number.isInteger(base.server?.port)
    && typeof base.pilot?.username === 'string' && base.pilot.username.length > 0
    && Array.isArray(base.setup) && Array.isArray(base.actions);
}

function validDeclaration(declaration) {
  return declaration && Array.isArray(declaration.identities) && declaration.identities.length > 0
    && declaration.identities.every(stableToken) && Array.isArray(declaration.cursorVocabulary)
    && declaration.cursorVocabulary.length > 0 && declaration.cursorVocabulary.every(stableToken)
    && validLegalCheckpoint(declaration.legalCheckpoint)
    && Array.isArray(declaration.terminalInvariants) && declaration.terminalInvariants.length > 0
    && declaration.terminalInvariants.every(stableToken)
    && declaration.terminalInvariants.slice().sort().join(',') === [...DIFFERENTIAL_INVARIANTS].sort().join(',');
}

function validLegalCheckpoint(value) {
  return value && typeof value.view === 'string' && typeof value.id === 'string'
    && Array.isArray(value.paths) && value.paths.length > 0 && value.paths.every(validPath);
}

function validateSemanticProjections(value, label) {
  if (!Array.isArray(value) || value.length !== DIFFERENTIAL_INVARIANTS.length
      || value.map((entry) => entry?.invariant).sort().join(',') !== [...DIFFERENTIAL_INVARIANTS].sort().join(',')) {
    throw new Error(`${label} must cover identity, claims, conservation, schedule and result`);
  }
  validateTerminalProjections(value, label);
}

function validateTerminalProjections(value, label) {
  if (!Array.isArray(value) || value.length === 0 || value.length > DIFFERENTIAL_INVARIANTS.length
      || new Set(value.map((entry) => entry?.invariant)).size !== value.length
      || value.some((entry) => !DIFFERENTIAL_INVARIANTS.includes(entry?.invariant))) {
    throw new Error(`${label} must contain 1..${DIFFERENTIAL_INVARIANTS.length} distinct semantic projections`);
  }
  for (const entry of value) {
    if (!stableToken(entry.invariant) || typeof entry.view !== 'string' || typeof entry.id !== 'string'
        || !Array.isArray(entry.paths) || entry.paths.length === 0 || entry.paths.some((path) => !validPath(path))) {
      throw new Error(`invalid ${label} projection: ${entry?.invariant}`);
    }
  }
}

/**
 * A variant may have intermediate diagnostics, but completion must be proven by the same five
 * semantic categories as the differential.  In particular, a final `{status:"ok"}` response
 * cannot be promoted to a process result.  The expected values remain variant-owned because
 * an ordinary player may legitimately be observing a different retained checkpoint.
 */
function requireTerminalCoverage(projections, variant, name) {
  const terminal = variant.assertions.filter((assertion) => assertion.after === 'terminal' || assertion.after === variant.actions.length);
  for (const projection of projections) {
    const assertion = terminal.find((candidate) => candidate.view === projection.view && candidate.id === projection.id
      && projection.paths.every((path) => expectedPath(candidate.expect, path) !== undefined));
    if (assertion === undefined) {
      throw new Error(`F0.V terminal assertion lacks ${projection.invariant} semantic projection: ${name}`);
    }
  }
}

function requireMatchingProjectionVocabulary(declaration, expected, actual, alignment) {
  for (const projection of expected) {
    const candidate = actual.find((entry) => entry.invariant === projection.invariant);
    if (candidate === undefined || candidate.view !== projection.view || candidate.id !== projection.id
        || !projection.paths.every((path) => candidate.paths.includes(path))
        || candidate.paths.some((path) => !allowedDifferentialPath(declaration, projection, path)
          && !(path === alignment?.instantPath && candidate.view === alignment.view && candidate.id === alignment.id))) {
      throw new Error(`F0.V differential must use the declared terminal semantic projection: ${projection.invariant}`);
    }
  }
}

function allowedDifferentialPath(declaration, terminal, path) {
  if (terminal.paths.includes(path)) return true;
  if (terminal.invariant === 'schedule' && path === 'schedule.entries') return true;
  const checkpoint = declaration.legalCheckpoint;
  return terminal.view === checkpoint.view && terminal.id === checkpoint.id && checkpoint.paths.includes(path);
}

function expectedPath(value, path) {
  return path.split('.').reduce((current, key) => current != null && typeof current === 'object'
    && Object.prototype.hasOwnProperty.call(current, key) ? current[key] : undefined, value);
}

function validAssertion(assertion) {
  return assertion && ((Number.isInteger(assertion.after) && assertion.after >= 1) || assertion.after === 'terminal')
    && typeof assertion.view === 'string' && typeof assertion.id === 'string'
    && assertion.expect && typeof assertion.expect === 'object' && !Array.isArray(assertion.expect);
}

function meaningfulExpectation(expect) {
  return Object.keys(expect).some((key) => key !== 'status') || Object.values(expect).some((value) => value && typeof value === 'object'
    && !Array.isArray(value) && meaningfulExpectation(value));
}

function validPath(value) { return typeof value === 'string' && /^[A-Za-z][A-Za-z0-9]*(?:\.[A-Za-z][A-Za-z0-9]*){0,7}$/.test(value); }

function stableId(value) {
  return typeof value === 'string' && /^[A-Za-z0-9][A-Za-z0-9_.:-]{2,127}$/.test(value);
}

function stableToken(value) {
  return typeof value === 'string' && /^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$/.test(value);
}

function validCrashRevision(value) {
  return (Number.isSafeInteger(value) && value >= 0) || value === 'observed_at_boundary';
}

function deepFreeze(value) {
  if (value && typeof value === 'object' && !Object.isFrozen(value)) {
    Object.freeze(value);
    for (const item of Object.values(value)) deepFreeze(item);
  }
  return value;
}

export { REQUIRED_VARIANTS, DIFFERENTIAL_INVARIANTS, SEMANTIC_CRASH_BOUNDARIES };
