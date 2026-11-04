# Combat responsiveness and recorder repair — 2026-11-04

## Evidence and scope

Human capture146c5de2 showed transient close SWEEP opportunities lost to a wave,
5–7ticks before visible warning, rare contact at normal walking speed, and two
visible attempts refused after their finite windows. The old64MiB JFR recording
also evicted its first71.73seconds because unrelated simulation events shared
its retention. Detailed evidence and qualification live in the tracing report.
User authorized repairs. No raw weapon damage/reach buff, permanent sprint,
new role, COLD damage, schema or hashed-rule changes. Same world is retained.

## One owner per responsibility

- Common CombatEngagementPlanner admits exact fresh native holding/attack-ready
  QUICK/SWEEP/FOLLOWUP without waiting for an assembly wave. Both immediate and
  coordinated admissions retain the same exact participant/body identity,
  readiness, weapon cadence, pending-attempt and two-live-strike limit. An
  existing reserved slot is consumed when present; otherwise the exact retained
  pending strike owns immediate admission. LUNGE still coordinates. Status
  formatting uses the same predicate. Canonical Prepared validation repeats it.
- Once the exact canonical pending strike is published, the native executor
  starts its reversible cue and parent-owned local guidance while the external
  journal is being admitted. This is not physical-damage permission. The sole
  Minecraft operation still requires its durable journal, live target/body/
  weapon/permission and both canonical/native preparation clocks. The contact
  window and committed cone still gate native damage. No server-thread wait
  or second actuator is introduced. Trace distinguishes PENDING_PREDECESSOR,
  PENDING_DURABILITY, CANONICAL_READY and external operation admission.
- Expired exact PREPARING cues retain their settlement obligation. They pass
  ordinary durable operation closure as MISS without damage outside the window,
  rather than becoming an unused cancellation. Target loss, changed permission,
  replacement body and cancelled cues retain their independent refusal paths.
  Normal missed-lunge recovery/followup remains one-shot and separately warned.
- A flanker may request the existing bounded burst before reaching its final
  flank. The selected endpoint must still satisfy geometry, legal contact and
  its assigned sector. Existing effort, travel/recovery and cooldowns remain;
  ordinary travel is still WALK/CAREFUL. Actual close contact takes precedence
  over retreating in response to a blow that already landed.
- Diagnostics own only immutable observations. CombatTraceCapture replaces its
  shared JFR retention with a1024-row nonblocking queue and one background JSONL
  writer,50k observations/128MiB output/16retained files. Caps/abort/disk failure
  stay explicit as INCOMPLETE/FAILED. Old evidence is not deleted. Existing
  console commands/analyzer and schema1 JSONL remain; no client update needed.

## Verification

Source d91c6d285cc9b00528bedd62d27271d71a221595. First guardrails/focused models/
recorder/nativecompile PASS12s. /tmp/pm-combat-responsive-final.log PASS28s:
guardrails,20CombatModule,11GuardDuty,4FrontierPersistenceCodec and3recorder
cases, JAR/package. Animation implementation is unchanged; no claim that the
unmatched animation filter added fresh render evidence. The new model case
checks immediate contact, stale body/stance, long-strike coordination, duplicate
pending and shared cap. Recorder cases cover immutable rows, no JVM recording,
automatic duration, storage failure and explicit incomplete abort.

/tmp/pm-combat-responsive-native-final.log:17native scene-strike tests PASS64s.
The strengthened expiry case traverses the actual async operation journal,
settles one real no-damage miss and proves it cannot replay. Other retained
cases cover native range, finite warning, real damage/no replay, followup cue,
committed aim, interposed bodies, directed visible swing and native trace events.
An initial invocation referenced nonexistent compileGameTestJava; correcting
that command to compilePilotJava resolved the harness error, not a product fix.

Clean detached RELEASE guardrails/JAR/package PASS9s. Same-world deployment and
checked-in fullpack live-combat-responsive.json evidence follows. Human enjoyment
and difficulty remain a separate playtest judgement.

