package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.extraction.*;
import java.util.*;

/** Extraction-owned opportunity/staffing policy; common labour selection never interprets deposits. */
final class ExtractionWorkPlanning {
    private ExtractionWorkPlanning() { }
    static SettlementStaffingPort.Demand staffingDemand(FrontierWorldState state, SubjectId home, SettlementLabourRules.Entry rules, long atTick) {
        boolean work = state.extractionSites().deposits().values().stream()
                .filter(deposit -> deposit.site().settlementId().equals(home))
                .anyMatch(deposit -> demanded(state, home, atTick) && (ExtractionWorkPolicy.remaining(deposit)
                        || ExtractionAreaPlanning.extensionAvailability(state, deposit.site().id(), atTick) != ExtractionAreaPlanning.Availability.NONE));
        return new SettlementStaffingPort.Demand(ResidentWorkKind.EXTRACTION, HumanCapability.EXTRACTION,
                work ? rules.targetWorkers() : 0, work ? rules.minimumLocalStaff() : 0, rules.priority(),
                state.extractionSites().work().values().stream().filter(job -> !job.terminal()
                    && state.extractionSites().deposits().get(job.siteId()).site().settlementId().equals(home))
                    .map(job -> job.execution().actorId()).collect(java.util.stream.Collectors.toSet()));
    }
    static boolean availableWork(FrontierWorldState state, ResidentProfile resident, long atTick) {
        if (!demanded(state, resident.settlementId(), atTick)) return false;
        try {
            return new SettlementExtractionAdmission(Set.of()).discover(state, resident, atTick).isPresent();
        } catch (io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation.RouteUnavailable pending) {
            // A read-only availability port grants nothing. An observed exact route wait
            // wakes its caller; pending/unknown calculation is never absence/depletion.
            return false;
        }
    }
    static boolean demanded(FrontierWorldState state, SubjectId home, long atTick) {
        return ExtractionStockDemand.requested(state, home, atTick);
    }
}
