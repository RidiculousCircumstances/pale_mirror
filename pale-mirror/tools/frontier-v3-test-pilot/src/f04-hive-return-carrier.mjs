import { isDeepStrictEqual } from 'node:util';

const SCENARIO = 'disposable_hive_return_restart';
const MOBILIZATION = 'mobilization:development-hive-mobilization';

/** Validates the retained native manifest, never a fixture copy or a derived summary. */
export function assertF04HiveReturnCarrier(manifest) {
  if (manifest?.status !== 'ok' || manifest.scenarioId !== SCENARIO || manifest.recovery?.mode !== 'graceful'
      || manifest.recovery?.splitAfterAction !== 3 || !Array.isArray(manifest.actions) || manifest.actions.length !== 5) {
    throw new Error('F0.4 return carrier lacks one exact persistent graceful lifecycle');
  }
  const values = (manifest.diagnostics ?? []).flatMap(entry => entry?.observed?.value ? [entry.observed.value] : entry?.value ? [entry.value] : []);
  const snapshots = values.filter(value => value?.kind === 'hive_mobilization' && value.id === MOBILIZATION
      && value.status === 'ok' && value.mobilizationStatus === 'RETURNING');
  if (snapshots.length < 2) throw new Error('F0.4 return carrier lacks retained pre/post-restart survivor observations');
  const before = snapshots.find(value => value.returnedMembers === 0 && Array.isArray(value.survivorPositions));
  const after = [...snapshots].reverse().find(value => Array.isArray(value.survivorPositions));
  if (!before || !after || before.survivors !== 4 || after.survivors !== 4 || before.returnComplete || after.returnComplete
      || !sameSurvivors(before.survivorPositions, after.survivorPositions)) {
    throw new Error('F0.4 return carrier observed reset, replacement, loss, or unbound survivor positions');
  }
  return Object.freeze({ mobilization: MOBILIZATION, survivors: Object.freeze(before.survivorPositions), recovered: Object.freeze(after.survivorPositions) });
}

function sameSurvivors(left, right) {
  const normalize = values => [...values].map(value => ({ id: value?.id, x: value?.x, y: value?.y, z: value?.z }))
    .sort((a, b) => String(a.id).localeCompare(String(b.id)));
  const first = normalize(left); const second = normalize(right);
  return first.length === 4 && first.every(value => typeof value.id === 'string' && Number.isInteger(value.x) && Number.isInteger(value.y) && Number.isInteger(value.z))
    && isDeepStrictEqual(first, second);
}
