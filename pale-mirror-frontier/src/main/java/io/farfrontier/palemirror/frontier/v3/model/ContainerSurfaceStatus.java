package io.farfrontier.palemirror.frontier.v3.model;

/** Durable physical lifecycle for one exact container, independent of its slot contents. */
public enum ContainerSurfaceStatus {
    UNMATERIALIZED, PREPARED, ACTIVE, CONFLICT;

    boolean mayTransitionTo(ContainerSurfaceStatus next) {
        return switch (this) {
            case UNMATERIALIZED -> next == PREPARED || next == CONFLICT;
            case PREPARED, ACTIVE -> next == CONFLICT || (this == PREPARED && next == ACTIVE);
            case CONFLICT -> false;
        };
    }
}