## Exact deployment and full client result

Production source d91c6d285cc9b00528bedd62d27271d71a221595; clean detached
RELEASE is identical. IMPL CLEAN HEAD8e8c04022 adds only diagnostic scenario
corrections (d6db990c1/bd7dade0c/8e8c04022), with no packaged gameplay changes.
Root PACK tracked state unchanged, preexisting .f0v-baseline/ retained.
GOV unrelated WIP preserved. No push.

MAIN /home/rd/far-frontier-server, far-frontier-v3-live.service, same
skill-combat-schema328-seed139-20261104 world. Actual Java22PID3969462,
wrapper3969428, start epoch1793781538. SHA512:

```text
dbc06f2d2ea2a37c3d51d28579b2767ad1e5fb70e3d3248a31ac93339b8aee3231615085a4697d00846dd10f118b380fc16d68963d0371677c943c07dd1e4bf6
```

Root receipts /tmp/pm-combat-responsive-{preflight,publish,stop,install,verify}.log
prove native saved dimensions + old JVM/ports exit, publication/install and fresh
startup. No world reset/client protocol change/human-state edit/new quarantine.

Fullpack20FPS/privateXvfb probe21948c4e-e820-4443-bd49-f6d8734cddbb completed
all14actions: ordinary harm against the exact selected guard, the selected
player contact/response, actual native player health loss, ordinary sprint/strafe
circling, dimension escape without another hit, contact/responders/maneuvers
released. Checked-in live-combat-responsive.json; artifacts in
IMPL/build/combat-responsive-20261104/live-local-{manifest,summary,phase-summary}.json.
Moving phase400pose samples/400level ticks: actual player planar distance87.138
blocks. Same guard Owen Ash made9SWEEP attempts:8actual hits/32damage and1
OUT_OF_REACH miss. Full trace11hits/44damage across12attempts. Render samples
include302SWEEP/35BURST/16LUNGE, with changing articulated poses. Both linked
frames personally reviewed: close armed human rigs and the circling guard with
intact body/sword. Still frames alone do not prove complete warning readability.
No universal balance/human difficulty claim.

Trace3c890aba-db52-40ba-8675-2877a091c493:2068/2068rows,complete=true,no loss
or cap. All12published CANONICAL_READY→cue delays are0native ticks; submission
→cue2–3ticks in this probe. Canonical durable admission is still necessary;
this is not zero-latency whole attack or a controlled historical A/B claim.
Earlier live probes also recorded ordinary FOLLOWUP and complete captures.

Earlier failures were retained and classified: e34a0c0c expected one globally
remembered hostile; rd plus PMTestPilot correctly made two. Its successor checks
the selected contact UUID. Runs2b6b9a29 and e2788990 teleported to another defence
post then followed an unavailable named old responder. Dynamic jobs invalidate
that fixture; changing its name was insufficient. The final scenario keeps the
original exact actor and ordinary circle, preserving actual movement, damage,
escape and release assertions. These failed receipts are not counted as green
full-story acceptance. Product mechanics did not change between these probes.

All task clients/private displays exited after each bounded check. Final recorder
status SAVED/inactive, MAIN running. Human can repeat combat with unchanged
console commands. The runtime mechanics and producer repair are delivered;
human judgement of skill/fun/difficulty remains separate.

## Subsequent human fight — 14:16–14:17 local

User requested analysis of captureb68568de-0e3f-4afb-b2d9-a437d5bc815f only.
No code, world, difficulty, player state or server process was changed.
Same production d91c6d285/JVM3969462/world. Complete9466/9466rows, no loss/cap;
about52.81seconds,1056native and1056canonical ticks,19.996TPS. Offline artifacts:
IMPL/build/combat-responsive-20261104/user-new-fight-{summary,findings}.json.

