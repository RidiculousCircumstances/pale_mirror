package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.extraction.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import java.util.*;

/** Area selection owns neither route policy nor depletion. Only proved reachable alternatives qualify. */
public final class ExtractionWorkTargets {
    private ExtractionWorkTargets() { }
    /** Shared area-work ranking keeps separate miners local without excluding anyone's remaining work. */
    public static Optional<ExtractionLayout.Cell> spatialSelection(FrontierWorldState state, ExtractionDeposit deposit,
            io.farfrontier.palemirror.frontier.v3.api.SubjectId actor, List<ExtractionLayout.Cell> available,
            AreaWorkSelection.SpatialPolicy policy, Optional<io.farfrontier.palemirror.frontier.v3.api.SubjectId> completing) {
        return spatialSelection(state, deposit, actor, available, policy, ExtractionPriorities.read(state, deposit, completing));
    }
    public static Optional<ExtractionLayout.Cell> spatialSelection(FrontierWorldState state, ExtractionDeposit deposit,
            io.farfrontier.palemirror.frontier.v3.api.SubjectId actor, List<ExtractionLayout.Cell> available,
            AreaWorkSelection.SpatialPolicy policy, ExtractionPriorities priorities) {
        var peers = peerStations(state, deposit, actor);
        var cells = available.stream().filter(cell -> priorities.require(cell.id()).missing() > 0)
                .filter(cell -> ExtractionWorkSafety.available(state, deposit, cell))
                .sorted(Comparator.comparingLong(ExtractionLayout.Cell::id)).toList();
        var best = cells.stream().filter(cell -> AreaWorkSelection.stationAvailable(cell.workstation(), peers))
                .map(cell -> priorities.require(cell.id())).min(io.farfrontier.palemirror.frontier.v3.model.supply.SupplyPriority::compareTo);
        var selected = AreaWorkSelection.spatiallySeparated(cells.size(), index -> best.isPresent()
                    && priorities.require(cells.get(index).id()).compareTo(best.orElseThrow()) == 0,
                state.actorLocations().get(actor).supportingSurface(), index -> cells.get(index).workstation(), peers, policy);
        return selected.isEmpty() ? Optional.empty() : Optional.of(cells.get(selected.getAsInt()));
    }
    private static List<SurfaceAnchor> peerStations(FrontierWorldState state, ExtractionDeposit deposit,
            io.farfrontier.palemirror.frontier.v3.api.SubjectId actor) {
        return state.extractionSites().work().values().stream()
                .filter(job -> job.siteId().equals(deposit.site().id()) && !job.execution().actorId().equals(actor))
                .flatMap(job -> job.target().stream().map(target -> deposit.site().layout().require(target.key().cell()).workstation()))
                .toList();
    }
    public static void requireStationAvailable(FrontierWorldState state, ExtractionDeposit deposit,
            io.farfrontier.palemirror.frontier.v3.api.SubjectId actor, ExtractionLayout.Cell cell) {
        if (!AreaWorkSelection.stationAvailable(cell.workstation(), peerStations(state, deposit, actor)))
            throw new IllegalArgumentException("mining source has a concurrently claimed worker station");
    }
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
            var movement = state.actorMovements().get(original.execution().actorId());
            if (movement == null || !(movement.context() instanceof ActorMovementContext.ExtractionLeg leg)
                    || !leg.jobId().equals(original.id())) continue;
            if (!movement.executionId().equals(original.execution()) || leg.jobRevision() != original.revision()
                    || !movement.order().equals(original.movementOrder(ExtractionWorkAuthority.site(state, original))))
                throw new IllegalArgumentException("mining geometry invalidation has a stale retained move");
            var knowledge = knowledge(state, original);
            try {
                // Only invalidate exact retained geometry. An observation must not discover
                // a replacement task or synchronously prove whether a detour exists.
                knowledge.requireRoute(original.movementOrder(ExtractionWorkAuthority.site(state, original)).legalStations());
                movement.coldTravel().ifPresent(travel -> knowledge.requireRoute(travel.route()));
                continue;
            } catch (IllegalArgumentException changed) {
                state = withdrawTargetRoute(state, original);
            }
        }
        return state;
    }
    public static Optional<ExtractionWorkDecision.Source> alternative(FrontierWorldState state, ExtractionWork job) {
        if (job.phase() != ExtractionWork.Phase.EXTRACT || job.pending().isPresent()) return Optional.empty();
        var deposit = state.extractionSites().deposits().get(job.siteId());
        var required = ExtractionStockDemand.requiredCells(state, deposit, Optional.of(job.id()));
        var available = deposit.available(state.extractionSites().reservedCells(job.siteId())).stream()
                .filter(cell -> required.contains(cell.id()))
                .filter(cell -> cell.definition().coldOutput().size() == 1
                        && cell.definition().coldOutput().getFirst().itemKind().equals(job.outputKind()))
                .toList();
        return plannedSelection(state, job, available, AreaWorkSelection.SpatialPolicy.LOCAL_CONTINUATION);
    }
    public static Optional<ExtractionWorkDecision.Source> plannedSelection(FrontierWorldState state, ExtractionWork job,
            List<ExtractionLayout.Cell> available, AreaWorkSelection.SpatialPolicy policy) {
        var deposit = state.extractionSites().deposits().get(job.siteId());
        return plannedAt(state, job, deposit, available, policy);
    }
    static Optional<ExtractionWorkDecision.Source> plannedAt(FrontierWorldState state, ExtractionWork job,
            ExtractionDeposit deposit, List<ExtractionLayout.Cell> available, AreaWorkSelection.SpatialPolicy policy) {
        var start = state.actorLocations().get(job.execution().actorId()).supportingSurface();
        var knowledge = knowledge(state, job);
        var priorities = ExtractionPriorities.read(state, deposit, Optional.of(job.id()));
        var cells = new ArrayList<>(available);
        while (!cells.isEmpty()) {
            var candidate = spatialSelection(state, deposit, job.execution().actorId(), cells, policy, priorities);
            if (candidate.isEmpty()) return Optional.empty();
            var cell = candidate.orElseThrow();
            cells.remove(cell);
            try {
                var route = knowledge.plannedBetween(start, cell.workstation());
                return Optional.of(new ExtractionWorkDecision.Source(job.execution(), start, ExtractionWorkEffects.target(deposit, cell),
                        route.size() > 1 ? Optional.of(new PedestrianRouteReceipt(route)) : Optional.empty()));
            } catch (KnownPedestrianNavigation.RouteUnavailable unavailable) {
                if (unavailable.status() != PedestrianRouteResult.Status.NO_PATH) throw unavailable;
            }
        }
        return Optional.empty();
    }
    public static void requireSelection(FrontierWorldState state, ExtractionWork job, ExtractionWorkDecision.Source selected) {
        var deposit = state.extractionSites().deposits().get(job.siteId());
        var cell = deposit.available(state.extractionSites().reservedCells(job.siteId())).stream()
                .filter(value -> ExtractionWorkEffects.target(deposit, value).equals(selected.target())).findFirst().orElseThrow(
                        () -> new IllegalArgumentException("selected mining source is removed, reserved or stale"));
        requireStationAvailable(state, deposit, job.execution().actorId(), cell);
        if (!ExtractionWorkSafety.available(state, deposit, cell))
            throw new IllegalArgumentException("selected mining source removes an occupied support or retained access edge");
        if (!ExtractionStockDemand.requiredCells(state, deposit, Optional.of(job.id())).contains(cell.id()))
            throw new IllegalArgumentException("selected mining source is not a requested product or prerequisite");
        if (ExtractionPriorities.read(state, deposit, Optional.of(job.id())).require(cell.id()).missing() == 0)
            throw new IllegalArgumentException("selected mining source duplicates assigned settlement demand");
        boolean emptyPart = job.phase() == ExtractionWork.Phase.SELECT_SOURCE && ExtractionWorkAuthority.carried(state, job) == 0;
        if (!job.execution().equals(selected.execution()) || !selected.origin().equals(
                state.actorLocations().get(job.execution().actorId()).supportingSurface())
                || !ExtractionWorkPolicy.supportsTool(state, cell, job)
                || !emptyPart && !ExtractionWorkPolicy.matchingOutput(cell, job))
            throw new IllegalArgumentException("selected mining source has a foreign execution, departure or output");
        var route = selected.route().map(PedestrianRouteReceipt::route).orElseGet(() -> List.of(selected.origin()));
        if (!route.getFirst().equals(selected.origin()) || !route.getLast().equals(cell.workstation()))
            throw new IllegalArgumentException("selected mining source has a foreign route");
        knowledge(state, job).requireRoute(route);
    }
    public static KnownPedestrianRouteKnowledge knowledge(FrontierWorldState state, ExtractionWork job) {
        return ResourceStorageNavigation.forJourney(state, ExtractionWorkAuthority.site(state, job).settlementId(),
                List.of(job.toolSource(), job.storageTarget())).forActor(state, job.execution().actorId());
    }
}
