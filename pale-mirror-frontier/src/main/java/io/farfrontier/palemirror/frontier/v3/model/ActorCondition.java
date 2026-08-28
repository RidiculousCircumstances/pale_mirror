package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;

import java.util.Objects;

/** Exact durable vitality; Minecraft health is observed at the HOT boundary, never inferred from absence. */
public record ActorCondition(ActorLifeStatus status, FixedScalar health) {
    public static final ActorCondition HEALTHY = new ActorCondition(ActorLifeStatus.ALIVE, FixedScalar.whole(20));

    public ActorCondition {
        Objects.requireNonNull(status, "actor life status");
        Objects.requireNonNull(health, "actor health");
        if (status == ActorLifeStatus.ALIVE && health.compareTo(FixedScalar.ZERO) <= 0) {
            throw new IllegalArgumentException("living actor must have positive health");
        }
        if (status == ActorLifeStatus.DEAD && !health.equals(FixedScalar.ZERO)) {
            throw new IllegalArgumentException("dead actor must have zero health");
        }
    }

    public static ActorCondition dead() { return new ActorCondition(ActorLifeStatus.DEAD, FixedScalar.ZERO); }
}
