package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.extraction.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import java.util.*;

/** Extraction policy supplies mandate, actual known clearance and reachability to generic area selection. */
public final class ExtractionDevelopment {
    private ExtractionDevelopment() { }
    public static Optional<ExtractionFrontierOpened> proposal(FrontierWorldState state, SubjectId siteId) {
        var deposit = Objects.requireNonNull(state.extractionSites().deposits().get(siteId), "declared extraction site");
        var rules = state.bootstrap().ruleset().extraction();
        if (rules.developmentBatchCells() == 0 || SettlementWorkPolicy.permissions(state, deposit.site().settlementId())
                .workers(ResidentWorkKind.EXTRACTION).isEmpty()) return Optional.empty();
        var accessible = deposit.accessibleSources(Set.of()).stream().map(ExtractionLayout.Cell::id)
                .collect(java.util.stream.Collectors.toSet());
        var layout = deposit.site().layout(); var known = KnownPedestrianRouteKnowledge.forFrontier(state);
        java.util.function.LongPredicate reachable = id -> {
            if (!accessible.contains(id)) return false;
            var station = layout.require(id).workstation();
            var order = new MovementOrder(siteId, siteId, 0, deposit.development().revision(), List.of(station),
                    TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.EXACT_STATION);
            try { known.path(layout.storagePort(), order); return true; }
            catch (KnownPedestrianNavigation.RouteUnavailable blocked) { return false; }
        };
        // Reservations do not mean depletion. A damaged developed front does not veto reachable neighbours.
        if (deposit.available(Set.of()).stream().anyMatch(cell -> reachable.test(cell.id()))) return Optional.empty();
        var cells = layout.cells().stream().map(cell -> new AdjacentWorkArea.Cell(cell.id(), cell.source())).toList();
        var additions = AdjacentWorkArea.extend(cells, deposit.development(), reachable, rules.developmentBatchCells());
        return additions.isEmpty() ? Optional.empty() : Optional.of(new ExtractionFrontierOpened(siteId, deposit.development().revision(), additions));
    }
    public static FrontierWorldState apply(FrontierWorldState state, SubjectId subject, ExtractionFrontierOpened event) {
        if (!subject.equals(event.siteId()) || !proposal(state, subject).equals(Optional.of(event)))
            throw new IllegalArgumentException("stale, unavailable or foreign extraction development");
        var deposit = state.extractionSites().deposits().get(subject);
        return state.withChanges(FrontierWorldStateUpdate.begin().extractionSites(state.extractionSites().replace(
                deposit.develop(event.expectedRevision(), event.cells()))));
    }
}
