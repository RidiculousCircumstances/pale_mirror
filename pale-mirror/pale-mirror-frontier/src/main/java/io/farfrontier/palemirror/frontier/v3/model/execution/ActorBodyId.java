package io.farfrontier.palemirror.frontier.v3.model.execution;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/** Physical incarnation. Activity generation, scene and navigation goal are deliberately absent. */
public record ActorBodyId(SubjectId actorId, long physicalEpoch) {
    public ActorBodyId {
        Objects.requireNonNull(actorId, "body actor");
        if (physicalEpoch < 1L) throw new IllegalArgumentException("body physical epoch must be positive");
    }
    public static UUID entityId(WorldId world, SubjectId actor) {
        Objects.requireNonNull(world, "body world"); Objects.requireNonNull(actor, "body actor");
        return UUID.nameUUIDFromBytes(("frontier-v3:actor:" + world.value() + ":" + actor.value())
                .getBytes(StandardCharsets.UTF_8));
    }
    /** Retains the established key spelling, not ownership by the former scene helper. */
    public static SubjectId recoveryBindingId(SubjectId actor) {
        return new SubjectId("recovery:body_" + Objects.requireNonNull(actor, "body actor").value().replace(':', '_'));
    }
}
