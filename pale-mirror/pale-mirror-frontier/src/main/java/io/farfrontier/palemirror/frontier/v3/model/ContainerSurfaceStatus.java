package io.farfrontier.palemirror.frontier.v3.model;

/** Durable physical lifecycle for one exact container, independent of its slot contents. */
public enum ContainerSurfaceStatus {
    UNMATERIALIZED, PREPARED, ACTIVE, CONFLICT, ADOPTING, RETIRED;

    boolean mayTransitionTo(ContainerSurfaceStatus next) {
        return switch (this) {
            case UNMATERIALIZED -> next == PREPARED || next == CONFLICT;
            case PREPARED, ADOPTING -> next == CONFLICT || next == ACTIVE;
            case ACTIVE -> next == CONFLICT || next == RETIRED;
            case CONFLICT, RETIRED -> false;
        };
    }


    public int wireTag() { return FrontierWireTags.tag(this); }
}
