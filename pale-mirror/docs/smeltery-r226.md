# R226 — autonomous smelting and a local metal warehouse

## Accepted scope

Separate graybox smeltery building and nearby storage preferring ore/ingots.
Raw iron + fuel -> iron ingots; no Create machines, smithing or invented ingot
consumption in this cut. Use actual stock, existing extraction and shared
shipment/UAE/navigation/custody. Workers load and collect; the automatic station
owns processing, without retaining a resident for its duration.

## Implementation boundaries

- Recipe/catalog owns ingredient, product, heat and processing mode. Equipment
  declares capabilities; the installed station owns inventory and one operation.
- Add generic settlement-directed automatic-station admission, distinct from a
  bakery job and a player's explicit operation. No fake bakery/player owner.
- Extend existing station supply through input and output ports, using existing
  internal shipments. Warehouse selection remains filter/priority/capacity-based;
  breaking the preferred chest must not bind production to a dead container.
- Graybox composition declares building, access, equipment and storage once;
  generation, movement and recovery consume the same declaration.
- HOT effects and COLD processing use the existing station clock and exact
  inventory receipt protocol, never simultaneous vanilla/simulation smelting.
- New immutable ruleset; disposable fresh world is authorized. Initial ingot
  reserve accepted by the user:64 ingots per settlement; no free ore/ingots.

## Acceptance

Recipe/energy/stock accounting and explicit initiator roundtrip; partial input,
occupied output, missing fuel, player withdrawal, destroyed/replaced storage and
machine; autonomous processing while workers do other work; HOT/COLD and restart
retain one operation/output. Check real building/warehouse and load-process-unload
with the existing full-pack client. Commit and deploy; no automatic push.

## Source findings

Shared station clock already supports AUTOMATIC; existing task admission is
bakery-specific. Station supply only carries fuel, and production ports reject
OUTPUT even as pickup endpoints. These boundaries must be extended rather than
duplicating the bakery executor or adding a separate hauling runtime.

## Implementation and current evidence

Immutable r24 adds automatic recipe/policy, institution initiator, shared
reserve/supply/stock adapters and SMELTING equipment. Three-slot PM smelter uses
the existing machine clock, heat, physical effect receipts and recovery; ordinary
internal shipments serve input/output. A separate 7x7 building and nearby
priority-100 ore/ingot/coal warehouse are added at each settlement. Reserve64,
1 raw iron ->1 ingot,200 processing ticks and200 heat per ingot, at most8 per
operation. No startup iron, fictional consumption or vanilla furnace clock.

Focused domain tests cover partial batches, actual heat, reserve stop/resume,
mining demand, codec recovery and real common internal-shipment output pickup/
delivery. Affected NeoForge v3 tests and guardrails passed. Native production
effect slice passed5/5. Broad NeoForge invocation was stopped in unchanged
reference-world full-year presentation test; it is NOT a full-suite pass.
Local receipts: build/r226-affected-tests.log, r226-native-production.log and
r226-package.log. Latest focused automatic suite13/13, NeoForge v3 slice744/744;
unchanged adjacent evidence was reused, not counted as a new complete suite.

## Live deployment and physical receipts

Source e7f25032; clean detached `/home/rd/proj/pm-r226-release/pale-mirror`.
Preflight, packaged JAR and post-start verification PASS. Live world
`frontier-v3-r226-smeltery-20261022`, seed126/r24. Previous R225 world retained;
both transitions used ordinary `save-all flush` / `stop`, all dimensions saved.
Artifact SHA512:
`5beda4ff409c72d3b92b7f4e8ad1c962293fa103787504edc3088e5244d5bf96c8500a5ee101b1102b053e1416a3562b6493e3b8b5218638d12ff5cce48f237e`.
Final restart1792692799, wrapperPID1013179. Pack tracked files unchanged.

Full-pack pilot20FPS/private Xvfb, ordinary inventory packets:

- `live-smeltery-supply`, run b7f12001-757e-4d6d-8eb2-5b1061bb7a9a,
  build/r226-live-smeltery2.json PASS. Player puts3 raw iron +1 coal into the
  nearby warehouse; common haulers load the station; settlement starts automatic
  processing without a player start click or resident attendant. Actual UI shows
  processing. The machine spends600 of1600 heat, and common output hauling
  delivers exactly3 ingots to the preferred warehouse; output clears.
- Graceful restart then `live-smeltery-recovery`, run
  e6a54626-e206-41c5-9bd5-df73f837e2f0, build/r226-live-recovery.json PASS.
  Same3 physical ingots in ordinary chest UI, retained1000 heat, no operation or
  duplicated machine output. Required/scene/inventory conflicts and incidents0.
  Building and machine/chest frames inspected. Client/display stopped afterward.

First supply attempt used an invalid player arrival Y above the ground; it was
stopped and the scenario corrected to actual feet height. No product fallback
or timeout relaxation. Start-up had a2.965s keep-up warning; this cut does not
claim a general performance improvement or long-run economic balance. Native
destructive replacement mid-smelt was not separately exercised; shared effect
and recovery gates remain the supporting evidence, not a claimed human pass.

