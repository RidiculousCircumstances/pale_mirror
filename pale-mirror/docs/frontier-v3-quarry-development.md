# Adjacent quarry development

Accepted and implemented 2026-10-10. Exact delivery scope and limits below.

## Scope and ownership

Develop the same quarry beyond its initial authored source set. Preserve site,
storage, actors, tools, depletion history and transport identities. Construction,
resource regeneration and a new remote quarry are out of scope.

Known geometry is a reusable read-only domain port: explicit material, knowledge
version and protected/unknown disposition. The graybox supplies its actual flat
stone stratum independently of work-cell layout. Work outcomes and observed
foreign changes override that baseline. Unknown is not stone. COLD cannot read
unloaded Minecraft; HOT validates each actual predecessor before effects.

A bounded adjacent-excavation planner consumes that port and reachable working
stations. It proposes real block work, not world writes, resource credit or an
instantaneous air corridor. The extraction owner admits the exact proposal under
its current development generation and settlement mandate. Existing miners,
navigation, effects, resource ledger and hauling execute it. A newly described
support may acknowledge existing rock; it cannot place a missing support.
Support eligibility and source mineability are separate injected predicates:
an ore-bearing front need not have an ore floor. The current explicit flat
graybox policy supplies stone for both; no ore content is introduced by this cut.

## Connected implementation

1. Add isolated known-volume/geometry and adjacent-work planning values. Persist
   the explicit geological policy and bound query/admission sizes.
2. Admit incremental source cells outside the old512 with stable cell identity,
   dependencies, existing connected standing clearance and natural support.
   Retain unavailable/unknown/protected reasons; cap growth visibly.
3. Evolve the site's owned layout atomically without changing old declarations,
   depletion or live targets. Invalidate derived native/navigation indices on
   declaration change, not every worker tick. Never renumber infrastructure.
4. Preserve source-region physical custody: expanding a physically held region
   requires its normal whole-region reconciliation before new work. Projection
   cannot mine new rock, heal player edits or replay COLD actions.
5. Cover bounded planner negatives, current-schema snapshot/WAL replay, actual
   continued mining/hauling beyond the initial reservoir and natural HOT/COLD
   ingress. Deploy a verified fresh disposable world where schema requires it.

## Acceptance

The decisive result is real resource production from newly admitted adjacent
blocks, not a larger initial reservoir or only a green planning endpoint.
There is one depletion and resource authority, no regeneration, duplicate yield,
competing work or new navigation implementation. Foreign/unknown geometry remains
unavailable rather than being overwritten. First visibility shows current
excavation. Actual images distinguish visual evidence from domain checks.

Safe excavation-only level changes may be admitted only with known support,
body clearance, legal adjacent work reach and a connected walkable descent;
absence of such geometry is an explicit hold, never fabricated stairs. This
cut does not promise a general deep-mine/earthworks construction system.

## R98 mobile-container departure correction

The ordinary player departure left `container:pack/settlement-1` ACQUIRED at
epoch4 while its actor body was UNLOADED and ambient lease CLOSED. The physical
journal retained the matching body departure, current residence2, a positive
native-save acknowledgement and exact empty attached-storage image. This was
an established product defect, not missing save evidence.

The common body owner retires its active BODY binding after saved departure.
The reference-container owner previously required `departure.current(state)`,
which in turn required that still-active binding. Once body retirement won the
ordering race, both custody checkpoint and release remained unreachable forever.

The correction distinguishes current actuation from retained dependent evidence.
`ActorBodyAuthority.retainsRetiredDeparture` validates only this exact living
actor's retired BODY epoch and RESUME_COLD disposition, with no successor binding.
`FrontierV3ActorBodyDeparture.retainedForDependentCheckpoint` also checks the
declared actor kind and deterministic UUID. Only the shared reference-container
checkpoint uses this historical fence. Body admission, mutation, and its own
departure still require current authority. There is no revived body, second
container owner, force-loading, inferred epoch or release based on absence alone.

