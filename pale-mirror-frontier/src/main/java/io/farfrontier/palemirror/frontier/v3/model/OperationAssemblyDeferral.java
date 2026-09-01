package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;

/** Exact loaded-world reason one HOT participant cannot advance its next assembly cursor. */
public record OperationAssemblyDeferral(SubjectId actorId, SurfaceAnchor target, SurfaceAnchor obstructionSurface, Reason reason) {
    public OperationAssemblyDeferral {
        actorId = Objects.requireNonNull(actorId, "deferred assembly actor");
        target = Objects.requireNonNull(target, "deferred assembly target");
        obstructionSurface = Objects.requireNonNull(obstructionSurface, "deferred assembly obstruction surface");
        reason = Objects.requireNonNull(reason, "deferred assembly reason");
    }

    public enum Reason {
        LOADED_WORLD_OBSTRUCTION;

        public int wireTag() { return FrontierWireTags.tag(this); }
    }
}
