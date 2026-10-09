# R81 self-test: connected positives and an actual departure defect

Main alone, no subagents. Scope: user-requested player/client check and bug
finding on the deployed R81, not another feature or an implementation change.
Date2026-10-10, local timezone+05. Deployment identity remains the R81 delivery
receipt; no new source candidate, branch commit, push, reset or deployment.

## What was actually run

Checked-in-style scenario in the implementation checkout:
`pale-mirror/tools/frontier-v3-test-pilot/scenarios/live-quarry-player-handoff-delivery.json`.
Full-pack graphical client, current frozen R81 source,20FPS; ordinary player
actions against the live disposable server. No simulation stock injection,
forced chunks or world-file edits. A single stone was given only to the creative
test player's inventory to perform the ordinary place/break intervention.

Run6229d71a-3c30-477c-89a0-693a9c75c75c completed28actions at01:32:28.
`pale-mirror/build/r81-live-quarry-player-test.{json,pmv3.jsonl}` in implementation;
launcher `/tmp/pm-r81-live-quarry-player-test.log`.
Scenario statusok is evidence for its assertions, NOT an all-feature pass.

Passed observed boundaries:

- Northwatch's finite256-source quarry was depleted. Returned to the same
  actual AIR source after ordinary departure/COLD advancement/ingress.
- Placing stone in a depleted lower source cell(-314,61,-340) retained the
  player's stone; breaking it left AIR. Canonical external classification1,
  current extracted classification255. Original mined provenance is retained;
  these disposition counters are not a claim that prior output was revoked.
- Departure removed live physical source-region custody; return projected
  current state, without regenerating the exhausted stone mass.
- The actual home chest held242cobblestone(3x64+50), plus normal other stock.
  This is real internal hauling/materialized home stock, not a diagnostic-only
  inventory endpoint.
- Ordinary admitted fast-forward advanced38695->50000,11,305simulation ticks
  in140seconds. No failure occurred within that advancement; no TPS or speedup
  claim follows from this single run.

Three actual X11 frames were reviewed, under the frozen Gradle root's
`build/frontier-v3-scenarios/`, with the scenario/run prefix above:
`cold-depleted-quarry-on-ordinary-ingress.png`, `real-home-depot-stone.png`,
`nonproducer-import-depot.png`. The quarry geometry rendered; the home chest
showed stone. The importer chest did NOT contain cobblestone.

## Confirmed live failure after player disconnect

At01:32:39, eleven seconds after the scenario finished, the live server entered
quarantine and stopped, saved chunks normally. Current service is inactive/dead;
test client/runner have exited. Do not claim this build is currently running or
that this self-test was an end-to-end success. The world/evidence are retained;
no blind restart was attempted.

Exact error:
`executor:actor-body-unloaded-r107318`, `REJECTED_BY_POLICY`,
`body unload retains a prepared inventory interaction`.
Stack: common ActorBodyController.progressDeparture ->
AmbientActorExecutor.releaseUnloadedReservedColdContinuation.

Read-only actual snapshot106933/instant50600 plus385WAL transactions were
validated/replayed IN MEMORY, without advancing or writing the runtime store.
Recovered exact tip107318/instant50693. Helper/evidence:
implementation `pale-mirror/build/quarry-r81-live-inspect.{jsh,log}`.
The sole retained inventory-interaction fence belongs to
`actor:pack/settlement-10`, attachment `container:pack/settlement-10`:

- Chest donkey, no assigned mission or resident execution.
- Body epoch1 RUNNING; ambient lease CLOSED.
- Attached surface PREPARED, reference custody epoch1 PREPARING.
- Replica EXPECTED, no observed fingerprint/provenance confirmation.
- Expected canonical projection revision106520. Log records body admission
  at106543 and actual saved departure at01:32:29.

This is not a miner/tool problem. Source identifies the connected contradiction:

1. `FrontierV3ReferenceContainerCustodyExecutor.initializeAttachment` durably
   prepares and writes the private body's attachment, intentionally deferring
   actual confirmation until a later discovery/reconciliation turn.
2. Player disconnect can unload that new body before confirmation. The
   container owner's unloaded path explicitly skips PREPARING/UNRESOLVED.
3. Ambient release validates current/suspended activity capabilities. The pack
   animal has no such activity, so its projection closes without settling the
   independent attachment fence.
4. `FrontierV3ActorBodyController.progressDeparture` fences its saved receipt
   and submits body unload without first assessing that inventory fence.
5. `ActorBodyAuthority.unloaded` correctly rejects that transition. Submission
   treats the rejection as fatal; the whole runtime stops.

Source anchors: native ReferenceContainerCustodyExecutor314(initialization),
125(unloaded PREPARING skip), ActorBodyController107/144;
core ReferenceContainerCustody100, ActorActivityCapabilities41,
ActorBodyAuthority243. These are active-path contradictions plus actual saved
state/error, not an unconfirmed hypothetical defect.

## Public trade: not yet a native accepted receipt

At50367, buyer10 owned0stone and retained incoming64. At exact saved tip:

- seller1->buyer10 shipment21c6654d... carries14, missionOUTBOUND;
- seller11->buyer10 shipment41e795fc... carries50, missionUNLOADING/groupAT_GOAL;
- both retain exact loaded cargo, no reception yet; no accepted sale is claimed.

Other stone shipments also retain ordinary outbound missions. Contracts,
allocation and dispatch are active. This does NOT prove that native unloading/
payment/title transfer have passed, nor establish a separate trade deadlock.
The server's confirmed stop prevents continued observation. Earlier pure-COLD
import evidence came from another isolated world's snapshot and cannot be used
as a substitute for this live physical receipt.

## Proposed repair boundary and the remaining check

Repair common body/attached-container lifecycle, not quarry-specific transport:
confirm the actual attached image at admission rather than depend on future
discovery; recover the already-saved matching attachment through its owning
reference-custody protocol before body retirement; common departure must assess
the registered inventory fence BEFORE modifying its durable body fence. A guard
that merely waits forever on an unloaded PREPARING scope is not a full fix.
Unknown/mismatched fingerprints must still fail closed, never clear custody by
assumption or erase stock.

Reuse this exact saved world/evidence for diagnosis. After repair, select a
focused immediate-admission/departure case and an actual importer terminal
receipt/chest observation. Include disconnect/post-client server status so a
green final frame cannot hide the failed departure. No new broad test matrix or
speculative mining redesign is justified by these findings.

## Repository/process state

No product source was changed in this testing assignment. One new declarative
scenario is retained as WIP; helpers/results are ignored build outputs.
Implementation HEAD77cf9376 unchanged,272WIP paths including that scenario;
clean private R81 remains clean. Outer pack unchanged(unrelated .f0v-baseline),
original nested source's23WIP paths untouched. No commit/push/deletion/reset.
Both task-owned graphical client and live server are now stopped, the latter
because of the confirmed product failure, not a requested pause.
