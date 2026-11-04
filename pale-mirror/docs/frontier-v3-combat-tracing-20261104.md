# Console-controlled combat tracing — 2026-11-04

## Operation

From server console/RCON (permission 4), with the selected player online:

```text
pale_mirror v3 combat_trace start rd 2 180
pale_mirror v3 combat_trace status
pale_mirror v3 combat_trace stop
```

The interval is 1..20 native server ticks; duration 1..600 real seconds. Defaults
are 2 ticks / 180 seconds. Wait for RECORDING before the fight. Partial completion is INCOMPLETE, never SAVED. Opening the scoped writer is
asynchronous: STARTING is not a recording acknowledgement. Stop is asynchronous:
EXPORTING then SAVED; the completion log/next status supplies both paths.
Expiry and player disconnect stop automatically. A stopped server explicitly
finishes the capture; only final server shutdown may wait up to 10 seconds for
its diagnostic export. This wait is absent from normal ticks and commands.
One capture may be active/exporting per server. Files remain under runtime
`debug/pale-mirror/combat/combat-<UUID>.jsonl`. Current captures do not start
a JVM recording; earlier `.jfr` evidence is retained unchanged.
No world reset, client protocol change or client update is required.

## Recorded evidence

Native positions are sampled only for the selected online player and at most 63
nearby loaded living entities in a 48-block inflated local bounding box.
Radius/body caps are declared in the header/frame; a full frame reports bodyLimitReached rather than pretending
to include everyone. No chunks are loaded or COLD bodies materialized by tracing.
Each position row includes exact native UUID/runtime ID, coordinates, velocity,
yaw/pitch, grounded/collision flags, health, armour, hurt resistance, held weapon
and wear; player mode/attack charge; native navigation completion/target. Managed
bodies add their declared actor/body epoch and current activity/owner, retained
readiness, next-attack deadline and exact pending attack. There is no job/target
selection, route search, native-boundary settlement or canonical mutation.

Timestamp fields join native serverTick, level worldTick, simulationTick,
UTC event time and monotonic nanoseconds. These clocks stay separate; a sampled
coordinate is not a proof of an unobserved intermediate frame or damage.

Events are independent of the coordinate interval: actual player attack input,
incoming damage (including observed cancellation), post-mitigation applied
damage, death/cancelled death, first visible guard gesture, attack preparation,
admission/operation status, HIT/MISS/cancellation and changed decision gates.
The native strike records frozen aim yaw, target angle, current eye-to-hitbox
distance, LOS and actual weapon reach/damage/cadence. Explicit misses distinguish
reach, interposed body, expired window, commitment cone and native damage refusal;
the last label does not assert a particular reason such as invulnerability.
Decision evidence distinguishes effort, recovery, pending effects, group slots,
maneuver occupancy and approach checkpoint. Present-body/weapon gates are dated
separately. Capture does not modify these decisions or the damage predicate.

## Storage and analysis

`CombatTraceCapture` owns one optional bounded JSONL writer; `FrontierV3CombatTrace`
owns operator-selected observation scope and native event subscriptions. Common
combat supplies read-only hooks, never a second gameplay owner. The server
copies/serializes each observation and offers it without waiting to a1024-row
queue. One daemon consumes immutable rows and performs all file I/O. Starting,
finishing, duration expiry and export do not wait for disk on ordinary ticks.
A terminal server shutdown alone may wait up to10seconds for diagnostic closure.
Disabled tracing performs no body scan, serialization or writer work.

Safety caps:50000observations;8192characters per row;128MiB output;16retained
JSONL captures. A cap, failed storage, observation failure or writer queue
saturation is explicit. INCOMPLETE never becomes SAVED. Sequence and expected/
written counts plus the footer must still be checked before treating absence
as evidence. The writer retains the beginning until an explicit stop/cap;
unrelated JVM-wide JFR traffic cannot evict it. No silent evidence deletion,
server-thread file flush, unbounded queue or claim of crash-complete recording.
New producer d91c6d285 supersedes the old JFR producer discussed historically
below; `dataLoss` now reports missing scoped rows, not a JVM JFR event.

```bash
python3 tools/engineering/combat_trace.py /absolute/path/combat-UUID.jsonl
```

The read-only summary lists actor identities, observed displacement/speeds,
health endpoints, technique/gate transitions, actual applied damage and deaths.
It refuses changed capture identity, flags missing/partial transport, and does
not count a respawn/dimension change as continuous movement. Raw coordinates and
per-attempt events remain available for a causal timeline or later plots.

