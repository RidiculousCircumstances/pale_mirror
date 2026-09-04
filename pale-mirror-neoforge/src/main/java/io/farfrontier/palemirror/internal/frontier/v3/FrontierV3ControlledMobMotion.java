package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.Vec3;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
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
    /** Ephemeral one-tick physical intents; canonical goals/cursors remain in the domain. */
    private static final Map<Mob, MotionIntent> PENDING = new IdentityHashMap<>();

    private FrontierV3ControlledMobMotion() { }

    static void moveToward(ServerLevel level, Mob actor, Vec3 target) {
        submit(level, actor, target, false);
    }

    /**
     * Follows a continuously moving local target without treating the ordinary arrival radius
     * as a stop-and-go patrol cadence.  This is presentation-only local motion: it does not
     * choose a route, change a cursor, or create a second canonical movement authority.
     */
    static void followContinuously(ServerLevel level, Mob actor, Vec3 target) {
        submit(level, actor, target, true);
    }

    private static void submit(ServerLevel level, Mob actor, Vec3 target, boolean continuous) {
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
        MotionIntent pending = PENDING.get(actor);
        // The normal production order is canonical executor (post tick) → entity pre-tick on
        // the next server tick.  Keep an already-due one-tick intent if another observer runs
        // before that pre-tick: overwriting it with a new future timestamp creates a permanent
        // stop/go loop whose outcome depends on event callback order rather than physical state.
        if (pending != null && pending.applyAtGameTime() <= level.getGameTime() + 1L) {
            return;
        }
        if (PENDING.size() < MAX_PENDING_INTENTS || pending != null) {
            PENDING.put(actor, new MotionIntent(level.getGameTime() + 1L, target, continuous));
        }
    }

    /** Runs from the normal server entity-tick boundary, before tracker replication. */
    static void advance(Mob actor) {
        MotionIntent intent = PENDING.get(actor);
        if (intent == null) return;
        if (!(actor.level() instanceof ServerLevel level) || actor.isRemoved() || !actor.isAlive()) { PENDING.remove(actor); return; }
        if (level.getGameTime() < intent.applyAtGameTime()) return;
        PENDING.remove(actor);
        apply(level, actor, intent.target(), intent.continuous());
    }

    static void stop(Mob actor) {
        PENDING.remove(actor);
        // The prior authority may already have submitted a collision move or left an ordinary
        // Minecraft velocity on the body.  Cancelling only our queued intent lets that residual
        // velocity carry a newly leased worker across its retained support between the durable
        // hand-off and the next scene tick.  Stopping is the authority-transfer boundary: it
        // freezes motion, but never rewrites the observed physical position or canonical cursor.
        actor.setDeltaMovement(Vec3.ZERO);
        actor.stopInPlace();
    }

    /**
     * Bounded read-only inspection for a HOT-scene diagnostic.  It exposes only whether this
     * actuator still owns a one-tick intent and never supplies a destination to any caller.
     */
    static String readiness(Mob actor) {
        MotionIntent intent = PENDING.get(actor);
        return intent == null ? "IDLE" : "PENDING_AT_" + intent.applyAtGameTime();
    }

    private static void apply(ServerLevel level, Mob actor, Vec3 target, boolean continuous) {
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
        double vertical = delta.y < 0.0D ? 0.0D : Math.min(delta.y, VERTICAL_SPEED);
        double speed = Math.min(actor instanceof Zombie ? BIOFORM_SPEED : RESIDENT_SPEED, horizontalDistance);
        Vec3 direct = new Vec3(delta.x / horizontalDistance * speed, vertical, delta.z / horizontalDistance * speed);
        // A retained operation/assembly edge has one persisted next support. Letting the
        // physical actuator try a side-step or a lifted alternate here would make a blocked
        // canonical edge look successful without any topology command. The same is true for
        // ambient presentation: it owns no route cursor, but it still owns one retained
        // support column for a later exact scene hand-off. A collision-driven lateral fallback
        // can leave that column even when its visual target is clamped locally. Presentation
        // may therefore pause at an obstruction; it never sidesteps around it.
        List<Vec3> candidates = motionCandidates(direct, continuous);
        for (Vec3 step : candidates) {
            Vec3 before = actor.position();
            actor.move(MoverType.SELF, step);
            Vec3 moved = actor.position().subtract(before);
            if (moved.x * moved.x + moved.z * moved.z <= 1.0E-8D) {
                // This is still the same retained X/Z edge, not a new route: it is only the
                // bounded collision step needed to enter an exact thin support such as the
                // graybox infection/route surface. A full block fails noCollision here; a
                // lateral candidate exists only for non-canonical continuous ambience.
                Vec3 lifted = step.add(0.0D, THIN_SURFACE_STEP, 0.0D);
                if (!level.noCollision(actor, actor.getBoundingBox().move(lifted))) continue;
                before = actor.position(); actor.move(MoverType.SELF, lifted); moved = actor.position().subtract(before);
                if (moved.x * moved.x + moved.z * moved.z <= 1.0E-8D) continue;
            }
            actor.setYRot((float) Math.toDegrees(Math.atan2(-moved.x, moved.z)));
            actor.yBodyRot = actor.getYRot();
            // Entity.move updates the authoritative server position but, unlike vanilla AI
            // travel, does not itself request an immediate tracker update.  A Zombie's normal
            // tracker interval then coalesces several 20 Hz PM steps into one client-visible
            // jump.  This is presentation replication only: the accepted collision result
            // above remains the sole physical fact, while the normal tracker publishes it on
            // this same tick to every observing player.
            actor.hasImpulse = true;
            return;
        }
    }

    /**
     * A single exact vector is deliberate for both retained travel and local ambience.  The
     * boolean remains part of the intent because it controls arrival behaviour, not permission
     * to choose an alternate spatial edge.
     */
    static List<Vec3> motionCandidates(Vec3 direct, boolean continuous) {
        if (direct == null) throw new IllegalArgumentException("motion direct vector is required");
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
        if (actor.position().y < before.y - 1.0E-8D) actor.hasImpulse = true;
    }

    private record MotionIntent(long applyAtGameTime, Vec3 target, boolean continuous) { }
}