rd adventure/unarmoured, empty hand for the first19.2seconds, then iron sword.
Five recorded kills: Owen Ash, Alex Bell resident1-born-2, Vera Moss, Alex Rowan,
Nora Bell. Initial captured health respectively4/19/12/20/4; these are not five
fresh full-health opponents. Owen died to four one-point punches; remaining
kills used the sword. Player20→12health via two actual four-point guard hits.
Seven visible weapon attacks:2hits,4OUT_OF_REACH,1WINDOW_CLOSED. Five more
attempts cancel OUTSIDE_APPROACH_ALLOWANCE before a cue. Thirteenth preparation
is followed by its attacker's death without a terminal trace row; absence of that
row is not proof that canonical pending cleanup was stuck.

Prior repairs are observed: seven CANONICAL_READY→cue delays0ticks;
submission→cue3ticks for six attempts/4ticks for one. SWEEP now actually starts
and one hits. Expired QUICK settles as MISS:WINDOW_CLOSED rather than a false
unused cancellation. This human behavior is repeated approach/hit/retreat and
knockback, not a controlled repetition of the pilot's constant circle. Do not
claim equal conditions or extrapolate the pilot's8/9hit rate to human PvP.

Remaining concrete issues/contributors:

- Observed median moving pace rd4.07blocks/sec vs guards1.93–2.0. BURST is finite
  and does occur, but ordinary pursuit cannot maintain this player's contact.
  This is current policy, not new server tick debt. No EFFORT gate was observed.
- The active maneuver gate suppresses actual close opportunities: Owen QUICK
  at1.43, Alex Bell SWEEP at1.90and2.06, Alex Rowan QUICK at1.59all report
  MANEUVER_BUSY. Source MeleeManeuver.busy is open travel or closed recovery;
  executor checks it before strike admission. Thus reaching weapon contact
  does not itself transition a closing maneuver into an available attack.
- Owen LUNGE197071 prepares at native44513,distance2.79; visible44516,d3.28;
  misses44528,d4.74. Native navigation target remains(-1501,64,-1317) through
  these rows; fighter goes(-1499.17,-1314.97)→(-1500.53,-1316.53), while player
  goes(-1502.21,-1314.13)→(-1504.69,-1313.48). No intervening hurt/knockback on
  Owen. This observed closing attempt does not close the gap and retains its
  previous native destination. The existing trace does not record each local
  guidance offer/refusal/consumer, so its exact rejection/expiry cause remains
  unconfirmed; a physical native path endpoint alone cannot prove that a
  particular semantic correction was accepted.
- Static counterexample: MeleeGuidance corrections expire after2native ticks,
  while consumption is via the bounded ambient actor probe before combat EFFECT
  in the shared physical pass. Canonical-predecessor waits and actor scheduling
  have no guarantee of consumption in those2ticks. The pass may therefore drop
  a still-owned closing correction before its consumer runs and keep the old
  path. This is a source-level lifetime/consumer contract defect; it is not yet
  uniquely correlated to the Owen attempt. Raising an arbitrary TTL alone would
  not establish the required exact owner/attempt/phase lifetime or consumption.
- The short QUICK197739 cue begins45184, contact window after4windup ticks
  through45191. Admission at45189sees3.35range; at45192range2.60but window closed.
  Position sample45190still3.141;45191is not sampled. Therefore missed contact
  on an intervening tick is unproved. Actual executors commonly observed every
  2–3ticks and global physical-pass admission waits for canonical predecessors;
  a future contact-window repair must examine that cadence, not assert missing
  per-tick evidence as a proven hit. Several other short attempts are knocked
  back by an actual player hit, then outdistanced during warning.
- Server config and read-only difficulty command both say Easy. Actual profile
  base damage6 becomes4against rd while player's charged sword usually deals6.
  Verified dependency snapshotac5901effb63: Player.hurt around903 delegates to
  DamageScaling.getScalingFunction; NeoForge IScalingFunction.DEFAULT lines24–35
  scales Easy damage to min(amount/2+1,amount) only for players. This is ordinary
  difficulty scaling, not a corrupt weapon profile; no difficulty change made.