## Existing fight and verification

Before instrumentation, MAIN logs show user rd connected 11:58:43, repeated
settlement 1 harm facts, Eli Stone killed by rd at 12:00:53, then rd killed by Nora
Bell at 12:00:58. Old logs have no detailed melee trace; snapshot/WAL/physical
operation compaction is not a permanent frame history. No exact old miss cause
or bare-hand verification is reconstructed from that absence. The user's
circling observation motivates the next recording, not an unproved rebalance.
Bounded retained excerpt: IMPL/build/combat-trace-20261104/previous-fight.log.

Source ea88de7a1 plus f705ad7d2. /tmp/pm-combat-recording-final-check.log:
guardrails, 2 actual-JFR lifecycle/export tests and 17 native strike tests PASS 73s.
The additional native case records an actual native player position and applied
one-point damage, validates completeness, and proves the observer leaves damage
unchanged; it is not an ordinary player-packet combat balance claim. Ordinary
live command capture uses checked-in live-combat-trace.json; live outcome and
exact deployment evidence follow below.
/tmp/pm-combat-recording-package.log final guardrails/unit/JAR/package PASS 12s.
Python analyzer compilation and declarative scenario validationPASS. An initial
JFR destination/stop double-dump error in new-code unit tests was corrected by
keeping the explicit single export owner. No known red affected check remains.

## Exact deployment and live command/file probe

Reviewed source commits ea88de7a1 and f705ad7d2; clean detached RELEASE
f705ad7d2180fd81aadb460d1ec01114eabd6b41 guardrails/JAR/package PASS 14s
(/tmp/pm-combat-recording-release.log). Installed SHA512:

```text
e4d0a5ba8db9a6a6579b2f54e9ea76863f99b83003c4f3c50c4d8d4d60986fe8e8cfd678999da95e5c9d8a36eace6af23ce90bb473d18b1b3f0a795893ec9059
```

Runtime /home/rd/far-frontier-server, far-frontier-v3-live.service, actual
Java 22 PID 3744466 (wrapper 3744440), restart epoch 1793777932. Original
skill-combat-schema328-seed139-20261104 retained. Native all-dimensions-saved
and old JVM/port exit preceded installation. Root preflight/publication/install
and fresh deployment verification passed; receipts are
/tmp/pm-combat-recording-{preflight,publish,stop,install,verify}.log.
First verification was attempted before the listening port existed and refused;
after ordinary startup the unchanged check passed. No new quarantine observed.
No human inventory, gamemode, health or position was edited.

Checked-in live-combat-trace.json completed on the full pack at 20 FPS through
the existing private-display pilot. Run a566a7a1-b4ea-4243-b9bd-831749c9a186;
IMPL/build/combat-trace-20261104/live-manifest.json and live-summary.json.
The ordinary player command started capture
4bb7ac23-1815-4e1f-8b22-a020693fa688; console status returned SAVED. Its
DURATION footer has 710 expected/written events, complete=true, dataLoss=false,
exportLimit=false. Offline analysis included PMTestPilot and 10 managed residents
with exact actor identities. This proves console-selected lifecycle, actual
loaded-body formatting and complete readable export. The live smoke performs
no attacks and is not proof of guard balance; actual damage capture is the
separately described native test. All task client/private-display processes
exited. MAIN remains running with no active recorder. No push performed.

## Historical human fight — old producer, 2026-11-04 13:02–13:04 local

Capture146c5de2-2bdf-485d-8fc7-cc655e715c09, same deployed source/JVM/world.
Operator stopped at13:04:59; no client or gameplay mutation by investigation.
Raw files remain in runtime debug/pale-mirror/combat; analyzer output is
IMPL/build/combat-trace-20261104/user-fight-summary.json.

**Partial recording:** expected25972 observations, exported12036; sequences
13937..25972 are contiguous. The first71.73seconds were evicted; retained
08:03:51.686–08:04:59.186UTC spans67.50seconds. Footer correctly declares
complete=false even though command status says SAVED. JFR summary proves that
unrelated ExecutionStage/DelayPoint/clock events dominate the64MiB rolling
recording, evicting early combat data. Current recording selection/retention is
therefore insufficient for the promised ordinary session duration. Do not
present the analyzer totals as the full fight or request another long fight
until this producer defect is repaired. A correction must preserve the scoped
combat stream independently of unrelated concurrent JFR events, rather than
merely increasing the global byte cap or concealing the completeness failure.

