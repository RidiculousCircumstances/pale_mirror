package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.LocalNavigationEnvelope;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import io.farfrontier.palemirror.frontier.v3.model.TraversalCapability;
import io.farfrontier.palemirror.frontier.v3.model.WorldBounds;
import io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder;
import io.farfrontier.palemirror.frontier.v3.model.navigation.TravelPace;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

import java.util.Objects;
import java.util.Optional;
import java.util.List;

/** Provider-neutral HOT boundary: the caller names one retained goal, never a path engine. */
final class FrontierV3GoalNavigation {
    private static final java.util.Map<Mob, FrontierV3ActorActuation> ACTUATIONS = new java.util.WeakHashMap<>();
    /** Opaque provider permission, minted here only and bound to the original command/body. */
    abstract static sealed class ProviderPermission permits CapturedPermission, UnmodeledPermission {
        private ProviderPermission() { }
        abstract boolean current(Mob actor);
        Optional<TravelPace> pace() { return Optional.empty(); }
    }
    private static final class CapturedPermission extends ProviderPermission {
        private final Mob actor;
        private final FrontierV3ActorActuation actuation;
        private final Optional<TravelPace> pace;
        private CapturedPermission(Mob actor, FrontierV3ActorActuation actuation, Optional<TravelPace> pace) {
            this.actor = Objects.requireNonNull(actor); this.actuation = Objects.requireNonNull(actuation);
            this.pace = Objects.requireNonNull(pace);
        }
        @Override boolean current(Mob candidate) { return candidate == actor && actuation.current(candidate); }
        @Override Optional<TravelPace> pace() { return pace; }
    }
    /** Unmodeled physical fixtures have no canonical identity and cannot acquire one through this permission. */
    private static final class UnmodeledPermission extends ProviderPermission {
        private final Mob actor;
        private UnmodeledPermission(Mob actor) { this.actor = Objects.requireNonNull(actor); }
        @Override boolean current(Mob candidate) {
            return candidate == actor && permitsUnmodeledNavigation(candidate.getPersistentData(), ACTUATIONS.containsKey(candidate));
        }
    }
    enum Status { IN_PROGRESS, ARRIVED, BLOCKED, AMBIGUOUS }
    enum BlockReason { PATH_UNAVAILABLE, PATH_STALLED, TRAFFIC_BLOCKED, TARGET_CHUNK_UNLOADED, OFF_CONTRACT,
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
                    || routeHint.size() > io.farfrontier.palemirror.frontier.v3.model.navigation.HierarchicalPedestrianSearch.MAX_ROUTE_SURFACES
                    || routeHint.stream().anyMatch(station -> !scope.permits(station.support()))
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
        static Goal station(SurfaceAnchor station, FrontierV3NavigationScope scope) {
            return new Goal(List.of(station), TraversalCapability.PEDESTRIAN, scope, Optional.empty(), List.of());
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

    /** Physical tactical/presentation target, not a producer of canonical ownership or progress. */
    static Result pursueLocalFeetTarget(ServerLevel level, Mob actor, net.minecraft.world.phys.Vec3 feet,
                                        FrontierV3NavigationScope scope, FrontierV3ActorActuation actuation) {
        Objects.requireNonNull(actuation, "local target actuation");
        if (!actuation.current(actor))
            return new Result(Status.AMBIGUOUS, "stale-body-or-execution-authority", Optional.empty(), Optional.empty());
        // Feet can lie on a fractional collision top (farmland/slab). This conversion names
        // the requested column; only the ordinary shared collision observation can award arrival.
        SurfaceAnchor station = SurfaceAnchor.at((int) Math.floor(feet.x), (int) Math.ceil(feet.y) - 1,
                (int) Math.floor(feet.z));
        if (!Double.isFinite(feet.x) || !Double.isFinite(feet.y) || !Double.isFinite(feet.z)
                || !scope.permits(station.support())) {
            stop(actor, actuation);
            return new Result(Status.BLOCKED, "physical-target-outside-task-scope",
                    Optional.of(BlockReason.OFF_CONTRACT), Optional.empty());
        }
        return pursue(level, actor, Goal.station(station, scope), actuation);
    }

    /** A topology owner supplies its retained edge; shared navigation owns physical actuation. */
    static Result pursueRetainedEdge(ServerLevel level, Mob actor, SurfaceAnchor current, SurfaceAnchor next,
                                     FrontierV3ActorActuation actuation) {
        return pursue(level, actor, Goal.station(next, new FrontierV3NavigationScope.Restricted(
                io.farfrontier.palemirror.frontier.v3.model.LocalNavigationEnvelope.around(
                        current.standingBody(), next.standingBody()))), actuation);
    }

    static Result pursue(ServerLevel level, Mob actor, Goal goal) {
        requireUnfenced(actor);
        return pursueProvider(level, actor, goal, new UnmodeledPermission(actor));
    }

    /** A successor may replace a path, but stale authority cannot refresh or cancel that path. */
    static Result pursue(ServerLevel level, Mob actor, Goal goal, FrontierV3ActorActuation actuation) {
        return pursue(level, actor, goal, actuation, Optional.empty());
    }
    static Result pursue(ServerLevel level, Mob actor, Goal goal, FrontierV3ActorActuation actuation,
                          Optional<TravelPace> pace) {
        Objects.requireNonNull(actuation, "navigation actuation");
        if (!actuation.current(actor))
            return new Result(Status.AMBIGUOUS, "stale-body-or-execution-authority", Optional.empty(), Optional.empty());
        if (FrontierV3PedestrianCourtesy.active(actor))
            return new Result(Status.IN_PROGRESS, "owner-authorized-spatial-yield", Optional.empty(), Optional.empty());
        return pursueCaptured(level, actor, goal, actuation, pace);
    }

    static Result pursueCourtesy(ServerLevel level, Mob actor, Goal goal, FrontierV3ActorActuation actuation) {
        if (!FrontierV3PedestrianCourtesy.active(actor))
            throw new IllegalArgumentException("courtesy motion requires its admitted owner checkpoint");
        return pursueCaptured(level, actor, goal, actuation, Optional.empty());
    }

    private static Result pursueCaptured(ServerLevel level, Mob actor, Goal goal, FrontierV3ActorActuation actuation,
                                          Optional<TravelPace> pace) {
        Objects.requireNonNull(actuation, "navigation actuation");
        if (!actuation.current(actor))
            return new Result(Status.AMBIGUOUS, "stale-body-or-execution-authority", Optional.empty(), Optional.empty());
        var previous = ACTUATIONS.get(actor);
        if (previous == null || !previous.id().equals(actuation.id())) {
            clearProvider(actor);
            FrontierV3ControlledMobMotion.retireLocalActuation(actor);
        }
        ACTUATIONS.put(actor, actuation);
        return pursueProvider(level, actor, goal, new CapturedPermission(actor, actuation, pace));
    }

    private static Result pursueProvider(ServerLevel level, Mob actor, Goal goal, ProviderPermission permission) {
        Objects.requireNonNull(level, "navigation level");
        Objects.requireNonNull(actor, "navigation actor");
        Objects.requireNonNull(goal, "navigation goal");
        if (goal.capability() != TraversalCapability.PEDESTRIAN) {
            clearProvider(actor);
            return new Result(Status.BLOCKED, "no-registered-provider-for-" + goal.capability(),
                    Optional.of(BlockReason.UNSUPPORTED_CAPABILITY), Optional.empty());
        }
        var physical = FrontierV3RouteNavigation.pursue(level, actor, goal, permission);
        return new Result(switch (physical.status()) {
            case IN_PROGRESS -> Status.IN_PROGRESS;
            case ARRIVED -> Status.ARRIVED;
            case BLOCKED -> Status.BLOCKED;
            case AMBIGUOUS -> Status.AMBIGUOUS;
        }, physical.reason(), physical.blockReason(), physical.arrivedStation());
    }

    static boolean advanceAtEntityBoundary(Mob actor) {
        if (!validateRetainedActuation(actor)) return false;
        return FrontierV3MinecraftGoalNavigation.advanceAtEntityBoundary(actor);
    }
    /** Read-only feasibility probe for owner-selected alternatives; never starts movement or awards work. */
    static boolean canReach(ServerLevel level, Mob actor, Goal goal) {
        return goal.capability() == TraversalCapability.PEDESTRIAN
                && FrontierV3MinecraftGoalNavigation.canReach(level, actor, goal.legalStations(), goal.scope());
    }
    static boolean controls(Mob actor) {
        return validateRetainedActuation(actor) && FrontierV3MinecraftGoalNavigation.controls(actor);
    }
    static void stop(Mob actor) {
        requireUnfenced(actor);
        clearProvider(actor);
    }
    static boolean stop(Mob actor, FrontierV3ActorActuation actuation) {
        if (FrontierV3PedestrianCourtesy.active(actor)) return false;
        return stopCourtesy(actor, actuation);
    }

    static boolean stopCourtesy(Mob actor, FrontierV3ActorActuation actuation) {
        Objects.requireNonNull(actuation, "stop actuation");
        if (!actuation.current(actor)) return false;
        var retained = ACTUATIONS.get(actor);
        if (retained != null && !retained.id().equals(actuation.id()) && retained.current(actor)) return false;
        ACTUATIONS.put(actor, actuation);
        clearProvider(actor);
        FrontierV3ControlledMobMotion.retireLocalActuation(actor);
        return true;
    }
    private static boolean validateRetainedActuation(Mob actor) {
        var retained = ACTUATIONS.get(actor);
        if (retained == null && permitsUnmodeledNavigation(actor.getPersistentData(), false)
                || retained != null && retained.current(actor)) return true;
        // Keep the fence after quiescing: an unversioned legacy refresh cannot revive this path.
        clearProvider(actor);
        FrontierV3ControlledMobMotion.retireLocalActuation(actor);
        return false;
    }
    /** Join/inspection cannot take a new activity's STOP authority; only obsolete commands quiesce. */
    static void quiesceStaleActuation(Mob actor) {
        validateRetainedActuation(Objects.requireNonNull(actor, "physical actor"));
    }
    /** Physical retirement, not a stale activity STOP or a lookup of its successor execution. */
    static boolean retireIncarnation(Mob actor, io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId body) {
        var declaration = FrontierV3ActorCarrierComposition.declaredBy(actor).orElse(null);
        if (declaration == null || !declaration.actorId().equals(body.actorId()) || declaration.epoch() != body.physicalEpoch())
            return false;
        var retained = ACTUATIONS.get(actor);
        if (retained != null && !retained.id().body().equals(body)) return false;
        clearProvider(actor);
        FrontierV3ControlledMobMotion.retireLocalActuation(actor);
        ACTUATIONS.remove(actor);
        return true;
    }
    private static void requireUnfenced(Mob actor) {
        if (!permitsUnmodeledNavigation(actor.getPersistentData(), ACTUATIONS.containsKey(actor)))
            throw new IllegalArgumentException("actor navigation requires its captured actuation authority");
    }
    static boolean permitsUnmodeledNavigation(net.minecraft.nbt.CompoundTag metadata, boolean captured) {
        return !captured && !FrontierV3ActorCarrierComposition.hasDeclarationMetadata(metadata);
    }
    private static void clearProvider(Mob actor) {
        FrontierV3RouteNavigation.stop(actor);
        FrontierV3MinecraftGoalNavigation.stop(actor);
    }
}
