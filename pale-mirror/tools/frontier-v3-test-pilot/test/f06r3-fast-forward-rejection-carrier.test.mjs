import assert from 'node:assert/strict';
import test from 'node:test';
import { assertF06r3FastForwardRejectionCarrier } from '../src/f06r3-fast-forward-rejection-carrier.mjs';

test('F0.6R3 relative rejection carrier rejects a cleared queue without the stable terminal receipt', () => {
  const declared = declaration(); const manifest = validManifest();
  assert.deepEqual(assertF06r3FastForwardRejectionCarrier({ declaration: declared, manifest }), {
    requestId: 1, targetInstant: 15310, admittedCheckpointInstant: 5310, reachedCheckpointInstant: 5342,
    owner: 'resource-site-projection:site:7-wheat-field', postRejectionSettlement: 'settlement:4', ordinaryProjectionContinued: true
  });
  manifest.diagnostics[0].value.fastForwardRequests = [];
  assert.throws(() => assertF06r3FastForwardRejectionCarrier({ declaration: declared, manifest }), /stable identity/);
});

test('F0.6R3 relative rejection carrier rejects a later projection that only follows a quarantined runtime', () => {
  const manifest = validManifest(); manifest.diagnostics[1].value.status = 'runtime_unavailable';
  assert.throws(() => assertF06r3FastForwardRejectionCarrier({ declaration: declaration(), manifest }), /remain active/);
});

function declaration() {
  const actions = [
    { type: 'visit' }, { type: 'fast_forward', ticks: 10000, expectTerminalStatus: 'REJECTED', expectReasonContains: 'resource-site-projection:site:7-wheat-field', timeoutMs: 30000 },
    { type: 'inspect' }, { type: 'visit' }, { type: 'inspect' }, { type: 'look' }, { type: 'assert_visible_block' }
  ];
  return { id: 'disposable_f06r3_fast_forward_rejection', isolation: { seed: 46 }, assertNoServerTickStallDuringIngress: true, actions };
}

function validManifest() {
  const actions = declaration().actions;
  return { status: 'ok', scenarioId: 'disposable_f06r3_fast_forward_rejection', actions: actions.map(action => ({ action })),
    diagnostics: [
      { actionStep: 3, value: { kind: 'performance', id: '', status: 'ok', fastForwardRemaining: 0, fastForwardRequests: [{ requestId: 1, kind: 'RELATIVE', requestedTicks: 10000, targetInstant: 15310, admittedCheckpointInstant: 5310, reachedCheckpointInstant: 5342, status: 'REJECTED', reason: 'physical work became pending during the relative interval: resource-site-projection:site:7-wheat-field' }] } },
      { actionStep: 5, value: { kind: 'settlement', id: 'settlement:4', status: 'ok' } }
    ], clientSegments: [{ ingressResponsiveness: { status: 'ok', ordinaryClientJoined: true, noServerTickStall: true } }],
    frames: [{ name: 'f06r3-post-rejection-site4-road', presentation: 'player', path: '/tmp/frame.png' }] };
}