The existing actual serialized-image acknowledgement, residence, return-read,
physical-presence, pending-effect, exact container, provenance and fingerprint
checks remain mandatory. A successor/prepared incarnation rejects old evidence;
native return reads revoke save eligibility. The same protocol applies to
declared mobile reference surfaces independently of settlement coordinates.

Source fix committed `ccfe491b81f4f052887f0267646c2e4f5a7ab055`; the previous
five-gap delivery is checkpointed separately as `2a2c46e6`. The final declarative
scenario is `live-mobile-container-departure.json`: a fixture explicitly requires
existing cargo, ordinary departure proves physical UNLOADED before RELEASED,
return checks all16 granite, player withdrawal removes16, and a second physical
departure checks empty RELEASED. It never manufactures saved-body proof.

Focused verification: domain25 and adapter27 tests passed, including current
codec recovery, wrong UUID, successor epoch and revoked-save negatives. The
real custody-executor GameTest retains unsaved cargo and releases the same
attachment after independent body retirement and positive save acknowledgement;
all10 required reference-projection tests passed. Field-turns17 passed.
The clean detached package/static gates passed; affected evidence is reused
only where executable dependencies are unchanged.

### R98 terminal native evidence and deployment

Final clean detached source `fed1c94293a72431aaa7ed862e126e0b3502d313`, tree
`1c06024f3e53951d1c0cb4c3fa8f2a789fd144ef`, checkout
`/home/rd/proj/pm-mobile-custody-r98-release-20261010`. Core SHA512:
`eaa8be2483e182fc7912e5fd99d20f2b2f29f17ee895d5b623611a92afcd7e80417a6d8eba67fd94ac8db4479d7e0a0b2b398d339e4c514b30d8ca9a05e19125`.
Visuals/schema269/r4 are unchanged. The same diagnostic world
`frontier-v3-shared-mechanisms-r97c-20261010` was preserved, not reset.

The original stuck epoch4 became RELEASED after deployment with the actor still
UNLOADED. An ordinary player then contributed16 granite. Its first carrier
failed because a partial expected array was compared by exact array equality,
despite the actual current16/ACQUIRED observation. The failure is retained;
the oracle now names the complete physical binding and the explicit existing
cargo fixture. Neither a timeout increase nor a product workaround was used.
Physical UNLOADED is required before each release assertion to reject transient
renewal as false departure evidence.

The contributed16 survived graceful same-world save/restart, then the final
full-pack scenario passed:15 actions,3 terminal assertions,2 reviewed UI frames,
run `0a3081e6-a76a-4581-b05e-8145f44da4eb`,41.724 seconds including client launch.
Manifest is frozen `pale-mirror/build/mobile-container-r98-terminal-native.json`;
scenario SHA256 `6e2354492db7d16118d3c2da76a109d8837c46fdf6ca71fc759e4ea61bda8c8e`.
Required full-pack mod inventory passed. Both actual PNGs show the same16 granite
in the donkey's first cargo slot before departure and after return. The cargo
is then withdrawn through the vanilla menu, not diagnostic mutation.

Final post-disconnect: actor UNLOADED, ambient lease CLOSED, container empty and
RELEASED epoch8; required/inventory/scene/custody conflicts all0. JSONL retained
in implementation `pale-mirror/build/mobile-container-r98-terminal-post-exit.jsonl`.
The final deployment verifier passed: wrapper3837269/start1791658848, invocation
`a6ab6fded21f4bc6a467998310e129ac`. The task client and private Xvfb96 are stopped.
Canonical ledger and architecture validators plus staged whitespace checks pass.
Governance's older Gradle configuration rejects the now-empty disabled-mod
catalog before running tasks; this is retained as a tooling limitation, not a
product failure or a green governance Gradle run. The current implementation
and final frozen release's complete applicable static/package gates passed.
No new performance percentage, whole-project M3 or arbitrary abrupt
player-and-container save atomicity claim is made.

