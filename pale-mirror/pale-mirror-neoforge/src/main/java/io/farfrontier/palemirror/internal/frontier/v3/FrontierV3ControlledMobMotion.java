package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.LocalNavigationEnvelope;
import io.farfrontier.palemirror.frontier.v3.model.WorldBounds;
import io.farfrontier.palemirror.internal.network.PaleMirrorNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
/**
 * The small, deterministic physical-motion primitive shared by v3 HOT bodies.
 *
 * <p>This is deliberately narrower than Minecraft goal AI.  A durable v3 lease chooses the
 * destination; this class performs only collision-checked local motion toward it.  In
 * particular it cannot acquire a target, use an item, attack, breed, or create a new strategic
 * decision outside the physical-intent boundary.</p>
 */
final class FrontierV3ControlledMobMotion {
    // These are per-tick distances, applied at EntityTickEvent.Pre.  The v3 executor chooses
    // an intent after a canonical tick, then this narrow actuator moves it before Minecraft's
    // entity tracking publishes the next position to remote players.
    private static final double RESIDENT_SPEED = 0.20D;
    private static final double BIOFORM_SPEED = 0.26D;
    private static final double ARRIVAL_DISTANCE = 0.35D;
    private static final double THIN_SURFACE_STEP = 0.125D;
    private static final double MAX_WALK_GRADE = 1.0D;
    private static final double VERTICAL_SPEED = 0.125D;
    private static final int MAX_PENDING_INTENTS = 4_096;
    private static final int MAX_AVOIDANCE_TURNS = 8;
    private static final int MAX_TRACE_SAMPLES = 96;
    /** Ephemeral one-tick physical intents; canonical goals/cursors remain in the domain. */
    private static final Map<Mob, MotionIntent> PENDING = new IdentityHashMap<>();
    /**
     * The latest presentation-only tending directive.  Unlike a retained checkpoint it has no
     * progress authority, but it must survive consumption of its one entity-pre turn: otherwise
     * a worker can visibly move for a tracker interpolation burst and then freeze until the
     * next durable scene callback.  Any retained checkpoint submission, conflict/release, or
     * explicit stop supersedes it.
     */
    private static final Map<Mob, MotionIntent> CONTINUOUS = new IdentityHashMap<>();
    /** One retained server-authored duty phase per body prevents stale sampling and packet churn. */
    private static final Map<Mob, String> DUTY_CUES = new IdentityHashMap<>();
    /**
     * Presentation-only arrival settling.  A server body can reach an observed station one
     * tracker update before an ordinary remote client finishes interpolating that same edge.
     * Do not start reporting the stationary duty until the authoritative body has stayed put
     * for this short, local hand-off; it has no route/cursor/lease authority.
     */
    private static final Map<Mob, StationaryPresentation> STATION_SETTLING = new IdentityHashMap<>();
    private static final int STATION_SETTLE_TICKS = 6;
    /**
     * HOT bodies deliberately retain NoAI, so their vertical travel cannot be delegated to the
     * vanilla goal loop.  Registration is weak and has no canonical meaning: it merely keeps
     * the same ordinary gravity/collision path alive at every entity-pre turn, including an
     * intentionally stationary body after a player removes its support.
     */
    private static final Map<Mob, Boolean> ORDINARY_PHYSICS = new WeakHashMap<>();
    /**
     * An exact retained ascent may span several entity turns.  Its source support is physical
     * evidence only: it lets the actuator distinguish a still-supported grade climb from a
     * player having removed that exact support midway through the same retained edge.
     */
    private static final Map<Mob, RetainedAscent> ASCENTS = new WeakHashMap<>();
    /** A crop-local tending pose is retained at one exact station, never paced by a semantic callback. */
    private static final Map<Mob, TendingPose> TENDING = new IdentityHashMap<>();
    /** The entity boundary may be observed explicitly by a test after vanilla already ran it. */
    private static final Map<Mob, AppliedIntent> LAST_ADVANCE = new IdentityHashMap<>();
    /** Ephemeral collision latitude, bounded to a retained local envelope and target. */
    private static final Map<Mob, Avoidance> AVOIDANCE = new IdentityHashMap<>();
    /** Bounded read-only evidence of actual accepted collision moves, not an alternate clock. */
    private static final Map<Mob, ArrayDeque<MotionSample>> TRACE = new WeakHashMap<>();
    private static final Map<Mob, TrackerObservation> TRACKER_OBSERVATIONS = new WeakHashMap<>();
    /** Last local actuator outcome, retained only for the read-only loaded-body diagnostic. */
    private static final Map<Mob, MotionObservation> MOTION_OBSERVATIONS = new WeakHashMap<>();

    record MotionSample(long gameTime, double x, double y, double z, double horizontalVelocity) { }
    record TrackerObservation(int calls, int impulseCalls) { }
    record MotionObservation(String status, Vec3 target, int acceptedMoves) {
        MotionObservation {
            Objects.requireNonNull(status, "motion status");
        }
        static MotionObservation idle() { return new MotionObservation("IDLE", null, 0); }
    }

    private FrontierV3ControlledMobMotion() { }

    static void moveToward(ServerLevel level, Mob actor, Vec3 target) {
        submit(level, actor, target, false, null, null, Double.MAX_VALUE, false);
    }

    /**
     * Retains the canonical frontier edge while a local physical scene executes a moving
     * tactical target.  This is a spatial safety fence only: it neither changes that target nor
     * selects an alternate route, scene position, or COLD continuation.
     */
    static void moveWithinWorldBounds(ServerLevel level, Mob actor, Vec3 target, WorldBounds bounds) {
        if (!insideWorldBounds(actor.position(), bounds)) {
            stop(actor);
            return;
        }
        submit(level, actor, target, false, null, bounds, Double.MAX_VALUE, false);
    }

