package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.extraction.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import java.util.*;

/** Mining-owned reassignment at an empty safe boundary. The common navigator and
 * storage selector prove destinations; no body, resource or geological state is recreated. */
public final class ExtractionWorkRelocation {
    private static final Set<ResourceStorageRole> STORAGE = Set.of(ResourceStorageRole.WAREHOUSE, ResourceStorageRole.LOCAL_BUFFER);
    private ExtractionWorkRelocation() { }

    public static Optional<ExtractionWorkDecision.Relocate> select(FrontierWorldState state, ExtractionWork job) {
        if (job.pending().isPresent() || job.phase() != ExtractionWork.Phase.SELECT_SOURCE && job.phase() != ExtractionWork.Phase.EXTRACT
                || ExtractionWorkAuthority.carried(state, job) != 0) return Optional.empty();
        var home = ExtractionWorkAuthority.site(state, job).settlementId();
        if (!SettlementWorkPolicy.permissions(state, home).permits(ResidentWorkKind.EXTRACTION, job.execution().actorId()))
            return Optional.empty();
        KnownPedestrianNavigation.RouteUnavailable waiting = null;
        for (var site : ExtractionSiteSelection.ranked(state, home, job.execution().actorId(), Set.of(job.siteId()), Optional.of(job.id()))) {
            var deposit = state.extractionSites().deposits().get(site);
            var priorities = ExtractionPriorities.read(state, deposit, Optional.of(job.id()));
            var available = new ArrayList<>(deposit.available(state.extractionSites().reservedCells(site)).stream()
                    .filter(cell -> ExtractionWorkPolicy.supportsTool(state, cell, job))
                    .filter(cell -> priorities.cells().containsKey(cell.id()) && priorities.require(cell.id()).missing() > 0).toList());
            try {
                while (!available.isEmpty()) {
                    var selected = ExtractionWorkTargets.plannedAt(state, job, deposit, available, AreaWorkSelection.SpatialPolicy.SPREAD_STARTS);
                    if (selected.isEmpty()) break;
                    var source = selected.orElseThrow();
                    var cell = deposit.site().layout().require(source.target().key().cell()); available.remove(cell);
                    var output = cell.definition().coldOutput().getFirst();
                    for (var destination : ResourceStorageSelection.receivingCandidates(state, home, output.itemKind(), output.quantity(),
                            cell.workstation(), STORAGE, Optional.empty())) {
                        try {
                            var route = ResourceStorageNavigation.plannedPath(state, cell.workstation(), destination);
                            return Optional.of(new ExtractionWorkDecision.Relocate(source, destination,
                                    route.size() > 1 ? Optional.of(new PedestrianRouteReceipt(route)) : Optional.empty()));
                        } catch (KnownPedestrianNavigation.RouteUnavailable pending) {
                            if (pending.status() != PedestrianRouteResult.Status.NO_PATH) waiting = pending;
                        }
                    }
                }
            } catch (KnownPedestrianNavigation.RouteUnavailable pending) {
                if (pending.status() != PedestrianRouteResult.Status.NO_PATH) waiting = pending;
            }
        }
        if (waiting != null) throw waiting;
        return Optional.empty();
    }

    public static ExtractionWork require(FrontierWorldState state, ExtractionWork job, ExtractionWorkDecision.Relocate selected) {
        var deposit = Objects.requireNonNull(state.extractionSites().deposits().get(selected.source().target().key().owner()), "selected worksite");
        var cell = deposit.site().layout().require(selected.source().target().key().cell());
        var home = ExtractionWorkAuthority.site(state, job).settlementId();
        if (!home.equals(deposit.site().settlementId()) || !selected.execution().equals(job.execution())
                || ExtractionWorkAuthority.carried(state, job) != 0
                || !SettlementWorkPolicy.permissions(state, home).permits(ResidentWorkKind.EXTRACTION, job.execution().actorId())
                || ExtractionPriorities.read(state, deposit, Optional.of(job.id())).require(cell.id()).missing() == 0)
            throw new IllegalArgumentException("mining relocation has held cargo, foreign authority or covered demand");
        var output = cell.definition().coldOutput().getFirst();
        var next = job.relocate(selected.source().target(), output.itemKind(), selected.destination());
        ExtractionWorkTargets.requireSelection(state, next, selected.source());
        if (!ResourceStorageSelection.receivesAt(state, selected.destination(), home, output.itemKind(), output.quantity(), STORAGE, Optional.empty()))
            throw new IllegalArgumentException("mining relocation lacks current receiving capacity");
        ResourceStorageNavigation.requireAcceptedDestination(state, cell.workstation(), selected.destination(), selected.storageRoute());
        return next;
    }
}
