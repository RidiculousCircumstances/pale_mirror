# Recurring food queue and bounded native navigation — 2026-11-04

Source `f7f012f56` implements the common navigation correction;
`2a98b0f26` adds change-only explanations for refused ambient actuation and
ambiguous meal navigation. Final implementation/release are clean at
`2a98b0f269a312468929feac75dc33faee8b971b`.

## Diagnosis

At instant71950 the server had no queued ordinary ticks, fast-forward or active
route searches. Recent64 ticks averaged6.63ms/max26.58ms. Thus the reported
resident stall was not established as global TPS loss.

Settlement1 had105bread,101usable. Leon Moss1-1, Leon Lake1-4, Nora Stone1-18
and Nora Bell1-20 retained MOVE meals, waited for access, and were stationary
near the warehouse. The initial longest sampled meal overdue was3432ticks.
Farmer Ada Bell1-7 stood on an owned access surface at(-1466,65,-1345), while its
current work cell was near x-1415…-1401. Its work target repeatedly changed,
but native path queries were unsuccessful. Owen Ash1-3 also retained a delivery
from the forge with an incomplete physical route and incompatible retained
COLD departure knowledge. Evidence is in IMPL `build/meal-stall-20261104/`.

The shared native path boundary accepts at most54nodes. A final target beyond
that query's reach was being treated as a local no-path result even when the
native candidate proved a useful loaded approach. The traffic wrapper could
also replace an incomplete vanilla candidate with null from its bounded
fallback. Without an available retained COLD hint, the actor repeatedly retried
the distant final target. A farmer standing in the service footprint could
therefore hold up meals while unable to start its own departure.

The original exact jam changed before installation: the human player died,
its native entity column became inactive, and COLD progressed some work/food.
The player remained in PlayerList with Health0. Online-player coordinates alone
were therefore not evidence of an active physical observer. No human health,
mode, respawn or inventory was changed. Do not claim that new code alone freed
that original unchanged physical incarnation.

## Common correction

The failed complete query now retains a separate advisory candidate. The common
native boundary may extract at most12edges only when the target distance or
candidate length exceeds the existing54node bound. The prefix must make strict
progress and pass loaded/dry scope, body profile and collision checks.

The same route follower uses the prefix endpoint as an intermediate station,
then continues the unchanged original order. The same native navigator rechecks
traffic and collision before moving; only actual arrival at the original legal
station can complete the task. No search bound was raised, no actor teleported,
no job/food source replaced, and no physical detour published as COLD knowledge.
Short blocked stations retain their normal alternative/courtesy behavior.
The generic body/operation/custody protocols remain unchanged.

## Verification and observed results

- `/tmp/pm-native-frontier-final.log`: guardrails,2 route adapter tests,
  **45 native local-navigation tests**, JAR/package verification, PASS39s.
- `/tmp/pm-native-frontier-physics.log`: **10 native physics tests**, PASS.
- A new test-only80×4×8 structure gives the real pathfinder a distant blocked
  target. The actor approaches through a safe prefix without completing beyond
  a closed wall; opening the wall lets the same original order finish. All
  fixture writes fit the actual template. Its resource is not a gameplay asset.
- The first broad run used the wrong slice property and exposed two regressions
  in the initial overly broad approach: short exit alternatives and an ambient
  landing assertion. Restricting prefixes to distant/bounded-out cases restored
  both existing tests. Final local-navigation and physics slices passed; the
  failed broad run is not reported as green.
- Detached release gates, root preflight/publish/install and exact runtime
  verification passed. No world reset or discarded resource/operation fence.

Ordinary full-pack client `live_meal_queue_after_distant_approach` observed
Leon Moss move through MOVE→TAKE→CLEAR_ACCESS→NOURISHED/NONE at99369, clearing
the retained meal which was overdue17229ticks before deployment. The scenario
then exceeded its60s observation bound for Leon Lake. Lake subsequently ate on
the same functional build at100791, before the diagnostic-only restart. A
separate persistent authority rejection was not established; later targeted
actuation logs showed no such refusal. Keep this bound failure and the eventual
terminal result distinct; no universal queue-latency guarantee is inferred.

Diagnostic run `234d6d13-29cd-49f3-ad9a-309f41dd8ccd` found Lake already
NOURISHED, back on GUARD with native movement, and Owen Ash also NOURISHED with
a successor production job. Final visit `d7ad0ae5-2206-4242-a6c2-943fc7e5b591`
confirmed farmer1-7 had already eaten in COLD at106455 before the client
connected. It is not proof of a new continuous HOT feeding cycle. Both final
frames were inspected: they show the warehouse and no visible feeding actor,
so they are not used as visual food/movement acceptance.

At109984 settlement1 had **22nourished,0hungry,0starving,0active meals** and
108usable bread. All four initially waiting residents had eaten. Final world
summary:340residents; required/scene/inventory conflicts0, incidentIndex0.
Last HOT diagnostic window averaged9.27ms/max26.97ms over64samples; the new
JVM lifetime maximum was69.73ms across4124samples. No >80ms slow tick was logged
in these post-deployment probes. This is bounded evidence, not a scale benchmark
or a claim that every future delay is eliminated. All task clients/private
displays exited; MAIN remains running.

## Deployment and repositories

MAIN `/home/rd/far-frontier-server`, same world
`combat-followup-schema327-seed139-20261103`, schema327/seed139.
Actual Java22PID1173366, service wrapper1173342, start1793735825.
JAR SHA256 `9eea22f09026540925ef552a6f2922bf53fd8522bb55a86a7455b1c944916ca0`;
SHA512 `ae7d8db0e6a3acdd13b464bc128016907e252b51893d0fd374f708b835217a568d6a20e8b87230cae077e348937f77d13ee0257624b49dda41b654d7f1c1aa61`.
Receipts `/tmp/pm-meal-authority-{preflight,publish,stop,install,verify}.log`.
Old native JVMs saved all dimensions and exited before installation.

IMPL and detached RELEASE are clean. PACK tracked state is unchanged; preexisting
`.f0v-baseline/` remains untracked. Unrelated GOV WIP is preserved; only this
report, ledger and the new bounded-native-approach architecture detail belong
to this task's documentation commit. No push, goal or subagents.
