package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Goal arbitration only. Durable commitments remain in StrategicPlanState and their operation owners. */
public final class SettlementManagement {
    private final List<SettlementOperationPlanner> planners;
    private final SettlementManagementPolicy policy;
    public SettlementManagement(List<SettlementOperationPlanner> planners, SettlementManagementPolicy policy) {
        this.planners = List.copyOf(planners); this.policy = Objects.requireNonNull(policy);
        if (planners.isEmpty() || planners.size() > 32 || planners.stream().map(SettlementOperationPlanner::id).distinct().count() != planners.size())
            throw new IllegalArgumentException("settlement planners need a bounded unique registry");
    }
    public record Consideration(String planner, SettlementOperationPlanner.Offer offer, boolean admitted) { }
    public record Hold(String planner, SettlementOperationPlanner.PlanningHold scope) { }
    public record Decision(SubjectId owner, Optional<StrategicOperationProposal> selected,
                           List<Consideration> considerations, List<Hold> holds, List<SubjectId> replacePendingTasks) {
        public Decision {
            considerations = List.copyOf(considerations); holds = List.copyOf(holds);
            replacePendingTasks = List.copyOf(replacePendingTasks);
        }
    }
    public Decision decide(FrontierWorldState state, Settlement settlement) {
        requireAuthority(state, settlement.id());
        List<Consideration> offers = new ArrayList<>(); List<Hold> holds = new ArrayList<>();
        for (SettlementOperationPlanner planner : planners) {
            var result = planner.assess(state, settlement);
            result.hold().ifPresent(reason -> holds.add(new Hold(planner.id(), reason)));
            for (var offer : result.offers()) offers.add(new Consideration(planner.id(), offer,
                    available(state, settlement.id(), offer)));
        }
        offers.sort(java.util.Comparator.comparing(Consideration::offer, policy.ordering()).thenComparing(Consideration::planner));
        Optional<SettlementOperationPlanner.Offer> chosen = offers.stream().filter(Consideration::admitted)
                .map(Consideration::offer).filter(offer -> holds.stream().noneMatch(hold ->
                        hold.scope().lane() == StrategicObjectiveLane.forKind(offer.proposal().kind()))).findFirst();
        return new Decision(settlement.id(), chosen.map(SettlementOperationPlanner.Offer::proposal), offers, holds,
                chosen.map(SettlementOperationPlanner.Offer::replacePendingTasks).orElse(List.of()));
    }
    public List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> expandActiveTasks(
            FrontierWorldState state, Settlement settlement,
            io.farfrontier.palemirror.frontier.v3.api.ScheduleId cause, long atTick) {
        requireAuthority(state, settlement.id());
        // A single selected family expansion is committed against this exact immutable predecessor.
        // The arbiter does not interpret jobs, machine phases or resource representations.
        for (SettlementOperationPlanner planner : planners) {
            var events = planner.expandActiveTasks(state, settlement, cause, atTick);
            if (!events.isEmpty()) return List.copyOf(events);
        }
        return List.of();
    }
    public static boolean available(FrontierWorldState state, SubjectId owner, StrategicOperationProposal proposal) {
        return available(state, owner, new SettlementOperationPlanner.Offer(owner, proposal, SettlementOperationPlanner.Priority.NORMAL));
    }
    private static boolean available(FrontierWorldState state, SubjectId owner, SettlementOperationPlanner.Offer offer) {
        requireAuthority(state, owner);
        if (!offer.ownerId().equals(owner)) throw new IllegalArgumentException("planner offered a foreign owner's operation");
        for (SubjectId id : offer.replacePendingTasks()) {
            StrategicTask task = state.strategicPlans().tasks().get(id);
            if (task == null || !task.ownerId().equals(owner) || task.status() != StrategicTaskStatus.PENDING)
                return false;
        }
        var occupied = state.strategicPlans().objectives().values().stream()
                .filter(value -> value.ownerId().equals(owner) && value.status() == StrategicObjectiveStatus.ACTIVE
                        && value.lane() == StrategicObjectiveLane.forKind(offer.proposal().kind())).toList();
        return occupied.stream().allMatch(objective -> {
            var retained = state.strategicPlans().tasks().values().stream()
                    .filter(task -> task.objectiveId().equals(objective.id())
                            && (task.status() == StrategicTaskStatus.PENDING || task.status() == StrategicTaskStatus.ACTIVE)).toList();
            return !retained.isEmpty() && retained.stream().allMatch(task -> offer.replacePendingTasks().contains(task.id()));
        });
    }
    private static void requireAuthority(FrontierWorldState state, SubjectId owner) {
        DecisionAuthority authority = state.strategicPlans().requireDecisionAuthority(owner);
        if (authority.kind() != DecisionAuthorityKind.SETTLEMENT || !authority.ownerId().equals(owner))
            throw new IllegalArgumentException("settlement management has no exact settlement authority");
        DecisionPolicyRegistry.require(authority);
    }
}
