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
                .anyMatch(deposit -> ExtractionWorkPolicy.remaining(deposit) || ExtractionAreaPlanning.hasKnownExtension(state, deposit.site().id()));
        return new SettlementStaffingPort.Demand(ResidentWorkKind.EXTRACTION, HumanCapability.EXTRACTION,
                work ? rules.targetWorkers() : 0, work ? rules.minimumLocalStaff() : 0, rules.priority(),
                state.extractionSites().work().values().stream().filter(job -> !job.terminal()
                    && state.extractionSites().deposits().get(job.siteId()).site().settlementId().equals(home))
                    .map(job -> job.execution().actorId()).collect(java.util.stream.Collectors.toSet()));
    }
    static boolean availableWork(FrontierWorldState state, ResidentProfile resident, long atTick) {
        return state.extractionSites().deposits().values().stream()
                .filter(deposit -> deposit.site().settlementId().equals(resident.settlementId()))
                .anyMatch(deposit -> !deposit.available(state.extractionSites().reservedCells(deposit.site().id())).isEmpty()
                        && state.inventory().items().values().stream().anyMatch(item ->
                            item.economicOwnerId().equals(resident.settlementId())
                            && item.itemKind().equals(state.bootstrap().ruleset().extraction().source().toolKind())
                            && state.extractionSites().work().values().stream().noneMatch(job -> !job.terminal() && job.toolId().equals(item.id()))
                            && item.custody() instanceof InventoryCustody.ContainerSlot slot
                            && slot.containerId().equals(deposit.site().containerId()))
                        && state.bootstrap().ruleset().extraction().source().coldOutput().stream().allMatch(output ->
                            state.canReceiveFungible(deposit.site().containerId(), output.itemKind(), output.quantity())));
    }
}
