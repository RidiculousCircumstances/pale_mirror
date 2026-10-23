package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import java.util.Optional;

/** Profile owner proposes operations; it never commits goals or takes a resident body. */
final class SettlementFoodPlanner implements SettlementOperationPlanner {
    @Override public String id() { return "frontier:food"; }
    @Override public java.util.List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> expandActiveTasks(
            FrontierWorldState state, Settlement settlement,
            io.farfrontier.palemirror.frontier.v3.api.ScheduleId cause, long atTick) {
        return StationTaskExpansion.expand(state, settlement, cause, atTick, StrategicObjectiveKind.SETTLEMENT_PRODUCE_BREAD);
    }
    @Override public Assessment assess(FrontierWorldState state, Settlement settlement, long atTick) {
        boolean workshop = settlement.structures().stream().anyMatch(structure -> structure.kind() == StructureKind.WORKSHOP
                && state.structureConditions().get(structure.id()) == StructureCondition.INTACT);
        boolean reserveShort = SettlementFoodPolicy.reserveCoverageBread(state, settlement.id()) < SettlementFoodPolicy.reserveRequirement(state, settlement.id(), atTick);
        if (!workshop || BakeryBatchSelection.admissibleStock(state, ProductionRights.publicService(settlement.id()), atTick).isEmpty()) return Assessment.empty();
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
