package io.farfrontier.palemirror.frontier.v3.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

/** Domain port: proposals and explicit temporary holds; no execution or physical effects. */
public interface SettlementOperationPlanner {
    String id();
    Assessment assess(FrontierWorldState state, Settlement settlement);

    enum Priority { CRITICAL, IMPORTANT, NORMAL, BACKGROUND }
    enum Reason { ROUTE_RECOVERY_ALREADY_OWNED, ROUTE_OBSERVATION_ALREADY_OWNED }
    record Offer(SubjectId ownerId, StrategicOperationProposal proposal, Priority priority, List<SubjectId> replacePendingTasks) {
        public Offer(SubjectId ownerId, StrategicOperationProposal proposal, Priority priority) {
            this(ownerId, proposal, priority, List.of());
        }
        public Offer {
            Objects.requireNonNull(ownerId); Objects.requireNonNull(proposal); Objects.requireNonNull(priority);
            replacePendingTasks = List.copyOf(replacePendingTasks);
            if (replacePendingTasks.size() > 128 || replacePendingTasks.stream().distinct().count() != replacePendingTasks.size())
                throw new IllegalArgumentException("replacement requests must name bounded unique tasks");
        }
        public List<StrategicTaskRequirement> requirements() { return StrategicOperationSpecifications.requirements(proposal.kind()); }
    }
    record PlanningHold(Reason reason, StrategicObjectiveLane lane) {
        public PlanningHold { Objects.requireNonNull(reason); Objects.requireNonNull(lane); }
    }
    record Assessment(List<Offer> offers, Optional<PlanningHold> hold) {
        public Assessment {
            offers = List.copyOf(offers); Objects.requireNonNull(hold);
            if (offers.size() > 64 || hold.isPresent() && !offers.isEmpty())
                throw new IllegalArgumentException("bounded planner assessment cannot offer while held");
        }
        public static Assessment offer(SubjectId ownerId, StrategicOperationProposal proposal, Priority priority) {
            return new Assessment(List.of(new Offer(ownerId, proposal, priority)), Optional.empty());
        }
        public static Assessment empty() { return new Assessment(List.of(), Optional.empty()); }
        public static Assessment held(Reason reason) {
            return new Assessment(List.of(), Optional.of(new PlanningHold(reason, StrategicObjectiveLane.STRATEGIC)));
        }
    }
}
