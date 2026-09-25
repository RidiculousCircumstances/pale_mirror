package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.LocalNavigationEnvelope;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import io.farfrontier.palemirror.frontier.v3.model.TraversalCapability;
import io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

import java.util.Objects;
import java.util.Optional;
import java.util.List;

/** Provider-neutral HOT boundary: the caller names one retained goal, never a path engine. */
final class FrontierV3GoalNavigation {
    enum Status { IN_PROGRESS, ARRIVED, BLOCKED, AMBIGUOUS }
    enum BlockReason { PATH_UNAVAILABLE, PATH_STALLED, TARGET_CHUNK_UNLOADED, OFF_CONTRACT, UNSUPPORTED_CAPABILITY }
    record Goal(List<SurfaceAnchor> legalStations, TraversalCapability capability, LocalNavigationEnvelope envelope,
                Optional<MovementOrder> order) {
        Goal {
            legalStations = List.copyOf(Objects.requireNonNull(legalStations, "navigation goal stations"));
            Objects.requireNonNull(capability, "navigation capability");
            Objects.requireNonNull(envelope, "navigation envelope");
            order = Objects.requireNonNull(order, "navigation order identity");
            if (legalStations.isEmpty() || legalStations.size() > 8
                    || legalStations.stream().anyMatch(Objects::isNull)
                    || legalStations.stream().distinct().count() != legalStations.size()
                    || legalStations.stream().anyMatch(station -> !envelope.contains(station.support()))
                    || order.isPresent() && (!order.orElseThrow().legalStations().equals(legalStations)
                        || order.orElseThrow().capability() != capability))
                throw new IllegalArgumentException("navigation goal stations must be distinct and inside the declared envelope");
        }
        Goal(List<SurfaceAnchor> legalStations, TraversalCapability capability, LocalNavigationEnvelope envelope) {
            this(legalStations, capability, envelope, Optional.empty());
        }
        Goal(SurfaceAnchor target, TraversalCapability capability, LocalNavigationEnvelope envelope) {
            this(List.of(target), capability, envelope);
        }
        Goal(MovementOrder order, LocalNavigationEnvelope envelope) {
            this(order.legalStations(), order.capability(), envelope, Optional.of(order));
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
        var physical = FrontierV3MinecraftGoalNavigation.pursue(level, actor, goal.legalStations(), goal.envelope(), goal.order());
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
    static boolean controls(Mob actor) { return FrontierV3MinecraftGoalNavigation.controls(actor); }
    static void stop(Mob actor) { FrontierV3MinecraftGoalNavigation.stop(actor); }
}
