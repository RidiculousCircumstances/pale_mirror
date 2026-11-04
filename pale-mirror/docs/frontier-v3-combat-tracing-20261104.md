# Console-controlled combat tracing — 2026-11-04

## Operation

From server console/RCON (permission 4), with the selected player online:

```text
pale_mirror v3 combat_trace start rd 2 180
pale_mirror v3 combat_trace status
pale_mirror v3 combat_trace stop
```

The interval is 1..20 native server ticks; duration 1..600 real seconds. Defaults
are 2 ticks / 180 seconds. Wait for RECORDING before the fight. Opening JFR is
asynchronous: STARTING is not a recording acknowledgement. Stop is asynchronous:
EXPORTING then SAVED; the completion log/next status supplies both paths.
Expiry and player disconnect stop automatically. A stopped server explicitly
finishes the capture; only final server shutdown may wait up to 10 seconds for
its diagnostic export. This wait is absent from normal ticks and commands.
One capture may be active/exporting per server. Files remain under runtime
`debug/pale-mirror/combat/combat-<UUID>.jsonl` and the matching `.jfr`.
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

`CombatTraceCapture` owns optional JFR lifecycle/export; `FrontierV3CombatTrace`
owns operator-selected observation scope and native event subscriptions. The
command branch delegates to that one recorder. Existing combat owners supply
read-only observation hooks; none receives a new behavior/scheduler dependency.
JFR writes bounded background chunks; starting/stopping/dumping and JSONL export
run on one daemon worker with a bounded queue. Disabled tracing uses constant
lookups, no entity scan, serialization or recording worker. Sampling/serialization
while enabled is bounded extra server work, not a claim of zero overhead.

Safety caps: 50,000 observations; JFR maxSize 64MiB; JSONL export 128MiB; at most 16
retained raw capture files before a new start explicitly refuses and asks that
completed captures be archived. There is no silent evidence deletion. Sequence,
expected/written counts, JFR loss, export limit and terminal reason make gaps or
aborted captures explicit. SAVED means files are available; footer complete must
still be checked before treating absence as evidence. A hard process failure may
leave only JFR emergency data, not a completed JSONL; no crash-completeness claim.

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