    /**
     * Pursues precisely the next retained checkpoint.  It deliberately has no pacing target,
     * turnaround, or scheduler-time input: a process cadence can govern the semantic
     * checkpoint, never an actor's physical stop/start clock.  Arrival remains only observed
     * evidence for the owning process; this actuator cannot expose a later route node.
     */
    static void pursueRetainedCheckpoint(ServerLevel level, Mob actor, Vec3 next) {
        // Keep precisely this retained edge physically live between semantic scene turns. A
        // one-shot queued intent made real body movement inherit the coarse scene cadence,
        // producing the user-visible stop/start regression; this does not expose a new route
        // node or advance the canonical cursor.
        // Crop tending is an intentionally local presentation directive.  A newly retained
        // route edge supersedes it: retaining the old pose here made the body circle its former
        // crop while the canonical job already declared TRAVELLING toward the next one.
        TENDING.remove(actor);
        STATION_SETTLING.remove(actor);
        // A crop gesture is a station-local action.  Vanilla keeps a received swing alive for
        // several render turns after the semantic crop receipt; leaving it intact when the
        // next retained edge becomes active makes the same physical body visibly harvest while
        // walking.  Retiring that presentation state at the sole travel-authority hand-off is
        // not a position/velocity rewrite and cannot advance a cursor.
        showTravellingDuty(level, actor);
        submit(level, actor, next, true, null, null, Double.MAX_VALUE, false);
    }

    /**
     * Ends only a station-local visual gesture.  This has no movement, lease, or canonical
     * progress authority; it is the presentation half of leaving a declared work station.
     */
    static void clearStationWorkGesture(ServerLevel level, Mob actor) {
        DUTY_CUES.remove(actor);
        STATION_SETTLING.remove(actor);
        actor.swinging = false;
        actor.swingTime = 0;
        PaleMirrorNetwork.sendStationWorkGesture(level, actor, false);
    }

    /** Starts one bounded client-visible work cue at an already-held crop station. */
    static void showStationWorkGesture(ServerLevel level, Mob actor) {
        STATION_SETTLING.remove(actor);
        DUTY_CUES.put(actor, "HARVESTING:PREPARED");
        PaleMirrorNetwork.sendStationWorkGesture(level, actor, true);
    }

    /**
     * Announces an already observed crop station before its due work turn.  It is a phase
     * observation only; crop mutation and the visible gesture remain owned by the work branch.
     */
    static void showHarvestStationDuty(ServerLevel level, Mob actor) {
        if (!settledForStationPresentation(level, actor)) return;
        actor.swinging = false;
        actor.swingTime = 0;
        if (!"HARVESTING:PREPARED".equals(DUTY_CUES.put(actor, "HARVESTING:PREPARED"))) {
            PaleMirrorNetwork.sendStationDutyCue(level, actor, "HARVESTING:PREPARED");
        }
    }

    /** Announces the already selected retained edge exactly once; it has no route authority. */
    private static void showTravellingDuty(ServerLevel level, Mob actor) {
        TENDING.remove(actor);
        STATION_SETTLING.remove(actor);
        actor.swinging = false;
        actor.swingTime = 0;
        if (!"TRAVELLING:PREPARED".equals(DUTY_CUES.put(actor, "TRAVELLING:PREPARED"))) {
            PaleMirrorNetwork.sendStationDutyCue(level, actor, "TRAVELLING:PREPARED");
        }
    }

    /**
     * The station phase is truthful only after the authoritative body has stopped on that
     * station long enough for its ordinary tracker update to reach remote clients.  The count
     * is deliberately local and ephemeral: a restart, route hand-off, or body replacement
     * starts it over and it cannot influence semantic work timing.
     */
    private static boolean settledForStationPresentation(ServerLevel level, Mob actor) {
        Vec3 position = actor.position();
        StationaryPresentation prior = STATION_SETTLING.get(actor);
        boolean consecutive = prior != null && prior.gameTime() + 1L == level.getGameTime()
                && prior.position().distanceToSqr(position) <= 1.0E-6D
                && actor.getDeltaMovement().horizontalDistanceSqr() <= 1.0E-6D;
        int turns = consecutive ? prior.turns() + 1 : 1;
        STATION_SETTLING.put(actor, new StationaryPresentation(level.getGameTime(), position, turns));
        return turns >= STATION_SETTLE_TICKS;
    }

    /**
     * Moves only inside the immutable HOT latitude retained by the current directive.
     *
     * <p>This is deliberately an actuator boundary, not a local path planner: it may choose a
     * collision-free intermediate body column in the supplied envelope, but it never changes
     * the directive's destination/checkpoint.  The caller still commits canonical progress only
     * after observing that exact checkpoint.</p>
     */
    static void moveWithinEnvelope(ServerLevel level, Mob actor, Vec3 target, LocalNavigationEnvelope envelope) {
        if (!insideEnvelope(level, actor, target, envelope) || !insideEnvelope(target, envelope)) {
            stop(actor);
            return;
        }
        submit(level, actor, target, false, envelope, null, Double.MAX_VALUE, false);
    }

    /** Same bounded actuator, but its endpoint remains live until an exact support observation. */
    static void moveWithinSemanticEnvelope(ServerLevel level, Mob actor, Vec3 target, LocalNavigationEnvelope envelope) {
        if (!insideEnvelope(level, actor, target, envelope) || !insideEnvelope(target, envelope)) { stop(actor); return; }
        submit(level, actor, target, false, envelope, null, Double.MAX_VALUE, true);
    }