## R97 shared-mechanism adoption (five audited gaps)

Implemented 2026-10-10 in the active implementation checkout. This is scoped
adoption of existing owners, not a replacement economy, new hive feature or a
claim that every historical subsystem is now migrated.

1. Fixed chests and declared mobile donkey cargo use the same durable vanilla
   click pre/post witness and resource reconciliation. A menu-provider adapter
   supplies the exact physical inventory and cargo-slot mapping; saddle/armor
   slots are excluded. Declared unavailable providers fail closed. Ordinary
   Minecraft Shift-click opens the mobile cargo; no diagnostic command edits it.
2. Removed player withdrawal/return polling that matched inventory stacks and
   inferred depots from settlement prefixes. A tracked return names its declared
   player binding, slot, account and epoch at admission, then durably retains
   both postimages. The shared handoff moves existing lots, never credits the
   same portion as a new gift. Partial returns and container-layout merges are
   supported. Tracked returns currently require quick-move with an empty cursor;
   unsupported mixing or ambiguous manipulation is rejected before the effect.
   Existing explicitly observed world-carrier/hopper transfers remain supported.
3. Farmer hand-to-depot delivery uses `FrontierV3ActorItemTransfer.FungibleStep`.
   The common physical transfer implements destination-first placement, exact
   source release and replay rejection. The harvest owner retains its full-chest
   witness, pending-effect fence and terminal receipt. Neither quantities nor
   recipe/work decisions move into the physical transfer helper.
4. `FungibleClaimForfeitureStateSupport` coordinates one atomic contribution
   through registered purpose owners. Production owns input reallocation,
   payment/order cancellation and execution retirement; the shared coordinator
   no longer reads production phases or hive-growth internals. Existing growth
   behavior is extracted unchanged, not extended. Unknown owners and prepared
   physical effects remain fenced; no partially updated world is published.
5. Ordinary diagnostic rendering reads `FrontierScheduleView`/`executionView()`
   rather than serializing `checkpointImage()`. Real persistence still owns
   checkpoint encoding. Live diagnostic `checkpointBytes` is null when not
   measured; its size is never obtained by secretly encoding the whole world.

### R97e verification and delivery

Clean detached source `81beb248abcf15af6d75876af8391f81ff5c8e9d`, tree
`d192bde72fa15482d6256affe09ce3b7ec9ce4cb`, private ref
`refs/pm-releases/shared-mechanisms-r97e-20261010`. Frozen directory retains its
initial name `/home/rd/proj/pm-shared-mechanisms-r97b-release-20261010`.
Core SHA512:
`fc5ce237eaaf0311cd1eef9ca4411cbb10be19d088dcbebffaab788eb95cc29e194ef9e7131a835873853a9ca1ec1fe1466bf71f469a5854a04b9d623d583c7b`.
Visuals/schema269/r4 unchanged. The physical click-journal format is now2;
fresh world `frontier-v3-shared-mechanisms-r97c-20261010` was created and the old
R96c world preserved. No user-branch commit/push; inherited WIP and real index
remain intact. The pack/deployment tracked checkout and frozen source are clean.

Affected verification:

- Domain78 and adapter91 focused checks passed: fungible accounting/claims,
  internal shipments, architecture, diagnostics, click-witness codec and
  harvest delivery/release/body custody. Exact selector command retained below.
- Native `field-turns` slice:17 required GameTests passed. Actual horse menu
  accessor and cargo indices exclude saddle/armor; shared destination-first
  actor transfer survives native entity NBT round-trip and rejects occupied
  destination/repeated source release without duplicate material.
- Pilot-focused Java tests and Node scenario53 passed. Final detached
  `guardrails check :pale-mirror-neoforge:build
  :pale-mirror-neoforge:verifyPackagedJar -x test --no-daemon` passed with the
  verified graybox disabled-mod catalog supplied explicitly. Affected tests
  and unchanged evidence were reused, not falsely reported as a fresh full suite.