Verified retained fight segment until death: native ticks29716..30790,
53.7seconds, three armed guards (Owen Ash resident1-3, Nora Bell resident1-16,
Vera Moss resident1-8). Names alone are ambiguous: another nearby Nora Bell
is a different body. The exact UUID/actor bindings disambiguate them.
All recorded rd position samples show adventure mode and empty main hand.
Three actual guard hits, all Nora, four damage each;13actual one-damage player
hits against the guards. Player health starts11 in this retained segment,
falls to3 at08:03:55.439UTC, then next hit/death at08:04:45.387UTC:
a49.95second interval without another recorded guard hit. No guard death is
present in the retained interval; nothing is inferred about the evicted part.
Native ticks and canonical ticks both advance1350 over67.4997seconds, about20TPS;
this does not prove no individual tick spike, but persistent tick debt is not
the observed cause of the slow pressure in this interval.

Concrete contributing observations and source boundaries:

- During sampled planar motion before death, rd's median moving speed is4.24
  blocks/sec; each guard's is about1.97–1.98. Guards move during roughly71–74%
  of consecutive samples. These are observed displacement statistics, not
  attribute values or a claim of a universally correct balance. Ordinary
  CombatLocomotion explicitly chooses WALK/CAREFUL, with RUN for bounded
  pending closing attacks/maneuvers. Reaching and maintaining weapon contact
  is difficult against this circling player.
- Three SWEEP opportunities are directly observed at ranges2.03/2.04/2.09;
  each is gated WAVE_SLOT. No SWEEP is subsequently prepared in this retained
  interval. Example Vera at native29852/canonical112561 has a slot opening
  at112562, distance2.03, but her next decision at29864 is APPROACH at4.15.
  Another Vera sweep at30650/canonical113359 waits for113360; by30662 the
  admitted technique is LUNGE at3.42 and cancels three ticks later at4.37.
  CombatEngagementPlanner adds assembly/release/slot gates; immediate contact
  is a transient fact and current policy may lose it while coordinating.
  This is observed lost opportunity, not proof that every WAVE_SLOT is invalid.
- Eleven PREPARE_SUBMITTED attempts:3HIT,3explicit OUT_OF_REACH misses,
  3early OUTSIDE_APPROACH_ALLOWANCE cancellations,2OPERATION:NOT_APPLIED.
  Submission-to-gesture delay is5–7native ticks for the eight visible cues;
  visible preparation then adds its ordinary4/8ticks. This extra admission
  delay is measured, but the trace lacks a complete per-transaction wait
  breakdown: WAITING_DURABILITY is observed, so attributing all delay to disk
  latency would exceed evidence. At successful/explicit-miss lunge contact,
  missed distances3.42/3.81/3.39 exceed weapon reach3.0; enlarging damage reach
  would hide the approach/timing issue rather than explain it.
- Nora QUICK attempt113327 begins at2.16, is visibly cued five ticks later
  at3.27, and terminates NOT_APPLIED14ticks after submission at5.71. rd hits
  Nora at30618; health9→8, hurtTime8 at30620, velocity roughly(0.325,0.121)
  away from rd. The initial displacement is consistent with native knockback,
  not established autonomous navigation in the wrong direction. Player also
  retreats. Do not turn that example into an unproved stale-route defect.
- Two visible attempts terminate NOT_APPLIED just after their preparation+
  contact lifetime. Source precondition preparationOpen rejects expired cues
  before applyImpact; NOT_APPLIED becomes CANCELLED. Thus there is a reachable
  distinction between an explicit physical MISS and an expired visible attack
  cancelled before impact. Followup behavior depends on that distinction.
  Exact refusal predicate is not recorded, so window expiry is source-supported
  and timing-consistent, not a uniquely proven cause for each operation.
- No EFFORT gate was recorded; decision readiness minima are59–70. No claim
  that fatigue caused the long hit gap. None of the guard position samples
  reports horizontalCollision; this alone cannot rule out actor avoidance.

Investigation changed no combat parameters, source, server process, world or
human state. Recorder is SAVED/inactive, no task client running. Next engineering
issues: lossless bounded scoped recording, opportunistic close-strike admission
inside coordinated attack limits, moving-target approach/visible timing, and
explicit terminal semantics for expired visible attacks. These need coherent
source repairs and focused verification; this report does not claim them fixed.

The producer and combat findings above are addressed by
[the subsequent responsiveness repair](frontier-v3-combat-responsiveness-repair-20261104.md).
Its full client story passed, and the new scoped capture2068/2068rows includes
actual circling SWEEP damage and safe encounter release. Old evicted observations
remain missing; the repair does not reconstruct them.
