package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.CommandRejection;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentLifecycleOwner;
import io.farfrontier.palemirror.frontier.v3.api.RejectionCode;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalIntentTransition;

import java.util.Objects;

/**
 * Executable owner-local disposition for a terminal confirmation, recovery-unknown or conflict.
 * ARC-001C extends these callbacks with the family's atomic obligation retirement; this boundary
 * deliberately carries behavior now instead of a descriptive lifecycle declaration.
 */
interface PhysicalIntentLifecycleRetirementPolicy {
    @FunctionalInterface interface Planner {
        CommandPlan apply(FrontierWorldState state, FrontierCommand command, PhysicalIntent intent,
                          PhysicalIntentTransition transition);
    }

    @FunctionalInterface interface Reducer {
        FrontierWorldState apply(FrontierWorldState state, SubjectId subject, PhysicalIntent intent,
                                 PhysicalIntentTransition transition);
    }

    CommandPlan plan(FrontierWorldState state, FrontierCommand command, PhysicalIntent intent,
                     PhysicalIntentTransition transition);

    FrontierWorldState reduce(FrontierWorldState state, SubjectId subject, PhysicalIntent intent,
                              PhysicalIntentTransition transition);

    static PhysicalIntentLifecycleRetirementPolicy of(Planner planner, Reducer reducer) {
        Objects.requireNonNull(planner, "physical retirement planner");
        Objects.requireNonNull(reducer, "physical retirement reducer");
        return new PhysicalIntentLifecycleRetirementPolicy() {
            @Override public CommandPlan plan(FrontierWorldState state, FrontierCommand command, PhysicalIntent intent,
                                              PhysicalIntentTransition transition) {
                return planner.apply(state, command, intent, transition);
            }

            @Override public FrontierWorldState reduce(FrontierWorldState state, SubjectId subject, PhysicalIntent intent,
                                                       PhysicalIntentTransition transition) {
                return reducer.apply(state, subject, intent, transition);
            }
        };
    }

    static PhysicalIntentLifecycleRetirementPolicy noPhysical(PhysicalIntentLifecycleOwner owner) {
        Objects.requireNonNull(owner, "physical retirement owner");
        return of((state, command, intent, transition) -> new CommandPlan.Rejected(new CommandRejection(
                        RejectionCode.REJECTED_BY_POLICY,
                        "physical lifecycle owner admits no retirement: " + owner.stableId())),
                (state, subject, intent, transition) -> {
                    throw new IllegalArgumentException("physical lifecycle owner admits no retirement: " + owner.stableId());
                });
    }
}