Next engineering focus is actual consumption/lifetime of parent-owned closing
corrections, contact-aware maneuver-to-strike transition and responsive approach
under real hit/retreat/knockback. Keep shared UAE/navigation/body/effect authority,
finite cues and dodge windows. Do not substitute raw damage/range buffs or a
permanent sprint, or classify every legitimate timed dodge as a product bug.


## Follow-up repair from the next human fight

Production `3f1839cd4fccc1fbd8ee9e6d79b8e56c5caf5c92` closes the evidenced
lifetime/consumer contradiction. The exact attack producer retains its local
correction until the original native preparation/contact deadline, rather than
renewing a two-tick expiry. It wakes the existing actor probe. The generic
provider retains exact parent/body/attempt permission and a producer-supplied
veto: cancellation, a successor claim/order/body or deadline invalidates an
already-running path at the native continuation boundary too. This is neither
a second semantic goal nor a combat-specific navigation controller. Early aim
tracking ends at the existing commitment point; later target motion cannot
extend the deadline or continuously home the committed attack.

The common combat process admits fresh close contact during a launched BURST
only after the registered movement owner proves its checkpoint. Interruption,
schedule cancellation, exact maneuver closure and `Prepared` share one event
batch. Replay still rejects `Prepared` while a maneuver is open. A checkpoint
wait is an unattempted policy refusal, not an effect receipt or fatal error.
Closing a burst preserves maneuver cooldown while permitting its contact
strike; BACKSTEP recovery remains committed. Item wear/damage still wait for
normal durable physical operations. Difficulty, weapon reach/damage, effort,
wave cap, hashed rules and schema remain unchanged.

Focused guardrails/78 frontier cases/4 adapter cases/compilePilot passed in33s;
17 actual Minecraft scene-strike cases passed in64s. This includes shared
checkpoint refusal, no false arrival, duplicate-preparation rejection and
snapshot round-trip. A preexisting followup fixture incorrectly prepared at
21014 although its installed rule retained readiness21023; it now consumes the
actual retained readiness instead of weakening cadence. Detached release
JAR/package/guardrails passed in9s. Logs `/tmp/pm-combat-contact-{check,native,
release}.log`. No affected test remains failing.

The current MAIN deploy retains the same seed139/schema328 world and human
outcomes. Actual Java22PID194019/wrapper193995, start1793785865,
`far-frontier-v3-live.service`, pinned SHA512
`48b1f5b81d3efd4fe4d4c7ff5c6c9259ed400bc4351a7f57cff4e546a2f1cb14341c797b50e2313910e9892ca8c4d786f469f1163b8cb03bd93f5a3bfd542c83`.
Preflight/publish/graceful-stop/install/fresh verification receipts are
`/tmp/pm-combat-contact-{preflight,publish,stop,install,verify}.log`. Final read-only
settlement status isok/NORMAL/0cases,0contacts/responders/open maneuvers/windups.
Server running; no client update or world reset required.

Fullpack probe `4a6fb283-3884-419e-8c93-b99d53484ac2` passes all14 actions with
exact Iris Moss/resident1-12, ordinary player harm,400 ticks/88.923blocks of
circling, real native damage, dimension escape without additional damage, and
terminal contact/responder release. Capture
`f52e1174-a2e4-480f-b62c-340198ff15aa` is3822/3822complete/no loss. It records15
guard hits/60 damage, including4 actual LUNGE hits, plus10 visible misses.
Nine offered exact attempt corrections reach the shared consumer:8start a
native path and1reports actual traffic waiting. Seven additional zombie hits
of2.5damage are separate and are not attributed to the guards. Both linked
rendered frames were personally inspected. Artifacts:
`IMPL/build/combat-contact-20261104/live-current-post-manifest.json` and
`live-summary.json`. This specific run does not prove the BURST contact-handoff
branch occurred, nor establish human combat balance; the focused canonical
check proves its atomic/recovery contract.

