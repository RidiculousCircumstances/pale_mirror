package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;

/** Location declaration, not a second physical pose or inventory authority. */
public sealed interface ContainerLocation permits ContainerLocation.Fixed, ContainerLocation.Mobile {
    record Fixed(BlockPosition position) implements ContainerLocation {
        public Fixed { Objects.requireNonNull(position, "fixed container position"); }
    }
    record Mobile(SubjectId actorId) implements ContainerLocation {
        public Mobile { Objects.requireNonNull(actorId, "mobile container actor"); }
    }
}
