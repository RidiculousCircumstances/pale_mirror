package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/** Ephemeral waiting leg; never changes the retained owner goal or awards its arrival. */
final class FrontierV3ServiceApproachNavigation {
    // One rebuildable local target per live body, never a canonical queue or a movement writer.
    private record Waiting(FrontierV3ActorActuation authority, SubjectId point, long ordinal,
                           long generation, SurfaceAnchor target) { }
    private static final Map<Mob, Waiting> WAITING = new WeakHashMap<>();
    private record Search(io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId authority,
                          SubjectId point, long ordinal, long generation, FrontierV3GoalAlternatives alternatives) { }
    private static final Map<Mob, Search> SEARCHES = new WeakHashMap<>();
    private FrontierV3ServiceApproachNavigation() { }

    static void forget(Mob body) { WAITING.remove(body); SEARCHES.remove(body); }

    /** Travel does not acquire service: reserve a local waiting target, never the shared egress. */
    static FrontierV3GoalNavigation.Result waitOutside(ServerLevel level, Mob body, FrontierWorldState state,
            SubjectId pointId, SubjectId actorId, long ordinal, long generation,
            FrontierV3ActorActuation actuation) {
        requireWaitingAuthority(state, actorId, actuation.id());
        var declared = ServiceBoundaryComposition.declaration(state, pointId);
        var point = ServiceBoundaryComposition.geometry(state, declared.identity());
        if (point.boundary().occupied(FrontierV3SurfaceObservation.observedBody(body))) {
            forget(body);
            var exit = FrontierV3ServiceClearanceNavigation.pursue(level, body, state, declared.settlementId(),
                    pointId, actuation.id().execution().activityOwnerId(), actorId, ordinal, generation, actuation);
            return exit.status() == FrontierV3GoalNavigation.Status.ARRIVED
                    ? new FrontierV3GoalNavigation.Result(FrontierV3GoalNavigation.Status.IN_PROGRESS,
                        "service-access:cleared-for-waiting", Optional.empty(), Optional.empty()) : exit;
        }
        var excluded = new HashSet<>(ServiceDestinationClaims.excludedFor(state, actorId));
        WAITING.entrySet().removeIf(entry -> !entry.getValue().authority().current(entry.getKey()));
        WAITING.forEach((other, waiting) -> { if (other != body) excluded.add(waiting.target()); });
        var prior = WAITING.get(body);
        if (prior != null && (!prior.authority().id().equals(actuation.id()) || !prior.point().equals(pointId)
                || prior.ordinal() != ordinal || prior.generation() != generation
                || !point.waitingSurfaces().contains(prior.target()) || excluded.contains(prior.target())
                || !FrontierV3SemanticMovement.targetGeometryIsNavigable(level, body, prior.target()))) {
            WAITING.remove(body); prior = null;
        }
        var candidates = ServiceClearanceTargets.waitingTargets(point,
                FrontierV3SurfaceObservation.observedBody(body).supportingSurface(), excluded).stream()
                .filter(candidate -> FrontierV3LoadedWorld.ready(level,
                        new net.minecraft.core.BlockPos(candidate.x(), candidate.y(), candidate.z()))
                        && FrontierV3SemanticMovement.targetGeometryIsNavigable(level, body, candidate)).toList();
        if (candidates.isEmpty()) {
            forget(body);
            FrontierV3GoalNavigation.stop(body, actuation);
            return new FrontierV3GoalNavigation.Result(FrontierV3GoalNavigation.Status.BLOCKED,
                    "service-access:no-reachable-free-waiting-target",
                    Optional.of(FrontierV3GoalNavigation.BlockReason.SEARCH_BUDGET_EXHAUSTED), Optional.empty());
        }
        var search = SEARCHES.get(body);
        if (search == null || !search.authority().equals(actuation.id()) || !search.point().equals(pointId)
                || search.ordinal() != ordinal || search.generation() != generation) {
            search = new Search(actuation.id(), pointId, ordinal, generation, new FrontierV3GoalAlternatives(candidates));
        } else if (!search.alternatives().matches(candidates)) {
            // Candidate order changes as the visitor walks. Keep already rejected identities,
            // rather than moving a numeric cursor through a freshly distance-sorted list.
            var rejected = new HashSet<>(search.alternatives().rejected()); rejected.retainAll(candidates);
            search = new Search(actuation.id(), pointId, ordinal, generation,
                    new FrontierV3GoalAlternatives(candidates, rejected));
        }
        var alternatives = search.alternatives().nextPassIfExhausted();
        var batch = prior == null ? alternatives.batch() : List.of(prior.target());
        var order = new io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder(
                actuation.id().execution().activityOwnerId(), actorId, ordinal, generation, batch,
                TraversalCapability.PEDESTRIAN,
                io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder.ArrivalPolicy.ANY_DECLARED_STATION);
        var goal = waitingGoal(point, batch, state.bootstrap().bounds(), order);
        var demand = new FrontierV3MinecraftGoalNavigation.Demand(batch, goal.scope(), goal.order());
        var selected = FrontierV3MinecraftGoalNavigation.selectedTarget(body, demand);
        var result = FrontierV3GoalNavigation.pursue(level, body, goal, actuation);
        if (FrontierV3GoalAlternatives.reconsider(result)) {
            var failed = result.blockReason().filter(reason -> reason == FrontierV3GoalNavigation.BlockReason.PATH_STALLED)
                    .isPresent() && selected.isPresent() ? List.of(selected.orElseThrow()) : batch;
            alternatives = alternatives.reject(failed);
            WAITING.remove(body);
        } else {
            // Reserve only the endpoint the shared provider actually selected, not every
            // alternative. This is a physical waiting claim, never service/task arrival.
            var target = result.arrivedStation().or(() -> FrontierV3MinecraftGoalNavigation.selectedTarget(body, demand));
            target.filter(batch::contains).ifPresent(station -> WAITING.put(body,
                    new Waiting(actuation, pointId, ordinal, generation, station)));
        }
        SEARCHES.put(body, new Search(actuation.id(), pointId, ordinal, generation, alternatives));
        return result.status() == FrontierV3GoalNavigation.Status.ARRIVED
                ? new FrontierV3GoalNavigation.Result(FrontierV3GoalNavigation.Status.IN_PROGRESS,
                    "service-access:waiting-outside target=" + result.arrivedStation().orElseThrow(), Optional.empty(), Optional.empty())
                : result; // Never return semantic ARRIVED to a work owner.
    }