The first client attempt failed before combat: Iris had moved from town to an
unobserved post. Test-only successor `86438341e` updates existing setup to visit
the exact actor's read-only current position before rendezvousing with its
received physical body. No resident is teleported or pinned. All task clients
and private displays are closed. The capture finished on pilot disconnect.


## Crash during human capture780adf4c: retained death resources

The failure on source3f at15:00:04–05 was in death/resource acknowledgement,
not in the recorder call stack. The native death of Vera Rowan/resident1-10
first threw because `UNIT_INVENTORY` aftermath had not closed immediately.
Ordinary recovery then physically moved pocket stock to a native item entity
and threw because canonical confirmation was still queued. Quarantine stopped
Minecraft normally. Human capture780adf4c-2d2b-429f-a188-4b6486c588b0 was saved
1198/1198complete without loss. The original remains under runtime debug;
`IMPL/build/combat-fatality-20261104/` retains its copy and exact failing log.

Repair2134fd17c makes the common entity-to-ground operation return pending
confirmation while its exact durable outbox owns continuation. Native effect
failure and genuine rejected required consequences remain invariant errors.
All seven shared drop callers were traced. Exact held items return actual
confirmation state; service death no longer treats that ordinary wait as fatal.
The body fatality owner retains corpse custody until resource owners complete.

The initial same-world restart/revisit then exposed another connected contract
violation: `retireDeadActor` removed native residency before aftermath finished,
while join admission had no exact corpse capability. It rejected the stored
corpse needed to recover the outstanding operation. Final45fc4ebc3 retains
provenance through owner closure, including ordinary retired-actor cleanup.
A stored corpse may rejoin solely under exact retained fatality command,
UUID/incarnation, domain body tombstone and actual operation marker; duplicate
UUID and wrong native lifetime are refused. Admission cannot revive the actor
or grant living execution. A derived protocol+owner index provides bounded
retirement checks instead of scanning the operation registry. No schema change,
second custody authority, missing-source reconstruction or loot replay is added.

Final native regression uses a real managed villager with7pocket cobblestone,
actual vanilla death and deliberately delayed real FrontierFileStore writes.
It proves one physical drop with pending canonical ownership, immutable outbox
hydration, residency retention through ordinary cleanup, serialization/rejoin
of the real corpse, duplicate/wrong-lifetime rejection, eventual exact stock
confirmation and owner closure, and no replay. This is native component evidence,
not a process-crash/player-packet simulation. All27body-lifetime cases pass28s;
19player-storage native cases pass25s. Guardrails,22operation unit cases and
pilot compilation pass9s. Detached release JAR/package/guardrails pass9s.
Initial invalid test-slice name and command-in-actions fixture are excluded
from acceptance; corrected runs use the existing allowed mechanisms. The owner
index test was also corrected to respect that recovered PREPARED is not an
unused-attempt permission; production safety was not weakened.

Final MAIN source45fc4ebc3489b19edbedb37570b0cb17faa9f1cf,
Java22PID335339/wrapper335313, start1793787989,
far-frontier-v3-live.service, same skill-combat-schema328-seed139-20261104 world.
Pinned SHA512:
`e24a90d1d2fd2d3780c7388ba674fa7748d00eac64a3062e8eedb4faa791d41a0785fce5b67a2311078ca1b267f639432d3a12966eeeb7d09ebed9d44f7d6180`.
Receipts `/tmp/pm-combat-fatality-final-{preflight,publish,stop,install,verify}.log`.
A premature intermediate start during installer completion was stopped; the
final verified start follows completed pinned installation and actual previous
JVM/ports exit. No human state, inventory, difficulty or world reset was used.

