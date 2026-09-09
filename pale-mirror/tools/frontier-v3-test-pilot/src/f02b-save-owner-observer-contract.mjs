import { createHash } from 'node:crypto';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { dirname, relative, resolve } from 'node:path';
import { SAVE_OWNER_OBSERVER_SLOT_OFFSETS_MS } from './save-owner-observer.mjs';

export const F02B_SAVE_OWNER_OBSERVER_REQUIREMENT = 'required';
const SCHEMA = 1;
const KIND = 'f02b-save-owner-observer-contract';
const ADMISSION_KIND = 'f02b-save-owner-observer-admission';
const HASH = /^[0-9a-f]{64}$/;

/** The ordered discriminator is the only F0.2B assignment that requires late save-owner evidence. */
export function requiresSaveOwnerObserver({ worker, lane, scenario }) {
  return worker === 'worker-2' && lane === 'normal-zero-player-recovery'
    && scenario === 'disposable-f02b-normal-product-recovery.json';
}

/** Dispatcher-owned immutable contract, written before the isolated scenario child is spawned. */
export async function writeSaveOwnerObserverContract({ project, output, worker, lane, runId, runAttempt,
  scenario, scenarioId, scenarioDeclarationSha256, evidenceRoot }) {
  const projectRoot = resolve(project);
  const target = checkedProjectBuildPath(projectRoot, output, 'contract path');
  const root = checkedProjectBuildPath(projectRoot, evidenceRoot, 'observer evidence root');
  const contract = Object.freeze({ schema: SCHEMA, kind: KIND, requirement: F02B_SAVE_OWNER_OBSERVER_REQUIREMENT,
    identity: checkedIdentity({ worker, lane, runId, runAttempt, scenario, scenarioId, scenarioDeclarationSha256 }),
    evidenceRoot: root, scheduledSlotOffsetsMs: [...SAVE_OWNER_OBSERVER_SLOT_OFFSETS_MS] });
  const encoded = `${JSON.stringify(contract, null, 2)}\n`;
  await mkdir(dirname(target), { recursive: true });
  await writeFile(target, encoded, { encoding: 'utf8', flag: 'wx' });
  return Object.freeze({ path: target, sha256: sha256(encoded), contract });
}

/** Child-owned admission: validates every independently supplied transport field before any server command. */
export async function admitSaveOwnerObserverContract({ project, requirement, contractPath, observerRoot,
  worker, lane, runId, runAttempt, scenario, scenarioId, scenarioDeclarationSha256 }) {
  const requested = requirement !== undefined;
  if (!requested) {
    if (contractPath !== undefined) throw new Error('F0.2B optional scenario has stray save-owner observer contract');
    return null;
  }
  if (requirement !== F02B_SAVE_OWNER_OBSERVER_REQUIREMENT) throw new Error('F0.2B save-owner observer requirement is invalid');
  if (!nonEmpty(contractPath) || !nonEmpty(observerRoot)) throw new Error('F0.2B required save-owner observer transport is missing or empty');
  const projectRoot = resolve(project);
  const path = checkedProjectBuildPath(projectRoot, contractPath, 'contract path');
  const root = checkedProjectBuildPath(projectRoot, observerRoot, 'observer evidence root');
  let text;
  try { text = await readFile(path, 'utf8'); }
  catch (failure) { throw new Error(`F0.2B required save-owner observer contract is unavailable: ${failure?.code ?? failure}`); }
  let contract;
  try { contract = JSON.parse(text); }
  catch { throw new Error('F0.2B required save-owner observer contract is malformed'); }
  if (!contract || contract.schema !== SCHEMA || contract.kind !== KIND || contract.requirement !== requirement
      || contract.evidenceRoot !== root || !sameJson(contract.scheduledSlotOffsetsMs, SAVE_OWNER_OBSERVER_SLOT_OFFSETS_MS)
      || !sameJson(contract.identity, checkedIdentity({ worker, lane, runId, runAttempt, scenario, scenarioId, scenarioDeclarationSha256 }))) {
    throw new Error('F0.2B required save-owner observer transport drifted');
  }
  const admission = Object.freeze({ schema: SCHEMA, kind: ADMISSION_KIND, status: 'admitted', requirement,
    contract: { path, sha256: sha256(text) }, identity: contract.identity, evidenceRoot: root,
    scheduledSlotOffsetsMs: [...SAVE_OWNER_OBSERVER_SLOT_OFFSETS_MS] });
  const receipt = resolve(root, `admission-${worker}-${runId}-${runAttempt}-${scenarioId}.json`);
  await mkdir(dirname(receipt), { recursive: true });
  await writeFile(receipt, `${JSON.stringify(admission, null, 2)}\n`, { encoding: 'utf8', flag: 'wx' });
  return Object.freeze({ ...admission, receipt });
}

/** Required result evidence is fail-closed: every declared late slot needs an explicit receipt. */
export function requireSaveOwnerObserverEvidence(admission, ownerObservation) {
  if (admission === null) return null;
  if (!admission || admission.status !== 'admitted' || !nonEmpty(admission.receipt)
      || !sameJson(admission.scheduledSlotOffsetsMs, SAVE_OWNER_OBSERVER_SLOT_OFFSETS_MS)) {
    throw new Error('F0.2B required save-owner observer admission is absent');
  }
  if (!ownerObservation || ownerObservation.status !== 'completed' || !Array.isArray(ownerObservation.slots)
      || ownerObservation.slots.length !== admission.scheduledSlotOffsetsMs.length
      || ownerObservation.slots.some((slot, index) => slot?.index !== index || slot?.offsetMs !== admission.scheduledSlotOffsetsMs[index]
        || (slot.status !== 'captured' && slot.status !== 'unavailable') || !nonEmpty(slot.receipt?.path) || !HASH.test(slot.receipt?.sha256 ?? ''))) {
    throw new Error('F0.2B required save-owner observation or scheduled-slot receipt is absent');
  }
  return ownerObservation;
}

function checkedIdentity(value) {
  if (!nonEmpty(value.worker) || !nonEmpty(value.lane) || !Number.isSafeInteger(Number(value.runId)) || Number(value.runId) <= 0
      || !Number.isSafeInteger(Number(value.runAttempt)) || Number(value.runAttempt) <= 0 || !nonEmpty(value.scenario)
      || !nonEmpty(value.scenarioId) || !HASH.test(value.scenarioDeclarationSha256 ?? '')) {
    throw new Error('F0.2B save-owner observer identity is malformed');
  }
  return Object.freeze({ worker: value.worker, lane: value.lane, runId: Number(value.runId), runAttempt: Number(value.runAttempt),
    scenario: value.scenario, scenarioId: value.scenarioId, scenarioDeclarationSha256: value.scenarioDeclarationSha256 });
}
function checkedProjectBuildPath(project, path, name) {
  if (!nonEmpty(path)) throw new Error(`F0.2B save-owner observer ${name} is empty`);
  const target = resolve(path); const rel = relative(project, target);
  if (!rel.startsWith('build/') || rel.startsWith('../') || rel.includes('/../')) throw new Error(`F0.2B save-owner observer ${name} escapes project build/`);
  return target;
}
function sameJson(left, right) { return JSON.stringify(left) === JSON.stringify(right); }
function sha256(value) { return createHash('sha256').update(value).digest('hex'); }
function nonEmpty(value) { return typeof value === 'string' && value.length > 0; }