Clearwater machine111/65/-18, nearby warehouse107/65/-23. Clients need the new
hosted mod because `pale_mirror:smelter` is a new registered block.

## Follow-up: independent provisions and planning cost

R226 runtime exposed a cargo/provision coupling: a valid mineral warehouse
rejects bread, yet the expedition previously required provisions from its cargo
endpoint. Provisioning now selects actual unclaimed stock through the common
warehouse owner/filter/custody rules and the actor's shared route/approach policy.
Each allocation retains its exact typed source endpoint and account. Navigation,
service access, HOT receipt/closure, COLD transfer, source-change recovery, death
and wake dependencies all use that retained address. Cargo destination and trade
settlement are unchanged. Replacement selection is explicit; replay cannot
rediscover another source. Schema300 rejects old saves; use a fresh test world.

HOT and COLD profiles independently identified repeated economic assessment
during worker preference as the principal sampled server CPU cost: HOT1548/2009
execution samples, COLD1564/2001. Speculative per-resident task activation created
a new whole-state identity before demand evaluation, defeating shared immutable
assessment views. Demand now reads the actual common input; only provisional
navigation sees the candidate job/task activation. Immature/empty field work
does not query market demand. Trip review rejects stock/funding/consent-infeasible
endpoint pairs before requesting regional route estimates. No priority bypass,
stale-cache tolerance, invented resource or asynchronous-wait-as-failure added.

Same retained R226 snapshot730869, identical twelve-settlement logistics-worker
selection: warm passes352–534ms before,18–24ms after (first pass1766/1137ms).
These are narrow offline measurements, not overall TPS or login latency. Original
world/profile retained; a fresh world must not be cited as the performance fix.
Focused separate-source replanning verifies retained pickup through codec recovery,
rejects arrival at the cargo port and source substitution, and conserves stock.
Existing expedition/trade/harvest/cultivation checks and713 NeoForge v3 checks
pass; the native economy/physical-resource slice passes53/53. Guardrails and
packaged-JAR verification pass. This is affected-path verification, not a new
complete repository test-suite pass.
## R227 deployment and bounded live verification

Implementation ba0abc0f deployed from clean detached `pm-r227-release`.
World `frontier-v3-r227-provisions-20261023`, seed126/r24/schema300. R226 world
and snapshot730869 retained for diagnosis; ordinary saved shutdown before reset.
Artifact SHA512:
`1c16735b118732d70b5eed400cb7f8c8b7a404c45d405306db3803abececfbcc7d0c8e4499e897c4a8fd74cf2388da77db18b3ace2279ff62725351affb4f39c`.
Clean-ref guardrails/JAR/package, pack validation and post-start identity passed.

Full-pack native pilot,20FPS/private Xvfb, no injected resources or money:

- `live-settlement-loading`, run a820455f-8cf3-436a-bf29-18d819821e61:
  first Clearwater target1.199s, repeat0.299s. Spectator target/packet readiness,
  NOT HOT actors or full-scene rendering. Immediate frames still had mesh holes.
- `live-settlement-ingress-render`, run
  59341309-834c-4be4-8002-04d23f993d38: actual HOT Clearwater/Ironmeadow/Southgate
  visits0.198/1.100/0.600s; three inspected frames after5000ms client wait show
  ground/roads/buildings without holes in those views. Not every scene/process
  or a mature-world latency guarantee. Client disconnected/stopped afterward.
- Relative6000-tick advance from2782 completed exactly at8782, no failed target.
  At9200 no pending fast-forward, route queue0, ready-event lag0; recent PM turn
  mean7.67ms, max308.39ms over64 samples. Startup global maximum1.476s remains;
  no `Can't keep up` or quarantine during these checks. This is PM turn time,
  not a claimed whole-server TPS measurement or absence of all spikes.

At snapshot11402 all347 residents were nourished, but no admitted trade contract
or completed live shipment had yet appeared. Separate-store provision selection,
actual COLD withdrawal/stock conservation and codec recovery are covered by the
focused domain test; full native separate-store commercial delivery is NOT
claimed. Fresh-world health does not establish long-run economic solvency or
explain away R226 fuel/cash starvation. Receipts: build/r227-live-loading.json,
r227-live-hot.json, r227-performance-cold-completed.json, r227-economy-final.txt;
profiles/r227-login-hot.jfr. Old/new profiles have different world ages; the
controlled same-snapshot comparison above is the performance regression evidence.

Ordinary same-world save/stop/restart also succeeded: restart1792730060,
wrapperPID2257302, exact artifact verification PASS. At11793 all retained required,
scene, inventory and replica-custody conflicts0; no incident. At11790 pending
fast-forward0, route waiting0 and ready-event lag0. Recovery has a distinct2.35s
startup keep-up warning (PM maximum2.338s); recent64 PM turns averaged11.08ms,
maximum87.50ms. Therefore this release does NOT claim elimination of startup
peaks. `build/r227-performance-recovery.json` retains the measurement. The first
verification attempt preceded the listening socket and was retried after the
ordinary ready record; it was not a runtime crash or silent verifier bypass.
