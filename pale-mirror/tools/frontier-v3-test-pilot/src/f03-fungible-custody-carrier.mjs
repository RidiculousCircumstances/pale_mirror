import { createHash } from 'node:crypto';
import { readFile, writeFile } from 'node:fs/promises';
import { dirname, resolve, relative } from 'node:path';

const SHA = /^[a-f0-9]{64}$/;
const SCENARIO = 'disposable_f03_fungible_player_graceful';
const ABRUPT_SCENARIO = 'disposable_f03_fungible_player_abrupt';
const OBSERVER_FREE_SCENARIO = 'disposable_f03_fungible_observer_free';
const SOURCE_ACCOUNT = 'custody:container-1-depot';
const PLAYER_UUID = 'bf39347d-cb86-3221-b6b7-7b89a1dcb4cf';
const PLAYER_ACCOUNT = `custody:player-${PLAYER_UUID}`;
const LOT = 'lot:bootstrap-1-wheat';

/**
 * F0.3's native receipt reads only action-bound diagnostic records produced by the ordinary
 * player/menu and server restart flow. It cannot create, amend, or infer a custody transition.
 */
export function verifyFungiblePlayerRecovery({ scenario, before, after, terminal }) {
  if (scenario?.id !== SCENARIO || !SHA.test(scenario.sha256 ?? '') || before?.status !== 'ok' || after?.status !== 'ok'
      || terminal?.status !== 'ok' || terminal?.recovery?.mode !== 'graceful') {
    throw new Error('F0.3 player recovery receipt is incomplete or foreign');
  }
  const sourceBefore = resource(before, 6, SOURCE_ACCOUNT);
  const playerBefore = resource(before, 7, PLAYER_ACCOUNT);
  const sharedPersistentManifest = before === after;
  const sourceAfter = resource(after, sharedPersistentManifest ? 8 : 1, SOURCE_ACCOUNT);
  const playerAfter = resource(after, sharedPersistentManifest ? 9 : 2, PLAYER_ACCOUNT);
  const source = sourceFact(sourceBefore, 32);
  const player = playerFact(playerBefore, 32);
  const recoveredSource = sourceFact(sourceAfter, 32);
  const recoveredPlayer = playerFact(playerAfter, 32);
  if (source.quantity + player.quantity !== 64 || recoveredSource.quantity + recoveredPlayer.quantity !== 64) {
    throw new Error('F0.3 partial player custody is not zero-sum');
  }
  if (!sameFact(source, recoveredSource) || !sameFact(player, recoveredPlayer)) {
    throw new Error('F0.3 graceful recovery replaced exact partial custody or its HOT binding');
  }
  const durable = terminal.lifecycle?.filter(entry => entry?.barrier === 'durable_server_save').length ?? 0;
  const closed = terminal.lifecycle?.filter(entry => entry?.barrier === 'game_port_closed').length ?? 0;
  if (durable < 1 || closed < 1 || !naturalDurableStop(terminal) || !Array.isArray(terminal.clientSegments) || terminal.clientSegments.length !== 1) {
    throw new Error('F0.3 graceful recovery receipt lacks one ordinary durable stop and persistent player continuity');
  }
  return Object.freeze({ scenario: scenario.id, scenarioSha256: scenario.sha256, source, player,
    recovered: { source: recoveredSource, player: recoveredPlayer }, total: 64, durableStops: durable, closedPorts: closed });
}

/** The abrupt carrier accepts no synthetic result: the server must have parked at the exact real split/submit gap. */
export function verifyFungiblePlayerAbruptRecovery({ scenario, after, terminal }) {
  if (scenario?.id !== ABRUPT_SCENARIO || !SHA.test(scenario.sha256 ?? '') || after?.status !== 'ok' || terminal?.status !== 'ok'
      || terminal?.recovery?.mode !== 'abrupt') throw new Error('F0.3 abrupt recovery receipt is incomplete or foreign');
  const crash = terminal.recovery.crash;
  if (crash?.boundary !== 'physical_effect_visible_before_typed_observation' || crash.owner !== SOURCE_ACCOUNT
      || crash.payloadType !== 'frontier.fungible_resource_handoff_observed' || !Number.isSafeInteger(crash.revision) || crash.revision < 0) {
    throw new Error('F0.3 abrupt receipt did not cross the exact physical-before-canonical boundary');
  }
  const source = sourceFact(resource(after, 3, SOURCE_ACCOUNT), 32);
  const player = playerFact(resource(after, 4, PLAYER_ACCOUNT), 32);
  if (source.quantity + player.quantity !== 64 || !naturalDurableStop(terminal)) throw new Error('F0.3 abrupt recovery is not zero-sum or durably quiesced');
  return Object.freeze({ scenario: scenario.id, scenarioSha256: scenario.sha256, crash: { boundary: crash.boundary, owner: crash.owner,
    payloadType: crash.payloadType, revision: crash.revision }, source, player, total: 64 });
}

