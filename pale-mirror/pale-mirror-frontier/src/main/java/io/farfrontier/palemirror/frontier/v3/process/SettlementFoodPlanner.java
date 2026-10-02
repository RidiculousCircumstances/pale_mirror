package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import java.util.Optional;

/** Profile owner proposes operations; it never commits goals or takes a resident body. */
final class SettlementFoodPlanner implements SettlementOperationPlanner {
    @Override public String id() { return "frontier:food"; }
    @Override public Assessment assess(FrontierWorldState state, Settlement settlement) {
        boolean workshop = settlement.structures().stream().anyMatch(structure -> structure.kind() == StructureKind.WORKSHOP
                && state.structureConditions().get(structure.id()) == StructureCondition.INTACT);
        SubjectId depot = FrontierWorldState.depotId(settlement.id());
        boolean reserveShort = SettlementFoodPolicy.reserveCoverageBread(state, settlement.id()) < SettlementFoodPolicy.reserveRequirement(state, settlement.id());
        boolean wheat = state.inventory().items().values().stream().anyMatch(item -> item.itemKind().equals("minecraft:wheat")
                && item.custody() instanceof InventoryCustody.ContainerSlot slot && slot.containerId().equals(depot)
                && !ResourceSiteHarvestLineage.hasPendingOutputReceipt(state.resourceSites().sites().values(), item.id()))
                || FungibleResourceCustodySupport.selectAtContainer(state, depot, settlement.id(), "minecraft:wheat", 64).isPresent();
        boolean breadCapacity = ProductionOutputCapacity.canAdmitBreadBatch(state, settlement.id());
        if (!workshop || !wheat || !breadCapacity) return Assessment.empty();
        var proposal = new StrategicOperationProposal(StrategicObjectiveKind.SETTLEMENT_PRODUCE_BREAD,
                Optional.empty(), reserveShort ? Long.MAX_VALUE - 1L : FixedScalar.SCALE);
        var replaced = state.strategicPlans().tasks().values().stream()
                .filter(task -> reserveShort && task.ownerId().equals(settlement.id())
                        && task.kind() == StrategicTaskKind.DECONTAMINATE_INFECTION_CELL
                        && task.status() == StrategicTaskStatus.PENDING)
                .map(StrategicTask::id).sorted().toList();
        return new Assessment(java.util.List.of(new Offer(settlement.id(), proposal,
                reserveShort ? Priority.CRITICAL : Priority.NORMAL, replaced)), Optional.empty());
    }
}
