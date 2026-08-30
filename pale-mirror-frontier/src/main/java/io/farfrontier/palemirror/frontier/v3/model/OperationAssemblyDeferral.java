package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;

/** Exact loaded-world reason one HOT participant cannot advance its next assembly cursor. */
public record OperationAssemblyDeferral(SubjectId actorId, BlockPosition target, BlockPosition obstructionFloor, Reason reason) {
    public OperationAssemblyDeferral {
        actorId = Objects.requireNonNull(actorId, "deferred assembly actor");
        target = Objects.requireNonNull(target, "deferred assembly target");
        obstructionFloor = Objects.requireNonNull(obstructionFloor, "deferred assembly obstruction floor");
        reason = Objects.requireNonNull(reason, "deferred assembly reason");
    }

    public enum Reason { LOADED_WORLD_OBSTRUCTION }
}
