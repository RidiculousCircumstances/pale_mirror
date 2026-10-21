# Continuity Ledger

## Goal / authority
R213 delivered 2026-10-22: storage points1–4 and common Minecraft operation
integration, scoped recovery verification, commits and fresh-world deployment.
Main alone; no subagents. No push. No further implementation requested yet.
Plan and detailed final receipt: docs/frontier-v3-storage-operation-integration.md.
Earlier component receipts: docs/archive/continuity-r213-before-field-causal-closure-20261022.md
and its linked archives. These are history, not active orders.

## Source and running server
Implementation /home/rd/proj/pm-f06r3-facility-lane-recovery/pale-mirror,
branch feat/baker-carry-orders-20260926; git root parent, tracked prefix pale-mirror/.
HEAD2e185ec054af7392becbc3eb12c120001efa74c7, clean.
Final corrections4f75ec6c integration,6b0f117b async migration admission,
2e185ec0 C2ME-compatible bounded shutdown. Preserve all histories.
Clean detached release /home/rd/proj/pm-r213-release.p58eDM/source/pale-mirror,
same2e185ec0. Pack /home/rd/proj/minecraft tracked clean;
preexisting untracked .f0v-baseline/ preserved. Governance has unrelated WIP;
do not stage it wholesale. R213 plan/ledger are task-owned documentation.

Live /home/rd/far-frontier-server, far-frontier-v3-live.service,
wrapperPID2407203, port25565, startEpoch1792618697.
World frontier-v3-r213-storage-20261022, seed125, r22, Java22,
C2ME combined8 workers/8 loads, heap4–12GiB. Old R204 world not deleted/reused.
Installed/hosted JAR SHA512:
ea400673ae100c60cec26e41bacfb99176563f514d80c77343f9e897fc1f199c8f76542adc768151c57799557d0ec80fc20fed11a7d7dccd1ef4b06ff3e3a79f.
Final deploy-verify PASS: build/r213-live-final-verify.log.
Final read-only state: build/r213-live-final-state.json, revision5797/instant3773,
green,389 residents,0 required/inventory/scene/replica-custody conflicts,
0 pending native operations/participants/owner obligations,0 ready schedule lag.
Test clients and task Xvfb117 stopped; no live background test or fast-forward.

## Delivered boundaries
- Existing WAL/shared storage retained. Live BLOCKS lazy hydration, bounded
  64-section LRU/min8 and512 decoded rows; local append/pressure/index work.
  Explicit exports/recovery audits may scan; ordinary get/attach/write do not.
- Common exact native envelope: declared owner/kind/participants, durable
  prepare, single attempt, co-saved native evidence, independent canonical/native
  durability, positive unused cancellation and obligation-aware compact fences.
  Historical APPLIED is not current native proof; ambiguous PREPARED is not
  permission to replay. No global cross-file ACID claim or per-frame transaction.
- Active farming, mining, bakery/stations, nutrition, storage/ground, hauling,
  caravans and body lifecycle use common ports. Ordinary player/container/hopper
  mutations and resource drops participate. External terrain observations remain
  read-only causal facts, never fabricated APPLIED or native execution permission.
- Business aftermath stays in registered exact owners. Explicit-owner outbox
  carries bounded pre-loot/context and conditional children; parent cannot retire
  before child/custody/owner closure. Applied outcomes settle existing work,
  unused preserve source, death never starts new work. Independent farmers survive.
- Container destruction wraps actual LevelChunk palette change before onRemove.
  Common outbox precedes reconstructable family index; receipts/resource
  disposition/custody retirement precede owner acknowledgement. Partial native
  drop refusal preserves source remainder and explicit ambiguity. Schema10,
  fresh worlds only. Resource indexes are derived, never another authority.
- Source review disproved suspected DRAINING revision bug; no speculative fix.
  Attachment retirement and shipment ground-pickup already had exact closure.
  Unexplained mining-tool loss remains explicit unresolved evidence, not free
  replacement. Inactive medical/combat/hive not activated or expanded.

