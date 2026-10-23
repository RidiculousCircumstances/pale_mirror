package io.farfrontier.palemirror.frontier.v3.process;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.api.*;
import java.util.*;
/** Adds another worker only when the same objective has an immediately admissible station order. */
final class StationTaskExpansion {
    static List<ProposedEvent> expand(FrontierWorldState state, Settlement settlement, ScheduleId cause,
                                     long atTick, StrategicObjectiveKind kind) {
        var active = state.strategicPlans().objectives().values().stream()
                .filter(value -> value.ownerId().equals(settlement.id())
                        && value.kind() == kind
                        && value.status() == StrategicObjectiveStatus.ACTIVE)
                .reduce((left, right) -> { throw new IllegalArgumentException("production objective has competing owners"); });
        if (active.isEmpty()) return java.util.List.of();
        var objective = active.orElseThrow();
        var authority = state.strategicPlans().requireDecisionAuthority(settlement.id());
        if (!objective.authorityId().equals(authority.ownerId())
                || objective.authorityEpoch() != authority.reconsiderationEpoch()) return java.util.List.of();
        if (state.strategicPlans().tasks().values().stream().anyMatch(task ->
                task.objectiveId().equals(objective.id()) && task.kind() == StrategicTaskKind.PRODUCE_AT_STATION
                        && task.status() == StrategicTaskStatus.PENDING)) return java.util.List.of();
        SubjectId id = new SubjectId("task:production-expansion-" + WorkOpportunityIdentity.digest(
                objective.id().value() + "|" + cause.value()));
        if (state.strategicPlans().tasks().containsKey(id)) return java.util.List.of();
        var task = new StrategicTask(id,
                objective.id(),
                settlement.id(),
                StrategicTaskKind.PRODUCE_AT_STATION,
                Optional.empty(),
                Optional.empty(),
                StrategicOperationSpecifications.requirements(kind),
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
    private StationTaskExpansion() { }
}