    /**
     * Keeps one already-declared semantic edge physically live until its exact endpoint is
     * observed.  This is the retained-edge counterpart of {@link #pursueRetainedCheckpoint}:
     * unlike the generic one-turn semantic-envelope submission, it cannot inherit the scene
     * callback cadence after a crop receipt.  The immutable envelope is still the complete
     * spatial authority, so continuity does not widen the route or permit a later checkpoint.
     */
    static void pursueRetainedSemanticCheckpoint(ServerLevel level, Mob actor, Vec3 target,
                                                 LocalNavigationEnvelope envelope) {
        if (!insideEnvelope(level, actor, target, envelope) || !insideEnvelope(target, envelope)) {
            stop(actor);
            return;
        }
        TENDING.remove(actor);
        STATION_SETTLING.remove(actor);
        showTravellingDuty(level, actor);
        submit(level, actor, target, true, envelope, null, Double.MAX_VALUE, true);
    }

    /**
     * Follows a continuously moving local target without treating the ordinary arrival radius
     * as a stop-and-go patrol cadence.  This is presentation-only local motion: it does not
     * choose a route, change a cursor, or create a second canonical movement authority.
     */
    static void followContinuously(ServerLevel level, Mob actor, Vec3 target) {
        TENDING.remove(actor);
        submit(level, actor, target, true, null, null, Double.MAX_VALUE, false);
    }

    /** Retains only the current crop station; it never creates a local orbit or a job cursor. */
    static void tendCurrentCrop(ServerLevel level, Mob actor, io.farfrontier.palemirror.frontier.v3.model.BlockPosition crop) {
        TendingPose pose = new TendingPose(crop.x() + .5D, crop.y(), crop.z() + .5D);
        TENDING.put(actor, pose);
        submit(level, actor, pose.target(level.getGameTime()), true, null, null, Double.MAX_VALUE, false);
    }

    /** Holds an already observed retained checkpoint visibly active without exposing another edge. */
    static void holdRetainedCheckpoint(ServerLevel level, Mob actor, Vec3 checkpoint) {
        TendingPose pose = new TendingPose(checkpoint.x, checkpoint.y, checkpoint.z);
        TENDING.put(actor, pose);
        submit(level, actor, pose.target(level.getGameTime()), true, null, null, Double.MAX_VALUE, false);
    }

    private static void submit(ServerLevel level, Mob actor, Vec3 target, boolean continuous, LocalNavigationEnvelope envelope,
                               WorldBounds bounds, double maximumStep, boolean exactEndpoint) {
        // A crop-local pose or legacy checkpoint directive supersedes the HOT-only
        // Minecraft path. Never let two physical actuators drive the same owned body.
        FrontierV3GoalNavigation.stop(actor);
        actor.setNoAi(true);
        // NoAI suppresses Minecraft's goal selector, not physical gravity.  Reassert the latter
        // because a retained entity can carry an old mod/AI no-gravity flag across a HOT handoff.
        actor.setNoGravity(false);
        actor.getNavigation().stop();
        Vec3 delta = target.subtract(actor.position());
        double horizontalDistance = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        if (!continuous && (exactEndpoint ? horizontalDistance == 0.0D && delta.y == 0.0D
                : horizontalDistance <= ARRIVAL_DISTANCE && Math.abs(delta.y) <= ARRIVAL_DISTANCE)) { stop(actor); return; }
        // A HOT adapter may only follow the next retained pedestrian edge.  Existing callers
        // that still use same-level local goals remain unaffected; a larger vertical gap is not
        // a licence to fly or to infer a route and is therefore left for the canonical planner.
        if (!walkGradeAllowed(actor.position(), target, exactEndpoint, envelope)) { stop(actor); return; }
        if (!Double.isFinite(maximumStep) || maximumStep <= 0.0D) throw new IllegalArgumentException("motion maximum step");
        MotionIntent pending = PENDING.get(actor);
        if (continuous && !CONTINUOUS.containsKey(actor) && CONTINUOUS.size() >= MAX_PENDING_INTENTS) return;
        if (!continuous) TENDING.remove(actor);
        MotionIntent replacement = new MotionIntent(level.getGameTime(), target, continuous, envelope, bounds, maximumStep, exactEndpoint);
        RetainedAscent ascent = ASCENTS.get(actor);
        if (ascent != null && !ascent.target().equals(target)) ASCENTS.remove(actor);
        if (continuous) CONTINUOUS.put(actor, replacement);
        else CONTINUOUS.remove(actor);
        Avoidance avoidance = AVOIDANCE.get(actor);
        if (avoidance != null && !avoidance.target().equals(target)) AVOIDANCE.remove(actor);
        // The normal production order is canonical executor (post tick) → entity pre-tick on
        // the next server tick. Keep an already-due *same* intent if another observer runs
        // before that pre-tick: overwriting it with a new timestamp creates a permanent
        // stop/go loop whose outcome depends on event callback order rather than physical state.
        // A changed retained directive is different: retaining a due crop-tending intent after
        // the canonical cursor selected its next exact edge leaves the actor visibly pinned to
        // the old station forever. The latest exact directive must supersede that local pose;
        // it neither chooses another route nor advances the cursor.
        if (pending != null && pending.applyAtGameTime() <= level.getGameTime() + 1L
                && sameDirective(pending, replacement)) {
            return;
        }
        if (PENDING.size() < MAX_PENDING_INTENTS || pending != null) {
            // This is eligible at the next entity-pre boundary, which is ordinarily the next
            // server tick because the canonical executor runs at post-tick. Using the current
            // canonical time also makes a GameTest's legitimate catch-up callbacks execute the
            // same retained one-tick actuator turns rather than stranding one future intent.
            PENDING.put(actor, replacement);
        }
    }

