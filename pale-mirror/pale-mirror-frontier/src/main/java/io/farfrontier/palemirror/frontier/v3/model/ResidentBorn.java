package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.ActorBirthIdentity;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Durable admission of one new exact resident; physical materialization is a later HOT lease. */
public record ResidentBorn(SubjectId jobId, ResidentProfile resident, BlockPosition position, ActorBirthIdentity birth) implements FrontierPayload {
    public ResidentBorn {
        Objects.requireNonNull(jobId, "resident birth job");
        Objects.requireNonNull(resident, "resident"); Objects.requireNonNull(position, "birth position");
        Objects.requireNonNull(birth, "birth identity");
        if (!jobId.value().startsWith("job:resident-birth-")
                || birth.kind() != ActorBirthIdentity.Kind.RESIDENT || !birth.actorId().equals(resident.id()))
            throw new IllegalArgumentException("resident birth lacks its exact job and resident identity");
    }
    @Override public java.util.Optional<ActorBirthIdentity> actorBirth() { return java.util.Optional.of(birth); }
    @Override public boolean requiresDurableBeforeEffect() { return true; }
    @Override public String type() { return "frontier.resident_born"; }
}
