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
    static int retryIntervalTicks() { return RETRY_TICKS; }
    private static final int STALLED_TICKS = 80;
    private static final double SPEED = 0.70D;
    private static final Map<Mob, Control> ACTIVE = new WeakHashMap<>();
    private static final Map<Mob, Failure> FAILURES = new WeakHashMap<>();
    private static final Map<Mob, CrossingWait> CROSSINGS = new WeakHashMap<>();
    private record CrossingWait(Path path, long since) { }

    private FrontierV3MinecraftGoalNavigation() { }

    static Result pursue(ServerLevel level, Mob actor, List<SurfaceAnchor> legalStations,
                         FrontierV3NavigationScope scope, Optional<MovementOrder> order,
                         FrontierV3GoalNavigation.ProviderPermission permission, boolean brakeOnArrival) {
        Objects.requireNonNull(permission, "physical provider permission");
        if (!permission.current(actor)) return new Result(Status.AMBIGUOUS, "stale-provider-authority");
        Objects.requireNonNull(level); Objects.requireNonNull(actor);
        legalStations = List.copyOf(Objects.requireNonNull(legalStations));
        Objects.requireNonNull(scope);
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
            finishStation(actor, brakeOnArrival);
            return new Result(Status.ARRIVED, "physical-goal-observed", Optional.empty(),
                    Optional.of(observedStations.getFirst()));
        }
        BlockPosition support = new BlockPosition(actor.getOnPos().getX(), actor.getOnPos().getY(), actor.getOnPos().getZ());
        if (!scope.permits(support)
                || legalStations.stream().anyMatch(station -> !scope.permits(station.support()))) {
            stop(actor);
            return new Result(Status.BLOCKED, "outside-task-scope",
                    Optional.of(FrontierV3GoalNavigation.BlockReason.OFF_CONTRACT));
        }
        Control current = ACTIVE.get(actor);
        Failure failed = FAILURES.get(actor);
        if (failed != null && (!failed.legalStations().equals(legalStations) || !failed.scope().equals(scope)
                || !failed.order().equals(order))) stop(actor);
        if (current != null && current.legalStations().equals(legalStations) && current.scope().equals(scope)
                && current.order().equals(order)) {
            Path activePath = actor.getNavigation().getPath();
            var rejected = activePath != null && !actor.getNavigation().isDone()
                    ? FrontierV3PhysicalPathInspection.inspect(level, actor, activePath, scope) : Optional.<FrontierV3PhysicalPathPolicy.Rejection>empty();
            if (rejected.isPresent()) {
                stop(actor);
                return new Result(Status.BLOCKED, "minecraft-path-invalid:" + rejected.orElseThrow().detail(),
                        Optional.of(rejected.orElseThrow().reason()));
            }
            Control observed = current.observed(actor, activePath, actor.position(), level.getGameTime());
            if (observed.lastProgressAt() > current.lastProgressAt()) FAILURES.remove(actor);
            current = observed;
            // Vanilla's ordinary graph ignores living bodies. Replan around a current
            // occupant before the no-motion deadline, without changing the semantic goal.
            var near = FrontierV3PedestrianTraffic.nearby(level, actor, activePath, permission.followingSpacing());
            boolean crossing = activePath != null && near.blocked() && near.crossing()
                    && waitForCrossing(actor, activePath, level.getGameTime());
            if (!near.blocked()) CROSSINGS.remove(actor);
            if (activePath != null && !actor.getNavigation().isDone() && near.blocked() && !crossing) {
                stopPath(actor);
                activePath = null;
            }
            double following = crossing ? 0 : FrontierV3PedestrianTraffic.followingPace(level, actor, activePath, permission.followingSpacing());
            current = current.following(following == 0, level.getGameTime());
            long baseProgressBudget = permission.pace().map(pace -> Math.max((long) STALLED_TICKS,
                    (long) Math.ceil(0.5 / pace.blocksPerTick())))
                    .orElse((long) STALLED_TICKS);
            long progressBudget = (long) Math.ceil(baseProgressBudget / (following == 0 ? 1 : following));
            // A deliberate headway stop is not a failed path. Exclude its duration,
            // including after the peer moves on, without fabricating body progress.
            if (following > 0 && level.getGameTime() - current.lastProgressAt()
                    - current.followingWait().duration(level.getGameTime()) >= progressBudget) {
                stopPath(actor);
                var alignment = FrontierV3NativeStationAlignment.rejection(level, actor, current.target(), current.progressPath(), current.scope());
                return new Result(Status.BLOCKED, "minecraft-path-stalled;alignment=" + alignment.map(Enum::name).orElse("CLEAR"),
                        Optional.of(FrontierV3GoalNavigation.BlockReason.PATH_STALLED));
            }
            if (settleObservedStation(level, actor, current)) {
                // Native graph nodes offset their wanted point for wide bodies.
                // Finish the accepted last node at the semantic station centre
                // under an observed collision/support sweep. Exact arrival remains
                // a separate physical observation; retain the same permission/control.
                actor.getNavigation().stop();
                ACTIVE.put(actor, current.refreshed(level.getGameTime(), permission));
                return new Result(Status.IN_PROGRESS, "minecraft-final-station-settling");
            }
            if (activePath != null && !actor.getNavigation().isDone()) {
                ACTIVE.put(actor, current.refreshed(level.getGameTime(), permission));
                return new Result(Status.IN_PROGRESS, "minecraft-path-active");
            }
            stopPath(actor);
        } else if (current != null) {
            stopPath(actor);
        }
        failed = FAILURES.get(actor);
        if (failed != null) {
            // A validated fresh request may retain the same leg/deadline, but must
            // explicitly replace its old presentation witness, never look it up later.
            failed = failed.withPermission(permission);
            FAILURES.put(actor, failed);
        }
        FrontierV3BodyObservation.refreshGroundContact(level, actor);
        if (failed != null && failed.groundedAtAttempt() == actor.onGround()
                && failed.goalOccupants().equals(FrontierV3PedestrianTraffic.goalOccupants(level, actor, legalStations))
                && level.getGameTime() - failed.lastAttemptAt() < RETRY_TICKS)
            return failureResult(failed, level.getGameTime());
        // The old custom actuator must not race the Minecraft path. This removes only an
        // ephemeral physical directive; it does not change the canonical work or route.
        if (!FAILURES.containsKey(actor)) FrontierV3ControlledMobMotion.retireLocalActuation(actor);
        actor.setNoAi(true);
        actor.setNoGravity(false);
        FrontierV3BodyObservation.refreshGroundContact(level, actor);
        SurfaceAnchor target = null;
        Path path = null;
        boolean unloaded = false;
        String rejection = "";
        FrontierV3GoalNavigation.BlockReason rejectionReason = FrontierV3GoalNavigation.BlockReason.PATH_UNAVAILABLE;
        FrontierV3PedestrianTraffic.Query traffic = null;
        // A service region is one semantic goal. Minecraft may choose whichever declared
        // support has a reachable, envelope-contained pedestrian path; this does not select
        // another work item or change the canonical task.
        for (SurfaceAnchor station : legalStations) {
            BlockPos feet = new BlockPos(station.x(), station.y() + 1, station.z());
            if (!FrontierV3LoadedWorld.ready(level, feet)) { unloaded = true; continue; }
            var query = FrontierV3PedestrianTraffic.query(level, actor, feet, scope, permission.navigationProfile());
            Path candidate = query.path();
            var refused = FrontierV3PhysicalPathPolicy.reject(level, candidate, scope);
            if (refused.isPresent() || !FrontierV3PathClearance.path(level, actor, candidate)) {
                if (traffic == null && query.trafficBlocked(level, scope)) traffic = query;
                if (rejection.isEmpty()) {
                    rejection = FrontierV3PathExplanation.rejected(level, station, candidate, scope);
                    rejectionReason = query.budgetExhausted()
                            ? FrontierV3GoalNavigation.BlockReason.SEARCH_BUDGET_EXHAUSTED
                            : refused.map(FrontierV3PhysicalPathPolicy.Rejection::reason).orElse(FrontierV3GoalNavigation.BlockReason.PATH_UNAVAILABLE);
                }
                continue;
            }
            if (path == null || candidate.getNodeCount() < path.getNodeCount()) {
                path = candidate;
                target = station;
            }
            // Transit candidates are ordered farthest first by the route owner.
            // Exact work goals retain their ordinary shortest reachable selection.
            if (!brakeOnArrival) break;
        }
        if (path == null) {
            if (traffic != null) {
                FrontierV3PedestrianYieldRequests.request(level, actor, permission, traffic,
                        new Demand(legalStations, scope, order));
                return retry(level, actor, legalStations, scope, order, permission, level.getGameTime(),
                        "traffic-blocked[" + FrontierV3PedestrianYieldRequests.explanation(level, traffic) + "]",
                        FrontierV3GoalNavigation.BlockReason.TRAFFIC_BLOCKED);
            }
            FrontierV3PedestrianYieldRequests.clear(actor);
            return retry(level, actor, legalStations, scope, order, permission, level.getGameTime(),
                    unloaded ? "target-chunk-unloaded" : "minecraft-path-unavailable[" + rejection + "]",
                    unloaded ? FrontierV3GoalNavigation.BlockReason.TARGET_CHUNK_UNLOADED : rejectionReason);
        }
        FrontierV3PedestrianYieldRequests.clear(actor);
        if (!actor.getNavigation().moveTo(path, speed(actor, permission)))
            return retry(level, actor, legalStations, scope, order, permission, level.getGameTime(),
                    "minecraft-path-refused", FrontierV3GoalNavigation.BlockReason.PATH_UNAVAILABLE);
        // Finding another path is not progress. Keep the same no-motion deadline
        // across path recomputations; only a real body displacement can reset it.
        Control retained = current != null && current.legalStations().equals(legalStations)
                && current.scope().equals(scope) && current.order().equals(order)
                ? current : null;
        ACTIVE.put(actor, retained == null
                ? new Control(legalStations, target, scope, FrontierV3PhysicalPathPolicy.corridor(path, target), order, permission,
                    level.getGameTime(), level.getGameTime(), actor.position(), path, remainingDistance(path, actor.position()), FollowingWait.empty(), brakeOnArrival)
                : new Control(legalStations, target, scope, FrontierV3PhysicalPathPolicy.corridor(path, target), order, permission,
                    level.getGameTime(), retained.lastProgressAt(),
                        actor.position(), path, remainingDistance(path, actor.position()), retained.followingWait(), brakeOnArrival));
        return new Result(Status.IN_PROGRESS, "minecraft-path-started");
    }

    /** A local hint is a pass-through point, never a work-arrival receipt. The next
     * accepted path starts from the actual body and still checks terrain and scope. */
    static Optional<SurfaceAnchor> transitHandoff(Mob actor, Demand demand, List<SurfaceAnchor> terminalStations) {
        var control = ACTIVE.get(actor);
        if (control == null || control.brakeOnArrival() || !control.permission().current(actor)
                || !demand.matches(control.legalStations(), control.scope(), control.order())
                || terminalStations.contains(control.target())) return Optional.empty();
        Path path = actor.getNavigation().getPath();
        if (path == null || path != control.progressPath() || !path.canReach()
                || path.getNextNodeIndex() < path.getNodeCount() - 2
                || FrontierV3BodyObservation.capture(actor).support().isEmpty()) return Optional.empty();
        Vec3 endpoint = path.getEntityPosAtNode(actor, path.getNodeCount() - 1);
        double lead = Math.max(1.0, actor.getBbWidth());
        if (actor.position().distanceToSqr(endpoint) > lead * lead) return Optional.empty();
        return Optional.of(control.target());
    }

    /** Called before the ordinary entity tick; NoAI prevents a second vanilla goal owner. */
    static boolean advanceAtEntityBoundary(Mob actor) {
        Control control = ACTIVE.get(actor);
        if (control == null) {
            var failure = FAILURES.get(actor);
            if (failure == null) return false;
            if (!failure.permission().current(actor)) { stop(actor); return false; }
            // An unfinished jump may temporarily make GroundPathNavigation unable to
            // replan. Keep ordinary collision/gravity alive while the bounded retry
            // waits for the same body to land; NoAI otherwise freezes it in mid-air.
            FrontierV3ControlledMobMotion.advanceOrdinaryGravity(actor);
            return true;
        }
        if (!control.permission().current(actor)) { stop(actor); return false; }
        if (!(actor.level() instanceof ServerLevel level) || actor.isRemoved() || !actor.isAlive()
                || control.permission().requiresPeriodicRefresh() && level.getGameTime() - control.refreshedAt() > RETRY_TICKS) {
            stop(actor);
            return true;
        }
        if (actor.getNavigation().isDone() || actor.getNavigation().getPath() == null) {
            // Native node tolerance is looser than station arrival. Keep the physical bridge
            // alive through final landing and centering, under the same exact permission.
            // Replanning a one-node path alone never gives MoveControl a final wanted point.
            settleObservedStation(level, actor, control);
            actor.getMoveControl().tick();
            actor.getJumpControl().tick();
            return true;
        }
        Path path = actor.getNavigation().getPath();
        if (!FrontierV3PedestrianTraffic.permitsProfile(path, control.permission().navigationProfile())) {
            stopPath(actor); return true;
        }
        if (FrontierV3PhysicalPathInspection.inspect(level, actor, path, control.scope()).isPresent()) {
            stopPath(actor);
            return true;
        }
        // Vanilla may replace its path. Validate the new candidate before updating local latitude.
        if (path != control.progressPath()) {
            control = control.observed(actor, path, actor.position(), level.getGameTime())
                    .withCorridor(FrontierV3PhysicalPathPolicy.corridor(path, control.target()));
            ACTIVE.put(actor, control);
        }
        if (FrontierV3SemanticMovement.arrived(level, actor, control.target())) {
            if (control.brakeOnArrival()) finishStation(actor, true);
            else actor.getNavigation().stop(); // Keep provider/ordinary travel until the next local leg is installed.
            return true;
        }
        actor.setNoAi(true);
        // The owned isEffectiveAi bridge enables LivingEntity.travel below, which
        // supplies its own gravity/collision. Applying the old NoAI gravity bridge
        // here as well would double-integrate each HOT pedestrian turn.
        FrontierV3BodyObservation.refreshGroundContact(level, actor);
        actor.getNavigation().setSpeedModifier(speed(actor, control.permission()));
        int previousNode = path.getNextNodeIndex();
        actor.getNavigation().tick();
        // Vanilla waypoint tolerance/corner cutting may skip the safe exit from a low-roof
        // pocket and aim directly at an impossible riser. Preserve the accepted native path;
        // undo only that unsafe skip before MoveControl/JumpControl can actuate it.
        if (actor.getNavigation().getPath() == path && !path.isDone()
                && path.getNextNodeIndex() > previousNode
                && !FrontierV3PathClearance.move(level, actor, actor.position(),
                        FrontierV3PathClearance.point(level, actor, path.getNextNodePos()))) {
            path.setNextNodeIndex(previousNode);
            var point = FrontierV3PathClearance.point(level, actor, path.getNextNodePos());
            if (!FrontierV3PathClearance.move(level, actor, actor.position(), point)) { stopPath(actor); return true; }
            actor.getMoveControl().setWantedPosition(point.x, point.y, point.z, speed(actor, control.permission()));
        }
        if (actor.getNavigation().isDone()) settleObservedStation(level, actor, control);
        actor.getMoveControl().tick();
        actor.getJumpControl().tick();
        return true;
    }

    /** Native endpoint centering under the retained path, never another route/arrival writer. */
    private static boolean settleObservedStation(ServerLevel level, Mob actor, Control control) {
        if (FrontierV3SemanticMovement.arrived(level, actor, control.target())
                || !FrontierV3NativeStationAlignment.safe(level, actor, control.target(), control.progressPath(), control.scope())) return false;
        Vec3 point = FrontierV3SemanticMovement.point(level, control.target());
        actor.getMoveControl().setWantedPosition(point.x, point.y, point.z, speed(actor, control.permission()));
        return true;
    }

    static boolean controls(Mob actor) {
        var control = ACTIVE.get(actor);
        return control != null && control.permission().current(actor);
    }

    /** A current unfinished physical leg, not velocity imparted by another body. */
    static boolean travelling(Mob actor) {
        var control = ACTIVE.get(actor);
        return control != null && actor.isAlive() && !actor.isRemoved()
                && actor.level() instanceof ServerLevel level && control.permission().current(actor)
                && (!control.permission().requiresPeriodicRefresh() || level.getGameTime() - control.refreshedAt() <= RETRY_TICKS)
                && !FrontierV3SemanticMovement.arrived(level, actor, control.target());
    }

    /** Exact currently requested native leg, including failed legs awaiting a traffic retry. */
    record Demand(List<SurfaceAnchor> stations, FrontierV3NavigationScope scope, Optional<MovementOrder> order) {
        Demand {
            stations = List.copyOf(stations); Objects.requireNonNull(scope); Objects.requireNonNull(order);
            if (stations.isEmpty()) throw new IllegalArgumentException("navigation demand has no station");
        }
        boolean matches(List<SurfaceAnchor> currentStations, FrontierV3NavigationScope currentScope, Optional<MovementOrder> currentOrder) {
            return stations.equals(currentStations) && scope.equals(currentScope) && order.equals(currentOrder);
        }
    }

    static boolean hasDemand(Mob actor, Demand demand) {
        var control = ACTIVE.get(actor);
        if (control != null) return control.permission().current(actor)
                && demand.matches(control.legalStations(), control.scope(), control.order());
        var failure = FAILURES.get(actor);
        return failure != null && failure.permission().current(actor)
                && demand.matches(failure.legalStations(), failure.scope(), failure.order());
    }

    static Path acceptedPath(Mob actor, Demand demand) {
        var control = ACTIVE.get(actor);
        if (control == null || !control.permission().current(actor)
                || !demand.matches(control.legalStations(), control.scope(), control.order())) return null;
        Path path = actor.getNavigation().getPath();
        return path == control.progressPath() ? path : null;
    }

    record Observation(String status, String reason, List<SurfaceAnchor> targets,
                       List<io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId> blockers) {
        static Observation idle() { return new Observation("IDLE", "", List.of(), List.of()); }
    }

    static Observation observation(Mob actor) {
        var courtesy = FrontierV3PedestrianCourtesy.observation(actor);
        if (courtesy.isPresent()) return courtesy.orElseThrow();
        var control = ACTIVE.get(actor);
        var crossing = CROSSINGS.get(actor);
        if (control != null && control.permission().current(actor) && crossing != null
                && crossing.path() == actor.getNavigation().getPath()
                && actor.level().getGameTime() - crossing.since() < RETRY_TICKS
                && FrontierV3PedestrianTraffic.nearby((ServerLevel) actor.level(), actor, crossing.path()).crossing())
            return new Observation("WAITING_FOR_CROSSING", "bounded-native-crossing-wait", control.legalStations(), List.of());
        if (control != null && control.permission().current(actor)) {
            String alignment = "";
            if (actor.level() instanceof ServerLevel level && FrontierV3LoadedWorld.ready(level, new BlockPos(control.target().x(), control.target().y(), control.target().z()))
                    && actor.position().distanceToSqr(FrontierV3SemanticMovement.point(level, control.target())) <= 9.0D)
                alignment = ";endpoint-alignment=" + FrontierV3NativeStationAlignment.rejection(level, actor, control.target(),
                        control.progressPath(), control.scope()).map(Enum::name).orElse("CLEAR");
            return new Observation("MOVING", control.permission().pace().map(pace -> "pace-blocks-per-tick="
                    + pace.blocksPerTick() + ";native-speed-modifier=" + speed(actor, control.permission())).orElse("") + alignment,
                    control.legalStations(), List.of());
        }
        var failure = FAILURES.get(actor);
        return failure != null && failure.permission().current(actor)
                ? new Observation(failure.blockReason().name(), failure.reason(), failure.legalStations(),
                    FrontierV3PedestrianYieldRequests.observedBlockers(actor)) : Observation.idle();
    }

    static boolean canReach(ServerLevel level, Mob actor, List<SurfaceAnchor> stations, FrontierV3NavigationScope scope) {
        return canReach(level, actor, stations, scope, java.util.Optional.empty());
    }
    static boolean canReach(ServerLevel level, Mob actor, List<SurfaceAnchor> stations, FrontierV3NavigationScope scope,
            java.util.Optional<io.farfrontier.palemirror.frontier.v3.model.navigation.UnitNavigationProfile> profile) {
        if (actor.level() != level || !actor.isAlive() || actor.isRemoved()) return false;
        for (SurfaceAnchor station : stations) {
            BlockPos feet = new BlockPos(station.x(), station.y() + 1, station.z());
            if (!scope.permits(station.support()) || !FrontierV3LoadedWorld.ready(level, feet)) continue;
            var path = FrontierV3PedestrianTraffic.query(level, actor, feet, scope, profile).path();
            if (FrontierV3PhysicalPathPolicy.reject(level, path, scope).isEmpty()
                    && FrontierV3PathClearance.path(level, actor, path)
                    && FrontierV3PedestrianTraffic.permitsProfile(path, profile)) return true;
        }
        return false;
    }

    static void stop(Mob actor) {
        if (actor == null) return;
        FrontierV3PedestrianYieldRequests.clear(actor);
        stopPath(actor);
        FAILURES.remove(actor);
        actor.getJumpControl().tick();
        actor.setJumping(false);
        actor.stopInPlace();
    }

    private static void stopPath(Mob actor) {
        ACTIVE.remove(actor);
        CROSSINGS.remove(actor);
        FrontierV3PedestrianTraffic.forget(actor);
        FrontierV3PhysicalPathInspection.forget(actor);
        actor.getNavigation().stop();
    }

    private static void finishStation(Mob actor, boolean brake) {
        if (brake) {
            stop(actor);
            actor.setDeltaMovement(Vec3.ZERO);
        } else {
            // Replacing a completed advisory leg must not zero momentum or stop
            // the body. The same turn installs its successor under the same owner.
            stopPath(actor);
            FAILURES.remove(actor);
        }
    }

    private static double speed(Mob actor, FrontierV3GoalNavigation.ProviderPermission permission) {
        var crossing = CROSSINGS.get(actor);
        if (crossing != null && crossing.path() == actor.getNavigation().getPath()
                && actor.level().getGameTime() - crossing.since() < RETRY_TICKS
                && FrontierV3PedestrianTraffic.nearby((ServerLevel) actor.level(), actor, crossing.path()).crossing()) return 0;
        double following = FrontierV3PedestrianTraffic.followingPace((ServerLevel) actor.level(), actor, actor.getNavigation().getPath(), permission.followingSpacing());
        if (following == 0) return 0;
        return permission.pace().map(pace -> {
            var support = actor.getBlockPosBelowThatAffectsMyMovement();
            double friction = actor.level().getBlockState(support).getFriction(actor.level(), support, actor);
            return FrontierV3NavigationPace.speedModifier(new io.farfrontier.palemirror.frontier.v3.model.navigation.TravelPace(pace.blocksPerTick() * following),
                    actor.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED), friction, SPEED);
        }).orElse(SPEED * Math.sqrt(following));
    }

    private static Result retry(ServerLevel level, Mob actor, List<SurfaceAnchor> legalStations, FrontierV3NavigationScope scope,
                                Optional<MovementOrder> order, FrontierV3GoalNavigation.ProviderPermission permission, long tick,
                                String reason, FrontierV3GoalNavigation.BlockReason blockReason) {
        Failure previous = FAILURES.get(actor);
        Failure next = previous == null || !previous.legalStations().equals(legalStations) || !previous.scope().equals(scope)
                || !previous.order().equals(order)
                ? new Failure(legalStations, scope, order, permission, tick, tick, reason, blockReason, actor.onGround(),
                        FrontierV3PedestrianTraffic.goalOccupants(level, actor, legalStations))
                : new Failure(legalStations, scope, order, permission, previous.since(), tick, reason, blockReason, actor.onGround(),
                        FrontierV3PedestrianTraffic.goalOccupants(level, actor, legalStations));
        FAILURES.put(actor, next);
        return failureResult(next, tick);
    }

    private static Result failureResult(Failure failure, long tick) {
        return tick - failure.since() >= RETRY_TICKS
                ? new Result(Status.BLOCKED, failure.reason(), Optional.of(failure.blockReason()))
                : new Result(Status.IN_PROGRESS, failure.reason());
    }

    static boolean pathWithinEnvelope(ServerLevel level, Path path, LocalNavigationEnvelope envelope) {
        if (path == null || path.getNodeCount() < 1 || path.getNodeCount() > FrontierV3PhysicalPathPolicy.MAX_PATH_NODES) return false;
        for (int index = path.getNextNodeIndex(); index < path.getNodeCount(); index++) {
            BlockPos feet = path.getNode(index).asBlockPos();
            BlockPos support = feet.below();
            if (!FrontierV3LoadedWorld.ready(level, feet) || !envelope.contains(new BlockPosition(
                    support.getX(), support.getY(), support.getZ()))
                    || !level.getFluidState(feet).isEmpty()) return false;
        }
        return true;
    }

    private record Control(List<SurfaceAnchor> legalStations, SurfaceAnchor target, FrontierV3NavigationScope scope,
                           LocalNavigationEnvelope corridor,
                           Optional<MovementOrder> order, FrontierV3GoalNavigation.ProviderPermission permission, long refreshedAt,
                           long lastProgressAt, Vec3 lastProgressPosition, Path progressPath, double bestRemaining, FollowingWait followingWait, boolean brakeOnArrival) {
        private Control observed(Mob actor, Path path, Vec3 position, long tick) {
            double remaining = path == null ? goalDistance(position, target) : FrontierV3PhysicalPathInspection.remainingDistance(actor, path, position);
            // A legal building detour can initially move AWAY from the final goal.
            // Progress is actual planar displacement along the accepted path, not
            // decreasing straight-line goal distance or mere path recomputation.
            if (progressPath != path)
                return new Control(legalStations, target, scope, corridor, order, permission, refreshedAt,
                        lastProgressAt, position, path, remaining, followingWait, brakeOnArrival);
            double displacement = Math.hypot(position.x - lastProgressPosition.x, position.z - lastProgressPosition.z);
            if (bestRemaining - remaining >= 0.25D && displacement >= 0.25D)
                return new Control(legalStations, target, scope, corridor, order, permission, refreshedAt, tick, position, path, remaining,
                        followingWait.since() < 0 ? FollowingWait.empty() : new FollowingWait(tick, 0), brakeOnArrival);
            return this;
        }
        private Control refreshed(long tick, FrontierV3GoalNavigation.ProviderPermission currentPermission) {
            return new Control(legalStations, target, scope, corridor, order, currentPermission, tick, lastProgressAt,
                    lastProgressPosition, progressPath, bestRemaining, followingWait, brakeOnArrival);
        }
        private Control withCorridor(LocalNavigationEnvelope value) {
            return new Control(legalStations, target, scope, value, order, permission, refreshedAt, lastProgressAt,
                    lastProgressPosition, progressPath, bestRemaining, followingWait, brakeOnArrival);
        }
        private Control following(boolean waiting, long tick) {
            return new Control(legalStations, target, scope, corridor, order, permission, refreshedAt, lastProgressAt,
                    lastProgressPosition, progressPath, bestRemaining, followingWait.at(waiting, tick), brakeOnArrival);
        }
    }
    private static boolean waitForCrossing(Mob actor, Path path, long tick) {
        var wait = CROSSINGS.get(actor);
        if (wait == null || wait.path() != path) { wait = new CrossingWait(path, tick); CROSSINGS.put(actor, wait); }
        return tick - wait.since() < RETRY_TICKS;
    }
    /** Local deadline accounting only; never movement progress or durable world time. */
    record FollowingWait(long since, long elapsed) {
        static FollowingWait empty() { return new FollowingWait(-1, 0); }
        FollowingWait at(boolean waiting, long tick) {
            if (waiting) return since >= 0 ? this : new FollowingWait(tick, elapsed);
            return since < 0 ? this : new FollowingWait(-1, duration(tick));
        }
        long duration(long tick) { return elapsed + (since < 0 ? 0 : tick - since); }
    }
    static double remainingDistance(Path path, Vec3 position) {
        double distance = 0.0D;
        double x = position.x, z = position.z;
        for (int index = path.getNextNodeIndex(); index < path.getNodeCount(); index++) {
            var node = path.getNode(index);
            double nextX = node.x + 0.5D, nextZ = node.z + 0.5D;
            distance += Math.hypot(nextX - x, nextZ - z);
            x = nextX; z = nextZ;
        }
        return distance;
    }
    static double goalDistance(Vec3 position, SurfaceAnchor target) {
        return Math.hypot(position.x - (target.x() + 0.5D), position.z - (target.z() + 0.5D));
    }
    private record Failure(List<SurfaceAnchor> legalStations, FrontierV3NavigationScope scope,
                           Optional<MovementOrder> order, FrontierV3GoalNavigation.ProviderPermission permission,
                           long since, long lastAttemptAt, String reason,
                           FrontierV3GoalNavigation.BlockReason blockReason, boolean groundedAtAttempt,
                           List<FrontierV3PedestrianTraffic.Body> goalOccupants) {
        private Failure withPermission(FrontierV3GoalNavigation.ProviderPermission currentPermission) {
            return new Failure(legalStations, scope, order, currentPermission, since, lastAttemptAt, reason,
                    blockReason, groundedAtAttempt, goalOccupants);
        }
    }
}
