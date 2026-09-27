package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.LocalNavigationEnvelope;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.List;
import java.util.WeakHashMap;

/**
 * HOT pedestrian path provider for one already-selected semantic goal.
 *
 * <p>Minecraft finds and drives the short physical path. The canonical job chooses the target,
 * and only the scene's independent support/clearance observation may advance it. NoAI remains
 * enabled: an owned-body mixin permits ordinary {@code LivingEntity.travel} while cancelling
 * {@code Mob.serverAiStep}; we tick only Minecraft navigation/move/jump controls at the owned
 * entity boundary, never Villager Brain, goal selector or target selector.</p>
 */
final class FrontierV3MinecraftGoalNavigation {
    enum Status { IN_PROGRESS, ARRIVED, BLOCKED, AMBIGUOUS }
    record Result(Status status, String reason, Optional<FrontierV3GoalNavigation.BlockReason> blockReason,
                  Optional<SurfaceAnchor> arrivedStation) {
        Result {
            Objects.requireNonNull(status);
            Objects.requireNonNull(reason);
            Objects.requireNonNull(blockReason);
            Objects.requireNonNull(arrivedStation);
            if ((status == Status.BLOCKED) != blockReason.isPresent()
                    || (status == Status.ARRIVED) != arrivedStation.isPresent())
                throw new IllegalArgumentException("physical goal result requires exact block or arrival evidence");
        }
        Result(Status status, String reason) { this(status, reason, Optional.empty(), Optional.empty()); }
        Result(Status status, String reason, Optional<FrontierV3GoalNavigation.BlockReason> blockReason) {
            this(status, reason, blockReason, Optional.empty());
        }
    }

    private static final int RETRY_TICKS = 20;
    private static final int STALLED_TICKS = 80;
    private static final int MAX_PATH_NODES = 54;
    private static final double SPEED = 0.70D;
    private static final Map<Mob, Control> ACTIVE = new WeakHashMap<>();
    private static final Map<Mob, Failure> FAILURES = new WeakHashMap<>();

    private FrontierV3MinecraftGoalNavigation() { }

