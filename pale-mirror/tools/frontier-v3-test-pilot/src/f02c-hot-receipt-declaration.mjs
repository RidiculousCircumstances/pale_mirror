const SCENE = 'assault:development-settlement-assault';
const INTENT = 'intent:scene-strike-assault-600092da23672798d69a6de709d8808bd63455bc5ea6172e4019d541c8541d39';
const RECEIPT = `observation:${INTENT.replace(':', '-')}`;
const EXACT = Object.freeze({
  strikeCause: 'cause:development-settlement-assault-epoch-0-attacker-bioform-west-18',
  strikeAttacker: 'bioform:west-18', strikeTarget: 'resident:1-1', strikeIntent: INTENT, strikeReceipt: RECEIPT,
  strikeHealthBefore: 20_000_000, strikeHealthAfter: 18_000_000, strikeEpoch: 0, nextStrikeEpoch: 1,
  strikeReceiptExact: true, strikeHealthChanged: true
});

/** Fail-closed declaration guard for the one later native HOT/restart carrier. */
export function assertF02cHotReceiptDeclaration(value) {
  if (!value || value.id !== 'disposable_settlement_assault_restart' || value.restart?.mode !== 'graceful' || value.restart.afterAction !== 2
      || !Array.isArray(value.actions) || value.actions.length !== 6 || !Array.isArray(value.assertions) || value.assertions.length !== 4) {
    throw new Error('F0.2C HOT carrier declaration has an invalid lifecycle topology');
  }
  const initial = value.setup?.find(entry => entry.type === 'assert_fixture')?.checks?.find(entry => entry.view === 'scene' && entry.id === SCENE);
  if (initial?.expect?.status !== 'not_found') throw new Error('F0.2C HOT carrier must begin with no lease or strike intent');
  const records = new Map(value.assertions.map(entry => [entry.after, entry]));
  if (records.size !== 4 || ![2, 3, 5, 6].every(step => records.has(step))) throw new Error('F0.2C HOT carrier assertions are reordered or incomplete');
  assertScene(records.get(2), 'HOT'); assertScene(records.get(3), 'HOT'); assertScene(records.get(5), 'CLOSED');
  const trace = records.get(6);
  if (trace?.view !== 'trace' || trace.id !== 'assault:assault:development-settlement-assault' || trace.expect?.status !== 'ok') {
    throw new Error('F0.2C HOT carrier lacks its terminal retained trace assertion');
  }
  const actionRecords = [value.actions[1], value.actions[2], value.actions[4]];
  if (actionRecords.some(entry => entry?.type !== 'wait_until_diagnostic' || entry.view !== 'scene' || entry.id !== SCENE)) {
    throw new Error('F0.2C HOT carrier action assertions do not retain the exact scene');
  }
  assertExpectation(actionRecords[0].expect, 'HOT'); assertExpectation(actionRecords[1].expect, 'HOT'); assertExpectation(actionRecords[2].expect, 'CLOSED');
  return Object.freeze({ scene: SCENE, intent: INTENT, receipt: RECEIPT });
}

function assertScene(assertion, status) {
  if (assertion?.view !== 'scene' || assertion.id !== SCENE) throw new Error('F0.2C HOT carrier has a foreign retained scene assertion');
  assertExpectation(assertion.expect, status);
}

function assertExpectation(expect, status) {
  if (!expect || expect.status !== 'ok' || expect.sceneKind !== 'SETTLEMENT_ASSAULT' || expect.leaseStatus !== status
      || expect.strikeStatus !== 'CONFIRMED' || Object.entries(EXACT).some(([key, value]) => expect[key] !== value)
      || (status === 'CLOSED' && (expect.assaultStatus !== 'COLD_COMBAT' || expect.coldContinuationAvailable !== true))
      || (status === 'HOT' && expect.assaultStatus !== 'HOT')) {
    throw new Error('F0.2C HOT carrier retained assertion is foreign, old, or incomplete');
  }
}
