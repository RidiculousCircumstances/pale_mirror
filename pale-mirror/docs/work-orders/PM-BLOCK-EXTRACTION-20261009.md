# PM-BLOCK-EXTRACTION — reusable Minecraft resource work

User accepted full implementation2026-10-09, SOLID and reuse required. Main alone,
no subagents. Implementation checkout `/home/rd/proj/pm-f06r3-facility-lane-recovery`,
Gradle root `pale-mirror`, HEAD63a7c011, previous resident-card WIP preserved.

## Outcome

Use one Minecraft extraction adapter for block-resource work; integrate the
farmer now. Preserve present grain production, crop interventions, hands/custody,
HOT/COLD and durable paired-effect recovery. No quarry or seed economy this cut.
Design/limits: [shared extraction](../frontier-v3-block-extraction.md).

## Implementation

- Pure `model.extraction.BlockExtraction` and `BlockExtractionPort`: complete
  immutable source/successor, tool/loot definition, resolved output, exact command.
- Generic `FrontierV3MinecraftBlockExtraction`: real Minecraft loot context,
  typed current actuation, read-only prepare and bounded loaded-world apply.
  Source/tool/authority failures do not mutate; successor presence is observation,
  not proof that the adapter earned output. No resource issuance or farm switch.
- `FieldHarvestExtraction`: explicit grain-only versioned profile. Common
  Minecraft loot-table resource declares wheat age7 -> one wheat. Replant is a
  separate existing field effect; seeds remain out of current balance.
- Existing field witness retains generic prepared data before its disk fence,
  validates exact cause/execution on hydration, and recovers the same output.
  Formats witness9/field-store17; no historical test-world migration.
- `ResourceSiteHarvestProcess` obtains quantity from the profile in HOT/COLD.
  `FungibleResourceLedger` accrues exact typed outputs/quantities without wheat
  specialization; hand/pocket bindings retain owner and physical authority.
- Reuses existing family actuation capture for both movement and extraction;
  no independent epoch store or private scope validation rules.
- Canonical/source architecture maps and contract updated together.

## Targeted evidence

Focused domain harvest/receipt/resource ledger and native witness/codec tests
PASS28s: `build/block-extraction-focused.log` in implementation Gradle root.
Meaningful new checks cover non-grain output2 in COLD/HOT pocket, incorrect
quantity/epoch, generic NBT roundtrip/missing identity/altered output, and retained
field cause substitution. Existing crop/hand/recovery negatives retained.

Isolated native `block-extraction` slice initially PASS: standard loot evaluation
for grain and vanilla stone with pickaxe, no inventory issuance or reroll, source
and tool changes rejected, missing authority cannot mutate. The final gate also
admits the actual declared worker entity in the world rather than retaining an
uninserted test Mob. Final admitted-body check passes below; initial run is not final proof.

Attempted general command:
`./gradlew guardrails check :pale-mirror-neoforge:runGameTestServer
:pale-mirror-neoforge:build :pale-mirror-neoforge:verifyPackagedJar
:pale-mirror-neoforge:runFrontierV3SceneGameTestServer
-PfrontierV3GameTestSlice=block-extraction --no-daemon`
Log `build/block-extraction-integration.log`.

### Final technical closure

`build/block-extraction-final.log`: PASS31s, guardrails/architecture constraints,
Frontier61 focused tests/0failures, NeoForge61 focused tests/0failures, packaged
JAR and actual inserted-body extraction GameTest. Selected harvest/receipt/ledger,
witness/codec and pilot-parser classes, plus Frontier architecture checks.

Final source also rejects block entities on both prepare and apply, including a
known-state bypass. Actual chest inventory remains untouched by either rejection.
`build/block-extraction-stateless-final.log`: PASS2m59s, guardrails, build/package,
actual native grain/stone/chest-negative adapter check, and all NeoForge unit
tests684/0failures/1skip. Frontier61 evidence retained; no unchanged broad repeat.

