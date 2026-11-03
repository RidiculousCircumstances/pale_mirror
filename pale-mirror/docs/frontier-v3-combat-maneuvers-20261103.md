# Shared combat maneuvers — 2026-11-03

Accepted: a short gap-closing burst and reactive backward/diagonal step, with
visible preparation, bounded travel, recovery and separate cooldowns. No
invulnerability, extra weapon reach, teleportation or COLD combat. A hit already
received is not retroactively evaded. Player future input is never observed.

Shared combat retains the exact execution/body, weapon, known opponent and fixed
selected destination. A role supplies permitted maneuver kinds, navigation
knowledge and its own confirmed-harm knowledge through CombatCapability. The
shared selector ranks safe local geometry and asks the existing planner for a
bounded complete route. Shared spatial claims include other active maneuver
endpoints; a new maneuver releases its old melee standing claim.

One registered COMBAT_MANEUVER movement context/provider uses ordinary UAE,
ActorMovementProcess, geometry, courtesy and goal navigation. Its MovementPermission
requests a temporary pace from the role's ordinary pace and hashed maneuver
multipliers. Neither tactical selection nor animation writes a body position.
The role continuation delegates the bounded maneuver lifecycle to common combat,
then returns to ordinary pursuit/role work. Recovery fences the next strike or
maneuver, never ordinary pursuit after the movement closes. Arrival, interruption, target loss,
timeout, death and body loss retain their normal exact authority boundaries.

Initial world policy:4 preparation ticks,20 travel ticks maximum,6 recovery;
BURST cooldown100, BACKSTEP60, shared40. Burst requests2.2 times normal combat
pace over up to3 horizontal blocks; backstep1.5 times over1 block. Recently
confirmed harm or crowding favors backstep; a retreating/distant opponent favors
burst. Terrain, actual profile, engagement envelope, service access and peer
claims constrain both. Native launch requires the real minimum visible preparation;
canonical fast-forward cannot compress that into an instantaneous impulse.

Client presentation vocabulary is distinct from weapon attack techniques:
QUICK/LUNGE attacks and BURST/BACKSTEP movement cues. The existing bounded,
UUID/epoch/attempt-fenced cue cache/renderer is reused. Burst braces/leans forward;
backstep raises the weapon and shifts backward. Neither cue causes damage.
Network protocol2 and schema323 require the updated client and fresh test world.

Additional user-reported defect: multiple guards hesitate as the player circles.
Source-proven cause: GuardResponse.redirect invalidated a station claim whenever
the observed target CELL changed, even with the same opponent and an otherwise
legal/free station. It now checks exact opponent/execution continuity, station
availability and actual tactical reach. Target motion outside that admissible
area, expired claims, blockage and changed opponents still require reselection.
The leased sector stays stable instead of being reassigned on each cell crossing.

Native trace also exposed an unnecessary role hold through maneuver recovery;
only an OPEN maneuver now owns locomotion. Closed recovery still gates strikes
and new maneuvers. Modeled recovery test composes both weapon and maneuver
recovery and proves ordinary approach starts. Optional unattempted tactical
proposals use admission results, not mandatory physical-effect assertions.

A native same-world restart found the role-resumption boundary: a CLOSED
attempt's original execution is historical cooldown evidence, not a requirement
that the guard never resume after a meal. Shared MeleeManeuver.retainedBy validates
the same declared actor/kind/owner and monotonic generation for this closed
history; OPEN attempts still require the exact current execution and weapon.
No historical body identity is rewritten. Defensive movement has a separate
role-supplied permitsManeuverInterruption policy from strike permitsApproach:
a guard with a current declared threat may yield its exact reversible mission
route at the ordinary movement checkpoint, including patrol/search, before
normal INTERCEPT selection finishes. Equipment/access/foreign movements remain
excluded. Strike preparation still only reuses an actual INTERCEPT approach.
This prevents role redirection latency from silently consuming the harm window. The existing pocket-meal, death and
resumption test now retains a completed maneuver across snapshot and actual
common-role resumption, rejecting an OPEN attempt across generations.

