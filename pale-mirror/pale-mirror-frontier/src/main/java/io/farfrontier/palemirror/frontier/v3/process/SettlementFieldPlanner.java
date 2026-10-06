package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import java.util.Comparator;
import java.util.Optional;

/** Profile owner proposes operations; it never commits goals or takes a resident body. */
final class SettlementFieldPlanner implements SettlementOperationPlanner {
    @Override public String id() { return "frontier:field"; }
    @Override public java.util.List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> expandActiveTasks(
            FrontierWorldState state, Settlement settlement,
            io.farfrontier.palemirror.frontier.v3.api.ScheduleId cause, long atTick) {
        for (var task : state.strategicPlans().tasks().values().stream()
                .filter(value -> value.ownerId().equals(settlement.id())
                        && value.kind() == StrategicTaskKind.HARVEST_RESOURCE_SITE
                        && value.status() == StrategicTaskStatus.ACTIVE)
                .sorted(Comparator.comparing(StrategicTask::id)).toList()) {
            var authority = state.strategicPlans().requireDecisionAuthority(settlement.id());
            if (!task.authorityId().equals(authority.ownerId())
                    || task.authorityEpoch() != authority.reconsiderationEpoch()) continue;
            var events = ResourceSiteHarvestPlanning.expandActiveTask(state, task, atTick);
            if (!events.isEmpty()) return events;
        }
        return java.util.List.of();
    }
    @Override public Assessment assess(FrontierWorldState state, Settlement settlement) {
        SubjectId depot = FrontierWorldState.depotId(settlement.id());
        Optional<ResourceSite> recoverableField = state.resourceSiteDescriptors().values().stream()
                .filter(site -> site.settlementId().equals(settlement.id()))
                .filter(site -> state.resourceSites().site(site.id()).phase() == ResourceSitePhase.READY)
                .filter(site -> state.structureConditions().get(site.facilityId()) == StructureCondition.INTACT)
                .min(Comparator.comparing(ResourceSite::id));
        if (recoverableField.isPresent() && state.firstFreeContainerSlot(depot).isPresent()
                && FrontierWorldStateSupport.availableWorkResident(state, settlement.id(),
                        ResidentWorkKind.AGRICULTURE, HumanCapability.AGRICULTURE).isPresent())
            return Assessment.offer(settlement.id(), new StrategicOperationProposal(
                StrategicObjectiveKind.SETTLEMENT_HARVEST_RESOURCE_SITE, Optional.empty(),
                Optional.of(recoverableField.orElseThrow().id()), FixedScalar.SCALE), Priority.BACKGROUND);
        return Assessment.empty();
    }
}
