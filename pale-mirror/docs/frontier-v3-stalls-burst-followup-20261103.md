# HOT vacancy, burst admission and missed-lunge continuation — 2026-11-03

## Source and diagnosis

Implementation commits: `1f2443524`, `aadfa5061`, `050516ca6` in the independent
Pale Mirror source history. Models require no further change: the user confirmed
normal models after updating the client.

On old MAIN `cb667fbfb`, exact observations in
`build/unit-stall-audit/actors-{1,2}.json` and final actor cuts showed Alex Vale
(`resident:1-13`) stationary at the shared warehouse passage, with a live owned
HOT body and no current execution. Vera Baker (`resident:1-19`) waited behind
that exact body/epoch during EQUIPMENT_RETURN. Delivery and meals also waited;
the warehouse had food and capacity. Courtesy excluded a vacant execution, and
both harvest terminal paths retired work without waking ordinary activity review.
This was a lifecycle/authority contradiction, not a stock or global TPS problem.
The original blockage changed on the old build after COLD/time advancement, before
installation: no claim that the repair freed that unchanged live jam.

A separate old-build crash at 23:02:09 occurred in the unload survivor constructor:
`released survivor must have positive health`. The floating-point guard admitted
NaN, and positive values below fixed-point precision could round to zero. Saved
entity data inspection found 46 remaining bodies and no retained anomalous value;
the exact original actor/raw health is unrecovered. These two reachable conversion
failures are covered, without inventing the original value.

## Ownership and behavior

The existing closed UAE physical-command registry now admits ActorPresenceStarted
for an exact vacant HOT body with an eligible registered passive purpose. Native
ambient admission and courtesy request that one transition; neither takes over
foreign work, drops a continuation, invents a body owner, teleports or bypasses
collision. HOT and COLD harvest completion wake normal resident arbitration.

Shared melee selection, canonical validation, launch and continuation require a
BURST endpoint that actually reaches weapon contact. An impossible observed gap
is refused before searching; an obsolete unlaunched endpoint closes normally.

Common CombatState now owns one bounded MeleeFollowup per combat owner. HIT,
MISS and CANCELLED have explicit stable tags. Only a physically executed missed
LUNGE opens a 24-tick continuation; a cancelled intent does not. Two ticks of
recovery still respect the actual weapon attack interval. Current execution,
body epoch, weapon, target and native target incarnation must all match.
One FOLLOWUP uses a three-tick short cue and at most one block of approach
allowance, through existing shared guidance/navigation and finite pending-strike
running. Normal strike coordination, current weapon/custody, visibility and native
contact checks remain mandatory. Preparation spends the continuation; another
miss cannot renew it. Withdrawal/retirement remove it; codec preserves it across
recovery. Expiry makes it ineligible, and retained entries remain resident-bounded.

The real transaction effect now explicitly distinguishes a visible whiff from
damage. A finite contact window can finish with a swing and a MISS receipt, while
actual damage always rechecks real sword reach and facing. Approach allowance
never grants remote damage. Quick animation is reused; no renderer/model change.

Common body capture, inspection, ambient observation and saved departure witnesses
use one finite positive-health conversion. Dead/nonfinite health cannot produce a
survivor receipt; subprecision positive health remains the minimum positive domain
unit. Nonfinite unload reports an explicit diagnostic. Native save/return tests
cover the tiny-positive value; no mob is healed to an arbitrary fallback value.

## Verification and delivery

`/tmp/pm-followup-final.log`: guardrails, 84 selected domain tests, all 910 adapter
tests, 14 physical strike tests, JAR and packaged-JAR verification PASS.
`/tmp/pm-followup-body-lifetime.log`: 26 physical body lifecycle tests PASS,
including actual serialization/unload/return at subprecision positive health.
Earlier `1f2443524` checks additionally covered 137 domain tests and 12 native
strikes; exact logs remain `/tmp/pm-stall-burst-final.log`.

Clean detached release `050516ca6f5a694230e93d192fae8e6bb67d446f`, release gate
`/tmp/pm-followup-release.log` PASS. JAR SHA256
`aca212dd2a91389eceb8a7f069a793731238a0f8d0c3e4148fa1f55dd9e673e4`.
Publish/install/preflight/verification receipts use `/tmp/pm-followup-*.log`.
MAIN fresh world `combat-followup-schema327-seed139-20261103`, seed139,
schema327, actual listening Java22 PID803091, started epoch1793730335.
The preceding world was saved normally and retained. The new schema intentionally
rejects old disposable saves; no migration guesses a missed attack history.

Exploratory full-pack pilot `c79a509c-0801-4c8c-a404-1fbae1dc85b5` completed
`live_stall_followup_deployment` successfully at20FPS; both captured frames were
inspected. The settlement rendered with human bodies and subsequent background
movement; this was a diagnostic visit, without combat assertions. Client and its
private display exited. Manifest/frames and sequential runtime cuts are under
IMPL `build/stall-followup-release/` and `build/frontier-v3-scenarios/`.
After client exit, final summary instant2769:12settlements/319residents,
required/scene/inventory conflicts0, incidentIndex0; pending ordinary ticks0,
fast-forward0. Recent64tick average3.24ms/max5.15ms. Startup lifetime max307.52ms
is separate from that recent sample. One Netty connection-reset error coincides
with the task client closing; no new runtime fatal/quarantine or body-rejection
error. Exact native body departure receipts were observed after leaving HOT.
 Automated contact/cue tests are not human combat
balance or full graphical combat acceptance. No push authorized/performed.
