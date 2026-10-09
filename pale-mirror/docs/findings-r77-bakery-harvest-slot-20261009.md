# R77 quarantine: prepared bakery slot stolen by harvest admission

Read-only diagnosis requested2026-10-09. No source repair, server restart,
world mutation, commit or deployment in this investigation.

## Exact evidence identity

R77 source2b4b0e4600ebfcab155f9997d4bca1fe0f3d77e8, detached checkout
/home/rd/proj/pm-resident-cards-r77-release-20261009; its exact packaged JAR
supplied the classes used below. Old world:
/home/rd/far-frontier-server/frontier-v3-resident-cards-r77-20261009.
Retained log: build/r78-prior-r77-incident.log in the implementation Gradle root.
Snapshot294627 plus260 WAL records restore head294887/instant138088.
Read through FrontierFileStore.recover; reconstructed pure in-memory engine
uses its default no-op TransactionCommitter, never the disk committer.
No Minecraft server, client, scheduler advance or old-world recovery repair ran.

## Proven initiating cause

One physical depot slot was independently promised to two owners:

1. WAL revision294878/instant138086: BakeryHotEffectPrepared reserves delivery
   slot16 in container:12-depot for
   job:production-objective-workforce-c941e61cae5e1895e19856eccc62c95a-1,
   resident:12-21. Prepared output is64 bread.
2. Revision294881/instant138087: ResourceSiteHarvestStarted admits
   job:site-harvest-12-wheat-field-28, resident:12-13, with the same output slot16.
3. Last durable head294887/instant138088 retains both promises. The common
   reserved-slot view contains16 only because of the harvest job; bakery's
   prepared physical step is missing from that view.
4. Native depot delivery physically places64 bread into slot16, emptying the
   baker's main hand. Read-only NBT region inspection confirms depot chest at
   377,65,354 has bread64/slot16, and baker body
   9aab77ca-0dd5-34cd-acc2-16e4f4dbb5b6 at375.51765,65,354.49914 has empty hands.
5. Confirmation attempts to publish the delivered inventory. Full-state
   validation correctly rejects occupying an active harvest reservation:
   IllegalArgumentException: active harvest output slot lacks exclusive container capacity
   at HarvestContainerReservations.validate:48.

Offline command replay used this actual final layout and retained source epoch
289539/destination epoch44, current body/lease and pending job identity.
The native receipt constructor uses the retained exact station body; no outcome
or canonical state was forged. Reconstructed receipt is diagnostic evidence,
not a retained original wire command or new native acceptance run.
The exact R77 engine returned INVARIANT_FAILURE with the above message.
The second pending baker's STATION_LOAD transition independently accepted;
its physical station chest was empty and worker still held64 wheat. This
discriminates the failing delivery from the other bakery operation.

### Ownership hole

ContainerPhysicalReservations:8-9 composes only harvest and shipment slot
providers; it omits the bakery's durable pending destination. BakeryProcess
checks target availability when preparing the effect, then retains the target
in BakeryPhysicalStep. Later harvest admission consults the common view and
can select that apparently free slot. Native delivery continues against its
retained target. The invariant catches the contradiction only after the
non-replayable physical transfer, too late to prevent it.
This is logical interleaving on one authoritative thread, not a need for a
parallelism lock, a longer timeout or a different chest slot ordering.

## Proven diagnostic failures

The original quarantine reporter was retained in a second offline replay;
its failure was captured without changing its result or adding disk writes.

1. Kernel tries to retain KernelQuarantineObserved. Its reducer updates the
   incident index, but subsequent retiredSchedules dispatch resolves its owner
   to kernel-schedule and calls module(kernel-schedule). MODULES deliberately
   excludes that kernel descriptor. Suppressed exception:
   IllegalArgumentException: no Frontier world process module for: kernel-schedule
   at FrontierWorldProcessCatalog.module:945 -> retiredSchedules:464.
   Incident transaction fails; head remains294887 and incident count remains1,
   the older unrelated production-blocked record. No primary quarantine event
   is retained in the old WAL.
2. BakeryPhysicalEffect.observe:106-112 treats every Rejected as a local
   ambiguous effect and submits another command even after engine quarantine.
   CommandSubmission:43 cannot retrieve active canonical state and throws
   v3 runtime is inactive. ServerLifecycle:392-394 replaces runtime failure
   detail with this secondary exception. DiagnosticTrace records only Accepted,
   so the initial rejected receipt is not written to its trace log either.

This explains the observed15:27:27 log and normal save/stop without assuming
that inactive-runtime was the initiating fault.

## Correct repair direction

