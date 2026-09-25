package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.ActorBirthIdentity;

import java.util.Objects;

/** Durable fact that the one named hive growth job completed without generating substitute IDs. */
public record HiveGrowthCompleted(SubjectId jobId, ActorBirthIdentity birth) implements FrontierPayload {
    public HiveGrowthCompleted {
        Objects.requireNonNull(jobId, "hive growth job id"); Objects.requireNonNull(birth, "birth identity");
        if (birth.kind() != ActorBirthIdentity.Kind.BIOFORM) throw new IllegalArgumentException("hive birth must declare a bioform");
    }
    @Override public java.util.Optional<ActorBirthIdentity> actorBirth() { return java.util.Optional.of(birth); }
    @Override public boolean requiresDurableBeforeEffect() { return true; }
    @Override public String type() { return "frontier.hive_growth_completed"; }
}