    static Result pursue(ServerLevel level, Mob actor, List<SurfaceAnchor> legalStations,
                         LocalNavigationEnvelope envelope, Optional<MovementOrder> order) {
        Objects.requireNonNull(level); Objects.requireNonNull(actor);
        legalStations = List.copyOf(Objects.requireNonNull(legalStations));
        Objects.requireNonNull(envelope);
        Objects.requireNonNull(order);
        if (legalStations.isEmpty()) throw new IllegalArgumentException("physical goal has no legal station");
        if (actor.level() != level || actor.isRemoved() || !actor.isAlive())
            return new Result(Status.AMBIGUOUS, "body-unavailable");
        List<SurfaceAnchor> observedStations = legalStations.stream()
                .filter(station -> FrontierV3SemanticMovement.arrived(level, actor, station)).toList();
        if (observedStations.size() > 1) {
            stop(actor);
            return new Result(Status.AMBIGUOUS, "multiple-legal-stations-observed");
        }
        if (observedStations.size() == 1) {
            stop(actor);
            actor.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            actor.stopInPlace();
            return new Result(Status.ARRIVED, "physical-goal-observed", Optional.empty(),
                    Optional.of(observedStations.getFirst()));
        }
        BlockPosition support = new BlockPosition(actor.getOnPos().getX(), actor.getOnPos().getY(), actor.getOnPos().getZ());
        if (!envelope.contains(support)
                || legalStations.stream().anyMatch(station -> !envelope.contains(station.support()))) {
            stop(actor);
            return new Result(Status.BLOCKED, "outside-retained-envelope",
                    Optional.of(FrontierV3GoalNavigation.BlockReason.OFF_CONTRACT));
        }
        Control current = ACTIVE.get(actor);
        Failure failed = FAILURES.get(actor);
        if (failed != null && (!failed.legalStations().equals(legalStations) || !failed.envelope().equals(envelope)
                || !failed.order().equals(order))) stop(actor);
        if (current != null && current.legalStations().equals(legalStations) && current.envelope().equals(envelope)
                && current.order().equals(order)) {
            Path activePath = actor.getNavigation().getPath();
            if (activePath != null && !actor.getNavigation().isDone()
                    && !pathWithinEnvelope(level, activePath, envelope)) {
                stop(actor);
                return new Result(Status.BLOCKED, "minecraft-path-left-envelope",
                        Optional.of(FrontierV3GoalNavigation.BlockReason.OFF_CONTRACT));
            }
            Control observed = current.observed(actor.position(), level.getGameTime());
            if (observed != current) FAILURES.remove(actor);
            current = observed;
            if (current.lastProgressAt() + STALLED_TICKS <= level.getGameTime()) {
                stopPath(actor);
                return new Result(Status.BLOCKED, "minecraft-path-stalled",
                        Optional.of(FrontierV3GoalNavigation.BlockReason.PATH_STALLED));
            }
            if (activePath != null && !actor.getNavigation().isDone()) {
                ACTIVE.put(actor, current.refreshed(level.getGameTime()));
                return new Result(Status.IN_PROGRESS, "minecraft-path-active");
            }
            stopPath(actor);
        } else if (current != null) {
            stopPath(actor);
        }
        // The old custom actuator must not race the Minecraft path. This removes only an
        // ephemeral physical directive; it does not change the canonical work or route.
        if (!FAILURES.containsKey(actor)) FrontierV3ControlledMobMotion.stop(actor);
        actor.setNoAi(true);
        actor.setNoGravity(false);
        refreshPhysicalGroundContact(level, actor);
        SurfaceAnchor target = null;
        Path path = null;
        boolean unloaded = false;
        // A service region is one semantic goal. Minecraft may choose whichever declared
        // support has a reachable, envelope-contained pedestrian path; this does not select
        // another work item or change the canonical task.
        for (SurfaceAnchor station : legalStations) {
            BlockPos feet = new BlockPos(station.x(), station.y() + 1, station.z());
            if (!level.hasChunkAt(feet)) { unloaded = true; continue; }
            Path candidate = actor.getNavigation().createPath(feet, 0);
            if (candidate == null || !candidate.canReach()
                    || !pathWithinEnvelope(level, candidate, envelope)) continue;
            if (path == null || candidate.getNodeCount() < path.getNodeCount()) {
                path = candidate;
                target = station;
            }
        }
        if (path == null) return retry(actor, legalStations, envelope, order, level.getGameTime(),
                unloaded ? "target-chunk-unloaded" : "minecraft-path-unavailable",
                unloaded ? FrontierV3GoalNavigation.BlockReason.TARGET_CHUNK_UNLOADED
                        : FrontierV3GoalNavigation.BlockReason.PATH_UNAVAILABLE);
        if (!actor.getNavigation().moveTo(path, SPEED))
            return retry(actor, legalStations, envelope, order, level.getGameTime(),
                    "minecraft-path-refused", FrontierV3GoalNavigation.BlockReason.PATH_UNAVAILABLE);
        // Finding another path is not progress. Keep the same no-motion deadline
        // across path recomputations; only a real body displacement can reset it.
        Control retained = current != null && current.legalStations().equals(legalStations)
                && current.envelope().equals(envelope) && current.order().equals(order)
                ? current : null;
        ACTIVE.put(actor, retained == null
                ? new Control(legalStations, target, envelope, order, level.getGameTime(), level.getGameTime(), actor.position())
                : new Control(legalStations, target, envelope, order, level.getGameTime(), retained.lastProgressAt(),
                        retained.lastProgressPosition()));
        return new Result(Status.IN_PROGRESS, "minecraft-path-started");
    }

    /** Called before the ordinary entity tick; NoAI prevents a second vanilla goal owner. */
    static boolean advanceAtEntityBoundary(Mob actor) {
        Control control = ACTIVE.get(actor);
        if (control == null) {
            if (!FAILURES.containsKey(actor)) return false;
            // An unfinished jump may temporarily make GroundPathNavigation unable to
            // replan. Keep ordinary collision/gravity alive while the bounded retry
            // waits for the same body to land; NoAI otherwise freezes it in mid-air.
            FrontierV3ControlledMobMotion.advanceOrdinaryGravity(actor);
            return true;
        }
        if (!(actor.level() instanceof ServerLevel level) || actor.isRemoved() || !actor.isAlive()
                || level.getGameTime() - control.refreshedAt() > RETRY_TICKS) {
            stop(actor);
            return true;
        }
        if (actor.getNavigation().isDone() || actor.getNavigation().getPath() == null) {
            stopPath(actor); // A completed/consumed short leg is not an off-contract path.
            return true;
        }
        if (!pathWithinEnvelope(level, actor.getNavigation().getPath(), control.envelope())) {
            stop(actor);
            return true;
        }
        if (FrontierV3SemanticMovement.arrived(level, actor, control.target())) {
            stop(actor);
            actor.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            actor.stopInPlace();
            return true;
        }
        actor.setNoAi(true);
        // The owned isEffectiveAi bridge enables LivingEntity.travel below, which
        // supplies its own gravity/collision. Applying the old NoAI gravity bridge
        // here as well would double-integrate each HOT pedestrian turn.
        refreshPhysicalGroundContact(level, actor);
        actor.getNavigation().tick();
        actor.getMoveControl().tick();
        actor.getJumpControl().tick();
        return true;
    }

