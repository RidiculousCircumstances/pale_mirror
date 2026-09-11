import assert from 'node:assert/strict';
import test from 'node:test';
import { createHash } from 'node:crypto';
import { verifyFungibleObserverFreeRecovery, verifyFungiblePlayerAbruptRecovery, verifyFungiblePlayerRecovery } from '../src/f03-fungible-custody-carrier.mjs';

const player = 'bf39347d-cb86-3221-b6b7-7b89a1dcb4cf';
const source = 'custody:container-1-depot'; const playerAccount = `custody:player-${player}`;
const sha = createHash('sha256').update('f03-player').digest('hex');
function resource(account, quantity, kind) {
  const address = kind === 'CONTAINER' ? { kind: 'CONTAINER_SLOT', container: 'container:1-depot', slot: 0 }
    : { kind: 'PLAYER_SLOT', player, slot: 9 };
  return { kind: 'resource', id: account, status: 'ok', account, quantity, custody: kind === 'CONTAINER'
    ? { kind, container: 'container:1-depot' } : { kind, player }, lots: [{ id: 'lot:bootstrap-1-wheat', owner: 'settlement:1', itemKind: 'minecraft:wheat', quantity }],
    claims: [], bindings: [{ id: `binding:${account}`, epoch: 1, itemKind: 'minecraft:wheat', quantity, address }] };
}
function manifest(stepSource, stepPlayer) {
  return { status: 'ok', diagnostics: [
    { assertion: { after: stepSource, view: 'resource', id: source }, observed: { value: resource(source, 32, 'CONTAINER') } },
    { assertion: { after: stepPlayer, view: 'resource', id: playerAccount }, observed: { value: resource(playerAccount, 32, 'PLAYER') } }
  ] };
}
function naturalStop() {
  const serverRunId = '00000000-0000-0000-0000-000000000091'; const serverPid = 12345;
  const nonce = '00000000-0000-0000-0000-000000000092';
  return { serverRunId, serverPid, arm: { serverRunId, serverPid, stopAdmissionNonce: nonce },
    eligible: { members: [{ generationRefCount: 0, readyForSaving: true, terminal: 'zero_ready' }] },
    admitted: { status: 'admitted', server: { serverRunId, serverPid }, stopAdmissionNonce: nonce } };
}
function valid() {
  const before = manifest(6, 7); const after = manifest(1, 2);
  return { scenario: { id: 'disposable_f03_fungible_player_graceful', sha256: sha }, before, after,
    terminal: { ...after, recovery: { mode: 'graceful' }, lifecycle: [{ barrier: 'durable_server_save' }, { barrier: 'game_port_closed' }],
      naturalDemandStops: [naturalStop()], clientSegments: [{}] } };
}

test('F0.3 receipt binds one real partial player portion across a graceful save/restart', () => {
  const facts = verifyFungiblePlayerRecovery(valid());
  assert.equal(facts.total, 64); assert.equal(facts.player.binding.address.player, player);
});

test('persistent player recovery retains both sides in one action-correlated manifest', () => {
  const terminal = manifest(6, 7);
  terminal.diagnostics.push(
    { assertion: { after: 8, view: 'resource', id: source }, observed: { value: resource(source, 32, 'CONTAINER') } },
    { assertion: { after: 9, view: 'resource', id: playerAccount }, observed: { value: resource(playerAccount, 32, 'PLAYER') } }
  );
  terminal.recovery = { mode: 'graceful' };
  terminal.lifecycle = [{ barrier: 'durable_server_save' }, { barrier: 'game_port_closed' }];
  terminal.naturalDemandStops = [naturalStop()]; terminal.clientSegments = [{}];
  const facts = verifyFungiblePlayerRecovery({ scenario: { id: 'disposable_f03_fungible_player_graceful', sha256: sha },
    before: terminal, after: terminal, terminal });
  assert.equal(facts.total, 64);
});

test('F0.3 receipt rejects duplicate quantity, absent HOT binding, stale recovery, or non-durable stop', () => {
  const duplicate = valid(); duplicate.before.diagnostics[1].observed.value.quantity = 64;
  assert.throws(() => verifyFungiblePlayerRecovery(duplicate), /player resource receipt/);
  const unbound = valid(); unbound.after.diagnostics[0].observed.value.bindings = [];
  assert.throws(() => verifyFungiblePlayerRecovery(unbound), /lot\/claim\/binding/);
  const stale = valid(); stale.after.diagnostics[1].observed.value.bindings[0].address.player = '00000000-0000-0000-0000-000000000000';
  assert.throws(() => verifyFungiblePlayerRecovery(stale), /HOT physical binding/);
  const noSave = valid(); noSave.terminal.lifecycle = [{ barrier: 'game_port_closed' }];
  assert.throws(() => verifyFungiblePlayerRecovery(noSave), /durable stop/);
  const unquiesced = valid(); unquiesced.terminal.naturalDemandStops[0].eligible.members[0].generationRefCount = 1;
  assert.throws(() => verifyFungiblePlayerRecovery(unquiesced), /durable stop/);
});

