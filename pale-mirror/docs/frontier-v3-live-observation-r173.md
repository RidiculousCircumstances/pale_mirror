# R173 — one-hour HOT/COLD observation

## Scope and identity

User requested an actual hour of periodic COLD/HOT observation, with repair and
deployment of discovered defects. Window: 2026-10-16 10:21:41–11:21:41 UTC.
World retained: `frontier-v3-reserves-r172-20261016`, ruleset r14/schema290.
Minute samples and bounded native receipts live in implementation
`build/r173-observation/`. This is exploratory live evidence, not complete
feature acceptance or a HUMAN_CANDIDATE promotion.

## Confirmed findings and corrections

1. **Economy diagnostic overflow.** `process settlement:6` returned only
   `response_limit` because optional order/history detail displaced stocks and
   reserve calculations. Commit `22b01ebc` reuses the common diagnostic byte
   budget; essential stock/planning facts remain, omitted sections are explicit.
   Same-world deployment returned `ok`, four reserve entries, 5185 bytes and
   `omittedSections:["orders"]`. No economy semantics changed.
2. **Unnecessary physical slot-index invalidation.** JFR Server-thread samples
   included `ContainerStockIndex.slots` in 1114/7805 samples. `withEconomics` and
   `withFungibleResources` reconstructed the exact-equipment slot index despite
   unchanged exact items. Commit `c68ece88` retains that immutable input. Actual
   stock/binding changes still invalidate their own inputs; constructor and
   capacity validation remain. This removes evidenced duplicate work; the sample
   proportion is not a promised TPS gain.
3. **Calculation lifetime gap at schedule wake.** The server admitted a bound
   next-tick wake, then immediately removed the continuation's calculation
   dependencies. The following navigation turn could cancel unfinished sibling
   searches before the consumer resumed. A ready naturally due action had the
   same premature retirement. Commit `ff41730c` transfers the retained demands
   to the exact runnable generation. Runnable consumers are not parked or
   notified twice; the owner’s subsequent review replaces/retires dependencies,
   and cancellation/stale generation releases them. No route, arrival or domain
   outcome is fabricated. Shared future consumers retain the same protection.

## Checks and observations so far

- Focused diagnostic/performance, stock/inventory/trip and planning tests pass;
  planning coverage includes exact-generation rejection, cancellation, shared
  demand, notification pressure and recovered group provisioning.
- HOT01 invalid scenario: airborne camera fell from Y69 to64; fixed the scenario
  to the actual graybox ground. Not classified as a simulation failure.
- HOT02 visited Hearthvale/Clearwater for120seconds each and exited; terminal
  summary had zero required/scene/inventory conflicts. Real frame shows the
  field and residents rendered. Night camera partially obstructs the field;
  this is not a complete work-cycle visual proof.
- One roughly2second ingress lag warning occurred. Subsequent COLD also spent
  about28–34ms in sampled host ticks, with economic review load. Route waits
  persisted while cheap estimates completed; the lifetime defect above is a
  confirmed code contradiction, but its whole live effect remains to measure.
- Early snapshot at14320ticks: six extractive sites, coal extracted at several
  sites (12–13cells), no invented starting coal. Trade completion not yet proved.
- First feeding wave started around22349ticks: hunger accompanied by active
  meal tasks and bread consumption. A hungry count alone is not a stuck-unit
  finding; retain follow-up progress observations.

## Mid-window follow-up

R173c deployed same world at10:43UTC, checksum
`392bafacf2f0c4abc3b6ada6ea7011cac9a8d208e5d8a552914b5e2e2f92e0d268865ecfbc36deb341a427ec9a86452ddde8a6958615662032c9d2813b64f5a1`.
Clean release fullNeoForge742cases:741pass/1skip; guardrails/build/packagePASS.
Preflight/poststartverifyPASS, restart1792147392, invocation
`58861f21fdcc470db8c7cd778611dca6`, unitPID3520929.

