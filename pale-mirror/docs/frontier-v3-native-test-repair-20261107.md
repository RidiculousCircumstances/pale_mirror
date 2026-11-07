# Native regression fixture repair — 2026-11-07

Source `2c0a3b0f5`: six test files, 100 additions and 41 removals.

The repair target is the 17 failures retained after clinical-care delivery
`041afc639`. The same 17 test names failed on the preceding clean `b7ae41ae7`
baseline. Their causes are now classified; none required weakening production
validation, deleting a test, increasing its deadline or changing the product.

## Causes and changes

- Eight storage/recovery cases: the shared ACTIVE fixture reconstructed an
  inventory without its fungible ledger and omitted container recovery fences.
  Authored warehouse containers now explicitly declare racks, whereas these
  isolated adapter tests place chests. Declare their actual chest provider,
  preserve all inventory fields and perform both recovery transitions in the
  initial fixture. Select empty physical slots when adding exact cold stock;
  the old COLD capacity query could select projected bulk wheat.
  Keep PREPARED surfaces under their lifecycle owner rather than requiring
  early inventory discovery. Round-robin discovery uses ACTIVE preconditions.
- Two production cases: the same provider/fence mismatch prevented the real
  physical adapter from executing. Both now use the repaired initial fixture.
- Four patrol cases: initial COLD assembly ran against authored obstruction
  facts before the test created its clear native floor. Prepare the bounded
  floor first and capture actual native shapes into its initial geometry.
  HOT movement, obstacle refusal, body-loss refusal and demand-loss release
  still use the registered executors and exact retained formation.
- Three service-death cases: a nominal infection worksite did not establish a
  reachable warehouse/worker route. Declare an ordinary outdoor source next
  to a known treatment worksite and stage the initial worker at its exact
  input station. Native and canonical body positions now agree. These remain
  isolated pre-loot accounting cases, not evidence of full service movement.
  Applied, unperformed and unbegun input outcomes still assert exact custody,
  resource conservation, one/no real drop, retirement and hydration.

## Verification

Focused commands use JDK21 and
`:pale-mirror-neoforge:runFrontierV3SceneGameTestServer --no-daemon`:

| Slice property | Required tests | Result | Log |
| --- | ---: | --- | --- |
| `frontierV3GameTestSlice=reference-projection` | 15 | PASS | `/tmp/pm-native-test-repair-storage-2.log` |
| `frontierV3GameTestSlice=production-effect` | 5 | PASS | `/tmp/pm-native-test-repair-production-1.log` |
| `frontierV3GameTestSlice=route-patrol` | 4 | PASS | `/tmp/pm-native-test-repair-patrol-1.log` |
| `frontierV3GameTestSlice=service-death` | 6 | PASS | `/tmp/pm-native-test-repair-service-1.log` |

The counts include neighbouring checks, with all 17 target failures covered.
The first full run passed 474/475 and exposed one additional equipment-return
fixture selecting a COLD capacity slot occupied by its projected bulk wheat.
Choose an actually vacant projected slot without discarding initial supplies.
The repaired `reference-conflict-restart` slice passed all 5 required tests
(`/tmp/pm-test-repair-equipment.log`). Static guardrails passed before the final
two-line fixture correction. Final gate PASS: JDK21 `./gradlew guardrails
:pale-mirror-neoforge:runGameTestServer :pale-mirror-neoforge:build
:pale-mirror-neoforge:verifyPackagedJar -x :pale-mirror-neoforge:test --no-daemon`.
All **475/475 required native tests passed**, 4.593 minutes of GameTests,
5m5s complete build (`/tmp/pm-test-repair-final-gate.log`). No test was removed.
The unchanged JVM production checks accepted in the medical receipt are reused;
the full unrelated JVM baseline is not claimed green.

Clean detached RELEASE at `2c0a3b0f50edd0317220a31c59e2734e3c90e0e3` also passed
JDK21 guardrails/build/package with the explicit root disabled-mod catalog and
`-x :pale-mirror-neoforge:test` (`/tmp/pm-test-repair-release.log`).
Evidence copies and machine summary: IMPL `build/native-test-repair-20261107`.

## Deployment

Same world `clinical-care-seed139-20261107`, seed139/r35/schema347; no reset.
RCON stop saved all dimensions at21:35:28.775, operation pending0, followed by
actual JVM/service and25565 exit. Preflight and post-start verifier PASS.
Root publisher verified all170index hashes; hosted HTTP download matches core:

`967b11931bb6b88c6a91025953ab9f35fbb0663f3ebe73095605ddf5ed1792b90e91de39948496549e1dfa014d871605c788cd69db89e491ef2a3aa9f9986b65`

Visuals unchanged (`2fd468cb…994601`). User service
`far-frontier-v3-live.service`, wrapper4078710, invocation
`87b1bcf3a3a94e99ad1efb9df296393c`, notBefore1794069344, Java22/25565.
First verifier ran before the TCP bind and rejected that incomplete startup;
verification after the actual ready/port boundary passed. No restart or reset
was needed for that observation timing.

Before restart tick56606/revision137591 and after tick56758/revision137901:
12settlements/335residents, green,0required/scene/inventory conflicts,
incidentIndex0. This is restart health, not prolonged economy acceptance.
IMPL and RELEASE tracked clean; PACK98a6701 tracked clean (only ignored hosted
artifacts changed). Unrelated IMPL `.serena/`, PACK `.f0v-baseline/` and GOV WIP
preserved. No push.
This is test repair; no new player-visible behavior or graphical acceptance is
claimed. Previously recorded unrelated JVM baseline debt is not discharged by
these native results.