## Acceptance and corrections
- Full final sweep build/r213-release-final.log: frontier1772/1failed;
  NeoForge796/0failed/1preexisting skip; native395/395 PASS. Domain324 and
  Visuals79 unchanged accepted reports. Do NOT call the failed aggregate green.
  The one failure was a new negative: migration used synchronous path and
  bypassed a bound async planner. plannedPath correction6b0f passed migration12
  +architecture7/guardrails/package in clean detached24s. Unaffected gate evidence
  reused deliberately, not another full/native matrix.
- Initial full failure also exposed unsupported constant-Y migration and
  engineering staging above absent ground; both now use common actor-profile
  navigation/ground. Test adapters updated for typed labour/death/command-only
  semantics. Full frontier heap2GiB avoids evidenced512MiB ArchUnit OOM.
- First deployment6b0f failed at startup: C2ME and PM both redirected Queue.size
  in processUnloads. Actual MC source and installed C2ME bytecode show MAX_VALUE
  sentinel. 2e185ec0 snapshots real size once on first poll; normal running
  budget unchanged, no competing redirect. Focused unload/guardrails PASS12s,
  final detached packaging PASS6s. Full-pack startup and actual vanilla stop pass.
- Native player64 withdrawal -> physical save -> process kill before canonical
  -> restart -> ordinary return64 PASS ca949280-78ed-4fa4-9078-12d6ea4eade1,
  build/r213-player-recovery-acceptance.json. Actual playerNBT64 and exact final
  GUI reviewed, no duplication. Specific saved-before-canonical cut, not arbitrary
  mixed-file atomicity. Earlier final.json oracle failure is not passing evidence.
- Full-pack player visits7->8->7, opens actual storage then exits: PASS
  71dc844e-1d2c-4b27-8b28-4440e04a7ef1, build/r213-live-storage-boundary.json.
  Same flow after save/restart PASS d9091f57-d1b9-49bf-83a6-61e0ef61b71f,
  build/r213-live-storage-after-restart.json. All6 GUI frames reviewed, stock
  retained/empty player inventory. Private display, not manual human M3 acceptance.
- First restart used explicit save-all flush then service termination (not
  proven vanilla graceful shutdown). Subsequent RCON stop completed all-dimension
  saves/C2ME shutdown, followed by final verified start. Retained stop receipt:
  build/r213-live-graceful-stop.log. Actual-source/runtime proof, no synthetic save.
- Live before restart:126 declared block sections/64 resident(limit64),160evictions;
  operation pending0/awaitingNative0/awaitingOwner0,retiredRanges1,journal queues0.
  After restart before visiting:126 declared/0resident/0reads, proving lazy open.
  Receipts:r213-live-before-restart.json,r213-live-after-restart.json.

## Limits / follow-up context
Initial new-world visits took19.3s/11.1s including chunk/render readiness;
post-restart visits1.4s/0.1s. Initial host peaks~3s remain observed, NOT a claim
that every chunk-latency problem is fixed. Quiet sampled COLD PM turns~2ms average
over64 turns, no ready backlog. No new long economic/scale run or speedup multiplier.
No additional acceptance rerun needed for unchanged dependencies.

Build Java21 /home/rd/.gradle/jdks/eclipse_adoptium-21-amd64-linux.2;
liveJava22 /home/rd/.local/share/far-frontier/java/temurin-22.0.2+9/bin/java.
Client pack /home/rd/proj/pm-r115-client-pack.OWzKjN refreshed from current pack;
pilot uses current compiled PM, installed Visuals checksum matched host.
Retained skills: release+matrix, serverops+operations-map, client-investigation,
engine-source; protocol/execution semantics. ModLens snapshot31d2b1d1 is dependency
evidence, not current source. No tool/skill mandates another proof campaign.