- One common owner-qualified capacity-claim protocol must include every prepared
  destination. Owning families supply explicit typed claims; common admission
  and validation enforce uniqueness. No storage coordinator inspecting bakery
  phases or extra shadow inventory ledger. Farm, bakery and freight must not
  make invisible private promises for the same physical slot.
- Retained effect keeps its exact target/claim until confirmation or explicit
  safe cancellation; pre-effect authority validation rejects a lost/foreign
  claim before Minecraft mutation. It must not quietly retarget an already
  applied or ambiguous effect.
- Kernel-owned diagnostic events need explicit kernel lifecycle treatment,
  not dispatch into the domain module catalog. Preserve primary and suppressed
  failures independently of whether a canonical incident can be committed.
- After an invariant-quarantining rejection, stop physical dispatch and retain
  its original cause; do not submit a local recovery command to an inactive
  engine. Ordinary nonfatal rejections retain their separate handling.

The relevant reservation, bakery physical receipt, lifecycle and process-catalog
paths are unchanged in R78 commit77cf9376. Fresh deployment does not fix this
defect; recurrence remains possible. No speculative additional defect claimed.
The context facade was pinned to another checkout (human-ingress-repair), not
R77/current implementation; its unavailable path result was not used as proof.
Diagnosis instead read exact frozen source and persisted evidence.

## Approved repair — 2026-10-09

The user subsequently approved implementation. Shared `ContainerSlotClaim`
projections now carry a declared family and exact owner. Harvest, shipment and
prepared bakery deliveries use one exclusive physical-slot view. Prepared
bakery capacity replaces its corresponding pooled demand instead of counting
twice; only its completing owner may exclude that promise. Complete state
validation rejects competing claims, and physical projection uses the same view.

`BakeryHotDeliveryAborted` is an explicit durable cancellation, not a successful
receipt or silent retarget. It requires the original lease/slot/worker, complete
unchanged hand and current resource epoch. Applied/ambiguous effects cannot
cancel. It releases only the pending step; cargo, allocation, worker and work
phase survive. Recognized external stock can then enter the ordinary container
observation path and a new preparation selects another slot. Unclassified drift
still remains a local reconciliation conflict; cancellation does not authorize
minting arbitrary player stock or bypassing container custody. Ordinary depot
click admission already rejects edits while a bakery effect is pending.

Kernel diagnostics now have explicit kernel schedule-retirement semantics,
rather than dispatch to an absent domain module. The engine retains its primary
exception in memory; the host logs its original stack, including suppressed
reporter failures. Physical command submission immediately halts on quarantine,
and secondary errors cannot replace the first runtime quarantine. Ordinary
nonfatal rejections remain available to family-local reconciliation and are logged.

Focused canonical, recovery and native transfer regressions pass; detailed
final source/artifact identity and deployment status belong to the continuity
ledger. This is not renewed whole-world/player acceptance.

## R79 follow-up: successive external field mutations — repaired in R80

The user broke several Clearwater field blocks; subsequent breaks were rejected
and growth remained6/7 despite completed fast-forward requests. Exact retained
site:7-wheat-field cell168 at soil128,63,-10/crop128,64,-10 had an AIR/AIR foreign
capture while the current physical pair differed. ForeignChangeExecutor skipped
that mismatch indefinitely. Player admission and growth fenced the entire site,
so one stale observation prevented unrelated cells from progressing. Runtime was
green: this was an unresolved local lifecycle, not a global quarantine.

The repair introduces a pure common CellMutationProtocol with typed family/owner/
cell address and separate external-observation/non-replayable-effect sequencing.
Field policy remains in its family. Uncommitted external captures can supersede;
accepted history must close before its separately versioned successor. Applied or
ambiguous resource effects cannot be overwritten. Four cell receipt types require
durable canonical storage before physical witnesses are replaced or retired.
Exact-cell claims now protect player/world/foreign changes; independent growth
and work continue. Complete-site handoff retains its stricter aggregate barrier.

Native witnesses persist monotonic versions and exact captured soil/crop NBT.
Each relevant block write captures the complete resulting pair; normal scanning
also catches uncaptured owned-state drift. Diagnostics retain cell/cause/version
and local pending reason; no timeout unlock, block regeneration or resource minting
substitutes for observation. Snapshot264 and FIELDS18 require a fresh test world.

Frozen R80 checks passed: affected Frontier98tests, NeoForge684tests (one existing
skip),16required native field-turns GameTests, guardrails and packaged-JAR gate.
R80 was published and installed on frontier-v3-cell-mutation-r80-20261009; current
post-start deploy verifier and read-only summary are green. Detailed immutable
source/JAR identities and limitations are in CONTINUITY.md. The live user mutation
scenario still requires manual replay; no full player acceptance is claimed.