- Full-pack real client ran checked-in `live-mobile-player-container-edit.json`:
 8 actions,2 terminal assertions,2 UI frames, run
  `6c452fa1-cd43-49ee-8564-79d0f60e00ec`, manifest
  `pale-mirror/build/shared-mechanisms-r97e-mobile-native.json` in the frozen
  checkout. Loaded required mods passed. Both PNGs were opened: granite16 moves
  into the donkey's first cargo cell and back into the player's hotbar. Domain
  observations match16 then0, with current replica and zero inventory conflicts.
- Same-world graceful RCON `stop` saved all dimensions. Post-restart diagnostic
  tick11084/revision12989:0 required/inventory/scene/custody conflicts, same
  mobile-container fingerprint and replica revision12. Live deploy verifier
  passed wrapper3752038, start1791656233, invocation
  `9463abeb29064bea94575653fe8629ae`. Task client and private Xvfb are stopped.

Focused selector (implementation Gradle root):

```sh
./gradlew :pale-mirror-frontier:test --tests '*Fungible*' \
  --tests '*InternalShipmentTest' --tests '*FrontierArchitectureTest' \
  :pale-mirror-neoforge:test --tests '*Diagnostic*' --tests '*DepotClickWitnessTest' \
  --tests '*ResourceSiteHarvestSceneExecutorTest' --tests '*HarvestDepotSlotReservationTest' \
  --tests '*HarvestSceneReleaseBarrierTest' --tests '*ActorBodyCustodyTest' --no-daemon
```

Logs retained in frozen `pale-mirror/build/pm-shared-mechanisms-{tests,native,
client-tests,node}.log`; final build log `pm-r97e-release-build.log`. Post-exit
and restart JSONL are in the implementation `pale-mirror/build/
shared-mechanisms-r97e-post-{exit,restart}.jsonl`.

Failed native carriers remain failed. The first invocation wrongly used the
client destination as its pack source and corrupted only that disposable client;
the original pack/server were unaffected. The next visit targeted a block above
the actual floor. After correcting the point, the pilot still sent ordinary
instead of secondary interaction: LocalPlayer reads Shift from Input, not its
Entity flag. The corrected ordinary packet path passed; no timeout increase or
weakened terminal criterion was used.

Limits: tracked-world-carrier return conservation has focused domain coverage,
not a filmed pickup/return experiment. Graceful restart does not establish atomic
native player-and-container saving across every abrupt crash. Post-exit the
mobile attachment remains ACQUIRED/BODY_UNAVAILABLE with a CLOSED ambient lease;
its saved fence was preserved, not heuristically released. This check establishes
the HOT click and recovered canonical image, not complete mobile COLD-handoff
liveness. No material whole-server speedup percentage or human M3 is claimed.

## R96 player-container correction and measured performance limits

The Ironmeadow incident retained an unpicked internal shipment with a prepared
36-cobblestone transfer after an untracked player withdrawal; its physical stock
matched neither retained image. Two miners then waited with their cargo. The
old interception covered only settlement depots. Polling intentionally skips
pending effects, so it cannot manufacture the missing player receipt afterwards.
The diagnostic worlds are retained; the new deployment uses a fresh world.

All declared fixed reference containers now share the ordinary vanilla click
pre/post witness. Ownership comes from the explicit container declaration, not
an ID prefix or a derived depot. Contributions likewise carry an exact container
ID through the resource ledger. The shared physical-effect fence includes a
pending player-click witness, preventing another observer from closing custody
between the click and its confirmed domain consequence.

Fungible stock loss delegates claim retirement to a closed owner capability
registry. Meals and unpicked internal hauling use their existing retirement
owners atomically with the stock delta and activity reconsideration. Prepared
non-replayable effects and unsupported claims remain protected: the fix does not
invent transfer completion, change goods title or hide ambiguous old drift.
Local couriers are labelled transport rather than an expedition.

