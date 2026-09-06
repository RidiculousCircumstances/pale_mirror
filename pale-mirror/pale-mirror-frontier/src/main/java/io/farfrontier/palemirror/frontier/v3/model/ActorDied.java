package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Immutable evidence that an exact HOT actor died through a real Minecraft death event. */
public record ActorDied(SceneLeaseId leaseId, SubjectId actorId, BodyPosition body, String cause) implements FrontierPayload {
    public ActorDied {
        Objects.requireNonNull(leaseId, "scene lease id");
        Objects.requireNonNull(actorId, "actor id");
        Objects.requireNonNull(body, "death body");
        Objects.requireNonNull(cause, "death cause");
        if (cause.isBlank() || cause.length() > 160) throw new IllegalArgumentException("death cause must be a bounded non-blank observation");
    }
    @Override public String type() { return "frontier.actor_died"; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }
}