    /** Runs from the normal server entity-tick boundary, before tracker replication. */
    static void advance(Mob actor) {
        // Prefer the latest retained local target over a stale queued copy.
        MotionIntent intent = CONTINUOUS.get(actor);
        if (intent == null) intent = PENDING.get(actor);
        if (intent == null) return;
        if (!(actor.level() instanceof ServerLevel level) || actor.isRemoved() || !actor.isAlive()) {
            PENDING.remove(actor); CONTINUOUS.remove(actor); LAST_ADVANCE.remove(actor); AVOIDANCE.remove(actor); ASCENTS.remove(actor); return;
        }
        // A direct diagnostic/GameTest call can legitimately occur after the registered
        // EntityTick.Pre callback. It is an observation aid, never a second movement authority;
        // applying the same persistent tending directive twice would create a synthetic
        // double-speed body. Identity, rather than game time, is deliberate: bounded tests may
        // exercise several newly submitted physical turns at one fixed game-time value.
        AppliedIntent previous = LAST_ADVANCE.get(actor);
        if (previous != null && intent == previous.intent() && level.getGameTime() == previous.gameTime()) return;
        if (level.getGameTime() < intent.applyAtGameTime()) return;
        // Apply the station-local pose at the ordinary entity turn. It remains bounded by the
        // immutable current crop/checkpoint and does not introduce a route or semantic clock.
        // A work dwell is visibly active through the executor's interaction, not synthetic
        // circular locomotion that would read as filler wandering.
        TendingPose tending = TENDING.get(actor);
        if (tending != null) {
            intent = new MotionIntent(level.getGameTime(), tending.target(level.getGameTime()), true, null, null, Double.MAX_VALUE, false);
            CONTINUOUS.put(actor, intent);
        }
        PENDING.remove(actor);
        LAST_ADVANCE.put(actor, new AppliedIntent(intent, level.getGameTime()));
        apply(level, actor, intent.target(), intent.continuous(), intent.envelope(), intent.bounds(), intent.maximumStep(), intent.exactEndpoint());
    }

    /**
     * The normal entity-pre boundary owns the physical turn, ahead of vanilla tracking.
     * Driving this after ServerTick moved accepted bodies behind the tracker and produced the
     * client-visible stop/start cadence seen after scheduler/lease integration.
     */
    static void advanceAtEntityBoundary(Mob actor) {
        if (ORDINARY_PHYSICS.containsKey(actor)) advanceOrdinaryGravity(actor);
        advance(actor);
    }

    static void observeVanillaTracker(Mob actor) {
        if (!CONTINUOUS.containsKey(actor) && !PENDING.containsKey(actor)) return;
        TrackerObservation prior = TRACKER_OBSERVATIONS.getOrDefault(actor, new TrackerObservation(0, 0));
        TRACKER_OBSERVATIONS.put(actor, new TrackerObservation(prior.calls() + 1, prior.impulseCalls() + (actor.hasImpulse ? 1 : 0)));
    }

    static TrackerObservation trackerObservation(Mob actor) {
        return TRACKER_OBSERVATIONS.getOrDefault(actor, new TrackerObservation(0, 0));
    }

    /** Bounded read-only actuator evidence; it never supplies a target or route to a caller. */
    static MotionObservation motionObservation(Mob actor) {
        return MOTION_OBSERVATIONS.getOrDefault(actor, MotionObservation.idle());
    }

    static boolean ordinaryPhysicsRegistered(Mob actor) { return ORDINARY_PHYSICS.containsKey(actor); }

    /** Compatibility hook for direct tests; production has one entity-pre movement authority. */
    static void advanceContinuously(ServerLevel level) {
        // PENDING and CONTINUOUS have independent, bounded admission. A snapshot makes a
        // collision/release retirement local to this physical turn; no world scan or load is
        // performed. A retained edge is driven at the same cadence as a local tending pose.
        java.util.Set<Mob> actors = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        actors.addAll(PENDING.keySet()); actors.addAll(CONTINUOUS.keySet());
        for (Mob actor : actors) {
            if (actor.level() != level) continue;
            TendingPose pose = TENDING.get(actor);
            if (pose == null) {
                advance(actor);
                continue;
            }
            MotionIntent turn = new MotionIntent(level.getGameTime(), pose.target(level.getGameTime()), true, null, null, Double.MAX_VALUE, false);
            PENDING.remove(actor);
            CONTINUOUS.put(actor, turn);
            advance(actor);
        }
    }

    static void stop(Mob actor) {
        FrontierV3GoalNavigation.stop(actor);
        retireLocalActuation(actor);
        actor.setDeltaMovement(Vec3.ZERO);
        actor.stopInPlace();
    }

    /** Retires only this actuator; acquiring a native path must not retire its route owner. */
    static void retireLocalActuation(Mob actor) {
        boolean moving = PENDING.containsKey(actor) || CONTINUOUS.containsKey(actor);
        PENDING.remove(actor);
        CONTINUOUS.remove(actor);
        TENDING.remove(actor);
        DUTY_CUES.remove(actor);
        STATION_SETTLING.remove(actor);
        LAST_ADVANCE.remove(actor);
        ASCENTS.remove(actor);
        MOTION_OBSERVATIONS.remove(actor);
        // A stop retires target authority but never rewrites observed physical position or the
        // canonical cursor.
        AVOIDANCE.remove(actor);
        // The prior authority may already have submitted a collision move or left an ordinary
        // Minecraft velocity on the body.  Cancelling only our queued intent lets that residual
        // velocity carry a newly leased worker across its retained support between the durable
        // hand-off and the next scene tick.  Stopping is the authority-transfer boundary: it
        // freezes motion, but never rewrites the observed physical position or canonical cursor.
        if (moving) {
            actor.setDeltaMovement(Vec3.ZERO);
            actor.stopInPlace();
        }
    }

