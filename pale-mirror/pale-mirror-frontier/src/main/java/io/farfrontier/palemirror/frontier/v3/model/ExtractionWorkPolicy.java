package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.extraction.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import java.util.*;

/** Extraction owns mandate/frontier policy; shared activity, navigation and inventory retain their authorities. */
public final class ExtractionWorkPolicy {
    private ExtractionWorkPolicy() { }
    public static boolean requested(FrontierWorldState state, ExtractionWork job) {
        return SettlementWorkPolicy.permissions(state, ExtractionWorkAuthority.site(state, job).settlementId())
                .permits(ResidentWorkKind.EXTRACTION, job.execution().actorId())
                && state.bootstrap().ruleset().extraction().source().coldOutput().stream().anyMatch(output ->
                    ProductionStockDemand.read(state, ExtractionWorkAuthority.site(state, job).settlementId(),
                            output.itemKind(), Optional.of(job.id())).requested());
    }
    public static boolean remaining(ExtractionDeposit deposit) {
        return deposit.cells().values().stream().anyMatch(cell -> cell.disposition() == ExtractionDeposit.Disposition.PRESENT);
    }
    /** Reservation by another miner is temporary unavailability, not depletion or completion. */
    public static Optional<ExtractionWorkDecision.Source> nextTarget(FrontierWorldState state, ExtractionWork job) {
        if (!requested(state, job)) return Optional.empty();
        var deposit = state.extractionSites().deposits().get(job.siteId());
        var available = deposit.available(state.extractionSites().reservedCells(job.siteId())).stream()
                .filter(cell -> cell.definition().coldOutput().size() == 1
                        && cell.definition().coldOutput().getFirst().itemKind().equals(job.outputKind()))
                .toList();
        return ExtractionWorkTargets.plannedSelection(state, job, available, AreaWorkSelection.SpatialPolicy.LOCAL_CONTINUATION);
    }
    public static boolean finished(FrontierWorldState state, ExtractionWork job) {
        var deposit = state.extractionSites().deposits().get(job.siteId());
        if (!requested(state, job) || !remaining(deposit)) return true;
        boolean matchingRemaining = deposit.site().layout().cells().stream().anyMatch(cell ->
                deposit.cells().get(cell.id()).disposition() == ExtractionDeposit.Disposition.PRESENT && matchingOutput(cell, job));
        if (!matchingRemaining) return true;
        var available = deposit.available(state.extractionSites().reservedCells(job.siteId()));
        // Another output's preparation must get an executor; reserved matching work still waits normally.
        return available.stream().noneMatch(cell -> matchingOutput(cell, job))
                && available.stream().anyMatch(cell -> !matchingOutput(cell, job));
    }
    public static boolean matchingOutput(ExtractionLayout.Cell cell, ExtractionWork job) {
        return cell.definition().coldOutput().size() == 1 && cell.definition().coldOutput().getFirst().itemKind().equals(job.outputKind());
    }
}
