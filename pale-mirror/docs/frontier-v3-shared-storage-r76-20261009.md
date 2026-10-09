# R76 shared physical storage delivery

## Identity and scope

User explicitly authorized commit and live server update on2026-10-09, no push.
Source `63a7c011297216b58aac68686df0fc9932db40e8`, active checkout
`/home/rd/proj/pm-f06r3-facility-lane-recovery`, clean detached release
`/home/rd/proj/pm-shared-storage-r76-release-20261009`. Both source trees clean.
The [implementation receipt](work-orders/PM-SHARED-STORAGE-20261009.md) records
the full gate, focused34-case checks and actual36-case native composition slice;
these were reused, not rerun as a confidence campaign. Clean release guardrails,
JAR and package verification passed11s and reproduced the verified artifact.

Pale Mirror SHA512:
`66cc549ad298857a7bcb491728254f7a6bd1d2430c95c342d6caa3017f4596bb6cfc552f04b2c6e4f266ab4ec4a13356645d19f7ff83c2da3835ed879f69ffe7`.
Unchanged Visuals SHA512:
`2fd468cb382d64fa62eaa2cb2239d7bd75c89a4a06c21ce1bf2a15f0775db02e5daf2591b91863b74d4dc87a90067ef714dd631e4174daf79c9caad78f994601`.
Published through outer pack `publish-client-artifacts.sh`, installed through
`install-server.sh`; hosted checksum and installed JAR agree. Pack validation,
clean detached source/profile/Java preflight and fresh startup verification PASS.

Runtime `/home/rd/far-frontier-server`, `far-frontier-v3-live.service`.
Fresh disposable world `frontier-v3-shared-storage-r76-20261009`, seed20260918065,
existing `frontier-v3-trade-playtest-r3` ruleset, pinned Temurin22.0.2+9,
heap4–12GiB, view8/simulation6. Graybox datapack installed before world creation.
Initial invocation `0f0819ca12ec4ac2b370d251356782fc`, start1791528745,
wrapper3789740. Nine current `.dat`/`.dat.journal` families exist in the graybox
data directory; the obsolete field-blast registry is absent.

## Ordinary full-pack client checks

One client at a time, capped20FPS, task-private Xvfb; this is native execution,
not human visual acceptance. Reports below are relative to the implementation
checkout's `pale-mirror/` Gradle root.

- `live-settlement-boundary-diagnostic.json`: PASS, run
  `77c61569-e84d-4a6b-b495-51aa9f295534`, `build/r76-live-boundary.json`.
  Ordinary Clearwater→Ironmeadow→Clearwater→overworld visits and current
  containers. Terminal instant757/revision1210 green, all conflict counts0.
  No ambient admission in this short flow; it is not body-insertion evidence.
- `live-carrier-journal-admission.json`: PASS, run
  `cb9b7d9f-6975-42be-983f-77e779b022f4`, `build/r76-live-admission.json`.
  Resident7-1 actually INDEXED with UUID2234e8e8-fc3d-30f0-99ab-c51f8d513485
  and HOT lease. All nine streams were registered; observed queue writes/bytes0.
  Terminal instant2452/revision1500 green, all conflict counts0.

Both clients and displays exited. No continuous graphical client or profiler
remains. These flows cover deployment/admission/boundary integrity; they do not
prove complete farming, trading, blast interruption or whole-feature M3.

## Same-world graceful restart

Authenticated ordinary `save-all flush` acknowledged, followed by `stop`.
All dimensions saved11:57:28.683. Restarted the same R76 world/artifact at
1791529058, invocation `c3c4266a70564a6194cdb42dc766540f`, wrapper3801056,
Java3801108. Fresh deploy verifier PASS; server remains active on25565.
Postrestart summary instant4926/revision2091 green:366 residents,12 settlements,
12 sites; required/scene/inventory/custody conflict counts0. Recovered journal
sequences include ACTORS21, BLOCKS117, FIELDS105. Eight naturally reopened
streams report queue writes/bytes0; lazily opened BOARDS was exercised before
restart, not re-opened by this diagnostic. All-adapter recovery/drain coverage
is in the reused focused/native checks, not inferred from that lazy registry.
Retained evidence `build/r76-restart-diagnostics.json`. Ordinary simulation
advance hold released again for interactive testing. No new PM quarantine,
journal failure or keep-up warning in the inspected restart log; optional-mod
mixin class warnings are not treated as PM storage failures.

No comparable before/after TPS or whole-path speedup is measured. R75 actor-only
7.674x remains evidence for that narrower stream, not the other eight families.

## Disposition and repository boundaries

R75 saved/stopped normally first, all dimensions saved11:50:46.773; old processes
exited. After healthy R76 startup its exact disposable37MiB world
`/home/rd/far-frontier-server/frontier-v3-carrier-journal-r75-20261009` was
permanently removed, no world backup. No old-format migration or history repair.
Installer recoverably archived the old JAR under
`.far-frontier-installer-cache/retired-pale-mirror/pale_mirror-hosted.jar.20261009T065144Z`.

Outer pack retains only unrelated `.f0v-baseline/`; original nested repository
retains23 unrelated WIP paths. Neither tracked tree changed. Canonical governance
commit includes this task's receipt/ledger and only scoped architecture,
performance and implementation-plan hunks; unrelated mixed WIP is preserved.
Source and governance commits are local; no push requested or performed.