A real native run exposed a second connected defect on player departure:
ambient cancellation closed its lease before the common body authority rejected
a prepared inventory interaction. Both unstarted-insertion cancellation and
ambient prepared release now check the existing shared inventory-owner fence
before changing either journal or lease. The canonical rejection is retained,
not caught and suppressed. Pending obligations remain owned until settled; this
does not add a new cancellation protocol for every inventory owner.

Performance attribution found a mining adapter reading the current tick through
`checkpointImage()`, which serializes the whole world. Mining and resident-card
clock reads now use the lightweight execution view. Manual full diagnostics can
still serialize a checkpoint; that explicit diagnostic cost is not claimed fixed.
Quarry cell lookup now uses an immutable derived exact-ID index, preserving the
same codec, equality and depletion authority. On the identical old snapshot,
100 warmed root-state constructions averaged1.795ms before and1.732ms after
indexing: no substantial whole-server speedup follows from that measurement.
The30s ingress JFR independently identified checkpoint codecs on the server
thread. Eliminating that mining call is source-proven; a matched mature-world
chunk-latency speedup has not been measured.

### R96c verification receipt

Clean detached source `39bb0a3749a8d8ae37d664ad3403e839ada0eb9b`, tree
`bbd91d6a0fa63a3833176e9b1df80c7372128f2f`, private ref
`refs/pm-releases/quarry-player-r96c-20261010`, directory
`/home/rd/proj/pm-quarry-player-r96c-release-20261010`.
Core SHA512:
`1cda02228d31918d4c6e8cbb0d7a1dd7481da46b4cd435a01341146b1aae1ea51071849fadde2b54e659521f0860af5d77d2ce1bd2776c087a6d49acae97ee78`.
Visuals/schema269/r4 unchanged. No user-branch commit or push; the real index and
all inherited WIP are preserved. Publication/install owns only the pack boundary.

Focused domain46 checks passed (architecture7, player-edit5, ledger25,
internal-shipment2, observation7), including exact claim loss, courier retirement,
duplicate rejection and codec round-trip. Adapter12 passed (body custody1,
ambient admission8, click witness3). Existing unchanged extraction and real
block-extraction evidence is reused. Final guardrails/check/build/package passed
with `-x test`, explicitly reusing affected tests, not claiming a new full suite.
An accidental unrelated v2 year-long test was stopped; it is not release evidence.

Checked-in `live-quarry-player-container-edit.json` passed9 player actions,
2 declared terminal assertions and2 reviewed full-pack graphical frames, run
`a946e526-f6ea-40ff-a674-2d7856231fa0`. Receipt is frozen
`pale-mirror/build/quarry-player-r96c-native.json`. The ordinary player inserted
and withdrew16 granite in the actual quarry chest; frames show its presence then
absence. This proves that container boundary, not native cancellation of a
claimed cobblestone load; the latter has focused domain coverage. The private
graphical display is not human M3 acceptance.

After client exit, live summary892 had0 required/inventory/scene conflicts and
the server remained active. Current64 host-through-PM turns averaged4.116ms,
max22.045ms; lifetime ingress peak794.940ms. Fresh-world values are observations,
not a matched improvement over the older mature world. Visit readiness took7s,
so instantaneous chunk visibility is not claimed. Test client and task display
are stopped. Failed R96/R96b carriers remain failed evidence; the R96b departure
trace is retained as implementation `build/quarry-r96b-post-exit-failure.log`.

Natural departure released quarry custody (epoch3) with the same current image,
no conflict and no inserted granite remaining. A graceful same-world restart
retained that image/revision10 and stock. Post-exit summary1642 and post-restart
2513 both had0 required/inventory conflicts. Final live verifier passed for
wrapper3650128, startup1791653369, invocation
`5d8a1ee925804c41bdc83dd467f2bd76`, world
`frontier-v3-quarry-player-r96c-20261010`. Bounded before/after receipts are in the
implementation `build/quarry-r96c-{before,after}-restart.jsonl`. This is graceful
recovery evidence, not a power-loss or retroactive old-world repair claim.

