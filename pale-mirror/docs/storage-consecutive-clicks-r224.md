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
inserted between clicks. Native GameTests alone are not a live UI acceptance claim.

## Delivery and real-client receipt

Implementation49f98f5a, ordinary-PICKUP regression302a3b4c. Focused domain/codec/
registry/architecture45 tests passed; NeoForge821 tests passed; guardrails passed.
Final native player-storage19/19 passed (`build/r224-native-pickup.log`), including
ordinary inventory pickup followed immediately by a carried-stack chest deposit.
Broad Frontier run was deliberately interrupted in an unrelated long-running
ExtractionSiteTest simulation (exit143); it is NOT claimed as a full-suite pass.
The affected focused storage gate completed in17s. Initial wrong native lane
selection was a launcher error, corrected to the existing player-storage slice.

Clean detached source302a3b4c packaged and verified. Normal RCON `save-all flush`
and `stop`, all dimensions saved21:32:45; old JVM exited. No world reset.
Published/installed SHA512:
`bf9ce55b9603f337adb31a471119742cdcf1f3450b3364f2e1e9c9ff5ff15c3fe18783a5fb341091f78ae1a0e3ef6bc47d99a9474269874ff168d6d36c84d0ae`.
Same world `frontier-v3-r222-current-handoff-20261022`, seed125; start1792686798,
service `far-frontier-v3-live.service`, wrapperPID778765. Preflight/verify PASS.

Full-pack20FPS private-Xvfb client PASS, run
`d7fbf8ed-42db-48aa-a57e-f8a34011266f`, `build/r224-live-clicks.json`.
Three consecutive64-granite deposits, then three withdrawals, ordinary packets:
same custody epoch5 throughout; replica revision18 →24 →30; canonical granite
0 →192 →0, original31coal retained. Both screenshots inspected: three stacks
in the double chest, then the same three in the player inventory. No rejected
clicks, no canonical-click conflict warnings and no new quarantine. This is
the actual full-pack client on a private display, not human visual acceptance.
Client/display stopped after the check; post-check save-all flush successful.
At112083: required/scene/inventory/custody conflicts0, incidents0. No global
economy or unrelated performance acceptance claimed. No push. Source task
committed; pack tracked clean/pre-existing `.f0v-baseline/` retained. Canonical
governance contract amendments preserve unrelated WIP.