    /**
     * Applies only the vanilla-style vertical gravity/collision half that a NoAI Mob skips.
     * This has no lateral target, route, cursor, or canonical-progress authority.  It exists so
     * a retained HOT body whose real support is removed falls and takes ordinary collision/fall
     * outcomes instead of retaining the last projected Y coordinate as a hover datum.
     */
    static void advanceOrdinaryGravity(Mob actor) {
        if (actor == null || actor.isRemoved() || !actor.isAlive() || actor.isNoGravity()) return;
        Vec3 before = actor.position();
        Vec3 velocity = actor.getDeltaMovement();
        double vertical = Math.max(-0.98D, (velocity.y - 0.08D) * 0.98D);
        actor.move(MoverType.SELF, new Vec3(0.0D, vertical, 0.0D));
        boolean moved = actor.position().y < before.y - 1.0E-8D;
        // Calling Entity.move directly at this narrow NoAI boundary does not reliably refresh
        // the vanilla onGround flag before the next tracker turn.  Collision itself is the
        // authoritative fact: a downward intent that made no vertical progress struck the
        // retained floor and must not accumulate a synthetic fall velocity.  Conversely an
        // unsupported body still moves downward and retains ordinary gravity/fall behavior.
        boolean downwardCollision = vertical < 0.0D && !moved;
        actor.setDeltaMovement(velocity.x, actor.onGround() || downwardCollision ? 0.0D : vertical, velocity.z);
        if (moved) publishAcceptedMove(actor);
    }

    /**
     * Restores the ordinary vertical physics path without selecting a movement target.  The
     * actual fall turn is intentionally the next entity-pre boundary: invoking it from the
     * server-post executor as well would double-integrate gravity for a frequently probed body.
     */
    static void restoreOrdinaryPhysics(Mob actor) {
        if (actor == null) return;
        actor.setNoGravity(false);
        // Minecraft persists fall distance with the exact body, while our no-AI bridge is
        // re-registered only after a JVM restart.  A body observed already resting on a real
        // support has no in-flight fall to continue; letting its historical counter reach the
        // first bridge move turns an old physical observation into a new, uncaused death.
        // Conversely an airborne body keeps its counter and ordinary fall outcome intact.
        if (!ORDINARY_PHYSICS.containsKey(actor) && actor.level() instanceof ServerLevel level
                && standingOnPhysicalSupport(level, actor)) actor.fallDistance = 0.0F;
        ORDINARY_PHYSICS.put(actor, Boolean.TRUE);
    }

    private static boolean standingOnPhysicalSupport(ServerLevel level, Mob actor) {
        return !level.noCollision(actor, actor.getBoundingBox().move(0.0D, -0.01D, 0.0D));
    }

    /**
     * Bounded read-only inspection for a HOT-scene diagnostic.  It exposes only whether this
     * actuator still owns a one-tick intent and never supplies a destination to any caller.
     */
    static String readiness(Mob actor) {
        MotionIntent intent = PENDING.get(actor);
        return intent == null ? "IDLE" : "PENDING_AT_" + intent.applyAtGameTime();
    }

    /** Retained only for bounded diagnostics/tests; callers cannot mutate motion through it. */
    static List<MotionSample> trace(Mob actor) {
        ArrayDeque<MotionSample> samples = TRACE.get(actor);
        return samples == null ? List.of() : List.copyOf(samples);
    }