## R95 systemic correction

The user authorized fixes1–5 after the R94 diagnosis. On2026-10-10 at20:49:48
the actual server stopped safely at revision1053609: HOT mining's successor
projection and adjacent-area extension were composed in the same transaction,
but supersession incorrectly required a strictly newer transaction revision.
The log does not establish that taking cobblestone caused this error. Original
log is retained in implementation `pale-mirror/build/quarry-r94-same-transaction-supersession-failure.log`.

1. Projection supersession may compose ordered images at the same canonical
   transaction revision while advancing replica revision and authority epoch.
   Stale epochs, older canonical revisions and conflicting/acquired images
   remain rejected. No second revision is invented.
2. PREPARING source custody has a distinct withdrawal path on natural unload
   or recovery of an already unloaded region. It is permitted only with no
   pending non-replayable source effect and native journals that are absent or
   settled under the same owner. Prepared writes, foreign observations and
   effect-half receipts remain fenced. Withdrawal leaves EXPECTED, never
   OBSERVED_CURRENT; re-entry reissues a fresh exact projection/epoch.
   Cached non-ticking view chunks may finish projection before release: merely
   ending ticking demand does not repeatedly cancel their preparation.
3. Mining diagnostics expose exact source custody and evaluated labor, rather
   than claiming unfinished labor for a completed task. The summary explicitly
   limits its green verdict to retained conflicts; progress is not assessed by
   that incident counter.
4. Trade demands, shared physical reservations, per-container stock and protected
   geological geometry reuse bounded derived immutable-input views. No cache
   owns resources, stores historical worlds, omits provider checks or persists.
   Changing any relevant input rebuilds the view; stock edits must invalidate
   capacity immediately. Protected declarations include all newly admitted land.
5. Audited deferred harvest receipt and obsolete-body paths: old terminal harvest
   jobs wait for naturally ticking owned depot observation and cannot be
   force-confirmed in COLD. RETIRED_INCARNATION rejects obsolete saved bodies,
   not the living canonical resident. No separate product defect was established
   in these two findings; preserve safety rather than manufacture a repair.

Current schema269/r4 is unchanged. No measured speedup, full human M3 or blanket
disappearance of historical lag spikes is claimed.

### R95 verification and delivery receipt

Final clean detached source `130a5680904e9543d83e5996197cd3bea9b2e6f1`, tree
`1075c39e3baab6a5b9423d2f6b8374cf1e9f9210`, private ref
`refs/pm-releases/quarry-recovery-r95d-20261010` in
`/home/rd/proj/pm-quarry-recovery-r95-release-20261010`.
CoreSHA512 `52ae16f05a78d42c3411a8541e2ca0595cd9974acecc7a3528693ff51135ebb071ce5396b63503f76c3201148548c707dddb4622b43c090b9ed92500997cc9bf`.
Visuals unchanged. MainHEAD/index unchanged; no user-branch commit or push.

Connected focused domain tests passed for PhysicalReplicaCustodyState,
PhysicalProjectionCustody, ExtractionSourceCustody, ExtractionAreaPlanning,
ContainerReservation, GoodsTrade, ExactInventory and ImmutableInputView.
Additional ContainerStockIndex1 plus architecture7 passed. Native diagnostic36,
actor recognition4, worksite witness5 and scenario parser43 passed. Final
read-only wait classification was checked by the36 diagnostic tests.
Node scenario53 passed. The existing native block-extraction GameTest1 passed
24s. Final frozen guardrails/check/build/package passed10s with `-x test`,
reusing those applicable focused results rather than claiming another full suite.

