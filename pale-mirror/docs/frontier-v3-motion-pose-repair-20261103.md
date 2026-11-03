# HOT retarget cost, Human Era pose and melee preparation — 2026-11-03

Approved follow-up to `frontier-v3-combat-motion-pose-findings-20261103.md`.
Sole main; no delegated work. Scope excludes economy and new combat roles.

## Connected changes

The field owner uses the existing shared `FrontierV3ClearanceProbe` for its native
alternative-cell pass. At most eight new candidate predicates (including state
construction and actual path queries) run per worker/native tick. Reentrant
callbacks cannot renew that tick's quota. Negative candidates survive continuation;
an exhausted pass retries live terrain/traffic at the navigator's existing cadence.
An unrelated global world revision cannot repeatedly reset the same prefix.
Changed body/execution, origin or layout invalidates the pass. These hints grant
no work or movement authority: a selected candidate is queried against live
Minecraft collision, its unit profile and exact actuation before the existing
retarget event. Yield is visible SEARCH_PENDING, never fabricated NO_PATH.
Shared service clearance and courtesy also use the native-tick quota.

Human Era's custom torso rotates around the adapter-declared hip, compensating
translation with the actual Z/Y/X rotation order. Scoped snapshots restore only
the semantic layer's modified parts before the next EMF animation evaluation.
Pack gait, other bones and external scale remain owned by Fresh Animations.
The adapter neither modifies/repackages author archives nor resets the entire rig.
The hand uses the same compensated body transform.

Durable operation admission starts visible melee preparation immediately. Native
and canonical windup now advance concurrently; both must finish before effect.
The finite native contact window, actual vanilla reach, LOS, exact incarnations,
weapon wear, journal confirmation and no-replay gates remain in force.
Lunge guidance may retain an exact pending-attack activity goal after its semantic
approach has arrived. Generic local corrections are consumed by the same shared
ambient navigator, fenced to body/execution/parent order/token and short lifetime.
The guard adapter declares its own permitted mission goal; the generic navigator
contains no guard interpretation. Completing the strike revokes that permission;
correction arrival never awards parent arrival. Target tracking remains early-only.
Group assembly is shorter (8 to4 ticks, release lead2 to1); strike spacing remains
12 ticks because equal native damage inside vanilla hurt invulnerability is lost.
Ordinary walking and cooldown-owned maneuver speeds are unchanged. Schema326/new
world adopts the changed hashed tactical policy; no old-world migration.

## Evidence

Baseline MAIN110336 JFR:3307/3505 sampled server CPU stacks in the farm retarget
predicate; field scene calls350–375ms. These are attribution and baseline metrics,
not a measured post-fix speedup. Original recording/summary are retained under
IMPL/build/melee-response-audit/. No claim that all server peaks are eliminated.

`/tmp/pm-motion-final.log`: full NeoForge909 tests and scene-strikes12 passed.
An earlier overlapping verification invocation failed writing report files; it is
not acceptance. The subsequent serialized boundary run is the authoritative
focused report: `/tmp/pm-motion-final-boundaries.log`, NeoForge16 / Frontier76 /
scene-strikes12 pass. This includes actual native damage/no replay, finite late-hit
rejection, concurrent windup, hip invariance/base restoration and retained-goal
revocation/recovery. Final helper type/format cleanup receives compile/guardrails
and affected unit checks in `/tmp/pm-motion-final-compile.log`.

Remaining player-facing validation: actual Human Era standing/work/combat frames,
lunge effectiveness against moving survival players and HOT performance under the
original obstructed field scene. Mathematical/native boundary tests do not prove
those whole visual/gameplay outcomes. User offered manual animation observations;
no broad automatic campaign or product-validated claim is inferred.

## Delivery

Source `cb667fbfb0ccf9661ecacf15b1ea502db43e779b`, clean IMPL and detached RELEASE.
Detached guardrails/jar/verifyPackagedJar PASS `/tmp/pm-motion-release.log`.
SHA256 `8f66800ef123f8ff05f641807fb499cd773d79e0ff3e764b470f5e84ba044579`;
SHA512 `8eaeea5f98321725bb6d5c7ea249ba988eee78ef2c940962069cb5e3a0aeaa514f3fc4732c4a21e65df2570857aa86417cb4f1f3f29d5abcb7a7bb45320d9aca`.
PACK remains888ed90d5e3b80b18c278107aa83bb003b541f69, tracked clean with preexisting
`.f0v-baseline/`.164 indexed files verified and both hosted artifacts published;
Visuals unchanged. No push.

Old MAIN110336 saved all dimensions21:28:13 and exited with both game/RCON sockets
closed. Installer selected fresh `motion-pose-schema326-seed139-20261103`, seed139,
existing rootr31 agricultural-flat datapack, without editing old canonical/WAL.
`far-frontier-v3-live.service` started1793723367 /actual Java22PID357266. Both
preflight and fresh pre-start check, publish/install and deploy-verify PASS:
`/tmp/pm-motion-{preflight,fresh-preflight,publish,install,verify}.log`.

Readonly startup slice: instant1201/revision1572,12settlements/319residents,
required/scene/inventory conflicts0, incidentIndex0, HOT leases0. COLD host recent
64ticks average2.56ms/max4.23ms, lifetime max85.55ms including startup; pending
ordinary ticks0/fast forward0. No runtime ERROR/quarantine observed; optional-mod
mixin ClassNotFound WARNs are not new crashes. This is startup/COLD state evidence,
not a comparable HOT speedup or visual/player validation. Raw bounded snapshots:
IMPL/build/motion-pose-repair/main-{summary,performance}.{txt,json}.
All task GameTest JVMs ended; no task client/display was started in this fix run.
Client update required for the new pose adapter. Remaining graphical/combat balance
check is explicit above; no HUMAN_CANDIDATE/full-pack claim.
