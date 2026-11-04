# Human combat trace: slow contact and lost pursuit prediction

Human report: player made a new recording and dealt considerable bare-hand damage;
guards felt sluggish. Diagnosis only; no implementation or runtime changes.

Exact complete capture `f8defb8f-09c4-42c9-b618-b83da6eadba9`, rd,180s requested,
operator stopped,7898events/no loss. Actual692ticks/34.5965s, all player samples
SURVIVAL; no global tick stall in this interval. Current deployed production
f70feb0ec, schema332 world combat-styles-schema332-seed139-20261104/native3141150.
This is a real human moving opponent; prior stationary task-player evidence did
not establish its balance. Human health/inventory/mode/location untouched.

Observed:8bare-hand hits/8damage dealt;4guard hits/16damage returned (Nora3/Eli1).
Player ended near1.17HP; no death recorded.13attack preparations,12actual terminal
swings:4HIT,8MISS all OUT_OF_REACH;1pre-physical cancellation outside approach
allowance. It was not a failure to damage an invulnerable creative player.

Concrete causal findings:

1. Broken native target-motion input to pursuit prediction. In
FrontierV3CombatExecutor, observedMotion derives radial/lateral velocity from
retained actual positions, but MeleeManeuvers.Facts receives target.getDeltaMovement
x/z instead. MeleePursuit.lead consumes these latter values. Server player native
horizontal delta is zero in310/347position samples despite312moving position pairs;
19/22PURSUIT_RUN proposals have zero native target velocity,16of those have clearly
nonzero position-derived radial/lateral motion. Example tick27870: gap6.94,
observed separating0.2125blocks/tick, but passed native vx/vz0/0. Thus configured
run prediction usually collapses to zero for this actual moving player. Fix should
use one identity-fenced observation-derived full vector for all motion consumers,
not a new per-role predictor or omniscient future input.

2. Contact opportunity does not preserve effective closing pressure through QUICK.
At real weapon reach, contactStrike closes the exact running movement and prepares
an attack; QUICK/SWEEP both explicitly have closesDistance=false. The existing
MeleeGuidance returns immediately for them, and CombatLocomotion only gives pending
LUNGE/FOLLOWUP their attack approach gait. Ordinary mission motion can continue,
but does not gain that bounded target correction. In the trace, QUICK starts near
2.5-3blocks; ordinary punch knockback plus player retreat yields4-6blocks at impact.
Example27886: native preparation distance2.77, hit check11ticks later4.30/MISS.
28199:2.76to5.86/11ticks after another punch. Counterpressure and finite short-attack
contact guidance need coherent shared mechanics; never extend actual weapon reach
or ignore native knockback.

3. Pipeline latency compounds the contact problem.12published strikes each take
3ticks from PREPARE_SUBMITTED to CANONICAL_READY, before initial visible cue;
QUICK total observed submission-to-contact7-12ticks (0.35-0.60s); SWEEP17-18ticks.
Visible QUICK4tick windup and journal preparation overlap after canonical readiness,
so this is not evidence of two serial identical windups. The trace does not split
that initial3tick publication delay into fsync vs scheduler cost. Do not label it
slow disk without additional evidence. Safety/durable effect authority remains;
optimize the reversible preparation/admission scheduling instead of bypassing it.

4. Pursuit is intermittent and lateral recovery consumes opportunities. Eli actually
ran6.0s at observed mean0.242blocks/tick; his other non-strike/non-maneuver samples
24.3s averaged0.054. Nora had no active pursuit-run segment in this capture;28s
ordinary samples averaged0.063. Human mean0.186; actual paths/distances differ, so
these are observed displacement rates, not configured gait multipliers. Several
nearby opportunities are MANEUVER_BUSY during lateral steps, then walk/recovery.
Effort is usually75-100, so fatigue is not the main cause of those examples.
34optional maneuver refusals:24route PLANNING and10no tactical destination. Their
existence does not prove planner overload; pending route handling/fallback needs
review separately from geometry or safety admission.

5. Actual encirclement failed in this capture. Both guards within6blocks in119
sample frames; none has separation>=60degrees, maximum59.21degrees. Roles were
Nora PRESSURE/Eli FLANK_RIGHT for most of it, but declared roles are not achieved
positions. CombatFormation reassignment handles confirmed unavailable positions,
not repeated failure to gain tactical angle while the goal remains reachable.
MeleePursuit ranks flank approach and uses faulty lead input above. Correct target
motion first; then assess bounded flank progress and shared pressure commitment,
without declaring the precise residual flank cause established by angle alone.

Suggested coherent next repair: common full-vector observed target motion;
closing-to-short-strike continuity and bounded contact correction; remove extra
reversible preparation latency while retaining durable effect admission; bounded
pursuit/flank progress with no unsafe route fallback. Existing styles remain
preferences over effective mechanics, not substitutes for them. Source unchanged;
no tests/client/redeploy needed for this read-only diagnosis. Compact numeric
summaries under IMPL/build/combat-style-human-review-20261104.
