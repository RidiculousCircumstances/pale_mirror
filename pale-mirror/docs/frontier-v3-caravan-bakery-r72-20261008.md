# Caravan birth and bakery boundary repair, 2026-10-08

Main alone in `/home/rd/proj/pm-f06r3-facility-lane-recovery`; no subagents.
Scope: actual stalled caravans and all evidenced connected bakery defects.
Excluded: hive, combat and inactive feature expansion. No manual canonical,
WAL, inventory or conflict repair. Old corrupted world is preserved offline;
the live test world was recreated under the user's disposable-world authority.

## Causes and implementation

- The two Northwatch groups at the reported road had coincident COLD checkpoints.
  One HOT body could not be admitted, and cohesion correctly held the other.
  Commit `7fa212413b2fc197ae88c70b10dd02a41dc583ee` uses the registered movement
  provider's known geometry for bounded connected body-birth alternatives.
  Actual support, collision, native connectivity and common body observation
  still decide admission. No semantic travel or work progress is fabricated.
- Live Southgate 9-15 and Mossbrook 10-27 repeatedly closed bakery scenes for
  no presentation demand and immediately readmitted them because the depot
  still held physical inventory custody. Actual unload then raced body insertion.
  Admission now uses the shared demand/readiness selector, not depot custody.
- Body insertion could still be pending, or its native section already draining.
  Common body admission now defers exact pending insertion and disallows birth
  into hidden/unloading/unrestored or unobserved non-ticking sections. A positive
  synchronous insertion refusal with successful exact rollback is retryable;
  interrupted unknown effects retain their durable fences.
- Successful native save can acknowledge insertion before supported admission
  moves canonical body PREPARED to RUNNING. Previously the same saved PREPARED
  body was no longer recognized once its pending ledger entry was acknowledged.
  The common owner now recognizes its exact current epoch/residence and recorded
  lifetime for indexing. Only subsequent supported indexed observation grants
  execution readiness. Competing carriers/departures/foreign bodies still veto.
- Bakery conflict recovery may request ordinary body presentation at a safe
  station checkpoint through the existing common body owner. It never resets
  authority, replaces an existing body or replays a recipe. CONFLICT remains
  until the existing exact family receipt proves station batch, cargo and
  physical-effect closure and opens ordinary drain/release.

## Linked native expedition evidence (R71 Java source)

Retained private world `v3-disposable_accelerated_expedition_bo-f71c5756`, seed41,
ports26720/26721, ordinary20TPS HOT segment, private Xvfb92/20FPS client.
Prepared artifact SHA256
`2f6a461ebdd4e00301a51ec422a02d0813607b52676312848bde838da271a0f0`
has dirty precommit metadata: not the identical clean live artifact bytes.

- `build/caravan-r71-retained-before.json`: PASS run63cb7ca0-51a5-4077-8037-1a3461043ace.
  Actual donkey provisioning and HOT motion, release to COLD, normal save/stop.
- `build/caravan-r71-retained-after.json`: partial failed carrier runf18924a1-a001-4a1c-be18-47e1afeb15be.
  Same-world restart, actual paid delivery16bread/32money, return and participant,
  animal and mission/budget release completed. The scenario omitted the final
  summary-read action required by its own assertion; exact client was terminated
  and server saved/stopped normally. Do not call this manifest green.
- `build/caravan-r71-retained-terminal.json`: PASS run5d17d2cd-a15b-49fd-a78e-fd86a817627e.
  Only the missing terminal subset ran on that same saved world. Seller132 and
  buyer68 from100 each, no active mission/animal reservation/budget, same living
  donkey UUID `60c79a93-cc39-3460-b31b-342c50275e25`, actual home frame and green
  summary. Both client and server exited normally. No whole-trip confidence rerun.

Future checked-in expedition scenario now reads summary explicitly and bounds
real loading/assembly at300seconds, the existing ordinary-wait ceiling, without
weakening terminal assertions. Linked segments establish this executed flow,
not a fresh all-green original manifest, exhaustive caravan validation or M3.

## Bakery verification and remaining work

Focused body admission/residency/first-admission/adoption and bakery domain checks
PASS; native production-work2 PASS. Native body-lifetime18 PASS, including actual
serialized PREPARED-body return after modeled save-ack ordering; it is a component
boundary check, not an entity-region crash/durability acceptance claim. Two old
pending-first native assertions were updated from CONFLICT to DEFERRED while
preserving the no-duplicate insertion requirement. Architecture/guardrails PASS.

R72 source `d1a9b513874fd3c867c0b3d0cd288980875a8b35` was committed clean.
Detached `/home/rd/proj/pm-bakery-body-r72-release-20261008` passed guardrails,
JAR build and packaged-JAR verification. Pack validation, preflight, pinned install
and fresh deploy verification PASS. Same live world
`frontier-v3-common-inventory-r65-20261008` retained, seed20260918065.
Service invocation `52744821a9f642dc880121a05bb9989c`, startUnix1791483262,
wrapper2414300/Java2414336. Installed+hosted PM SHA512
`0f9af309b3f30dd019785402b66f36edf769e6fe89b13033d859c5436e5ce946dd277445c2b9ad9445b83dad86ca8a7653227cde1ea977c73b3294e44983668f`.
No new quarantine; all four preexisting scene conflicts remain before client visit.

