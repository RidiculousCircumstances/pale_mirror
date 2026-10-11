package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.extraction.*;
import java.util.*;

/** Extraction-owned opportunity/staffing policy; common labour selection never interprets deposits. */
final class ExtractionWorkPlanning {
    private ExtractionWorkPlanning() { }
    static SettlementStaffingPort.Demand staffingDemand(FrontierWorldState state, SubjectId home, SettlementLabourRules.Entry rules) {
        boolean work = state.extractionSites().deposits().values().stream()
                .filter(deposit -> deposit.site().settlementId().equals(home))
                .anyMatch(deposit -> demanded(state, home) && (ExtractionWorkPolicy.remaining(deposit)
                        || ExtractionAreaPlanning.hasKnownExtension(state, deposit.site().id())));
        return new SettlementStaffingPort.Demand(ResidentWorkKind.EXTRACTION, HumanCapability.EXTRACTION,
                work ? rules.targetWorkers() : 0, work ? rules.minimumLocalStaff() : 0, rules.priority(),
                state.extractionSites().work().values().stream().filter(job -> !job.terminal()
                    && state.extractionSites().deposits().get(job.siteId()).site().settlementId().equals(home))
                    .map(job -> job.execution().actorId()).collect(java.util.stream.Collectors.toSet()));
    }
    static boolean availableWork(FrontierWorldState state, ResidentProfile resident, long atTick) {
        if (!demanded(state, resident.settlementId())) return false;
        return state.extractionSites().deposits().values().stream()
                .filter(deposit -> deposit.site().settlementId().equals(resident.settlementId()))
                .anyMatch(deposit -> deposit.available(state.extractionSites().reservedCells(deposit.site().id())).stream().anyMatch(cell ->
                        cell.definition().coldOutput().stream().allMatch(output ->
                                ResourceStorageSelection.destination(state, resident.settlementId(), output.itemKind(), output.quantity(),
                                        state.actorLocations().get(resident.id()).supportingSurface(),
                                        Set.of(ResourceStorageRole.WAREHOUSE, ResourceStorageRole.LOCAL_BUFFER), Optional.empty(),
                                        endpoint -> ResourceStorageNavigation.reachable(state, cell.workstation(), endpoint)).isPresent()))
                        && ExtractionToolSelection.select(state, resident).isPresent()
                        );
    }
    static boolean demanded(FrontierWorldState state, SubjectId home) {
        return state.bootstrap().ruleset().extraction().enabled() && state.bootstrap().ruleset().extraction().source().coldOutput().stream()
                .anyMatch(output -> ProductionStockDemand.read(state, home, output.itemKind()).requested());
    }
}