/** Normal observer loss must release the exact HOT binding without converting it into a COLD spend. */
export function verifyFungibleObserverFreeRecovery({ scenario, before, after, terminal }) {
  if (scenario?.id !== OBSERVER_FREE_SCENARIO || !SHA.test(scenario.sha256 ?? '') || before?.status !== 'ok' || after?.status !== 'ok'
      || terminal?.status !== 'ok' || terminal?.recovery?.mode !== 'graceful') throw new Error('F0.3 observer-free receipt is incomplete or foreign');
  const hot = sourceFact(resource(before, 2, SOURCE_ACCOUNT), 64);
  const sharedPersistentManifest = before === after;
  const released = releasedFact(resource(before, 6, SOURCE_ACCOUNT));
  const recovered = releasedFact(resource(after, sharedPersistentManifest ? 8 : 2, SOURCE_ACCOUNT));
  releasedContainer(container(before, 5)); releasedContainer(container(after, sharedPersistentManifest ? 7 : 1));
  if (hot.quantity !== released.quantity || released.quantity !== recovered.quantity || !sameReleased(released, recovered)) {
    throw new Error('F0.3 observer-free release changed exact custody quantity or consumed COLD stock');
  }
  const durable = terminal.lifecycle?.filter(entry => entry?.barrier === 'durable_server_save').length ?? 0;
  const closed = terminal.lifecycle?.filter(entry => entry?.barrier === 'game_port_closed').length ?? 0;
  if (durable < 1 || closed < 1 || !naturalDurableStop(terminal) || !Array.isArray(terminal.clientSegments) || terminal.clientSegments.length !== 1) {
    throw new Error('F0.3 observer-free receipt lacks ordinary durable restart continuity');
  }
  return Object.freeze({ scenario: scenario.id, scenarioSha256: scenario.sha256, hot, released, recovered, total: released.quantity,
    durableStops: durable, closedPorts: closed });
}

export async function writeFungibleObserverFreeRecoveryReceipt({ project, scenarioPath, terminalPath, output }) {
  const root = resolve(project); const scenarioFile = resolve(root, scenarioPath); const terminalFile = resolve(root, terminalPath);
  const terminal = JSON.parse(await readFile(terminalFile, 'utf8'));
  const beforePath = terminal?.recovery?.beforeRestartManifest === undefined
    ? undefined : resolve(root, terminal.recovery.beforeRestartManifest);
  if (!inside(root, scenarioFile) || !inside(root, terminalFile) || (beforePath !== undefined && !inside(root, beforePath))) {
    throw new Error('F0.3 observer-free receipt path escapes project');
  }
  const [scenarioSource, before] = await Promise.all([
    readFile(scenarioFile, 'utf8'), beforePath === undefined ? Promise.resolve(terminal) : readJson(beforePath)
  ]);
  const scenario = JSON.parse(scenarioSource); const sha256 = hash(scenarioSource);
  const facts = verifyFungibleObserverFreeRecovery({ scenario: { id: scenario.id, sha256 }, before, after: terminal, terminal });
  const receipt = Object.freeze({ schema: 1, kind: 'frontier-v3-f03-fungible-observer-free-recovery', status: 'ok', facts,
    terminalPath: relative(root, terminalFile), terminalSha256: hash(await readFile(terminalFile)),
    ...(beforePath === undefined ? { preRestartFacts: 'terminal_action_correlations' } : {
      beforeRestartPath: relative(root, beforePath), beforeRestartSha256: hash(await readFile(beforePath))
    }), sourceIdentity: terminal.build ?? null });
  const target = resolve(root, output);
  if (!inside(root, target)) throw new Error('F0.3 observer-free receipt output escapes project');
  await writeFile(target, `${JSON.stringify(receipt, null, 2)}\n`, { flag: 'wx' });
  return receipt;
}

