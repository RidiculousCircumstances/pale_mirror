# Live combat, motion and pose findings — 2026-11-03

Read-only investigation requested while the user manually tests. Source/release
648dc5a3f, MAIN Java110336/service110312/world139; installed SHA256
 a05d4bf7ce3ad41c2fed2c979cbaffa50cb14f393b386236cf029ed71363f939.
No source change, private client, restart or deployment in this investigation.

## General movement — runtime-confirmed cause

Player rd returned near -1469,64,-1344. Current repeated PHYSICAL
scene-resource_site_harvest turns cost350–375ms; one native complete-tick maximum
397.4ms. Fast forward is0, pending ordinary ticks1 in the snapshot. Host CPU and
free memory do not establish an external resource shortage.

One bounded45s JFR of that exact MAIN JVM was captured through the existing
capture-runtime-jfr script (pm-worldgen-performance method). IMPL evidence:
build/melee-response-audit/hot-jerks.jfr and jfr-summary.json. Of3505 server-thread
CPU samples,3307 (94.35%) include tryRetargetWorkTarget's candidate predicate.
This is inclusive CPU sampling attribution, not94% wall time or measured TPS.
The stack is AreaWorkSelection.reachableAfter -> candidate field/job state ->
GoalNavigation.canReach -> MinecraftGoalNavigation.canReach -> PedestrianTraffic
search -> PathClearance collision sweeps. The field callback checks alternatives
synchronously and retries the full pool on later turns. A blocked farm target
can therefore stall all physical residents, not just the farmer. Blame locates
this path in032a43b201/33c3d1c3c5 (Sept27/Oct1), not the new work-cue branch.

Remedy: cheap deterministic candidate ranking followed by bounded resumable
reachability through the shared navigation owner, exact worker/target/layout/
geometry fences, distinct pending/no-path outcomes, and reuse until relevant
input changes. Do not merely increase a tick allowance or put a timeout around
the full scan. No new per-farm pathfinding implementation.
The3.4GiB expanded JFR JSON was discarded after producing the compact summary;
the original26MiB JFR and diagnostic snapshots remain. Capture has ended.

## Combat — source-proven limitations, not per-attempt attribution

The last actual combat in the previous MAIN log occurred before20:05:36;
2026-11-03-7.log.gz contains MELEE_WINDUP and MELEE_CONTACT_WINDOW waits, but the
opt-in detailed attack trace is off. No current per-attempt miss-rate claim.

- prepareImpact first waits canonicalTick >= strike.impactAt (preparedAt plus
  windup), then starts a new native visible windup of the same length. Default
  QUICK4+4ticks, LUNGE6+6ticks before contact, plus admission/durability/sensing.
  At20TPS these are0.4/0.6s minimum under aligned clocks, not measured user latency.
  A single truthful preparation clock must retain durability and full visible
  telegraph without counting preparation twice.
- Lunge opportunity admits up to0.65blocks beyond ordinary weapon reach. Contact
  still requires ordinary native hitbox reach. MeleeGuidance can only correct an
  existing permitted GuardMissionLeg; absent/completed approach means no new
  lunge movement. This explains a reachable insufficient-closing failure.
- Native aim tracks only early (default elapsed ticks0..3), at12degrees per tick;
  final preparation/contact freezes facing and the last corrected station.
  Correction must stay within2 support steps of the old goal. Expanding the cone
  to170degrees did not add reach, guarantee closure or change that movement owner.
- Encounter waves space slots12ticks (0.6s at20TPS), not simultaneous converging
  attacks. Attack recovery, sensing every4ticks, and defensive maneuver recovery
  further constrain reaction. Ordinary pursuit/close approach uses WALK/CAREFUL;
  accelerated motion needs an existing lunge or cooldown-owned maneuver.

Remedy: connect the lunge's finite closing phase to the shared movement owner,
then one commit/contact/recovery sequence; preserve dodge and permission fences.
Quick contact should exploit current native reach promptly. Coordinate useful
converging attack windows rather than a fixed long per-fighter separation.
Do not compensate for global350ms stalls by blindly raising guard speed/damage.

## Models — source-confirmed seam; exact visible deformation still needs verification

HumanEraVisuals.Rig.torso directly assigns EMF_body.xRot/yRot after EMF animation,
while legs retain independent Fresh Animations transforms. The supported activator
villager.jem's body has an authored translate[0,-24,0] and torso boxes at Y11..23;
legs are separate model parts. The adapter supplies no hip-pivot compensation or
explicit baseline snapshot/restore for its overridden parts. Thus it is not a
verified anatomical torso-tilt adapter. The user's torso/leg separation report
matches this seam. Do not claim a measured exact displacement or prove stale
state merely from absence of a reset: EMF may reset animated properties itself.

Remedy: a scoped semantic pose overlay over the current base pose, correct
model-specific joint pivot and attached head/arms/item transforms; restore only
owned overrides. Preserve Fresh Animations locomotion and separate actor poses.
The same rule applies to combat and work, not just a farmer-specific tilt clamp.
No new visual/client acceptance run was claimed here.
