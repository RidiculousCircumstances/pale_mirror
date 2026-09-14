package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.LocalNavigationEnvelope;
import io.farfrontier.palemirror.frontier.v3.model.WorldBounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
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
    /**
     * HOT bodies deliberately retain NoAI, so their vertical travel cannot be delegated to the
     * vanilla goal loop.  Registration is weak and has no canonical meaning: it merely keeps
     * the same ordinary gravity/collision path alive at every entity-pre turn, including an
     * intentionally stationary body after a player removes its support.
     */
    private static final Map<Mob, Boolean> ORDINARY_PHYSICS = new WeakHashMap<>();
    /** A crop-local tending pose is derived each server turn, never paced by a semantic callback. */
    private static final Map<Mob, TendingPose> TENDING = new IdentityHashMap<>();
    /** The entity boundary may be observed explicitly by a test after vanilla already ran it. */
    private static final Map<Mob, AppliedIntent> LAST_ADVANCE = new IdentityHashMap<>();
    /** Ephemeral collision latitude, bounded to a retained local envelope and target. */
    private static final Map<Mob, Avoidance> AVOIDANCE = new IdentityHashMap<>();
    /** Bounded read-only evidence of actual accepted collision moves, not an alternate clock. */
    private static final Map<Mob, ArrayDeque<MotionSample>> TRACE = new WeakHashMap<>();
    private static final Map<Mob, TrackerObservation> TRACKER_OBSERVATIONS = new WeakHashMap<>();

    record MotionSample(long gameTime, double x, double y, double z, double horizontalVelocity) { }
    record TrackerObservation(int calls, int impulseCalls) { }

    private FrontierV3ControlledMobMotion() { }

    static void moveToward(ServerLevel level, Mob actor, Vec3 target) {
        submit(level, actor, target, false, null, null, Double.MAX_VALUE);
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
        submit(level, actor, target, false, null, bounds, Double.MAX_VALUE);
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
        submit(level, actor, next, true, null, null, Double.MAX_VALUE);
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
        submit(level, actor, target, false, envelope, null, Double.MAX_VALUE);
    }

    /**
     * Follows a continuously moving local target without treating the ordinary arrival radius
     * as a stop-and-go patrol cadence.  This is presentation-only local motion: it does not
     * choose a route, change a cursor, or create a second canonical movement authority.
     */
    static void followContinuously(ServerLevel level, Mob actor, Vec3 target) {
        TENDING.remove(actor);
        submit(level, actor, target, true, null, null, Double.MAX_VALUE);
    }

    /** Retains only the current crop cell; time changes its local pose but never a job cursor. */
    static void tendCurrentCrop(ServerLevel level, Mob actor, io.farfrontier.palemirror.frontier.v3.model.BlockPosition crop) {
        TendingPose pose = new TendingPose(crop.x() + .5D, crop.y(), crop.z() + .5D);
        TENDING.put(actor, pose);
        submit(level, actor, pose.target(level.getGameTime()), true, null, null, Double.MAX_VALUE);
    }

    /** Holds an already observed retained checkpoint visibly active without exposing another edge. */
    static void holdRetainedCheckpoint(ServerLevel level, Mob actor, Vec3 checkpoint) {
        TendingPose pose = new TendingPose(checkpoint.x, checkpoint.y, checkpoint.z);
        TENDING.put(actor, pose);
        submit(level, actor, pose.target(level.getGameTime()), true, null, null, Double.MAX_VALUE);
    }

    private static void submit(ServerLevel level, Mob actor, Vec3 target, boolean continuous, LocalNavigationEnvelope envelope,
                               WorldBounds bounds, double maximumStep) {
        actor.setNoAi(true);
        // NoAI suppresses Minecraft's goal selector, not physical gravity.  Reassert the latter
        // because a retained entity can carry an old mod/AI no-gravity flag across a HOT handoff.
        actor.setNoGravity(false);
        actor.getNavigation().stop();
        Vec3 delta = target.subtract(actor.position());
        double horizontalDistance = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        if (!continuous && horizontalDistance <= ARRIVAL_DISTANCE && Math.abs(delta.y) <= ARRIVAL_DISTANCE) { stop(actor); return; }
        // A HOT adapter may only follow the next retained pedestrian edge.  Existing callers
        // that still use same-level local goals remain unaffected; a larger vertical gap is not
        // a licence to fly or to infer a route and is therefore left for the canonical planner.
        if (Math.abs(delta.y) > MAX_WALK_GRADE + ARRIVAL_DISTANCE) { stop(actor); return; }
        if (!Double.isFinite(maximumStep) || maximumStep <= 0.0D) throw new IllegalArgumentException("motion maximum step");
        MotionIntent pending = PENDING.get(actor);
        if (continuous && !CONTINUOUS.containsKey(actor) && CONTINUOUS.size() >= MAX_PENDING_INTENTS) return;
        if (!continuous) TENDING.remove(actor);
        MotionIntent replacement = new MotionIntent(level.getGameTime(), target, continuous, envelope, bounds, maximumStep);
        if (continuous) CONTINUOUS.put(actor, replacement);
        else CONTINUOUS.remove(actor);
        Avoidance avoidance = AVOIDANCE.get(actor);
        if (avoidance != null && !avoidance.target().equals(target)) AVOIDANCE.remove(actor);
        // The normal production order is canonical executor (post tick) → entity pre-tick on
        // the next server tick. Keep an already-due intent if another observer runs before that
        // pre-tick: overwriting it with a new timestamp creates a permanent
        // stop/go loop whose outcome depends on event callback order rather than physical state.
        if (pending != null && pending.applyAtGameTime() <= level.getGameTime() + 1L) {
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
            PENDING.remove(actor); CONTINUOUS.remove(actor); LAST_ADVANCE.remove(actor); AVOIDANCE.remove(actor); return;
        }
        // A direct diagnostic/GameTest call can legitimately occur after the registered
        // EntityTick.Pre callback. It is an observation aid, never a second movement authority;
        // applying the same persistent tending directive twice would create a synthetic
        // double-speed body. Identity, rather than game time, is deliberate: bounded tests may
        // exercise several newly submitted physical turns at one fixed game-time value.
        AppliedIntent previous = LAST_ADVANCE.get(actor);
        if (previous != null && intent == previous.intent() && level.getGameTime() == previous.gameTime()) return;
        if (level.getGameTime() < intent.applyAtGameTime()) return;
        // Derive the station-local pose at the ordinary entity turn.  It remains bounded by
        // the immutable current crop/checkpoint and does not introduce a route or semantic
        // clock, but it prevents the scheduler from becoming the body's cadence owner.
        TendingPose tending = TENDING.get(actor);
        if (tending != null) {
            intent = new MotionIntent(level.getGameTime(), tending.target(level.getGameTime()), true, null, null, Double.MAX_VALUE);
            CONTINUOUS.put(actor, intent);
        }
        PENDING.remove(actor);
        LAST_ADVANCE.put(actor, new AppliedIntent(intent, level.getGameTime()));
        apply(level, actor, intent.target(), intent.continuous(), intent.envelope(), intent.bounds(), intent.maximumStep());
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
            MotionIntent turn = new MotionIntent(level.getGameTime(), pose.target(level.getGameTime()), true, null, null, Double.MAX_VALUE);
            PENDING.remove(actor);
            CONTINUOUS.put(actor, turn);
            advance(actor);
        }
    }

    static void stop(Mob actor) {
        PENDING.remove(actor);
        CONTINUOUS.remove(actor);
        TENDING.remove(actor);
        LAST_ADVANCE.remove(actor);
        // A stop retires target authority but never rewrites observed physical position or the
        // canonical cursor.
        AVOIDANCE.remove(actor);
        // The prior authority may already have submitted a collision move or left an ordinary
        // Minecraft velocity on the body.  Cancelling only our queued intent lets that residual
        // velocity carry a newly leased worker across its retained support between the durable
        // hand-off and the next scene tick.  Stopping is the authority-transfer boundary: it
        // freezes motion, but never rewrites the observed physical position or canonical cursor.
        actor.setDeltaMovement(Vec3.ZERO);
        actor.stopInPlace();
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
        actor.setDeltaMovement(velocity.x, actor.onGround() ? 0.0D : vertical, velocity.z);
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
        ORDINARY_PHYSICS.put(actor, Boolean.TRUE);
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
                              WorldBounds bounds, double maximumStep) {
        Vec3 delta = target.subtract(actor.position());
        double horizontalDistance = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        if (!continuous && horizontalDistance <= ARRIVAL_DISTANCE && Math.abs(delta.y) <= ARRIVAL_DISTANCE) { actor.stopInPlace(); return; }
        if (Math.abs(delta.y) > MAX_WALK_GRADE + ARRIVAL_DISTANCE) return;
        if (horizontalDistance <= 1.0E-8D) {
            settleExactDescent(actor, delta.y);
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
                        || !level.noCollision(actor, actor.getBoundingBox().move(lifted))) continue;
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
            return;
        }
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
    private static void settleExactDescent(Mob actor, double verticalDelta) {
        if (verticalDelta >= -ARRIVAL_DISTANCE) return;
        Vec3 before = actor.position();
        actor.move(MoverType.SELF, new Vec3(0.0D, -Math.min(Math.abs(verticalDelta), VERTICAL_SPEED), 0.0D));
        if (actor.position().y < before.y - 1.0E-8D) publishAcceptedMove(actor);
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

    private record MotionIntent(long applyAtGameTime, Vec3 target, boolean continuous, LocalNavigationEnvelope envelope,
                                WorldBounds bounds, double maximumStep) { }
    private record AppliedIntent(MotionIntent intent, long gameTime) { }
    private record TendingPose(double x, double y, double z) {
        private Vec3 target(long gameTime) {
            double angle = (Math.PI * 2.0D * Math.floorMod(gameTime, 32L)) / 32.0D;
            return new Vec3(x + Math.cos(angle) * .16D, y, z + Math.sin(angle) * .16D);
        }
    }
    private record Avoidance(Vec3 step, Vec3 target, int remainingTurns) { }
}
