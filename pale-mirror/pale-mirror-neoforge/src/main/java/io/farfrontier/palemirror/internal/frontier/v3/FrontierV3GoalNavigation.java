package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.LocalNavigationEnvelope;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import io.farfrontier.palemirror.frontier.v3.model.TraversalCapability;
import io.farfrontier.palemirror.frontier.v3.model.WorldBounds;
import io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

import java.util.Objects;
import java.util.Optional;
import java.util.List;

/** Provider-neutral HOT boundary: the caller names one retained goal, never a path engine. */
final class FrontierV3GoalNavigation {
    enum Status { IN_PROGRESS, ARRIVED, BLOCKED, AMBIGUOUS }
    enum BlockReason { PATH_UNAVAILABLE, PATH_STALLED, TARGET_CHUNK_UNLOADED, OFF_CONTRACT,
        UNSUPPORTED_CAPABILITY, UNSUPPORTED_MEDIUM, SEARCH_BUDGET_EXHAUSTED }
    record Goal(List<SurfaceAnchor> legalStations, TraversalCapability capability, FrontierV3NavigationScope scope,
                Optional<MovementOrder> order, List<SurfaceAnchor> routeHint) {
        Goal {
            legalStations = List.copyOf(Objects.requireNonNull(legalStations, "navigation goal stations"));
            Objects.requireNonNull(capability, "navigation capability");
            Objects.requireNonNull(scope, "navigation scope");
            order = Objects.requireNonNull(order, "navigation order identity");
            routeHint = List.copyOf(Objects.requireNonNull(routeHint, "navigation route hint"));
            if (legalStations.isEmpty() || legalStations.size() > 8
                    || legalStations.stream().anyMatch(Objects::isNull)
                    || legalStations.stream().distinct().count() != legalStations.size()
                    || legalStations.stream().anyMatch(station -> !scope.permits(station.support()))
                    || routeHint.size() > 4096 || routeHint.stream().anyMatch(station -> !scope.permits(station.support()))
                    || !routeHint.isEmpty() && !legalStations.contains(routeHint.getLast())
                    || order.isPresent() && (!order.orElseThrow().legalStations().equals(legalStations)
                        || order.orElseThrow().capability() != capability))
                throw new IllegalArgumentException("navigation stations/hint must fit the declared goal and hard scope");
        }
        Goal(List<SurfaceAnchor> legalStations, TraversalCapability capability, LocalNavigationEnvelope envelope) {
            this(legalStations, capability, new FrontierV3NavigationScope.Restricted(envelope), Optional.empty(), List.of());
        }
        Goal(SurfaceAnchor target, TraversalCapability capability, LocalNavigationEnvelope envelope) {
            this(List.of(target), capability, envelope);
        }
        Goal(MovementOrder order, LocalNavigationEnvelope envelope) {
            this(order.legalStations(), order.capability(), new FrontierV3NavigationScope.Restricted(envelope),
                    Optional.of(order), List.of());
        }
        static Goal routed(MovementOrder order, List<SurfaceAnchor> routeHint, WorldBounds bounds) {
            return new Goal(order.legalStations(), order.capability(), new FrontierV3NavigationScope.ObservedWorld(bounds),
                    Optional.of(order), routeHint);
        }
    }
    record Result(Status status, String reason, Optional<BlockReason> blockReason,
                  Optional<SurfaceAnchor> arrivedStation) {
        Result {
            Objects.requireNonNull(status); Objects.requireNonNull(reason); Objects.requireNonNull(blockReason);
            Objects.requireNonNull(arrivedStation);
            if ((status == Status.BLOCKED) != blockReason.isPresent()
                    || (status == Status.ARRIVED) != arrivedStation.isPresent())
                throw new IllegalArgumentException("goal navigation result requires exact block or arrival evidence");
        }
    }

    private FrontierV3GoalNavigation() { }

    static Result pursue(ServerLevel level, Mob actor, Goal goal) {
        Objects.requireNonNull(level, "navigation level");
        Objects.requireNonNull(actor, "navigation actor");
        Objects.requireNonNull(goal, "navigation goal");
        if (goal.capability() != TraversalCapability.PEDESTRIAN) {
            stop(actor);
            return new Result(Status.BLOCKED, "no-registered-provider-for-" + goal.capability(),
                    Optional.of(BlockReason.UNSUPPORTED_CAPABILITY), Optional.empty());
        }
        var physical = FrontierV3RouteNavigation.pursue(level, actor, goal);
        return new Result(switch (physical.status()) {
            case IN_PROGRESS -> Status.IN_PROGRESS;
            case ARRIVED -> Status.ARRIVED;
            case BLOCKED -> Status.BLOCKED;
            case AMBIGUOUS -> Status.AMBIGUOUS;
        }, physical.reason(), physical.blockReason(), physical.arrivedStation());
    }

    static boolean advanceAtEntityBoundary(Mob actor) {
        return FrontierV3MinecraftGoalNavigation.advanceAtEntityBoundary(actor);
    }
    /** Read-only feasibility probe for owner-selected alternatives; never starts movement or awards work. */
    static boolean canReach(ServerLevel level, Mob actor, Goal goal) {
        return goal.capability() == TraversalCapability.PEDESTRIAN
                && FrontierV3MinecraftGoalNavigation.canReach(level, actor, goal.legalStations(), goal.scope());
    }
    static boolean controls(Mob actor) { return FrontierV3MinecraftGoalNavigation.controls(actor); }
    static void stop(Mob actor) {
        FrontierV3RouteNavigation.stop(actor);
        FrontierV3MinecraftGoalNavigation.stop(actor);
    }
}
