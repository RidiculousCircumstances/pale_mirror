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
        var access = known.accessSelection(layout.storagePort());
        java.util.function.LongPredicate reachable = id -> accessible.contains(id) && access.reachable(layout.require(id).workstation());
        // Reservations do not mean depletion. A damaged developed front does not veto reachable neighbours.
        if (deposit.available(Set.of()).stream().anyMatch(cell -> reachable.test(cell.id()))) return Optional.empty();
        access.awaitUnresolved();
        var cells = layout.cells().stream().map(cell -> new AdjacentWorkArea.Cell(cell.id(), cell.source())).toList();
        var additions = AdjacentWorkArea.extend(cells, deposit.development(), reachable, rules.developmentBatchCells());
        if (additions.isEmpty()) { access.awaitUnresolved(); return Optional.empty(); }
        return Optional.of(new ExtractionFrontierOpened(siteId, deposit.development().revision(), additions,
                access.receipt(additions.stream().map(id -> layout.require(id).workstation()).collect(java.util.stream.Collectors.toSet()))));
    }
    public static FrontierWorldState apply(FrontierWorldState state, SubjectId subject, ExtractionFrontierOpened event) {
        var deposit = state.extractionSites().deposits().get(subject);
        var rules = state.bootstrap().ruleset().extraction();
        if (!subject.equals(event.siteId()) || deposit == null || deposit.development().revision() != event.expectedRevision()
                || rules.developmentBatchCells() == 0 || event.cells().size() > rules.developmentBatchCells()
                || SettlementWorkPolicy.permissions(state, deposit.site().settlementId()).workers(ResidentWorkKind.EXTRACTION).isEmpty())
            throw new IllegalArgumentException("stale, unavailable or foreign extraction development");
        var accessible = deposit.accessibleSources(Set.of()).stream().map(ExtractionLayout.Cell::id).collect(java.util.stream.Collectors.toSet());
        if (!accessible.containsAll(event.cells())) throw new IllegalArgumentException("developed source has changed physical prerequisites");
        var cells = deposit.site().layout().cells().stream().map(cell -> new AdjacentWorkArea.Cell(cell.id(), cell.source())).toList();
        if (!AdjacentWorkArea.extend(cells, deposit.development(), event.cells()::contains, rules.developmentBatchCells()).equals(event.cells()))
            throw new IllegalArgumentException("developed source is not a bounded adjacent continuation");
        KnownPedestrianRouteKnowledge.forFrontier(state).requireAccess(event.access(),
                deposit.site().layout().storagePort(), event.cells().stream().map(id -> deposit.site().layout().require(id).workstation())
                        .collect(java.util.stream.Collectors.toSet()));
        return state.withChanges(FrontierWorldStateUpdate.begin().extractionSites(state.extractionSites().replace(
                deposit.develop(event.expectedRevision(), event.cells()))));
    }
}
