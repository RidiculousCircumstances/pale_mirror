# Moving-opponent combat, tactical gait and rear approach — 2026-11-05

The deployed repair supplies measured player motion to pursuit, preserves short
strike contact, gives bounded approaches a variable gait, and prefers observed
rear approaches without sacrificing assigned formation or immediate contact.
Human difficulty remains a playtest judgement.

## Owners and resulting behavior

Common CombatMotion derives full horizontal velocity from actual coordinate pairs.
The native observation cache remains fenced by current actuation, target identity,
time and sight. Scalar radial/lateral facts and maneuver prediction now consume
the same measured vector; walking ServerPlayer.getDeltaMovement is no longer the
misleading zero-vector source.

The existing common CombatEngagement owns the retained formation frame, best
alignment and progress clock. It can reorient after committed drift and retry a
nonprogressing flank; pending geometry or an unsettled effect is not failure.
Pressure and flank approaches use gait multipliers2100 and2400 per mille outside
contact; close holding remains careful. Finite pursuit spans8blocks/40ticks, with
90tick cooldown and the same shared effort budget. There is no second movement
controller, teleportation, permanent sprint or knockback immunity.

QUICK preparation is2ticks and SWEEP6. QUICK uses bounded common local guidance,
within1block correction latitude, without ordering retreat from actual contact.
A retreating target does not select the stationary lateral sweep. Native reach,
LOS, committed direction, identity and damage refusal still determine real hits.
The canonical publication callback queues the exact native intent promptly;
visible preparation and the finite contact clock start only after durable external
admission. Journal latency therefore cannot consume an unstarted contact window.
Cancelled cues remain cancelled and effects cannot replay.

Common CombatFacing/MeleeRearApproach rank legal station and finite pursuit
candidates using fresh observed horizontal target facing. No sight or unknown
horizontal direction means no bias. CombatStyle data rear weights are Pressing0,
Cautious80, Mobile160. Assigned sectors, geometry, traffic and real attack-ready
contact outrank this preference; the pressure fighter must not circle forever.
This is an inclination to approach from behind, not invisibility, silent footsteps
or a full stealth system. Stance codecs carry explicit optional facing; schema334
requires a fresh disposable world. Network protocol4 is unchanged.

## Source and deployment

Implementation commits:6d5a9cc71,64cee2e66,a1582151d. IMPL and detached RELEASE are
clean at a1582151d07b8427fa830160d45f1d7df466a952. PACK remains tracked clean at
888ed90d5e3b80b18c278107aa83bb003b541f69; preexisting .f0v-baseline/ is retained.
No push was requested. Governance unrelated WIP is preserved.

MAIN: /home/rd/far-frontier-server, far-frontier-v3-live.service,
combat-contact-final-schema334-seed139-20261104, seed139/rootr31;
native Java22 PID3621836, wrapper3621810, start epoch1793818651. Installed SHA512:

```
dd6b534e3a1c67364ecac08fb58c1647923bfd0cb65978194a8f8968c04e704752306a53da284c84ffb53a58f13c91a92597cf1e1da11a3494a31164879e1e5c
```

Release/preflight/publication/install/runtime receipts are
/tmp/pm-contact-rear-{release,preflight,publish,install,verify-runtime}.log.
Saved stops23:26 and23:46 were planned deployment restarts. Diagnostic worlds332
and333 and their captures remain intact. Human rd inventory, health, mode and
position were never edited. No client update is required by this server-only cut.

## Focused and native evidence

78common checks plus6animation checks passed across dependency-valid focused
runs. Final rear fixture/guardrails/package passed10s; clean detached final
release guardrails/JAR/package passed9s. Relevant logs:
/tmp/pm-contact-pressure-final.log, /tmp/pm-contact-rear-verified-final.log,
/tmp/pm-rear-approach-final.log, /tmp/pm-contact-rear-release.log.
The unchanged native effect entry has21required scene-strikes GameTests PASS in
/tmp/pm-contact-pressure-window-final.log, including actual measured walking,
body replacement fencing, durable preparation, cancellation and no replay. This
native gate preceded the final rear-only stance/ranking addition; it is reused
boundary evidence, not a claim of rerunning the final334 native suite.

The first live333 capture36c0d9db identified a real3/4QUICK expiry before native
admission, repaired by64cee2e66. Three added native assertions initially demanded
PREPARING after successful APPLIED/IMPACT; those invalid assertions were moved to
the actual before-effect predicate. Cancellation/no-replay checks remain. The
rear preference fixture initially compared pressure choices and improperly
expected personal bias to override the team. It now compares two equally legal
flank-sector candidates. Animation samples now use authored preparation timing
rather than obsolete hardcoded ages. No production rule was weakened for a test.

## Exact final full-pack evidence and limits

Checked-in live-combat-moving-target.json completed26actions, run
44c50fac-95b8-4c48-9a3e-865df3fc8870. Ordinary sword donation setup
01cec335-64e0-489b-9a01-3dc1c00c3c56 passed9actions; this is not natural sword
production acceptance. A20FPS full-pack client ran on a private Xvfb display.
Capturebfb40986-9080-472a-bbad-aa3c8dfdfa09 contains6504events, complete/no loss.
Manifests and summary are retained under IMPL/build/contact-pressure-20261104/.

The task player moved by ordinary sprint input and performed5ordinary punches
for5damage; Eli and Nora returned17confirmed hits for68damage. The pair were both
within6blocks for189samples and separated by at least60degrees for97samples;
maximum178.91degrees.21of29run proposals used nonzero measured motion. These are
observed group approaches, not proof of universally successful encirclement.
Both final pair frames were reviewed; the contact frame shows two upright armed
guards and an attack preparation. Different roles and initial positions prevent
claiming a controlled statistical effect from rear sample counts alone.

Outcomes:17HIT,4OUT_OF_REACH,1OUTSIDE_ARC,2WINDOW_CLOSED,
2NATIVE_DAMAGE_REFUSED,1BODY_BLOCKED. Both remaining window expiries had actual
contact-window checks: Eli's target moved105degrees behind committed direction,
Nora's sweep target124degrees, then further behind. These are allowed arc dodges,
unlike the initial333 admission-window defect. No immunity or guaranteed hit was
introduced. The diagnostic task player had1000HP, so this is contact/movement
acceptance, not a survival balance benchmark.

Ordinary target departure proved zero responders, contacts and maneuvers, with
styles retained in COLD. All task clients/displays exited and pmcontacttest was
deopped. Human rd subsequently joined and fought independently; current active
contacts belong to that later human encounter and must not be cleared for cleanup.
Last retained settlement1@8201 was statusok/NORMAL/0activeCases,5ready guards.