export async function writeFungiblePlayerRecoveryReceipt({ project, scenarioPath, terminalPath, output }) {
  const root = resolve(project); const scenarioFile = resolve(root, scenarioPath); const terminalFile = resolve(root, terminalPath);
  const terminal = JSON.parse(await readFile(terminalFile, 'utf8'));
  const beforePath = terminal?.recovery?.beforeRestartManifest === undefined
    ? undefined : resolve(root, terminal.recovery.beforeRestartManifest);
  if (!inside(root, scenarioFile) || !inside(root, terminalFile) || (beforePath !== undefined && !inside(root, beforePath))) {
    throw new Error('F0.3 receipt path escapes project');
  }
  // A persistent player JVM owns one final manifest: its action-correlated
  // diagnostics contain both pre-stop (6/7) and recovered (8/9) facts. The
  // nonpersistent carrier keeps a separate before-restart manifest instead.
  const [scenarioSource, before] = await Promise.all([
    readFile(scenarioFile, 'utf8'), beforePath === undefined ? Promise.resolve(terminal) : readJson(beforePath)
  ]);
  const after = terminal;
  const scenario = JSON.parse(scenarioSource); const sha256 = hash(scenarioSource);
  const facts = verifyFungiblePlayerRecovery({ scenario: { id: scenario.id, sha256 }, before, after, terminal });
  const receipt = Object.freeze({ schema: 1, kind: 'frontier-v3-f03-fungible-player-recovery', status: 'ok', facts,
    terminalPath: relative(root, terminalFile), terminalSha256: hash(await readFile(terminalFile)),
    ...(beforePath === undefined ? { preRestartFacts: 'terminal_action_correlations' } : {
      beforeRestartPath: relative(root, beforePath), beforeRestartSha256: hash(await readFile(beforePath))
    }), sourceIdentity: terminal.build ?? null });
  const target = resolve(root, output);
  if (!inside(root, target)) throw new Error('F0.3 receipt output escapes project');
  await writeFile(target, `${JSON.stringify(receipt, null, 2)}\n`, { flag: 'wx' });
  return receipt;
}

export async function writeFungiblePlayerAbruptRecoveryReceipt({ project, scenarioPath, terminalPath, output }) {
  const root = resolve(project); const scenarioFile = resolve(root, scenarioPath); const terminalFile = resolve(root, terminalPath);
  if (!inside(root, scenarioFile) || !inside(root, terminalFile)) throw new Error('F0.3 abrupt receipt path escapes project');
  const [scenarioSource, terminalSource] = await Promise.all([readFile(scenarioFile, 'utf8'), readFile(terminalFile, 'utf8')]);
  const terminal = JSON.parse(terminalSource); const scenario = JSON.parse(scenarioSource); const sha256 = hash(scenarioSource);
  const facts = verifyFungiblePlayerAbruptRecovery({ scenario: { id: scenario.id, sha256 }, after: terminal, terminal });
  const receipt = Object.freeze({ schema: 1, kind: 'frontier-v3-f03-fungible-player-abrupt-recovery', status: 'ok', facts,
    terminalPath: relative(root, terminalFile), terminalSha256: hash(terminalSource), sourceIdentity: terminal.build ?? null });
  const target = resolve(root, output);
  if (!inside(root, target)) throw new Error('F0.3 abrupt receipt output escapes project');
  await writeFile(target, `${JSON.stringify(receipt, null, 2)}\n`, { flag: 'wx' });
  return receipt;
}

function resource(manifest, actionStep, account) {
  const record = manifest?.diagnostics?.find(entry => entry?.assertion?.after === actionStep && entry?.assertion?.view === 'resource'
    && entry?.assertion?.id === account && entry?.observed?.value?.kind === 'resource' && entry.observed.value.id === account);
  if (!record) throw new Error(`F0.3 receipt lacks action-bound resource diagnostic ${account}@${actionStep}`);
  return record.observed.value;
}

function sourceFact(value, quantity) {
  if (value?.status !== 'ok' || value.account !== SOURCE_ACCOUNT || value.quantity !== quantity || value?.custody?.kind !== 'CONTAINER'
      || value.custody.container !== 'container:1-depot') throw new Error('F0.3 source resource receipt is not the exact depot remainder');
  return bindingFact(value, quantity, 'CONTAINER_SLOT', undefined);
}

function playerFact(value, quantity) {
  if (value?.status !== 'ok' || value.account !== PLAYER_ACCOUNT || value.quantity !== quantity || value?.custody?.kind !== 'PLAYER'
      || value.custody.player !== PLAYER_UUID) throw new Error('F0.3 player resource receipt is not the exact ordinary player portion');
  return bindingFact(value, quantity, 'PLAYER_SLOT', PLAYER_UUID);
}

