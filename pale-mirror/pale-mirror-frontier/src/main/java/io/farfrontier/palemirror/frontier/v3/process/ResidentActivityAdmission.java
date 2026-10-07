package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;
import java.util.Set;

/** Derived admission, not a second activity or durable task. Invalid authority remains an error. */
public sealed interface ResidentActivityAdmission {
    record Ready() implements ResidentActivityAdmission { }

    record Waiting(Reason reason, Set<SubjectId> dependencies) implements ResidentActivityAdmission {
        public Waiting {
            Objects.requireNonNull(reason, "activity wait reason");
            dependencies = Set.copyOf(dependencies);
            if (dependencies.isEmpty()) throw new IllegalArgumentException("activity wait needs an owner wake");
        }
    }

    enum Reason {
        RETAINED_MEAL,
        MOVEMENT_HANDOFF,
        RESIDENT_STATE,
        CONTAINER_CUSTODY,
        SERVICE_ACCESS,
        FOOD_STOCK,
        MISSION_SUPPLY,
        INVENTORY_CAPACITY,
        WORK_CHECKPOINT,
        MEAL_CLEARANCE
    }
}