- Feeding wave reached200hungry, then zero at36049ticks without resetting the
  world or forcing outcomes. Bread consumption and meal completions observed.
- Accepted coal trades and returning missions observed; later shipments also
  carried wheat. Repeated purchases continue; natural minute sampling is not a
  full counterfactual proof of supply-chain resilience.
- HOT03 exposed one farmer stopped near the last mature crop with PATH_STALLED;
  after unloading it finished that cell and delivered. The unresolved physical
  reason is tracked rather than classifying COLD completion as a HOT fix.
- HOT04 long observation entered a later naturally admitted work batch; at51348
  ticks the same farmer processed22cells with no navigation block. No artificial
  inventory removal or fast-forward was performed to produce that result.
- Samples afterR173c often9–16ms, but workload changed and old queues also drained
  before deploy. Do not present this as a controlled measured speedup.

## Late HOT follow-up

HOT04 completed normally. HOT05 re-entered Hearthvale at54839ticks; at59324
the later job `job:site-harvest-6-wheat-field-5` had a confirmed physical
delivery receipt and the field was GROWING again. The exact worker
`resident:6-13` remained alive, nourished and physically indexed with the same
entity UUID. This is a successful later batch, not proof of the earlier
PATH_STALLED cause or a continuously observed full-cycle promotion.

Second feeding wave fell from20hungry/20meals at58424ticks to6/6 at60772;
starving remained zero. At62855ticks Clearwater had sold50wheat to settlement8;
Hearthvale had received9wheat from settlement5. These are accepted contracts,
not merely orders. Participant `completedSales` includes either side of a
transaction; it must not be interpreted as imports without checking the parties.
Hearthvale also had an OUTBOUND provisioned coal shipment. Tiny coal loads
remain consistent with the current low consumption and max-wait policy;
this observation does not establish that transport economics are well balanced.

At64352ticks the second feeding wave also reached zero hungry across all12
settlements. Later hunger is a new wave, not evidence of the previous meals
being stuck. Snapshot66474 retained12/21/19 extracted coal cells in the three
coal-bearing sites, versus about12–13 early: extraction continued as demand
arose. No active extraction at that snapshot is consistent with satisfied
targets. Cobblestone has no active consuming recipe and therefore target zero;
this policy deliberately does not fill warehouses with unusable surplus.

## Completed window

Sampler finished2026-10-16T11:21:41.391Z:60scheduled minute samples,59successful,
one connection gap at10:27:41 during the planned deployment restart. All59
successful samples reported zero required/scene/inventory conflicts. No server
crash or quarantine observed. HOT05 completed11:19:45, run
9ce54a58-5f5f-4d00-ae53-dea779eebb6d. Task client Java processes exited; last
minute sample is COLD (zero managed actors). Server left running, world retained.

Post-R173c samples from10:44UTC:6COLD and31HOT samples. Median of sampled
recent host-tick means:9.58ms COLD and9.79ms HOT; largest sampled tick110.78ms
and139.06ms respectively. These are short sampling windows, not exhaustive
hour-wide TPS percentiles or matched-workload speedup measurements. Oldest
ready queue lag stayedzero. Final minute71456:8hungry with8active meals, no
starving residents, one navigation wait,4.64ms recent mean. Two prior feeding
waves had cleared completely; final wave not falsely classified as stuck.
Final read at73740ticks: navigationwaiting0, ready0, lag0, conflicts0. The
cumulative host-tick maximum since this runtime started is1106.98ms, larger
than the sampled-window maxima. Its exact timestamp/cause is not established
by this aggregate: do not claim spikes were eliminated or globally bounded139ms.

Source is committed throughbf616928; deployed runtime isff41730c. No push.
Pack tracked files unchanged; preexisting .f0v-baseline/ preserved. Governance
unrelated WIP preserved. Existing broad-suite debt and the earlier HOT farmer
PATH_STALLED reason remain explicitly open. No speculative fix was applied to
the latter merely because a later batch succeeded.
