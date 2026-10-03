# SA-06/07/09 lifecycle follow-through

Status: implementation checkpoint for L1/L2, stopped for user testing; not whole
SA closure or a release receipt. Main works alone. Latest instruction: user owns
gameplay testing; no native scenarios, deployment or further cycle at this checkpoint.
This supplements the [original audit](frontier-v3-static-audit-2026-09-22.md).
User accepted source-first lifecycle analysis on 2026-09-23 after repeated long
native iterations. Do not start another long scenario without a named question
that source inspection and focused checks cannot answer. Soil mutation is a
separate physical incident; a clean repeat does not repair it.

Source root: `/home/rd/proj/pm-f06r3-facility-lane-recovery/pale-mirror`.
Paths below use N = neoforge `internal/frontier/v3`, K = frontier `v3`.

## Confirmed contradictions

### L1 — new actor bodies lose canonical injuries (SA-07)

Source chain: `N/FrontierV3ActorCarrierFactory.create` created a Vanilla mob,
stamped UUID/ownership, but never supplied canonical health. Both production
callers (`AmbientActorExecutor.materialize`, `SceneExecutor.materializeBodiesInReadyColumns`)
also omitted health. `SceneExecutor.mark` transfers ownership only; bioform
configuration changes protection/equipment, not health. Thus a safely released
injured actor reconstructed from a carrier receives default Minecraft health.
Canonical health can still read 19 while the physical body has 20; a later
release can then import that false healing back into canonical history.

Correction implemented: factory requires `ActorCondition`; both owners pass the
exact actor's condition. Only a newly constructed body is hydrated. Dead actors
and health beyond the body's supported maximum are rejected rather than
resurrected/clamped. Existing loaded/adopted bodies are never rehydrated.
No persistence schema change, alternate UUID or historical-world repair.

Focused unit/composition selection: 10656 terminal0, 7s, five tests passed;
`compilePilotJava` also passed. Native first-admission slice additionally checks
both kinds/owners, physical health/NBT and that repeat scene materialization
does not overwrite an existing body's newer injury. Native81362 terminal0,
23s, all5 tests passed; log `build/sa-static-health-hydration-fixture-fixed-20260923.log`.
Initial66688 rejected the new fixture's uppercase SubjectId before construction;
fixed fixture spelling, preserved its run directory, then reran only this slice.
This is focused Minecraft component evidence, not a full player/restart scenario.

Earlier 90291 is terminal OK (19:12:52 local), not still running. Its real punch,
departure/common release and graceful restart prove canonical 19HP continuity.
It does NOT prove physical reconstruction health. L1 was found by source reading,
not by that green scenario; do not promote the old receipt to cover this fix.

### L2 — loaded survivor release protection is family-dependent (SA-06)

Source chain:

- `SceneExecutor.release` validates current physical survivors and commits
  `SceneLeaseReleased`. Its common durable actor fences cover **departed**
  members only, not the loaded survivors.
- Production calls `AmbientActorExecutor.fenceDrainingSceneBody` before common
  release. Harvest separately retains a visible body or fences it on cold release.
- Medical, engineering, service, patrol, assault and logistics use common release
  without that family-side loaded-survivor fence.
- `SceneExecutor.cleanClosedBodies` retains harvest bodies specially; other
  owned CLOSED bodies reach `ClosedProjectionFence.bodyIsStale` and `discard`.
  The canonical stale-body tombstone rejects an old projection; it is not the
  inactive carrier authorizing reconstruction in `AmbientCarrierLedger`.
- Later `AmbientActorExecutor.materialize` rejects an absent established actor
  with no inactive carrier or unused first-admission permit. This safety check
  is correct; the preceding release/cleanup lacks its required evidence.

Status: source-confirmed unprotected loaded-release path, corrected at the common
boundary in this checkpoint; native composition remains for user testing.
Do not disable absent-body rejection, reissue a birth permit or select a new actor.

Required shared correction: release must select an explicit disposition for
every survivor: retained live same-body handoff, durable inactive reconstruction
carrier, or validated departure. Dead actors have a separate terminal outcome.
Prepare and validate the entire set before canonical close; cleanup may discard
only a body covered by its exact durable disposition. Put family policy in the
closed behavior registration, not another family switch in shared lifecycle.
Preserve harvest's visible same-body succession rather than fencing/disappearing
it merely to make other families match production. Remove duplicate family-side
mechanics after shared adoption. Cover rejection and restart between fence,
canonical close, discard and later admission, plus a mixed-member scene.