    private static void apply(ServerLevel level, Mob actor, Vec3 target, boolean continuous, LocalNavigationEnvelope envelope,
                              WorldBounds bounds, double maximumStep, boolean exactEndpoint) {
        Vec3 delta = target.subtract(actor.position());
        double horizontalDistance = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        if (!continuous && (exactEndpoint ? horizontalDistance == 0.0D && delta.y == 0.0D
                : horizontalDistance <= ARRIVAL_DISTANCE && Math.abs(delta.y) <= ARRIVAL_DISTANCE)) {
            actor.stopInPlace(); observeMotion(actor, "ARRIVED", target); return;
        }
        if (!walkGradeAllowed(actor.position(), target, exactEndpoint, envelope)) {
            observeMotion(actor, "REJECTED_GRADE", target); return;
        }
        // A one-block retained ascent is an ordinary collision move, but it cannot share the
        // same shallow diagonal vector as horizontal walking: that lets a NoAI body slide into
        // the lower adjacent column beneath the named higher support.  Complete the bounded
        // vertical half in the current clear column first, then walk the already-retained X/Z
        // edge.  This neither changes the target nor creates a stair/side-route search.
        if (delta.y > (exactEndpoint ? 0.0D : ARRIVAL_DISTANCE)) {
            observeMotion(actor, "ASCENDING", target);
            settleExactAscent(level, actor, target, delta.y);
            // Gravity runs before this actuator. Once the retained lift reaches the target
            // height, take the collision-checked lateral half in this same turn. Returning
            // unconditionally makes an exact endpoint oscillate: gravity lowers the body,
            // ascent restores it, and the next turn repeats without ever walking forward.
            // Recompute from the actual collision result; a blocked/incomplete lift must not
            // authorize lateral travel or a fabricated arrival.
            delta = target.subtract(actor.position());
            if (delta.y > (exactEndpoint ? 0.0D : ARRIVAL_DISTANCE)) return;
            horizontalDistance = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        }
        if (exactEndpoint ? horizontalDistance == 0.0D : horizontalDistance <= 1.0E-8D) {
            observeMotion(actor, "DESCENDING", target);
            settleExactDescent(actor, delta.y, exactEndpoint);
            return;
        }
        // A descending retained edge is a physical walk-off, not a downward impulse.  Pushing
        // into the lower block while the body still overlaps the upper support makes vanilla
        // collision cancel the whole X/Z step at a one-block lip.  Keep the exact retained
        // lateral edge, then a later exact-X/Z turn settles vertically onto the declared lower
        // support. Ascents remain bounded controlled lifts; neither case can invent an alternate
        // edge.
        double speed = Math.min(walkingSpeed(actor), Math.min(maximumStep, horizontalDistance));
        // An envelope is not permission to lift through arbitrary air columns.  It may climb
        // only when the next horizontal body column has a named higher support; that admits a
        // retained stair/grade while refusing an early climb toward a distant checkpoint.
        BlockPosition currentSupport = support(actor.position());
        int ascentX = (int) Math.floor(actor.getX() + Math.signum(delta.x) * .5D);
        int ascentZ = (int) Math.floor(actor.getZ() + Math.signum(delta.z) * .5D);
        boolean localAscent = envelope != null && (envelope.contains(new BlockPosition(ascentX, currentSupport.y() + 1, ascentZ))
                || emptyCurrentUpperCell(level, actor, currentSupport)
                && envelope.contains(new BlockPosition(ascentX, currentSupport.y(), ascentZ)));
        double vertical = envelope != null ? localAscent && delta.y > 0.0D ? Math.min(delta.y, VERTICAL_SPEED) : 0.0D
                : delta.y < 0.0D ? 0.0D : Math.min(delta.y, VERTICAL_SPEED);
        Vec3 direct = new Vec3(delta.x / horizontalDistance * speed, vertical, delta.z / horizontalDistance * speed);
        // A retained operation/assembly edge has one persisted next support. Only a directive
        // that carries an explicit local envelope may try a collision-free lateral body column;
        // the exact checkpoint remains unchanged and the caller alone observes its arrival.
        // Other retained travel and ambient presentation still pause at an obstruction rather
        // than inventing an alternate route or leaving their retained hand-off column.
        Avoidance avoidance = envelope == null ? null : AVOIDANCE.get(actor);
        if (avoidance != null && !avoidance.target().equals(target)) {
            AVOIDANCE.remove(actor);
            avoidance = null;
        }
        List<Vec3> candidates = motionCandidates(direct, continuous, envelope != null);
        if (avoidance != null) {
            List<Vec3> retainedCandidates = new ArrayList<>(candidates.size() + 1);
            retainedCandidates.add(avoidance.step());
            retainedCandidates.addAll(candidates);
            candidates = retainedCandidates;
        }
        for (Vec3 step : candidates) {
            // At the lip of a named stair the body needs one vector whose sampled feet are
            // still in the old X column while its collision box enters the next upper support.
            // The already-verified localAscent is that exact support; it is not a free-space
            // bypass or alternate destination.
            if ((envelope != null && !insideEnvelope(actor.position().add(step), envelope) && !localAscent)
                    || bounds != null && !insideWorldBounds(actor.position().add(step), bounds)) continue;
            // `Entity.move` resolves terrain but permits living bodies to overlap and lets
            // vanilla's later push-out turn decide their fate.  A retained Frontier edge has
            // no authority to create that collision: the exact checkpoint remains owned by
            // the caller, so a body waits locally until the observed column clears instead of
            // selecting a side-step, pushing another retained actor, or rewriting position.
            if (livingBodyOccupies(level, actor, actor.getBoundingBox().move(step))) continue;
            Vec3 before = actor.position();
            actor.move(MoverType.SELF, step);
            Vec3 moved = actor.position().subtract(before);
            // A collision may yield a token tangential slide from an attempted diagonal while
            // making no meaningful part of that candidate.  Treating that as success starves
            // the following bounded pure-lateral alternative forever at a block corner.
            // This concerns only physical vector execution; it never changes the retained
            // checkpoint, port, intent, or local envelope.
            if (moved.horizontalDistanceSqr() > 1.0E-8D
                    && moved.horizontalDistanceSqr() < step.horizontalDistanceSqr() * 0.25D) continue;
            if (moved.x * moved.x + moved.z * moved.z <= 1.0E-8D) {
                // This is still the same retained X/Z edge, not a new route: it is only the
                // bounded collision step needed to enter an exact thin support such as the
                // graybox infection/route surface. A full block fails noCollision here; a
                // lateral candidate exists only for non-canonical continuous ambience.
                Vec3 lifted = step.add(0.0D, THIN_SURFACE_STEP, 0.0D);
                if ((envelope != null && !insideEnvelope(actor.position().add(lifted), envelope) && !localAscent)
                        || bounds != null && !insideWorldBounds(actor.position().add(lifted), bounds)
                        || !level.noCollision(actor, actor.getBoundingBox().move(lifted))
                        || livingBodyOccupies(level, actor, actor.getBoundingBox().move(lifted))) continue;
                before = actor.position(); actor.move(MoverType.SELF, lifted); moved = actor.position().subtract(before);
                if (moved.x * moved.x + moved.z * moved.z <= 1.0E-8D) continue;
            }
            actor.setYRot((float) Math.toDegrees(Math.atan2(-moved.x, moved.z)));
            actor.yBodyRot = actor.getYRot();
            boolean continuingAvoidance = avoidance != null && avoidance.step().equals(step);
            if (envelope != null && (continuingAvoidance || isPureLateral(step, direct))) {
                int remaining = continuingAvoidance ? avoidance.remainingTurns() - 1 : MAX_AVOIDANCE_TURNS;
                if (remaining > 0) AVOIDANCE.put(actor, new Avoidance(step, target, remaining));
                else AVOIDANCE.remove(actor);
            } else {
                AVOIDANCE.remove(actor);
            }
            // Entity.move is the authoritative collision result.  Do not publish that already
            // applied displacement again as ordinary velocity: a NoAI body does not consume a
            // navigation velocity like vanilla AI does, and the extra motion packet caused the
            // remote body to decay between sparse tracker positions.  The normal tracker sees
            // this real server position on the same tick through hasImpulse; no client-only
            // interpolation or second movement calculation is involved.
            publishAcceptedMove(actor);
            recordMove(level, actor, moved);
            acceptedMotion(actor, target);
            return;
        }
        observeMotion(actor, "NO_MOVABLE_CANDIDATE", target);
    }