Real isolated player scenario `disposable-resident-worker-meal.json` PASS96.7s:
`build/block-extraction-farmer-held.json`, run
`d5e0594f-5f39-4df2-a82f-1804b81ede16`, source-content digest
`88a54838384784eda49fe8180a5f6d93e064003e97d90ca99fd6afa416299a19`.
The fixture begins with64 of65 cells already accounted/delivered. The actual
farmer yields for hunger, eats one bread, resumes its same job, harvests/replants
the remaining physical cell, delivers and confirms its receipt; field GROWING,
container ACTIVE/OBSERVED_CURRENT/ACQUIRED with no replica conflict. Twelve
ordinary actions passed; no simulated client readiness or fabricated loot.
Main reviewed both PNGs under `build/frontier-v3-scenarios/` for that run:
`worker-after-meal` shows the last ripe tuft; `field-after-worker-return` shows
young planted cells and the departing body. Nighttime/distant screenshots are
not general visual/readability or complete field-cycle acceptance.
The subsequent block-entity guard affects neither wheat nor air; its native
negative and ordinary grain/stone positive are freshly checked, consumer proof
reused. No full-pack or general recovery-product promotion is claimed.

Invocation: from `tools/frontier-v3-test-pilot`,
`DISPLAY=:0 FRONTIER_V3_PILOT_PORT=25591
FRONTIER_V3_PILOT_INITIAL_CANONICAL_HOLD=true npm run scenario:isolated --
scenarios/disposable-resident-worker-meal.json build/block-extraction-farmer-held.json`.
Initial hold protects the deliberately imminent hunger fixture until its normal
release action; it is not a runtime fix. All task-owned clients/servers stopped,
FPS20, ports25591/25592 closed. No live service or hosted artifact mutation.

### Failed attempts and residual findings — not hidden acceptance

- The general core run executed352 tests with9 failures. Its independent list
  is retained below. Attribution of the remaining failures is not established;
  they are not labelled product regressions or harmless harness errors.
- Exact incomplete carrier declaration in the body-observation fixture was
  corrected to a complete explicit tuple. No production authority was weakened.
- Running native build preparation beside the broad Frontier test JVM rewrote
  its dependency JAR. Subsequent ClassNotFound failures are invalid test evidence;
  that exact owned worker was stopped. Do not compile/rewrite shared outputs
  while another verification JVM reads them; parallel builds need isolated
  outputs/checkouts. Final focused verification was serialized.
- The first farmer attempt failed before actions because Java's native parser
  rejected diagnostic name references already allowed by Node and the pilot
  resolver. Aligned the bounded closed selector contract and added regression
  parsing the actual checked-in farmer scenario before any client launch.
- The next attempt failed setup because fixture hunger progressed during client
  startup. Corrected invocation to its existing initial-hold/release contract;
  no assertion or timeout was weakened. These attempts are not product proof.

Original core failures (follow-up sanitation below):
`farmerReleaseCapturesRealCollisionSupportButNotAnAirborneBody`,
`occupiedRetainedNextBodyBlocksTheSameDemandedPatrol`,
`soundCropWithBlockedWorkerHeadroomHasAnIndependentAccessObservation`,
`growthBatchRecoversIndividuallyAfterPartialPhysicalApplication`,
`pausedFieldDoesNotOwnOtherFieldsTurnsAndResumesItsExactPrefix`,
`missingOwnedHotBodyBlocksTheSameDemandedPatrol`,
`fixedSeedSettlementAssaultUsesIndependentColdPlansAndHotReceipts`,
`freshAmbientAdmissionDefersForOccupiedExactBodyColumn`,
`demandedManagedResidentAndBioformFallThroughExecutorCadence`.
At the original extraction cut the general gate remained non-green; that cut
closed only bounded reusable
extraction integration and the actual last-cell farmer consumer, not those other
subsystem promises. No speculative unrelated repair or hive migration attempted.

### Follow-up: retire obsolete setup, repair current tests

