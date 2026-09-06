package io.farfrontier.palemirror.frontier.v3.model;

/** Stable lifecycle of one exact retained-route repair. */
public enum RouteMaintenanceStatus {
    BUILDING,
    READY,
    CONFLICT;

    public int wireTag() {
        return switch (this) {
            case BUILDING -> 0;
            case READY -> 1;
            case CONFLICT -> 2;
        };
    }
}
