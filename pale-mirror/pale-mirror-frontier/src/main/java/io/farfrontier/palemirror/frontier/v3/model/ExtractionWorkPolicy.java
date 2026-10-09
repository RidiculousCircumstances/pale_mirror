package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.extraction.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import java.util.*;

/** Extraction owns mandate/frontier policy; shared activity, navigation and inventory retain their authorities. */
public final class ExtractionWorkPolicy {
    private ExtractionWorkPolicy() { }
    public static boolean requested(FrontierWorldState state, ExtractionWork job) {
        return SettlementWorkPolicy.permissions(state, ExtractionWorkAuthority.site(state, job).settlementId())
                .permits(ResidentWorkKind.EXTRACTION, job.execution().actorId());
    }
    public static boolean remaining(ExtractionDeposit deposit) {
        return deposit.cells().values().stream().anyMatch(cell -> cell.disposition() == ExtractionDeposit.Disposition.PRESENT);
    }
    /** Reservation by another miner is temporary unavailability, not depletion or completion. */
    public static Optional<ExtractionTarget> nextTarget(FrontierWorldState state, ExtractionWork job) {
        if (!requested(state, job)) return Optional.empty();
        var deposit = state.extractionSites().deposits().get(job.siteId());
        var start = state.actorLocations().get(job.execution().actorId()).supportingSurface();
        var knowledge = KnownPedestrianRouteKnowledge.forFrontier(state);
        return deposit.available(state.extractionSites().reservedCells(job.siteId())).stream()
                .filter(cell -> cell.definition().coldOutput().size() == 1
                        && cell.definition().coldOutput().getFirst().itemKind().equals(job.outputKind()))
                .sorted(Comparator.comparingLong((ExtractionLayout.Cell cell) -> ExtractionWorkEffects.distance(start, cell.workstation()))
                        .thenComparingLong(ExtractionLayout.Cell::id))
                .filter(cell -> {
                    var order = new MovementOrder(job.id(), job.execution().actorId(), ExtractionWork.Phase.EXTRACT.wireTag(),
                            job.revision(), List.of(cell.workstation()), TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.EXACT_STATION);
                    // This policy is also replayed by the reducer: volatile planner readiness
                    // must never select a different successor or turn waiting into completion.
                    try { knowledge.path(start, order); return true; }
                    catch (KnownPedestrianNavigation.RouteUnavailable unavailable) { return false; }
                }).findFirst().map(cell -> ExtractionWorkEffects.target(deposit, cell));
    }
    public static boolean finished(FrontierWorldState state, ExtractionWork job) {
        return !requested(state, job) || !remaining(state.extractionSites().deposits().get(job.siteId()));
    }
}