User requested removal of legacy tests and repair of current obligations.
Static audit established that none of the nine failed semantic obligations was
retired: field/body persistence, physics, exact admission, patrol obstruction and
observer combat calibration still have active owners. Red tests alone are not
retirement evidence. Removed the unused synchronous scene-body fixture overload
and cleanup helper, and replaced the old synchronous materialize/persist/retry
loop in every retained caller with asynchronous readiness callbacks.

The actual Minecraft templates decode to `bastion/mobs/empty=[1,1,1]` and
`bastion/treasure/big_air_full=[38,48,38]`. The affected field, body, patrol and
ambient fixtures now use the larger envelope, with field/support positions in
its interior. The farmer save fixture supplies the complete carrier declaration.
Patrol intervention waits for actual HOT admission rather than callback3.
Ambient obstruction clearance waits for the real durable insertion result.
Combat setup supplies ordinary observation before admission, yields through
the insertion journal and chains samples only after each actual body cohort.
GameTestServer's `waitUntilNextTick()` only runs queued tasks: it does not pace
simulation ticks against asynchronous disk I/O. Old40/80/100-tick deadlines were
therefore invalid for this setup. The scene fixture now has a ten-second real
no-progress watchdog started after yielding from batch construction, renewed
only by additional indexed exact members, plus a finite outer GameTest bound.
No production admission, persistence, physics or semantic assertion is weakened.

The first focused `native-regressions` run passed all eight non-combat original
failures; exposed the shared scene-body fixture problem in calibration and its
eight strike consumers. Subsequent narrow strike runs established native
residency and pending/ready insertion receipts, distinguishing improper tick/I/O
deadlines and competing batch construction from a missing product transition.
Final focused native receipt: `build/native-regressions-final.log`, all42
required tests PASS, including all nine original failures and all eight strike
consumers of the new asynchronous helper. Independent calibration retained all16
seeds201..216: both runs successes16/casualties0, measured duration COLD320/HOT32
inside the unchanged declared tolerance1200. This is repaired native regression
coverage, not full release/product acceptance. Native run finished successfully
in3m5s. Final `guardrails :pale-mirror-neoforge:compileTestJava
:pale-mirror-frontier:compileTestJava --no-daemon` PASS10s, receipt
`build/native-regressions-static-final.log`; source diff check clean.
Task-owned native server exited normally; no task client/server remains.
Outer pack repo and original nested source were read-only and retain their
pre-existing five modified pack paths/untracked baseline and23 source paths.
No full matrix rerun,
live deployment, commit or push is part of this sanitation follow-up.

## Delivery boundary

At the implementation/sanitation cut, no live deployment, commit or remote push
was authorized; R77 was the selected live server. This evidence is technical integration, not renewed full
farmer visual acceptance, Vanilla seed economy, arbitrary mod loot or a quarry.
Deployment must use a frozen clean source identity, fresh disposable world and
matching packaged artifact under the normal separate server workflow.

### Authorized follow-up: commit and deploy

User subsequently requested commit and deployment. Source77cf9376 and pack
4d514997 are committed separately; R78 is deployed on a fresh schema-compatible
world. The earlier no-deployment boundary describes the implementation/sanitation
cut, not the current authorized delivery. Exact build, artifact, service/world
identity and bounded health evidence:
[R78 receipt](../frontier-v3-block-extraction-r78-20261009.md).
Discovery found R77 already stopped after an inactive-runtime quarantine;
its exact world/log are retained and initiating cause remains unconfirmed.
No remote push or whole-product graphical acceptance is claimed.

Historical implementation-cut disposition: HEAD63a7c011,117 dirty/untracked
paths including preserved resident-card WIP; no staging/commit/push then. Original
`minecraft/pale-mirror` retains its23 unrelated dirty paths, untouched. Outer
pack retains five previously modified installer/index/pack files plus unrelated
untracked `.f0v-baseline/`; no pack/deployment edits in this assignment. All three
diff-whitespace checks pass. Final source/canonical doc guardrails PASS9s,
`build/block-extraction-docs-final.log`; no running verification task remains.
