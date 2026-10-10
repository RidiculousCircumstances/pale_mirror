package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.extraction.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder;
import io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation;
import io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovementContext;
import java.util.*;

/** Area selection owns neither route policy nor depletion. Only proved reachable alternatives qualify. */
public final class ExtractionWorkTargets {
    private ExtractionWorkTargets() { }
    /** A source revision invalidates its mining leg, not an independent service exit
     * or the route of a current activity while this exact work is suspended. */
    public static FrontierWorldState withdrawTargetRoute(FrontierWorldState state, ExtractionWork job) {
        var movement = state.actorMovements().get(job.execution().actorId());
        if (movement == null || !(movement.context() instanceof ActorMovementContext.ExtractionLeg leg)
                || !leg.jobId().equals(job.id())) return state;
        if (!movement.executionId().equals(job.execution())
                || !movement.context().equals(new ActorMovementContext.ExtractionLeg(job.id(), job.revision()))
                || !movement.order().equals(job.movementOrder(ExtractionWorkAuthority.site(state, job))))
            throw new IllegalArgumentException("changed mining source has a stale or foreign dependent route");
        return io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovementWithdrawal.apply(state, job.execution());
    }
    /** A changed site may invalidate an already-issued goal, not just its next admission. */
    public static FrontierWorldState reconcile(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.SubjectId site) {
        for (var original : state.extractionSites().work().values().stream().filter(job -> job.siteId().equals(site))
                .sorted(Comparator.comparing(ExtractionWork::id)).toList()) {
            if (original.phase() != ExtractionWork.Phase.EXTRACT || original.pending().isPresent()
                    || !state.actorExecutions().actors().get(original.execution().actorId()).current().equals(Optional.of(original.execution()))) continue;
            var start = state.actorLocations().get(original.execution().actorId()).supportingSurface();
            try {
                // A retained geometry event must select the same successor on
                // replay, independently of the runtime planner's readiness.
                KnownPedestrianRouteKnowledge.forFrontier(state).path(start,
                        original.movementOrder(ExtractionWorkAuthority.site(state, original)));
                continue;
            } catch (KnownPedestrianNavigation.RouteUnavailable blocked) {
                var next = alternative(state, original);
                if (next.isEmpty()) continue; // No invented route, arrival or destructive effect.
                state = withdrawTargetRoute(state, original);
                state = state.withChanges(FrontierWorldStateUpdate.begin().extractionSites(state.extractionSites().replaceWork(
                        original, original.transition(ExtractionWork.Phase.EXTRACT, next))));
            }
        }
        return state;
    }
    public static Optional<ExtractionTarget> alternative(FrontierWorldState state, ExtractionWork job) {
        if (job.phase() != ExtractionWork.Phase.EXTRACT || job.pending().isPresent()) return Optional.empty();
        var deposit = state.extractionSites().deposits().get(job.siteId());
        var cells = deposit.site().layout().cells();
        long current = job.target().orElseThrow().key().cell();
        int previous = -1;
        for (int i = 0; i < cells.size(); i++) if (cells.get(i).id() == current) previous = i;
        var available = deposit.available(state.extractionSites().reservedCells(job.siteId())).stream()
                .filter(cell -> cell.definition().coldOutput().size() == 1
                        && cell.definition().coldOutput().getFirst().itemKind().equals(job.outputKind()))
                .map(ExtractionLayout.Cell::id).collect(java.util.stream.Collectors.toSet());
        var start = state.actorLocations().get(job.execution().actorId()).supportingSurface();
        var knowledge = KnownPedestrianRouteKnowledge.forFrontier(state);
        var selected = AreaWorkSelection.reachableAfter(cells.size(), previous, index -> {
            var cell = cells.get(index);
            if (!available.contains(cell.id())) return false;
            var order = new MovementOrder(job.id(), job.execution().actorId(), job.phase().wireTag(), job.revision(),
                    List.of(cell.workstation()), TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.EXACT_STATION);
            try { knowledge.path(start, order); return true; }
            catch (KnownPedestrianNavigation.RouteUnavailable unavailable) { return false; }
        });
        return selected.isEmpty() ? Optional.empty() : Optional.of(ExtractionWorkEffects.target(deposit, cells.get(selected.getAsInt())));
    }
}
