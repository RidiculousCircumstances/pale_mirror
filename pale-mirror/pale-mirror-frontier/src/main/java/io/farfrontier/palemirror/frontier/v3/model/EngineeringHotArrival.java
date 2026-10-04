package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind;
import java.util.Objects;

/** Authority captured by the physical actuator, not reconstructed from a later current owner. */
public record EngineeringHotArrival(ActorActuationId actuation, long leaseRevision) {
    public EngineeringHotArrival {
        Objects.requireNonNull(actuation, "engineering arrival actuation");
        if (leaseRevision < 1 || actuation.execution().activityKind() != ActorActivityKind.ENGINEERING_ASSEMBLY)
            throw new IllegalArgumentException("engineering arrival lacks a captured assembly scope");
    }
}