    static boolean controls(Mob actor) { return ACTIVE.containsKey(actor); }

    static void stop(Mob actor) {
        if (actor == null) return;
        stopPath(actor);
        FAILURES.remove(actor);
    }

    private static void stopPath(Mob actor) {
        ACTIVE.remove(actor);
        actor.getNavigation().stop();
    }

    private static void refreshPhysicalGroundContact(ServerLevel level, Mob actor) {
        // NoAI skips Mob.serverAiStep, including the ordinary path update. The existing
        // gravity bridge moves through real collision but Entity.move does not consistently
        // refresh this flag. GroundPathNavigation refuses to search while it is false.
        // Reconstruct only the contact fact from the body's exact collision box; never
        // manufacture a support, location, path node or semantic arrival.
        actor.setOnGround(!level.noCollision(actor, actor.getBoundingBox().move(0.0D, -0.01D, 0.0D)));
    }

    private static Result retry(Mob actor, List<SurfaceAnchor> legalStations, LocalNavigationEnvelope envelope,
                                Optional<MovementOrder> order, long tick,
                                String reason, FrontierV3GoalNavigation.BlockReason blockReason) {
        Failure previous = FAILURES.get(actor);
        Failure next = previous == null || !previous.legalStations().equals(legalStations) || !previous.envelope().equals(envelope)
                || !previous.order().equals(order)
                ? new Failure(legalStations, envelope, order, tick) : previous;
        FAILURES.put(actor, next);
        return tick - next.since() >= RETRY_TICKS
                ? new Result(Status.BLOCKED, reason, Optional.of(blockReason)) : new Result(Status.IN_PROGRESS, reason);
    }

    static boolean pathWithinEnvelope(ServerLevel level, Path path, LocalNavigationEnvelope envelope) {
        if (path == null || path.getNodeCount() < 1 || path.getNodeCount() > MAX_PATH_NODES) return false;
        for (int index = path.getNextNodeIndex(); index < path.getNodeCount(); index++) {
            BlockPos feet = path.getNode(index).asBlockPos();
            BlockPos support = feet.below();
            if (!level.hasChunkAt(feet) || !envelope.contains(new BlockPosition(
                    support.getX(), support.getY(), support.getZ()))
                    || !level.getFluidState(feet).isEmpty()) return false;
        }
        return true;
    }

    private record Control(List<SurfaceAnchor> legalStations, SurfaceAnchor target, LocalNavigationEnvelope envelope,
                           Optional<MovementOrder> order, long refreshedAt,
                           long lastProgressAt, Vec3 lastProgressPosition) {
        private Control observed(Vec3 position, long tick) {
            // Collision jitter and vertical bobbing are not progress. Retain the
            // best planar approach to this exact station across path retries so
            // oscillation against a newly materialized wall cannot renew the
            // stall deadline forever.
            if (goalDistance(lastProgressPosition, target) - goalDistance(position, target) >= 0.25D)
                return new Control(legalStations, target, envelope, order, refreshedAt, tick, position);
            return this;
        }
        private Control refreshed(long tick) {
            return new Control(legalStations, target, envelope, order, tick, lastProgressAt, lastProgressPosition);
        }
    }
    static double goalDistance(Vec3 position, SurfaceAnchor target) {
        return Math.hypot(position.x - (target.x() + 0.5D), position.z - (target.z() + 0.5D));
    }
    private record Failure(List<SurfaceAnchor> legalStations, LocalNavigationEnvelope envelope,
                           Optional<MovementOrder> order, long since) { }
}