function bindingFact(value, quantity, addressKind, player) {
  if (!Array.isArray(value.lots) || value.lots.length !== 1 || value.lots[0]?.id !== LOT || value.lots[0]?.owner !== 'settlement:1'
      || value.lots[0]?.itemKind !== 'minecraft:wheat' || value.lots[0]?.quantity !== quantity || !Array.isArray(value.claims) || value.claims.length !== 0
      || !Array.isArray(value.bindings) || value.bindings.length !== 1) throw new Error('F0.3 resource receipt lost lot/claim/binding exactness');
  const binding = value.bindings[0];
  if (!Number.isSafeInteger(binding?.epoch) || binding.epoch < 1 || binding.itemKind !== 'minecraft:wheat' || binding.quantity !== quantity
      || binding?.address?.kind !== addressKind || player !== undefined && binding.address.player !== player) {
    throw new Error('F0.3 resource receipt has no exact current HOT physical binding');
  }
  return Object.freeze({ account: value.account, custody: value.custody, quantity, lot: value.lots[0], claims: [], binding: {
    epoch: binding.epoch, itemKind: binding.itemKind, quantity: binding.quantity, address: binding.address } });
}

function sameFact(left, right) {
  return left.account === right.account && left.quantity === right.quantity && JSON.stringify(left.custody) === JSON.stringify(right.custody)
    && JSON.stringify(left.lot) === JSON.stringify(right.lot) && JSON.stringify(left.claims) === JSON.stringify(right.claims)
    && left.binding.itemKind === right.binding.itemKind && left.binding.quantity === right.binding.quantity
    && left.binding.address.kind === right.binding.address.kind
    && (left.binding.address.player === undefined || left.binding.address.player === right.binding.address.player);
}

function releasedFact(value) {
  if (value?.status !== 'ok' || value.account !== SOURCE_ACCOUNT || value.quantity !== 64 || value?.custody?.kind !== 'CONTAINER'
      || value.custody.container !== 'container:1-depot' || !Array.isArray(value.lots) || value.lots.length !== 1
      || value.lots[0]?.id !== LOT || value.lots[0]?.owner !== 'settlement:1' || value.lots[0]?.itemKind !== 'minecraft:wheat'
      || value.lots[0]?.quantity !== 64 || !Array.isArray(value.claims) || value.claims.length !== 0
      || !Array.isArray(value.bindings) || value.bindings.length !== 0) throw new Error('F0.3 observer-free receipt retains HOT custody or changes COLD quantity');
  return Object.freeze({ account: value.account, custody: value.custody, quantity: value.quantity, lot: value.lots[0], claims: [], bindings: [] });
}

function releasedContainer(value) {
  if (value?.status !== 'ok' || value?.custody?.status !== 'RELEASED' || value?.physicalSocket?.chunk !== 'UNLOADED') {
    throw new Error('F0.3 observer-free receipt lacks released unloaded container custody');
  }
}

function container(manifest, actionStep) {
  const record = manifest?.diagnostics?.find(entry => entry?.assertion?.after === actionStep && entry?.assertion?.view === 'container'
    && entry?.assertion?.id === 'container:1-depot' && entry?.observed?.value?.kind === 'container' && entry.observed.value.id === 'container:1-depot');
  if (!record) throw new Error(`F0.3 receipt lacks action-bound depot diagnostic at ${actionStep}`);
  return record.observed.value;
}

function sameReleased(left, right) {
  return left.account === right.account && left.quantity === right.quantity && JSON.stringify(left.custody) === JSON.stringify(right.custody)
    && JSON.stringify(left.lot) === JSON.stringify(right.lot) && JSON.stringify(left.claims) === JSON.stringify(right.claims)
    && left.bindings.length === 0 && right.bindings.length === 0;
}

/** A real graceful halt may follow only the server's retained all-holder snapshot. */
function naturalDurableStop(terminal) {
  return Array.isArray(terminal?.naturalDemandStops) && terminal.naturalDemandStops.some(stop =>
    typeof stop?.serverRunId === 'string' && Number.isInteger(stop?.serverPid) && stop.serverPid > 1
      && stop.arm?.serverRunId === stop.serverRunId && stop.arm?.serverPid === stop.serverPid
      && typeof stop.arm?.stopAdmissionNonce === 'string'
      && stop.admitted?.status === 'admitted' && stop.admitted?.server?.serverRunId === stop.serverRunId
      && stop.admitted?.server?.serverPid === stop.serverPid && stop.admitted?.stopAdmissionNonce === stop.arm.stopAdmissionNonce
      && Array.isArray(stop.eligible?.members) && stop.eligible.members.length > 0
      && stop.eligible.members.every(member => member?.generationRefCount === 0 && member?.readyForSaving === true
        && member?.terminal === 'zero_ready'));
}

function hash(value) { return createHash('sha256').update(value).digest('hex'); }
function inside(root, path) { const candidate = relative(root, path); return candidate !== '' && !candidate.startsWith('..') && !candidate.includes('/..'); }
async function readJson(path) { return JSON.parse(await readFile(path, 'utf8')); }