The first live full-pack pilot copied43GB of server worlds and timed out before
connecting. Actual Gradle stack showed copying then whole-root fingerprinting;
this is invalid harness preparation, not a native product result. An unconnected
second attempt was deliberately stopped to avoid redundant hashing. Commit
`d61122d3` limits Sync to mods/config/defaultconfigs/resourcepacks/shaderpacks
and removes redundant unfiltered inputs.dir; the task's own filtered tree remains
tracked. Ordinary Sync removed only generated copies, not source/live worlds.
Separate preparation PASS80seconds including the one-time old-output fingerprint
and cleanup; final client profile245MB. Existing preflight check PASS after
correcting its stale source path to the actual extracted FullPackPreflight owner.
Java source is unchanged from R72; the pack-native pilot uses those source classes,
not the identical installed JAR bytes.

Both originally reported groups70718fce and3f7c9c60 are now missionCOMPLETE,
groupCLOSED revision6/goal2, with home participants released in normal live COLD.
This is not proof of the exact overlapping HOT birth on those groups.

## Retained-world recovery limit and fresh deployment

`build/r72-bakery-live-connected.json`, runf953f8ad-1cb3-4d44-861b-b03d2c05cd22,
FAILED the first actual live recovery visit: Southgate9-15 stayed absent/CONFLICT
after90seconds although its placement was physically available. Saved ledger
inspection for all4 finds ESTABLISHED physical_history, current residence marks,
but no inactive carrier/pending insertion/body departure authorizing current
PREPARED incarnations. Read-only addressed entity-region inspection finds none
of those4 bodies in the selected graybox dimension; repeated after normal
save-all-flush/stop gives the same result. This diagnostic is not a global
absence/reconstruction permit. Do not infer fresh creation or manually clear
their conflict. The older lost history is NOT declared repaired.

Fresh-world-only policy applies: old world retained offline for the exact
diagnosis; new selected world `frontier-v3-caravan-bakery-r72-20261008`, same
seed20260918065, exact unchanged clean R72 artifact above. Managed graybox
datapack copied from the pack-owned source before genesis. Preflight/fresh deploy
verification PASS, service invocation5687d208c62b44e29242c9b8d8defecb,
startUnix1791484036/wrapper2443498. Ordinary advance hold released; no manual
canonical/WAL/inventory edit, no old-world deletion or push.

## Complete bakery native result

`build/r72-bakery-complete-restart.json`: PASS, run
790f6e30-2b94-4f2e-8b93-6161f13d68b1, clean source12f650be,
prepared artifactSHA2565797889391f23466803bff3d33908b352b9341dda5230b5508b153b59d255e44.
Java implementation unchanged from deployed d1a9b513; harness-only commits differ.
Private ordinary20TPS world `v3-disposable_bakery_full_cycle_restart-b68637ac`,
seed41/ports26720-26721/private display92/20FPS. Actual baker1-9 takes wheat,
walks to the station and loads it, visible named BAKER and station confirmed;
linked clean frame inspected (close camera, not M3/human acceptance). Graceful
restart split after action8 retains the same exact job/input/station; processing
continues to80, bread physically unloaded and carried to the depot. Exact job
retires, output lot64bread remains canonically in depot, physical chest slot0
contains64bread/OBSERVED_CURRENT/custodyACQUIRED/no mismatch or pending inbound.
Private client exited and server port closed by runner-owned final cleanup.

Initial full-cycle attempt FAILED a stale test visit coordinate: declared feet65
vs actual64 after gravity. The product had taken grain, loaded the station and
started processing while the pilot waited for an impossible camera arrival.
Commit12f650be corrects those declared heights and releases the ordinary canonical
hold explicitly before/after restart. No timeout weakening or fake arrival.

## Live full-pack boundary result and remaining performance debt

`build/r72-live-boundary.json`: PASS, run
7ee66a12-93ad-4cb3-a2d8-289081e0aa96, clean source2d4e6a02/Java unchanged.
Checked-in `live-settlement-boundary-diagnostic.json` SHA256
cffdf0fede635b0811a7e528262e051e4ec21d863f0af31af7f9d55e3a5b228c.
Ordinary20FPS full-pack player visits Clearwater→Ironmeadow→Clearwater,
observes naturally loaded depots as OBSERVED_CURRENT, then exits to overworld.
Terminal instant6719/revision2812: green/sceneConflicts0/requiredConflicts0/
inventoryConflicts0/replicaCustodyDiagnostics0. Client disconnects normally
23:35:59; no task client or private scenario server remains. Live service stays
active, Java2443547. Later instant10188/revision3843 has the same green zero
conflict result. This is a narrow entry/unload/reentry diagnostic, not an
entire new caravan expedition, harvest cycle or player-comprehension gate.

Two native boundary lag warnings remain:23:35:34.940 reports2028ms/40ticks,
23:35:52.487 reports2025ms/40ticks. No performance closure is claimed.
Cumulative PM instrumentation after the visit has physical ambient maximum
295ms, validation18ms and WAL31ms; those separate cumulative maxima do not
attribute the2s stall or exclude native chunk/entity storage and uninstrumented
callbacks. A future latency correction should profile this exact boundary,
not repeat the already terminal caravan/bakery cycles for confidence.

Actual overlapping caravan HOT reentry has component/pure coverage but has
not been observed on the originally reported groups. No exhaustive all-caravan,
HUMAN_CANDIDATE or M3 acceptance claim. Old four conflicted baker histories
remain preserved offline, not reconstructed or declared recovered.