Native9 reproduced a real no-motion maneuver: canonical movement was admitted,
but the budgeted ordinary ambient census did not necessarily arm its physical
navigation before the short maneuver deadline. The shared actor probe cursor now
consumes exact immutable ambient-goal/movement-order deltas and wakes the existing
observer lane. Only HOT changes compete for probes; equal goal/order, position
and COLD-clock updates do not create repeated wakes. Existing fairness and body
validation remain. This serves all movement purposes, not a combat executor.
A maneuver request explicitly carries its exact body's independently captured
supported origin. Shared combat uses that dated observation only as a planning
input, under current actuation/target/engagement permission; it never publishes
ActorLocation. Waiting for a durable inspection before each moving intent would
starve a runner which crosses a new cell before the next sensing turn. The
existing common body inspector remains the sole canonical position writer.
The modeled regression deliberately retains an older canonical travel pose and
proves maneuver selection uses the supplied origin without relocating the actor.

The final native close-contact run was interrupted by a separate field crash:
append-only field expansion met a durable canonical-first growth projection on
an old cell. New land and that old projection are disjoint. The field witness
now preserves old canonical-first causes verbatim across an exact declared
same-epoch layout extension; physical-first labour/hand effects and epoch
handoffs still wait. Every pending effect explicitly serializes its own layout
revision (witness format12, fresh world) rather than implicitly relabelling it
with the new outer layout. The native existing growth test now appends land
between intent and actual block write, NBT-roundtrips, then confirms and retires
the original effect. Focused23 field GameTests PASS; missing effect-layout tag
fails before recovery. No change to crop rates, yields, demand or staffing.

Verification and delivery
-------------------------

Source commits: adb13df4b (independent field projection correction),
9ae557ddf (shared maneuvers and pursuit). Focused final authority checks passed,
including out-of-world/engagement observed-origin refusal before transition,
without canonical position publication. Initial full integration guardrails,
check/build/package and416 required native GameTests passed. Subsequent field
serialization correction passed23 affected field GameTests; unaffected evidence
is reused. Final detached release verification is recorded in CONTINUITY.md.

Native real-client evidence under implementation build/melee-maneuvers:
- final-field12-native.json PASS13/13: actual confirmed player harm, admitted
  BACKSTEP with >=.5block same-cue body displacement, BURST>=1.5block,
  ordinary fast retreat and circling, terminal cleared response. Both guards
  moved; coarse wall-second trace found no distant stationary interval above
  one second during the circuits. Corrected centred contact sheet reviewed.
  The only later production change rejects an observed origin outside the
  world/engagement before admission; this inside-envelope positive path is
  unchanged and reused for BACKSTEP evidence.
- final-live-pursuit.json PASS13/13 on the final candidate: real harm,
  legitimate ordinary-response fallback when optional retreat is unavailable,
  actual rendered BURST/body displacement,240tick player circuit and terminal
  escape/responseclear. This is the exact final prepared artifact.
- Saved SAME-world stop10:42:47/all dimensions, fresh JVM, then
  final-live-recovery.json PASS4/4: remembered hostile player actually damaged
  without a new offence, ordinary escape and terminal responseclear. Final
  saved stop10:44:07, all dimensions; private server/client/Xvfb/ports closed.

The stricter intermediate carrier failed when its single hurt opportunity met
route PLANNING and an ordinary strike then owned the actor. Continuing ordinary
combat is the intended safe fallback, so neither optional retreat nor its cue
is demanded after every hit. Checked-in carriers now inspect reaction admission,
retain actual BURST/moving-player progression assertions, and do not turn a
policy refusal into a mandatory physical-effect error. The declared disposable
carrier also includes graceful restart and the four existing recovery actions;
accepted final evidence is the two saved-world live runs above, not a false
reclassification of the failed earlier aggregate.

A separate standalone restart attempt used an already automatically deleted
successful disposable world and failed without the graybox dimension. This was
invalid harness setup, not product recovery evidence. Later retained-world
recovery above is the actual accepted boundary. No WAL/body edits manufactured
outcomes. Lite20FPS Xvfb checks establish their native claims, not full-pack
HUMAN_CANDIDATE or M3 visual acceptance. Network2 requires client update;
schema323 and field witness12 require the fresh disposable deployment world.

Final release9ae557ddf: clean detached guardrails/build/verifyPackagedJar PASS
2m38s,902NeoForge tests0fail. All6460classes/resources equal final native
artifact; only runtime identity metadata differs. Published158validated Packwiz
inputs. MAIN fresh schema323/seed136 deployment passed preflight/deployverify
and actual summary: statusok, required/inventory conflicts0. Exact artifact and
runtime identity retained in CONTINUITY.md; source and pack tracked trees clean.