    private static boolean livingBodyOccupies(ServerLevel level, Mob actor, AABB candidate) {
        return !level.getEntities(actor, candidate.inflate(0.001D), entity -> entity instanceof LivingEntity living
                && living.isAlive() && !living.isSpectator()).isEmpty();
    }

    /**
     * The retained route bounds named support grades, while a collision-safe local step may
     * leave the real feet one thin-surface increment above that support.  The next grade-one
     * descent must not be rejected as a grade greater than one before its vertical half runs.
     * Only an exact edge with its already-declared envelope receives that increment; ordinary
     * goals and unbounded physical gaps keep their previous limits.
     */
    static boolean walkGradeAllowed(Vec3 feet, Vec3 target, boolean exactEndpoint,
                                    LocalNavigationEnvelope envelope) {
        double allowance = exactEndpoint ? (envelope == null ? 0.0D : THIN_SURFACE_STEP) : ARRIVAL_DISTANCE;
        return Math.abs(target.y - feet.y) <= MAX_WALK_GRADE + allowance + 1.0E-8D;
    }

    /**
     * A single exact vector is deliberate for both retained travel and local ambience.  The
     * boolean remains part of the intent because it controls arrival behaviour, not permission
     * to choose an alternate spatial edge.
     */
    static List<Vec3> motionCandidates(Vec3 direct, boolean continuous) {
        return motionCandidates(direct, continuous, false);
    }

    private static List<Vec3> motionCandidates(Vec3 direct, boolean continuous, boolean localEnvelope) {
        if (direct == null) throw new IllegalArgumentException("motion direct vector is required");
        if (localEnvelope && direct.horizontalDistanceSqr() > 1.0E-8D) {
            double horizontal = Math.sqrt(direct.horizontalDistanceSqr());
            double side = Math.min(horizontal, 0.125D);
            double leftX = -direct.z / horizontal * side, leftZ = direct.x / horizontal * side;
            return List.of(direct, new Vec3(direct.x + leftX, direct.y, direct.z + leftZ),
                    new Vec3(direct.x - leftX, direct.y, direct.z - leftZ),
                    new Vec3(leftX, 0.0D, leftZ), new Vec3(-leftX, 0.0D, -leftZ));
        }
        return List.of(direct);
    }

    /**
     * Completes only the vertical half of an already-reached retained descending edge.
     * NoAI bodies do not reliably run the vanilla travel/gravity path, so merely waiting after
     * the horizontal walk-off can leave a body hovering at the old datum.  This is deliberately
     * staged: an actor never applies a downward vector while its body can still collide with the
     * upper ledge, and it cannot use this branch to choose a different X/Z position.
     */
    private static void settleExactDescent(Mob actor, double verticalDelta, boolean exactEndpoint) {
        if (verticalDelta >= (exactEndpoint ? 0.0D : -ARRIVAL_DISTANCE)) return;
        Vec3 before = actor.position();
        actor.move(MoverType.SELF, new Vec3(0.0D, -Math.min(Math.abs(verticalDelta), VERTICAL_SPEED), 0.0D));
        if (actor.position().y < before.y - 1.0E-8D) publishAcceptedMove(actor);
    }

    /** Moves only upward through the current exact clear body column before one retained step. */
    private static void settleExactAscent(ServerLevel level, Mob actor, Vec3 target, double verticalDelta) {
        RetainedAscent prior = ASCENTS.get(actor);
        // Integer feet rounding points one block *below* a fractional support
        // such as farmland. Use Minecraft's observed collision support instead.
        BlockPos sourceSupport = prior == null ? actor.getOnPos() : prior.sourceSupport();
        // A controlled lift is a grade move from one real physical support, never a flight
        // response to an absent or player-destroyed floor.  This keeps a retained climb live
        // across ordinary gravity while immediately returning the body to vanilla falling when
        // that exact support disappears.
        // The shared standing provider accepts any non-empty collision support, including the
        // deliberately exact farmland under a harvest crop.  `isFaceSturdy` is stricter than
        // that provider and rejects farmland, leaving ordinary gravity to create a perpetual
        // apparent ascent at a same-level field edge.  Use the same physical support notion:
        // this still rejects air, pass-through decoration and a removed floor, without adding
        // a coordinate exception or alternate path.
        if (prior == null && !standingOnPhysicalSupport(level, actor)
                || level.getBlockState(sourceSupport).getCollisionShape(level, sourceSupport).isEmpty()) {
            ASCENTS.remove(actor);
            return;
        }
        Vec3 before = actor.position();
        actor.move(MoverType.SELF, new Vec3(0.0D, Math.min(verticalDelta, VERTICAL_SPEED), 0.0D));
        if (actor.position().y > before.y + 1.0E-8D) {
            // The ordinary gravity half has already run at this entity boundary.  Retaining
            // its downward velocity would make it accumulate across the next bounded lift,
            // so a real grade-one walker can never clear an otherwise open named support.
            // Clearing only that consumed vertical impulse preserves normal gravity on every
            // later turn without a retained ascent, including an observed support loss.
            actor.setDeltaMovement(actor.getDeltaMovement().x, 0.0D, actor.getDeltaMovement().z);
            ASCENTS.put(actor, new RetainedAscent(target, sourceSupport));
            publishAcceptedMove(actor);
        }
    }

