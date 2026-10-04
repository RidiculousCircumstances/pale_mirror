package io.farfrontier.palemirror.frontier.v3.model.execution;

import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldStateUpdate;
import java.util.Objects;
import java.util.Optional;

/** An exact activity owner supplies its domain consequences, independently of its scene.
 * The lifecycle, not the strategy, commits common authority retirement. */
@FunctionalInterface
public interface ActorActivityDeath {
    enum Disposition { RETAIN_CAUSAL_OWNER, RETIRE_EXACT_EXECUTION, RETIRE_DECLARED_GROUP }
    record Acknowledgement(FrontierWorldState expectedState, ActorExecutionId execution,
                           FrontierWorldStateUpdate changes, Disposition disposition,
                           Optional<ActorExecutionGroup> retiringGroup) {
        public Acknowledgement(FrontierWorldState expectedState, ActorExecutionId execution,
                               FrontierWorldStateUpdate changes, Disposition disposition) {
            this(expectedState, execution, changes, disposition, Optional.empty());
        }
        public Acknowledgement {
            Objects.requireNonNull(expectedState); Objects.requireNonNull(execution);
            Objects.requireNonNull(changes); Objects.requireNonNull(disposition);
            retiringGroup = Objects.requireNonNull(retiringGroup);
            if ((disposition == Disposition.RETIRE_DECLARED_GROUP) != retiringGroup.isPresent())
                throw new IllegalArgumentException("group retirement requires its explicit exact cohort");
            retiringGroup.ifPresent(group -> {
                if (!group.members().contains(execution) || group.members().stream().anyMatch(member ->
                        member.activityKind() != execution.activityKind()
                                || !member.activityOwnerId().equals(execution.activityOwnerId())))
                    throw new IllegalArgumentException("death retirement group has a foreign owner or purpose");
            });
        }
    }
    Acknowledgement acknowledge(FrontierWorldState state, ActorExecutionId execution, long atTick);
}