    /** A staging checkpoint is a physical position fact, never owner/service arrival. */
    static boolean reachedWaitingTarget(ServerLevel level, Mob body, SubjectId point) {
        var waiting = WAITING.get(body);
        return waiting != null && waiting.point().equals(point) && waiting.authority().current(body)
                && FrontierV3SemanticMovement.arrived(level, body, waiting.target());
    }

    /**
     * The owning provider validates its service/order relation before calling us.
     * A parcel can legitimately be serviced by a GROUP_MEMBER execution whose
     * owner is the group, not the parcel. Waiting only exercises that actor's
     * exact captured body/execution; it cannot acquire or complete service.
     */
    static void requireWaitingAuthority(FrontierWorldState state, SubjectId actorId,
            io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId authority) {
        if (!authority.execution().actorId().equals(actorId))
            throw new IllegalArgumentException("service waiting addresses a foreign actor: actor="
                    + actorId + "; authority=" + authority);
        ActorBodyAuthority.requireActuation(state, authority);
    }

    private static List<SurfaceAnchor> waitingHint(ServiceAccessPoint point, SurfaceAnchor target, List<SurfaceAnchor> approach) {
        if (approach.isEmpty()) return List.of();
        var hint = new java.util.ArrayList<SurfaceAnchor>();
        for (var surface : approach) {
            if (!point.boundary().cleared(surface.standingBody())) break;
            if (hint.size() >= io.farfrontier.palemirror.frontier.v3.model.navigation.HierarchicalPedestrianSearch.MAX_ROUTE_SURFACES - 1) break;
            hint.add(surface);
            long distance = Math.abs((long) surface.x() - target.x()) + Math.abs((long) surface.y() - target.y())
                    + Math.abs((long) surface.z() - target.z());
            // Branch towards this visitor's buffer before reaching the common throat.
            if (distance <= FrontierV3RouteNavigation.MAX_LEG_SUPPORTS) break;
        }
        if (hint.isEmpty()) return List.of();
        if (!hint.getLast().equals(target)) hint.add(target);
        return List.copyOf(hint);
    }

    static FrontierV3GoalNavigation.Goal waitingGoal(ServiceAccessPoint point, SurfaceAnchor target, WorldBounds bounds,
            io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder order) {
        return waitingGoal(point, target, bounds, order, List.of());
    }
    static FrontierV3GoalNavigation.Goal waitingGoal(ServiceAccessPoint point, SurfaceAnchor target, WorldBounds bounds,
            io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder order, List<SurfaceAnchor> approach) {
        if (!point.waitingSurfaces().contains(target) || point.egressSurfaces().contains(target)
                || !point.boundary().cleared(target.standingBody()))
            throw new IllegalArgumentException("waiting target must belong to the declared non-egress buffer");
        return new FrontierV3GoalNavigation.Goal(List.of(target), TraversalCapability.PEDESTRIAN,
                new FrontierV3NavigationScope.OutsideService(new FrontierV3NavigationScope.ObservedWorld(bounds), point.boundary()),
                Optional.of(order), waitingHint(point, target, approach));
    }
    static FrontierV3GoalNavigation.Goal waitingGoal(ServiceAccessPoint point, List<SurfaceAnchor> targets,
            WorldBounds bounds, io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder order) {
        if (targets.isEmpty() || targets.stream().anyMatch(target -> !point.waitingSurfaces().contains(target)
                || point.egressSurfaces().contains(target) || !point.boundary().cleared(target.standingBody())))
            throw new IllegalArgumentException("waiting targets must belong to the declared non-egress buffer");
        return new FrontierV3GoalNavigation.Goal(targets, TraversalCapability.PEDESTRIAN,
                new FrontierV3NavigationScope.OutsideService(new FrontierV3NavigationScope.ObservedWorld(bounds), point.boundary()),
                Optional.of(order), List.of());
    }

}
