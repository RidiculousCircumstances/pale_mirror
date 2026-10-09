# Block extraction R78 delivery — 2026-10-09

User requested commit and deployment; no remote push. Main alone, no subagents.
Implementation and retained test repair:
[active order](work-orders/PM-BLOCK-EXTRACTION-20261009.md).

## Frozen identities

- Source:77cf9376e921a7c79f5b844fdc99639e20f44fca,
  tree9c30e73371102ec73b14c52ca1baac6ea5397d05.
- Clean detached source:/home/rd/proj/pm-block-extraction-r78-release-20261009.
- Implementation branch:feat/baker-carry-orders-20260926 in
  /home/rd/proj/pm-f06r3-facility-lane-recovery; clean after commit.
- Pack:4d5149973b2563c55deada9fc8c64d157982bfa1. Separate commit records
  macOS Bash3.2 compatibility and exact index/pack pins.
- Runtime:/home/rd/far-frontier-server; far-frontier-v3-live.service.
- World:frontier-v3-block-extraction-r78-20261009; actual vanilla seed
  query20260918065; Java22; trade-playtest-r3; view8/simulation6; heap4–12GiB.
- Service start1791541905; invocation50bf642fe2c1402caaa5488c133eae5d;
  MainPID112578, Java112602.
- PM SHA512:
  4344de3b43f1045855e33f089d7b2dd4ff4358a6eb8ad8b0d803e3829ca0f1b73d25864e56ab74975890d42cf6aed3ab16249e469f29fff683f634bd90e2fff6.
- Unchanged Visuals SHA512:
  2fd468cb382d64fa62eaa2cb2239d7bd75c89a4a06c21ce1bf2a15f0775db02e5daf2591b91863b74d4dc87a90067ef714dd631e4174daf79c9caad78f994601.

## Verification and operations

The order retains focused domain/witness checks, NeoForge684/0fail/1skip,
actual native extraction positives/negatives, last-cell farmer after hunger,
and repaired native regressions42/42. Those identity-compatible results are
reused, not reported as another fresh full matrix or whole-farm acceptance.

Fresh detached build:

```bash
./gradlew \
  -PfrontierV3GrayboxDisabledModsCatalog=/home/rd/proj/minecraft/config/graybox-disabled-mods.txt \
  guardrails :pale-mirror-neoforge:build \
  :pale-mirror-neoforge:verifyPackagedJar --no-daemon
```

PASS2m52s, build/r78-release-build.log in the implementation Gradle root.
Pack scripts/test-install-client-retirement-policy.sh and scripts/validate.sh
also passed. Unchanged earlier broad Frontier aggregate is not claimed.

Published through scripts/publish-client-artifacts.sh using the clean source JAR
and unchanged installed Visuals. Hosted HTTP SHA and installed PM SHA agree.
scripts/install-server.sh installed pins and required datapack before genesis;
selected the exact new world using --level-name, Java22 and pack URL
http://127.0.0.1:8092/pack.toml. Existing EULA remained true.
Field witness9/store17 reject old-world reuse; no automatic migration.

scripts/frontier-v3-deploy-preflight.sh PASS with the exact clean source ref,
artifact SHA, runtime/world/Java and --require-world-absent.
Started through systemd-run; fresh Done and Frontier v3 runtime started recorded.
scripts/frontier-v3-deploy-verify.sh PASS for the same SHA/world/service and
--not-before1791541905. Port25565 listening, live process verified.

Removed test-only initial_canonical_hold JVM property so normal gameplay advances
immediately. Fresh RCON summary at revision1388/instant2155: verdict green,
12 settlements,366 residents,48 bioforms,12 sites, required/scene/inventory/custody
conflicts0 and incidentIndexSize0. No new quarantine or keep-up warning at this
bounded cut. This is not a sustained TPS claim.

The first auxiliary vanilla seed query used the PMV3-JSON-specific helper and
timed out15s; it did not mutate or interrupt the server. A bounded vanilla frame
query returned Seed:[20260918065], followed by the normal complete PMV3 summary.
No test-harness change or rerun campaign was introduced.

## Separate pre-existing R77 incident

Discovery found R77 already stopped, not an active service requiring shutdown.
At15:27:27+05 it threw v3 runtime is inactive from BakeryPhysicalEffect.block
through CommandSubmission, quarantined and stopped normally, saving all
dimensions. Initiating cause remains UNCONFIRMED; R78's new-world startup does
not prove a fix for it. Old world frontier-v3-resident-cards-r77-20261009 is
retained for that exact diagnostic question. Old log copied to
build/r78-prior-r77-incident.log. No world/evidence deletion or manual repair.

## Delivery limits

Matching client artifact is hosted; use the normal client updater.
No task client/display remains. No renewed graphical full-pack HUMAN_CANDIDATE,
whole-field cycle, quarry, seed economy or full expedition acceptance claimed.
Only the changed extraction boundary and recorded tests are confirmed.
Original nested source23 unrelated paths and pack .f0v-baseline/ preserved;
canonical mixed historical WIP not blanket-staged. No remote push.
