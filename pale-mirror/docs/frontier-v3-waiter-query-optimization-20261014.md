# Waiting-work query optimization

User-authorized2026-10-14: optimize the confirmed R134 delay, repair additional
profile-supported costs, commit and push. Main alone. No persistence/schema,
ruleset, price, movement-speed or actor/physical-custody policy change.

## Confirmed cause

Commit `bd2c91b85` (2026-10-11) changed the before-plan order guard from the
runnable index to the full canonical ordered queue. This corrected missed
derived wakes, but repeated expensive business eligibility for every parked
predecessor before each admitted transaction. Action/weight budgets do not
charge this read-only guard work.

Saved R134 snapshot2173992/tick537908 has393 residents,461 resource lots,
31 accounts,46 containers,3 missions and400 due schedules. Selection parks389.
A single before-plan head check visits357 resident reviews plus other parked
families.327 resident reviews wait for food stock and29 retain a meal.
The old JAR SHA512 is
`de89f7a5b0c406a88b80bab513757a9dfa90730d08432bd78bf8b4453ee4bcbc7582a2ba43b2d85f45204c2ba8f2e48250eaa99b2c1bd431dc3aabb2b1168247`.
The old30s JFR corroborates repeated food/lot/inventory/assignment/dependency
calculation, not a route-planning or disk queue in that sample.

## Implemented boundaries

- Keep the full canonical-order guard and missed-wake reconciliation. No parked
  task is assumed permanently ineligible from an approximate index.
- The resident activity owner memoizes only canonical FOOD_STOCK and
  RETAINED_MEAL refusals for the exact immutable world image and exact action.
  A new state or a different deadline cannot inherit the refusal. External
  route-calculation holds remain checked by the catalogue on every call;
  clearance/ready/movement outcomes are deliberately not memoized.
- Wake addresses are reused only within that exact image. The existing causal
  wake protocol and periodic safety audit remain unchanged.
- `FungibleResourceReadIndex` is a disposable ledger query: container lookup and
  lazy per-account owner/commodity availability. Lots, claims and accounts are
  all identity-bound inputs; a title/reservation/custody change invalidates it.
  Bindings alone do not alter owned quantities. Balances are not precomputed
  across every account for a one-account query and are never persisted or used
  instead of the allocation/physical-receipt validator.
- Storage declarations are indexed by their declared owner, then filtered by
  the same role/item policy. Live/unresolved physical custody and access are
  still evaluated by their existing owners, not cached as eligibility.
- Expedition supply owns its per-actor slot-reservation index, invalidated by
  the exact immutable mission map. Common inventory never inspects missions.
- One `UnitInventoryPresentation.View` supplies both current presentation and
  occupied capacity to food selection. Static slot declarations are immutable
  shared values, not regenerated for every query.
- Assignment projection has an exact-current-world fast path before its
  existing dependency-identity cache. Another world/state still checks those
  dependencies and validates competing claims through the existing compiler.

Indices retain one image per owner thread; resident query maps are capped4096.
They own no stock, actor assignment, event, replay or permission. The linear
cheap order walk, whole-state refusal invalidation and broad existing depot
wakes remain; more granular indexing requires evidence, not a speculative new
scheduler. This is not a claim that all historical tail stalls are closed.

## Verification / measurement

Existing kernel missed-wake/order/WAL/recovery tests remain unchanged. Existing
resident/ledger cases now explicitly check repeated empty-stock assessment,
different deadlines, replenishment without a wake, revisiting old immutable
images, claim transfer, foreign owner/commodity and title-change availability.
No new client campaign or test-infrastructure framework.

Read-only comparison uses the same old snapshot and old JAR, with only the
source-compatible changed query classes overlaid in a separate offline JVM.
It does not rewrite the world or fake native acceptance. Both versions admit4,
park389 and return identical resident refusal counts. After the first cold/JIT
pass, old head checks11.02/12.79ms versus1.96/2.09ms after; old selection13.08/
10.71ms versus3.17/3.57ms after. This small local probe establishes removed
repeated query cost, not whole-server TPS or a statistically controlled speedup.
It excludes the new expedition slot index and final assignment/static-slot fast
paths, which are verified separately.

Local evidence in the implementation `build/`:

- `market-r134-predeploy.jfr`, prior bounded caller/pauses summaries;
- `OldRuntimeDelayProbe.java`, `market-r134-offline-delay-probe.log`;
- `r134-optimization-overlay/`, `queued-waiter-r134-after-probe.log`;
- `queued-waiter-optimization-focused.log` (first focused gate PASS2m53s);
- `queued-waiter-optimization-final.log`:64 affected Frontier tests,726 NeoForge
  tests (one unchanged skip), guardrails and packaged JAR PASS8m55s;
- `queued-waiter-final-read-view-check.log`:15 assignment/inventory/architecture
  cases plus guardrails/package PASS15s for the final immutable fast paths.

Source commit `ce029d50eadb3fad03981ebc0028419430579874` is pushed to
`origin/feat/baker-carry-orders-20260926`, including preceding market commit654f0ee4.
Clean detached release `/home/rd/proj/pm-release-waiter-queries.A7A3Er/source/pale-mirror`
passed guardrails/jar/package in12s. Source tree
`7b2595d1fad2adcceec5a7b6094c6d3636f62e9f`; SHA512:
`043c01f17845393c263112c27dba22a389d05fcd02a9154464a9bf24526cf262843984aa36a8866857ee5d3161720120a41611a7d2d26de3a842ee63b06cad4e`.
Delivered as R136 under the standing verified-delivery policy, using the
same R135 world/schema/ruleset, not a reset. Successful `save-all flush`, ordinary
RCON stop, all-dimensions-saved and closed25565/25575 barriers preceded install.
Fresh-start verifier PASS. Start epoch1791924801, service
`far-frontier-v3-live.service`, invocation `33cb330dd72248f4a661fabdbef70497`,
wrapper2069005/JVM2069029, game25565/RCON25575. At42847 the retained-conflict
verdict is green:383 residents, zero required/inventory/scene conflicts;
ready lag0. No new quarantine or world recreation. Recent64 observed host
ticks average18.13ms/max175.15ms. This short varying-workload startup sample
is not a comparable speedup measurement; route/ordinary-work CPU and tail
spikes remain outside the proved query-cost improvement. Evidence:
`queued-waiter-r136-{preflight,install,deploy-verify}.log`,
`queued-waiter-r136-startup-diagnostics.log`, `queued-waiter-r135-stop.log`.
The eight unrelated baseline Frontier
failures are not relabelled green or repaired by this scope.
