package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.extraction.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import java.util.*;

/** Extraction owns mandate/frontier policy; shared activity, navigation and inventory retain their authorities. */
public final class ExtractionWorkPolicy {
    private ExtractionWorkPolicy() { }
    /** An admitted source is an owned work commitment, not spare production
     * capacity. Recomputing marginal demand after reserving it cannot withdraw
     * that commitment. Permission withdrawal and source invalidation still apply;
     * selecting any successor separately requires fresh demand. */
    public static boolean mayFinishTarget(FrontierWorldState state, ExtractionWork job, long atTick) {
        if (!SettlementWorkPolicy.permissions(state, ExtractionWorkAuthority.site(state, job).settlementId())
                .permits(ResidentWorkKind.EXTRACTION, job.execution().actorId())) return false;
        var deposit = state.extractionSites().deposits().get(job.siteId());
        return job.target().filter(target -> {
            var source = deposit.cells().get(target.key().cell());
            return source != null && source.revision() == target.revision()
                    && source.disposition() == ExtractionDeposit.Disposition.PRESENT;
        }).isPresent() || requested(state, job, atTick);
    }
    /** Demand for another source, never permission to cancel an admitted one. */
    public static boolean requested(FrontierWorldState state, ExtractionWork job, long atTick) {
        return SettlementWorkPolicy.permissions(state, ExtractionWorkAuthority.site(state, job).settlementId())
                .permits(ResidentWorkKind.EXTRACTION, job.execution().actorId())
                && ExtractionStockDemand.requested(state, state.extractionSites().deposits().get(job.siteId()), Optional.of(job.id()), atTick);
    }
    public static boolean remaining(ExtractionDeposit deposit) {
        return deposit.cells().values().stream().anyMatch(cell -> cell.disposition() == ExtractionDeposit.Disposition.PRESENT);
    }
    /** Reservation by another miner is temporary unavailability, not depletion or completion. */
    public static Optional<ExtractionWorkDecision.Source> nextTarget(FrontierWorldState state, ExtractionWork job, long atTick) {
        if (!requested(state, job, atTick)) return Optional.empty();
        var deposit = state.extractionSites().deposits().get(job.siteId());
        var required = ExtractionStockDemand.requiredCells(state, deposit, Optional.of(job.id()), atTick);
        var available = deposit.available(state.extractionSites().reservedCells(job.siteId())).stream()
                .filter(cell -> required.contains(cell.id()))
                .filter(cell -> supportsTool(state, cell, job))
                .filter(cell -> ExtractionWorkAuthority.carried(state, job) == 0 || matchingOutput(cell, job))
                .toList();
        return ExtractionWorkTargets.plannedSelection(state, job, available, AreaWorkSelection.SpatialPolicy.LOCAL_CONTINUATION, atTick);
    }
    public static boolean finished(FrontierWorldState state, ExtractionWork job, long atTick) {
        var deposit = state.extractionSites().deposits().get(job.siteId());
        return !requested(state, job, atTick) || !remaining(deposit);
    }
    public static boolean supportsTool(FrontierWorldState state, ExtractionLayout.Cell cell, ExtractionWork job) {
        return cell.definition().coldOutput().size() == 1
                && cell.definition().toolKind().equals(Objects.requireNonNull(
                        state.inventory().items().get(job.toolId()), "retained mining tool").itemKind());
    }
    public static boolean matchingOutput(ExtractionLayout.Cell cell, ExtractionWork job) {
        return cell.definition().coldOutput().size() == 1 && cell.definition().coldOutput().getFirst().itemKind().equals(job.outputKind());
    }
}
