const SCENARIO = 'disposable_f06r3_complete_first_ingress';
const SETTLEMENT = 'settlement:7';
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const POSITION_EPSILON = 0.35;
// An idle lease may retain the same support column while showing its explicitly bounded
// presentation-only local stance (the adapter caps its radius at 0.40). This is neither a
// route edge nor a work hand-off; leave a small packet-interpolation allowance above that cap.
const STATIONARY_DRIFT_EPSILON = 0.45;
const STATIONARY_STEP_EPSILON = 0.2;

/**
 * Validates one normal-client first visibility of every settlement resident against the only
 * pre-visit canonical population receipt. The local observer never performs a post-visit
 * server lookup, so a shared spawn row cannot be retrospectively explained as a station.
 */
export function assertF06r3CompleteFirstIngressCarrier({ declaration, manifest }) {
  if (declaration?.id !== SCENARIO || declaration?.isolation?.mode !== 'disposable_lite'
      || declaration?.isolation?.seed !== 47 || declaration?.zeroPlayerPrelude !== undefined
      || declaration?.assertNoServerTickStallDuringIngress !== true || manifest?.status !== 'ok'
      || manifest?.scenarioId !== SCENARIO) {
    throw new Error('F0.6R3 complete first-ingress carrier lacks its exact fresh ordinary-client declaration');
  }
  const canonical = exactly(manifest, 1, 'settlement_population', SETTLEMENT);
  const visible = exactly(manifest, 3, 'pilot_settlement_population', SETTLEMENT);
  const expected = completeCanonicalPopulation(canonical);
  const observed = completeVisiblePopulation(visible, expected);
  for (const [uuid, record] of expected) {
    const body = observed.get(uuid);
    if (!sameStation(record.position, body.first)) {
      throw new Error(`F0.6R3 first visibility did not materialize ${record.actor} at its retained canonical station`);
    }
    if (!record.dutyPhase.startsWith('TRAVELLING:')
        && (body.maxDisplacement > STATIONARY_DRIFT_EPSILON || body.maxStep > STATIONARY_STEP_EPSILON)) {
      throw new Error(`F0.6R3 stationary resident ${record.actor} left its bounded canonical station after ordinary first ingress`);
    }
  }
  const firstPositions = new Set([...observed.values()].map(value => positionKey(value.first)));
  if (firstPositions.size < 2) throw new Error('F0.6R3 complete resident population first appeared at one shared bootstrap position');
  const ingress = manifest.clientSegments?.length === 1 ? manifest.clientSegments[0].ingressResponsiveness : null;
  if (ingress?.status !== 'ok' || ingress.ordinaryClientJoined !== true || ingress.noServerTickStall !== true) {
    throw new Error('F0.6R3 complete population proof lacks its normal-client ingress responsiveness receipt');
  }
  return Object.freeze({ settlement: SETTLEMENT, residents: expected.size, firstPositions: firstPositions.size,
    stationaryResidents: [...expected.values()].filter(value => !value.dutyPhase.startsWith('TRAVELLING:')).length });
}

function exactly(manifest, actionStep, kind, id) {
  const found = (manifest.diagnostics ?? []).filter(entry => entry?.actionStep === actionStep && entry.value?.kind === kind && entry.value?.id === id);
  if (found.length !== 1 || found[0].value?.status !== 'ok') throw new Error(`F0.6R3 first-ingress carrier lacks one ${kind} receipt at action ${actionStep}`);
  return found[0].value;
}

function completeCanonicalPopulation(value) {
  if (!Number.isInteger(value.residentCount) || value.residentCount < 20 || value.residentCount > 40
      || !Array.isArray(value.residents) || value.residents.length !== value.residentCount) {
    throw new Error('F0.6R3 pre-visit census is not a bounded complete settlement population');
  }
  const rows = new Map();
  for (const row of value.residents) {
    if (typeof row?.actor !== 'string' || !row.actor.startsWith('resident:7-') || !UUID.test(row.entityUuid ?? '')
        || typeof row?.dutyPhase !== 'string' || !position(row.position) || rows.has(row.entityUuid)) {
      throw new Error('F0.6R3 pre-visit census has an ambiguous canonical resident binding');
    }
    rows.set(row.entityUuid, row);
  }
  return rows;
}

function completeVisiblePopulation(value, expected) {
  if (!Number.isInteger(value.residentCount) || value.residentCount !== expected.size
      || !Array.isArray(value.residents) || value.residents.length !== expected.size) {
    throw new Error('F0.6R3 client did not observe the complete pre-visit population');
  }
  const rows = new Map();
  for (const row of value.residents) {
    const canonical = expected.get(row?.entityUuid);
    if (canonical === undefined || canonical.actor !== row.actor || canonical.dutyPhase !== row.dutyPhase || !position(row.first)
        || !Number.isInteger(row.samples) || row.samples < 40 || !nonNegative(row.maxDisplacement) || !nonNegative(row.maxStep)
        || rows.has(row.entityUuid)) {
      throw new Error('F0.6R3 client first-visibility receipt has a missing, replaced, or malformed resident');
    }
    rows.set(row.entityUuid, row);
  }
  return rows;
}

function position(value) { return value !== null && typeof value === 'object' && [value.x, value.y, value.z].every(Number.isFinite); }
function nonNegative(value) { return Number.isFinite(value) && value >= 0; }
function sameStation(left, right) { return Math.abs(left.x + .5 - right.x) <= POSITION_EPSILON && Math.abs(left.y - right.y) <= POSITION_EPSILON && Math.abs(left.z + .5 - right.z) <= POSITION_EPSILON; }
function positionKey(value) { return `${value.x.toFixed(2)}:${value.y.toFixed(2)}:${value.z.toFixed(2)}`; }