test('F0.3 abrupt receipt requires the real physical-before-canonical boundary and one recovered zero-sum portion', () => {
  const after = manifest(3, 4);
  const terminal = { ...after, recovery: { mode: 'abrupt', crash: { boundary: 'physical_effect_visible_before_typed_observation', owner: source,
    payloadType: 'frontier.fungible_resource_handoff_observed', revision: 17 } }, naturalDemandStops: [naturalStop()] };
  const facts = verifyFungiblePlayerAbruptRecovery({ scenario: { id: 'disposable_f03_fungible_player_abrupt', sha256: sha }, after, terminal });
  assert.equal(facts.total, 64);
  terminal.recovery.crash.owner = 'custody:foreign';
  assert.throws(() => verifyFungiblePlayerAbruptRecovery({ scenario: { id: 'disposable_f03_fungible_player_abrupt', sha256: sha }, after, terminal }), /exact physical-before-canonical/);
});

test('F0.3 observer-free receipt proves release/restart clears only HOT binding without a COLD spend', () => {
  const released = resource(source, 64, 'CONTAINER'); released.bindings = [];
  const container = { kind: 'container', id: 'container:1-depot', status: 'ok', custody: { status: 'RELEASED' }, physicalSocket: { chunk: 'UNLOADED' } };
  const before = { status: 'ok', diagnostics: [
    { assertion: { after: 2, view: 'resource', id: source }, observed: { value: resource(source, 64, 'CONTAINER') } },
    { assertion: { after: 5, view: 'container', id: 'container:1-depot' }, observed: { value: container } },
    { assertion: { after: 6, view: 'resource', id: source }, observed: { value: released } }
  ] };
  const after = { status: 'ok', diagnostics: [
    { assertion: { after: 1, view: 'container', id: 'container:1-depot' }, observed: { value: container } },
    { assertion: { after: 2, view: 'resource', id: source }, observed: { value: released } }
  ] };
  const terminal = { ...after, recovery: { mode: 'graceful' }, lifecycle: [{ barrier: 'durable_server_save' }, { barrier: 'game_port_closed' }],
    naturalDemandStops: [naturalStop()], clientSegments: [{}] };
  const facts = verifyFungibleObserverFreeRecovery({ scenario: { id: 'disposable_f03_fungible_observer_free', sha256: sha }, before, after, terminal });
  assert.equal(facts.total, 64); assert.equal(facts.recovered.bindings.length, 0);
  after.diagnostics[1].observed.value.lots[0].quantity = 63;
  assert.throws(() => verifyFungibleObserverFreeRecovery({ scenario: { id: 'disposable_f03_fungible_observer_free', sha256: sha }, before, after, terminal }), /HOT custody|COLD quantity/);
});

test('persistent observer-free recovery reads post-restart action correlations from its one client manifest', () => {
  const released = resource(source, 64, 'CONTAINER'); released.bindings = [];
  const container = { kind: 'container', id: 'container:1-depot', status: 'ok', custody: { status: 'RELEASED' }, physicalSocket: { chunk: 'UNLOADED' } };
  const terminal = { status: 'ok', diagnostics: [
    { assertion: { after: 2, view: 'resource', id: source }, observed: { value: resource(source, 64, 'CONTAINER') } },
    { assertion: { after: 5, view: 'container', id: 'container:1-depot' }, observed: { value: container } },
    { assertion: { after: 6, view: 'resource', id: source }, observed: { value: released } },
    { assertion: { after: 7, view: 'container', id: 'container:1-depot' }, observed: { value: container } },
    { assertion: { after: 8, view: 'resource', id: source }, observed: { value: released } }
  ], recovery: { mode: 'graceful' }, lifecycle: [{ barrier: 'durable_server_save' }, { barrier: 'game_port_closed' }],
  naturalDemandStops: [naturalStop()], clientSegments: [{}] };
  const facts = verifyFungibleObserverFreeRecovery({ scenario: { id: 'disposable_f03_fungible_observer_free', sha256: sha },
    before: terminal, after: terminal, terminal });
  assert.equal(facts.total, 64);
});
