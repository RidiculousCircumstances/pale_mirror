package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Objects;

/** Exact resident disease state and the simulation tick at which that state began. */
public record ResidentHealth(ResidentHealthStatus status, long sinceTick, ResidentStarvation starvation) {
    public ResidentHealth(ResidentHealthStatus status, long sinceTick) {
        this(status, sinceTick, ResidentStarvation.NONE);
    }
    public ResidentHealth {
        Objects.requireNonNull(status, "resident health status");
        Objects.requireNonNull(starvation, "resident starvation condition");
    }

    static ResidentHealth healthyAt(long tick) { return new ResidentHealth(ResidentHealthStatus.HEALTHY, tick); }

    ResidentHealth transition(ResidentHealthStatus next, long tick) {
        Objects.requireNonNull(next, "next resident health status");
        if (tick < sinceTick) throw new IllegalArgumentException("resident health transition cannot move backwards in time");
        if (!allowed(status, next)) throw new IllegalArgumentException("resident health transition is not allowed");
        return new ResidentHealth(next, tick, starvation);
    }

    public ResidentHealth withStarvation(ResidentStarvation next) {
        return new ResidentHealth(status, sinceTick, next);
    }

    private static boolean allowed(ResidentHealthStatus current, ResidentHealthStatus next) {
        return current == ResidentHealthStatus.HEALTHY && next == ResidentHealthStatus.EXPOSED
                || current == ResidentHealthStatus.EXPOSED && (next == ResidentHealthStatus.INFECTED || next == ResidentHealthStatus.HEALTHY)
                || current == ResidentHealthStatus.INFECTED && next == ResidentHealthStatus.RECOVERING
                || current == ResidentHealthStatus.RECOVERING && (next == ResidentHealthStatus.HEALTHY || next == ResidentHealthStatus.EXPOSED);
    }
}
