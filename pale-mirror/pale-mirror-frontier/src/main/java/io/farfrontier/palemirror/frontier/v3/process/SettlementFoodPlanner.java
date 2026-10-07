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
        var active = state.strategicPlans().objectives().values().stream()
                .filter(value -> value.ownerId().equals(settlement.id())
                        && value.kind() == StrategicObjectiveKind.SETTLEMENT_PRODUCE_BREAD
                        && value.status() == StrategicObjectiveStatus.ACTIVE)
                .reduce((left, right) -> { throw new IllegalArgumentException("food objective has competing owners"); });
        if (active.isEmpty()) return java.util.List.of();
        var objective = active.orElseThrow();
        var authority = state.strategicPlans().requireDecisionAuthority(settlement.id());
        if (!objective.authorityId().equals(authority.ownerId())
                || objective.authorityEpoch() != authority.reconsiderationEpoch()) return java.util.List.of();
        if (state.strategicPlans().tasks().values().stream().anyMatch(task ->
                task.objectiveId().equals(objective.id()) && task.kind() == StrategicTaskKind.PRODUCE_BREAD
                        && task.status() == StrategicTaskStatus.PENDING)) return java.util.List.of();
        SubjectId id = new SubjectId("task:food-expansion-" + WorkOpportunityIdentity.digest(
                objective.id().value() + "|" + cause.value()));
        if (state.strategicPlans().tasks().containsKey(id)) return java.util.List.of();
        var task = new StrategicTask(id,
                objective.id(),
                settlement.id(),
                StrategicTaskKind.PRODUCE_BREAD,
                Optional.empty(),
                Optional.empty(),
                StrategicOperationSpecifications.requirements(StrategicObjectiveKind.SETTLEMENT_PRODUCE_BREAD),
                java.util.List.of(),
                StrategicTaskStatus.PENDING,
                objective.authorityId(),
                objective.authorityEpoch());
        var projected = state.withStrategicPlans(state.strategicPlans().addTask(task));
        var admission = ProductionProcess.planStart(projected, ProductionProcess.start(task, atTick));
        if (admission.stream().noneMatch(event -> event.payload() instanceof ProductionStarted))
            return java.util.List.of();
        var events = new java.util.ArrayList<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent>();
        events.add(new io.farfrontier.palemirror.frontier.v3.api.ProposedEvent(settlement.id(), new StrategicTaskPlanned(task)));
        events.addAll(admission);
        return java.util.List.copyOf(events);
    }
    @Override public Assessment assess(FrontierWorldState state, Settlement settlement) {
        boolean workshop = settlement.structures().stream().anyMatch(structure -> structure.kind() == StructureKind.WORKSHOP
                && state.structureConditions().get(structure.id()) == StructureCondition.INTACT);
        boolean reserveShort = SettlementFoodPolicy.reserveCoverageBread(state, settlement.id()) < SettlementFoodPolicy.reserveRequirement(state, settlement.id());
        if (!workshop || BakeryBatchSelection.admissible(state, settlement.id()).isEmpty()) return Assessment.empty();
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