Same diagnostic world recovered without reset: Northwatch's region(-22,-22)
released PREPARING/epoch2 without a fictitious observation; both original jobs
advanced from stalled1042 to1046, then1060 extracted in ordinary COLD. Actual
startup passed the old1053609 failure. Subsequent area extension and HOT mining
continued without that supersession exception.

Checked-in `live-quarry-custody-recovery.json` passed9 actions/3 assertions/1
reviewed frame, run `39619e6f-6fed-45c7-9019-7cf4dee01f52`, frozen
`pale-mirror/build/quarry-r95-final-custody-recovery.json`. Actual extracted count
1110→1113→1114; source-region leases0→11→0; workers COLD→HOT→COLD. The frame
shows the owned chest/physical quarry, not a diagnostic-only endpoint.
This carrier ran on source `20610d7892170b5b38cdbb9a92a40b5c9025d27e`, before
the final read-only wait-label refinement. Mining, withdrawal, resource capacity,
geometry and carrier bytes are unchanged in the final source. Reuse is restricted
to those unchanged behavior claims; no fresh final-label visual acceptance is
claimed. Test client exited and remained off between checks,20FPS while active.

Its first global assertion correctly failed on a retained incident: the earlier
1053609 crash remains durable with occurrences1. The corrected recovery fixture
requires that exact historical incident/cause/revision and unchanged occurrence,
plus exactly one retained conflict and no inventory/scene conflicts. No incident
was deleted, acknowledged through a bypass or reclassified to manufacture green.
The two earlier parser refusals (milestone length and diagnostic action/assertion
binding) occurred before native actions; final Node validation passes. This is
not an acceptance claim for arbitrary fresh worlds or unrelated incidents.

Normal save/stop, preflight, publish/install and restart preserve world
`frontier-v3-quarry-development-r92-20261010`. Final restart1791648763,
invocation `e4d29377b880453f8fe52ee3a2561cbf`. Live verification is recorded in
the current continuity ledger: verifier PASS, wrapper3487733/port25565,
summarytick236256/revision1100901, inventory0/scene0. Northwatch1142/1152,
source leases0/workersCOLD, home512/site558. Recent64 PMturns mean19.29ms/max
99.53ms, ready/deadline lag0, no pending FF; not a matched speedup measurement.
Historical diagnostic verdict remains blocked
solely because that reviewed crash record is intentionally retained; it is not
evidence of current engine quarantine. Historical2s latency trigger remains
unconfirmed. No new test matrix/soak, manufactured old-receipt confirmation or
obsolete-body admission was introduced.

## Prior R94 delivery receipt

Shared `KnownBlockGeometry` and `AdjacentExcavationPlanner` contain no quarry,
commodity, actor or accounting policy. `ExtractionGroundKnowledge` supplies the
explicit graybox stratum and protected/foreign-change facts;
`ExtractionAreaPlanning` owns mining admission, append-only declarations and
source-region custody renewal. Existing miners, tools, UAE, navigation, physical
block extraction and internal shipments execute the work. No second actuator,
inventory ledger or terrain generator was added. Infrastructure IDs and previous
cells remain stable; geology exclusions and development survive snapshot/WAL.

Schema269/ruleset `frontier-v3-quarry-graybox-r4` declares actual flat stone
-63..62; startup validates it against the real flat generator without loading
chunks. Unknown/noise terrain is not claimed supported. The8192-cell declaration
cap remains visible; already retained access/worksite columns cannot be
undermined. No regeneration, construction or fabricated unknown stone.

### Identity

Implementation `/home/rd/proj/pm-f06r3-facility-lane-recovery` retains HEAD
`15fdec7ea375a0abaaac1b621f2cc00ae371d703` and its unchanged real index.
Clean detached `/home/rd/proj/pm-quarry-development-r94-release-20261010`:
source `07358cbe8ad24627f9274131816504bd022d5b32`,
tree `64afaead1d12c664191ee87347d7208c223b1c15`.
Core SHA512:
`a989c00514987ebd8a472c45809b12383fd3c2386372071a2f6f9303cf3df776e2a44140b1762dfbd686311c850427620f9f1c575c4a3a5a38ba9f9c25cb0ecf`.
Visuals unchanged. No user-branch commit/push;69 implementation WIP paths,
23 original nested WIP paths and unrelated outer `.f0v-baseline/` preserved.