### Checkpoint implementation and verification

`SceneBehaviorRegistry` now requires a body-release policy for every registered
family. The common release inspects the complete survivor set, collects loaded
and departed carrier declarations, preflights all fences, persists them before
canonical close, and only after an accepted close stops/discards the exact
loaded bodies. Production's separate wrapper was removed. Harvest keeps its
registered visible-body retention and domain-specific conflict disposition;
its duplicate fence/discard mechanics were removed. Cargo validation remains
independent and cannot be skipped by the actor plan.

`AmbientCarrierLedger.canFenceAll/fenceAll` rejects foreign/duplicate actor or
UUID sets before any member mutation, respects bounded inventory and supports
idempotent retry. New tests cover mixed resident/bioform sets, reload, rejected
later members, duplicate identity, exact old-versus-new epoch/owner, and failed
new-body admission restoring the same release carrier.

Static follow-through also caught the post-close/pre-discard boundary:
cleanup now consumes an exact retained fence **before** harvest retention.
It compares the historical body's own authority/epoch, not a newer ambient
lease revision. Canonical death permits retirement separately. A stale canonical
tombstone without a physical reconstruction fence no longer authorizes deleting
the only surviving body. This is prevention, not automatic repair of historical
worlds whose bodies/evidence were already lost.

Verification66248 terminal0/16s: compile production and pilot code, 24 focused
tests, JAR build. Log `build/sa-shared-release-checkpoint-20260923.log`.
`git diff --check` passed. No native client/server was started in this increment.
JAR `pale-mirror-neoforge/build/libs/pale_mirror-0.3.0-SNAPSHOT.jar`, SHA256
`79b5f37a1288b9421ebd328a8d2a736f9a6c6f81b6c7431f6be48cd72cf03831`.
This is a dirty-WIP local checkpoint, not a clean-ref deployable release claim.
Live remains unchanged. No commit/push/reset. Root pack repository unchanged;
existing root AGENTS.md and .f0v-baseline WIP preserved, source remains dirty.

Human diagnostic after installation should inspect harvest-to-next-job worker
continuity, industrial worker return after leaving/revisiting, and preserved
injuries after reconstruction. A restart is useful specifically across scene
completion. These checks do not claim every medical/combat/cargo path is already
accepted. Soil mutation remains unexplained and separately instrumented.

## Transition map and remaining source work

| Transition | Owner / inspected evidence | Remaining obligation |
| --- | --- | --- |
| Actor birth → first body | FirstAdmissionBoundary persists PENDING before insertion; explicit failure rolls back, exception retains ambiguity | Review actual-save/late return composition, not permission reset |
| Inactive carrier → new body | AdoptionAdmission persists pending transfer before insertion | L1 corrected; inspect interrupted pre-close fence versus pending adoption |
| Loaded body → another owner | ActorHandoffAdmission/Recovery, exact retained binding | Follow both source-join and target-join rejection without duplicate writers |
| Scene → released actor | Common release plus family wrappers and closed cleanup | L2 must become one exhaustive shared disposition |
| Damage → unload → release | SceneDepartureObserver validates actor/lease/revision/UUID/baseline; release reads current body or exact departure | 90291 covers canonical result; L1 covers missing reverse projection |
| Work → output → next work | ProductionProcess.planCompletion/reduceColdWorkAdvanced, ResourceSiteLedger cycle receipt | Complete SA09 terminal/rejection/resource-release path review; do not declare full audit from these two methods |
| Restart → continuation | SceneExecutor.reclaim/reclaimObservedBodies; permits only explicit unstarted admission | Audit unstarted/cancelled and partially admitted multi-member combinations before changing recovery policy |

The mixed dead/unstarted suspicion alone is not a confirmed defect:
`FrontierSceneBehaviors.recoveredStatus` deliberately returns DRAINING if any
member is dead. Do not change the admission preflight merely because it rejects
that set; inspect cancellation/release semantics first.

## Execution order

1. Complete L1 focused physical checks, without repeating long harvest cycles.
2. L2 shared release correction is implemented with focused regressions above;
   native composition is deliberately not rerun before the user checkpoint.
3. Finish the remaining source paths in the table and record concrete defects
   versus evidence gaps separately. SA06/07/09 remain open until their actual
   scope is satisfied; this document is not a new acceptance programme.
4. Choose a final native composition only for the remaining physical question.
   Reuse existing two-cycle/production receipts for unchanged claims. Do not
   rerun matrices or full cycles for reassurance.

No deployment, commit or publication performed by this audit increment.
