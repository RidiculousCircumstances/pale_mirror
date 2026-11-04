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
