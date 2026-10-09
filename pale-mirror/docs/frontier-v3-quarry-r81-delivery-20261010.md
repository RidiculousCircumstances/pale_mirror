# R81 quarry test-server delivery

Current status supersedes the original deployment below: the self-test found an
actual attached-container/body departure failure and R81 stopped at01:32:39.
See [self-test evidence and exact cause](frontier-v3-quarry-r81-self-test-20261010.md).
Do not infer current service health or completed native public stone delivery
from the original startup/early inspection success.

Implemented by main alone, no subagents. Accepted scope remains the finite
quarry / separate home-hauling / ordinary public stone-reserve trade cut.
Source/evidence implementation receipt: ledger-named checkout's
`pale-mirror/docs/frontier-v3-quarry-r81-20261010.md`.

## Identity and deployment

Clean private detached monorepo:
`/home/rd/proj/pm-quarry-r81-release-20261010`.
Source29bd176d4c280fc308bdc9ae4af57342509dae44;
tree0e3efa8b28e1b400049c3b604e8f9de9e4f6958d.
Alternate-index/commit-tree checkpoint only: implementation branch/index remain
unchanged at77cf9376e921a7c79f5b844fdc99639e20f44fca; no branch commit or push.

Frozen build guardrails, NeoForge build/test/packaged-JAR PASS2m50s,
`/tmp/pm-quarry-r81-frozen-build.log`.689native tests0fail/1existing skip.
Affected Frontier1258 integration cases had11 stale fixture expectations;
all five affected suites corrected and66cases0fail passed afterward. The1247
successful unaffected cases are reused, not claimed as a fresh full rerun.
16native field-turns GameTests passed. Exact logs/counts in source receipt.

Runtime `/home/rd/far-frontier-server`, `far-frontier-v3-live.service`;
fresh world `frontier-v3-quarry-r81-20261010`, seed20260918065,
profile `frontier-v3-quarry-graybox-r1`, Java22, heap4–12GiB, view8/simulation6.
Start1791575831, invocation4dc97e1a16784632bf90d5fdee28c43d,
wrapper1187695. Preflight and post-start deploy-verify PASS, actual port25565.
R80 saved/flushed and stopped normally; old worlds retained, none deleted.
Root publication/install/profile scripts installed the SHA-pinned candidate
and graybox datapack before genesis. Client normal updater required.

Pale Mirror SHA512:
e4c440ad400d1bf7ea30ec904fefcccca4f70fe91df84dee0a7f696455a46aaccf1a48360c706d382935a67a8cf8a21d3ffebd078ccc14791a3402373381116d.
Visuals unchanged SHA512:
2fd468cb382d64fa62eaa2cb2239d7bd75c89a4a06c21ce1bf2a15f0775db02e5daf2591b91863b74d4dc87a90067ef714dd631e4174daf79c9caad78f994601.

## Connected evidence and limits

Native run8: actual HOT mining, exact tool/cargo/body/source departure to COLD,
separate courier accepted64cobblestone at home. Native10 retained world passed
graceful restart; its original delivery wait timed out during normal meals.
The checked-in existing-world follow-up passed home delivery at40020 and
returned to observe AIR at the original extracted cell. The joined history
does not retrospectively turn run10 into a green terminal aggregate.

Pure COLD continuation from its real final saved snapshot101895/instant40988
completed ordinary imported stone at46987: seller settlement11 -> buyer10,
50units, exact shipment receipt, payment/title transfer, original mined lot
`lot:extraction-work-1`. Source build's
`build/quarry-produced-stock-continuation.{jsh,log}` retain evidence. No synthetic
stock/outcome and no native-world mutation. This is not native stone-caravan
video or proof that every importer already completed its64-unit reserve.

Live full-pack exploratory inspection: run5dcf0cd0-d3ca-41bd-a6b6-ad101b3ae8a8,
`build/quarry-live-fullpack-inspection-2.{json,pmv3.jsonl}` in implementation
Gradle root, statusok. Frozen source ran against the actual R81 server with
updated full-pack client source `/home/rd/f06r3-r9-fullpack-client`,20FPS.
Reviewed frame in frozen Gradle root's `build/frontier-v3-scenarios/` shows
actual rendered pit/stone/shelter/chest, not transparent chunks. Initial
COLD extracted5 became HOT extracted9; two exact workers HOT carrying5/4.
Summary revision2141/instant2428 green:12settlements366residents, required/
scene/inventory/replica diagnostics0, incidentIndexSize0. Client exited.

First full-pack launch failed the NeoForge early-loader-window handoff before
connecting. Task-owned client config now explicitly disables that loader window,
matching the existing lite-client configuration; no game/render/simulation change.
Second launch connected/passed. Connection reset after completed scenario was
the runner closing its client, not a server crash. Optional third-party asset
warnings remain outside quarry scope. No TPS/speedup claim follows from this visit.

This visit is bounded exploratory full-pack evidence, NOT formal whole-feature
HUMAN_CANDIDATE/M3 acceptance. The early quarry frame partly occludes workers;
continuous mining/body identity is supported by native traces, not that frame
alone. Source/geometry interventions and split-effect recovery have focused
coverage, not an exhaustive filmed player/crash campaign. Unobserved abrupt
custody stays fenced until positive natural observation. No tool manufacture/
wear, ores, tunnels or construction sink promised.

## Hand-test coordinates

All in `pale_mirror:frontier_graybox`, safe ground-level ramp entrances:
Northwatch `-317 64 -344`; Stonefield `-77 64 -344`;
Redwillow `403 64 -344`; Hearthvale `-77 64 -4`;
Ironmeadow `403 64 -4`; Westhaven `163 64 336`.
Storage/work floor is three blocks lower.
Each site starts with256 finite source blocks/two picks. Other six settlements
have no quarry; bounded funded imports use the existing caravans/trade.

Pack repo HEAD4d514997 unchanged, unrelated??.f0v-baseline only; hosted artifacts
ignored. Original nested Pale Mirror checkout's23WIP paths untouched.
Implementation~271WIP paths preserved, private release clean; governance mixed
historical WIP preserved. No blanket staging/reset/repository migration.
