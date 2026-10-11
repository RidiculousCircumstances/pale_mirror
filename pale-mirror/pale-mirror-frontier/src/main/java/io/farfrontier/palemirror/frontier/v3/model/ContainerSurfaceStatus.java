package io.farfrontier.palemirror.frontier.v3.model;

/** Durable physical lifecycle for one exact container, independent of its slot contents. */
public enum ContainerSurfaceStatus {
    UNMATERIALIZED, PREPARED, ACTIVE, CONFLICT, ADOPTING;

    boolean mayTransitionTo(ContainerSurfaceStatus next) {
        return switch (this) {
            case UNMATERIALIZED -> next == PREPARED || next == CONFLICT;
            case PREPARED, ADOPTING, ACTIVE -> next == CONFLICT || ((this == PREPARED || this == ADOPTING) && next == ACTIVE);
            case CONFLICT -> false;
        };
    }


    public int wireTag() { return FrontierWireTags.tag(this); }
}
