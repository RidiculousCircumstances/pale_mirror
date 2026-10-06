package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;

/** Ephemeral owner-issued access claim; no copied job, resource ledger or movement authority. */
public record ServiceAccessDemand(Identity identity, Priority priority, Presence presence, long requestedAtTick,
                                  boolean physicallyAdmitted) {
    public enum Kind { MEAL, PRODUCTION, FIELD_HARVEST, COURIER }
    public enum Priority { SELF_CARE, WORK }
    public enum Presence { OCCUPIED, ENTERING, APPROACH }
    public record Identity(Kind kind, SubjectId ownerId, SubjectId actorId, SubjectId pointId) {
        public Identity {
            Objects.requireNonNull(kind, "declared service family");
            Objects.requireNonNull(ownerId, "declared service owner");
            Objects.requireNonNull(actorId, "declared service actor");
            Objects.requireNonNull(pointId, "declared service point");
        }
    }
    public ServiceAccessDemand {
        Objects.requireNonNull(identity, "service identity");
        Objects.requireNonNull(priority, "declared service priority");
        Objects.requireNonNull(presence, "service presence");
        if (requestedAtTick < 0) throw new IllegalArgumentException("negative service request instant");
    }
}
