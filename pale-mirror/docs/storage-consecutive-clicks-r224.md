# R224 — consecutive player inventory edits

## Proven defect

Live R222 world, deployed 0453d60e, 2026-10-22 21:15:10–21:15:23:
`container:observed--110_64_-333`, an adopted double trapped chest, rejected
repeated PICKUP packets (including player inventory menu slot 82) with
`CUSTODY_NOT_ACQUIRED:RELEASED`. It had free capacity, not a full inventory.

`ContainerPlayerEditProcess` accounted the first click, then emitted
`ReferenceMutationClosed`. That transition released custody and native stack
bindings. Further UI clicks required ACQUIRED; reacquisition depended on the
bounded background discovery scan. This incorrectly treated each ordinary edit
as a HOT-to-COLD authority handoff.

## Correction and responsibility

- Shared replica/custody owner provides `ReferenceMutationObserved`: exact
  predecessor validation and an atomic observed successor under the SAME owner
  and epoch. Native slot bindings remain intact. Stale observations fail.
- Player accounting composes this checkpoint for every declared menu member in
  the same canonical transaction as the resource changes. No new stock owner,
  storage queue, slot repacking, fabricated observation, or discovery shortcut.
- The next native callback retires an already acknowledged click receipt before
  admission; it does not await the ordinary executor scan or replay the click.
- Pending native/canonical evidence remains pending, not a false rejection log.
- Real unload/release, unconfirmed effects and conflict fences are unchanged.
  Historical `ReferenceMutationClosed` WAL retains its existing interpretation.

## Verification

Focused process checks cover single/composed edits, persisted successor, next
edit admission without reacquisition, exact-tool ingress, invalid declarations,
stale observation refusal and quantity/title preservation. Existing native
exact-tool test previously waited for reacquisition: that wait was removed and
three consecutive 64-coal deposits were added, without any custody executor
turn. Native player-storage lane: 19/19 passed, `build/r224-native.log`.

The full-pack packet scenario `live-storage-consecutive-clicks.json` targets the
reported chest with three deposits and withdrawals. It uses granite to avoid
NPC consumption racing its UI quantity observations. No discovery wait is
inserted between clicks. Live verification/deployment receipt follows after
the verified clean build is installed; native GameTests alone are not a live
UI acceptance claim.