    /** Requests vanilla's normal position-delta publication after an accepted move. */
    private static void publishAcceptedMove(Mob actor) {
        actor.hasImpulse = true;
    }

    private static void recordMove(ServerLevel level, Mob actor, Vec3 moved) {
        ArrayDeque<MotionSample> samples = TRACE.computeIfAbsent(actor, ignored -> new ArrayDeque<>());
        if (samples.size() == MAX_TRACE_SAMPLES) samples.removeFirst();
        samples.addLast(new MotionSample(level.getGameTime(), actor.getX(), actor.getY(), actor.getZ(), Math.sqrt(moved.horizontalDistanceSqr())));
    }

    private static void observeMotion(Mob actor, String status, Vec3 target) {
        MotionObservation prior = motionObservation(actor);
        MOTION_OBSERVATIONS.put(actor, new MotionObservation(status, target, prior.acceptedMoves()));
    }

    private static void acceptedMotion(Mob actor, Vec3 target) {
        MotionObservation prior = motionObservation(actor);
        MOTION_OBSERVATIONS.put(actor, new MotionObservation("ACCEPTED", target, Math.addExact(prior.acceptedMoves(), 1)));
    }

    private static double walkingSpeed(Mob actor) { return actor instanceof Zombie ? BIOFORM_SPEED : RESIDENT_SPEED; }

    private static double horizontalDistance(Vec3 left, Vec3 right) {
        double x = left.x - right.x, z = left.z - right.z;
        return Math.sqrt(x * x + z * z);
    }

    private static boolean isPureLateral(Vec3 step, Vec3 direct) {
        return Math.abs(step.x * direct.x + step.z * direct.z) <= 1.0E-8D
                && step.horizontalDistanceSqr() > 1.0E-8D;
    }

    /**
     * A vanilla stair begins collision ascent just before the body crosses the next integer
     * X/Z column.  Admit that transient only when the retained target direction reaches the
     * one named upper support immediately ahead; this cannot select a side route or remote
     * coordinate.
     */
    static boolean insideEnvelope(ServerLevel level, Mob actor, Vec3 target, LocalNavigationEnvelope envelope) {
        if (insideEnvelope(actor.position(), envelope)) return true;
        Vec3 delta = target.subtract(actor.position());
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        if (horizontal <= 1.0E-8D) return false;
        BlockPosition lower = support(actor.position());
        int nextX = (int) Math.floor(actor.getX() + Math.signum(delta.x) * .5D);
        int nextZ = (int) Math.floor(actor.getZ() + Math.signum(delta.z) * .5D);
        return delta.y > 0.0D && envelope.contains(new BlockPosition(nextX, lower.y() + 1, nextZ))
                || emptyCurrentUpperCell(level, actor, lower)
                && envelope.contains(new BlockPosition(nextX, lower.y(), nextZ));
    }

    /** Package-visible exact horizontal conversion used by the assault release regression. */
    static boolean insideWorldBounds(Vec3 feet, WorldBounds bounds) {
        return bounds.contains(new BlockPosition((int) Math.floor(feet.x), 0, (int) Math.floor(feet.z)));
    }

    private static boolean emptyCurrentUpperCell(ServerLevel level, Mob actor, BlockPosition support) {
        BlockPos upper = new BlockPos((int) Math.floor(actor.getX()), support.y(), (int) Math.floor(actor.getZ()));
        return level.getBlockState(upper).getCollisionShape(level, upper).isEmpty();
    }

    private static boolean insideEnvelope(Vec3 feet, LocalNavigationEnvelope envelope) {
        BlockPosition lower = support(feet);
        return envelope.contains(lower) || envelope.contains(new BlockPosition(lower.x(), lower.y() + 1, lower.z()));
    }

    private static BlockPosition support(Vec3 feet) {
        return new BlockPosition((int) Math.floor(feet.x), (int) Math.floor(feet.y) - 1, (int) Math.floor(feet.z));
    }

    static boolean sameDirective(MotionIntent first, MotionIntent second) {
        return first.target().equals(second.target())
                && first.continuous() == second.continuous()
                && Objects.equals(first.envelope(), second.envelope())
                && Objects.equals(first.bounds(), second.bounds())
                && Double.compare(first.maximumStep(), second.maximumStep()) == 0
                && first.exactEndpoint() == second.exactEndpoint();
    }

    record MotionIntent(long applyAtGameTime, Vec3 target, boolean continuous, LocalNavigationEnvelope envelope,
                                WorldBounds bounds, double maximumStep, boolean exactEndpoint) { }
    private record AppliedIntent(MotionIntent intent, long gameTime) { }
    private record RetainedAscent(Vec3 target, BlockPos sourceSupport) { }
    private record TendingPose(double x, double y, double z) {
        private Vec3 target(long gameTime) {
            return new Vec3(x, y, z);
        }
    }
    private record StationaryPresentation(long gameTime, Vec3 position, int turns) { }
    private record Avoidance(Vec3 step, Vec3 target, int remainingTurns) { }
}