Checked-in `live-combat-fatality-recovery.json` visits the exact dead actor's
read-only current position with only the task player. Final fullpack20FPS probe
7454117d-2382-4302-97ea-7f9d9008d7a5 passes6actions: natural fight-area loading,
no revival, summary0required/inventory/scene conflicts, ordinary dimension exit.
Its capture8f6c4f2a-8c87-4480-9b34-9c61b04a929b is326/326complete. Manifest:
`IMPL/build/combat-fatality-20261104/live-final-recovery-manifest.json`.
This proves loaded-world health/no revival, not direct closure of every old
operation or general economic/balance acceptance. Exact serialized aftermath
closure is covered by the native regression. Initial revisit also logged an
unrelated SHIPMENT_TRANSFER declaration ambiguity; it was not a quarantine and
is not silently classified as resolved. Final statusok/NORMAL/0cases,
revision932024/simulation266227. All task clients/private displays are stopped;
MAIN runs for human testing. Code/release clean; pack tracked clean with its
preexisting .f0v-baseline/ retained; unrelated governance WIP preserved.


## Hoe hit-and-retreat capture45d4ee60: passive physics and contact selection

Read-only analysis of complete3604/3604capture45d4ee60-c0a7-402a-9612-7fd52171b132,
source45/PID335339,52.80s and1056matching native/canonical/level ticks. rd uses
stone_hoe in Adventure, all7hits are full charge and1damage. Nora Bell1-20holds
iron_sword, starts17HP and ends10; rd5.333HP is unchanged. No kill is captured.
There are only2LUNGE preparations: one cancelled outside approach allowance,
one actual swing missing out of reach. This is not evidence of slow TPS or a
player invulnerability mode.

A concrete shared-physics defect is visible and supported by source. All sampled
X/Z coordinates remain exactly -1397.5114599238286/-1355.1632580042606 across
native ticks4687–4753 (~3.3seconds), with nonzero horizontal velocity and no
horizontal collision. Vertical motion still responds to hits. X/Z velocity
simply decays by0.98per tick and can accumulate across subsequent hits.
The NoAI native-travel bridge is conditional on retained path control in
FrontierV3MobMotionLifecycle/FrontierV3GoalNavigationMobMixin. Once that path
ends or cannot start, ControlledMobMotion.advanceOrdinaryGravity moves onlyY
and leaves horizontal velocity untouched. ModLens snapshotac5901e actual
Minecraft1.21.1/NeoForge source confirms LivingEntity.aiStep0.98 damping,
LivingEntity.travel2161 local-control gate, Entity.isControlledByLocalInstance
3050 and Mob.isEffectiveAi1396 NoAI condition. The observed factor matches the
fallback path. This is a common body-physics defect, not just guard tactics.
The two-tick recording cannot establish each unsampled tick's exact trajectory.

There is also an evidenced contact-selection weakness. QUICK is limited to
min(2.2,actual reach3*0.75)=2.2blocks. Contact at2.37..2.97, inside weapon
reach, is therefore classified LUNGE, which still uses group-wave admission.
Seven WAVE_SLOT transitions span distances2.37..3.59; some of those positions
are outside ordinary reach and are not evidence of a withheld contact strike. The guard does not reject every action during
hurtTime: the first preparation is submitted at distance3.07/hurtTime8 and
cancels three ticks later at4.07. The second lunge268635starts its cue at native
4675/distance2.4, receives the player's hit at4679, then misses at4687/distance
4.83. Its correction is actually offered and consumed by the shared navigator;
this is different from the former two-tick-lifetime defect.

Recommended next repair: provide passive full native physics for exact managed
bodies independently of navigation demand, suppress vanilla task selection
separately, and ensure one physics integration per tick. Separately distinguish
an ordinary full-weapon-reach contact strike from a closing lunge, preserving
item reach, LOS/hitbox, cue, cadence, effort and finite dodge windows. Reassess
retreat/counterattack under repeated hit-and-retreat only after fixing these
causal defects; no blanket knockback immunity or unmeasured difficulty buff is
justified by this trace alone. No gameplay/source/runtime/player/world mutation
or deployment was performed in this analysis. Machine-readable evidence:
IMPL/build/combat-knockback-20261104/{summary,findings}.json.