Live `/home/rd/far-frontier-server`, unit `far-frontier-v3-live.service`,
wrapper3247511, invocation `e19b335200e34f65a35330af11e21123`, startup1791641013.
World `frontier-v3-quarry-development-r92-20261010`, seed20260918065/r4/schema269;
world name is not binary identity. Normal save/stop, immutable-source preflight,
pinned publish/install and live verifier passed. R94 recovered the same saved
diagnostic world, not a reset hiding R93 failure. Earlier worlds retained.

### Evidence

Connected focused checks: domain42 plus22, native worksite4 and real Minecraft
block-extraction GameTest1 passed. Final correction command:
`./gradlew guardrails :pale-mirror-frontier:test --tests '*ExtractionAreaPlanningTest' :pale-mirror-neoforge:test --tests '*FrontierV3TestPilotScenarioTest' --no-daemon`.
Domain5 plus architecture7/native parser43 passed25s; Node scenario53 passed.
Final frozen guardrails/check/build/package/client preparation passed11s with
`-x test`, reusing those affected tests and unchanged earlier gates, not a freshly
repeated full suite. Negatives/recovery include unknown/protected/disconnected
geometry, safe one-block descents, foreign invalidation, stale/repeated events,
stable IDs, snapshots/codecs and PREPARING/ACQUIRED custody renewal.

Ordinary COLD mining reached544 declared/534 extracted (22 outside the initial512).
Same-world graceful recovery retained jobs11/12, actors1-10/1-11, tools and cargo.
R94 later reached624/623 (111 new blocks). New exact lots are in the original
quarry container, including `lot:extraction/work/extraction-11/batch-4` and later
batches. Home reserve512 arrived through ordinary hauling; its observed lots
still comprise original batches. New-front-to-home delivery was not separately
filmed and reserve/capacity policy was not changed to force that outcome.

Checked-in `live-quarry-adjacent-development.json` passed9 ordinary actions,
2 terminal assertions/2 frames on the final full-pack client:
run `c6b2ce14-ccf3-4fcc-a9d8-c224dabab2cd`, frozen
`pale-mirror/build/quarry-r94-adjacent-development.json`. Ordinary ingress saw
newly depleted source AIR and current owned storage; natural departure returned
workers/source regions to COLD. Both actual PNGs were opened: chest, miner and
excavated front, not sky-only evidence. This is not a filmed complete HOT mining
shift, human M3 acceptance or an abrupt-crash automatic-retirement guarantee.
The test client exited; server remains active, normal tick rate20, no pending FF.

### Confirmed fixes / remaining limits

An actual kernel continuation exposed exhausted-site staffing requiring workers
before admitting the work that requests them. Staffing now queries known
extension availability independently of its not-yet-assigned workforce.

Actual R93 stopped at98479: a proposed lower floor shared an existing higher
access column, violating the single-surface graph. R94 excludes retained access
columns in the generic planner and whole declared worksite columns in its
knowledge adapter. The same world recovered and advanced past that point.
Original log `pale-mirror/build/quarry-r93-undermined-access-failure.log` in the
implementation remains failed evidence. R93's first client carrier failed before
actions because Java lacked the `firstAdjacentSource` whitelist entry; final
parser coverage now checks the exact scenario. No old failure was painted green.

Post-client summary103798/revision473093 is green,0 required/inventory/scene/
custody conflicts. Startup/ingress still recorded roughly2s catch-up warnings;
PM host-turn history includes1.968s peak, later64-turn sample mean33.89ms/max96.24ms.
Native TPS alone omits some PM post-tick cost. No measured speedup or elimination
of those existing pauses is claimed by this feature.
