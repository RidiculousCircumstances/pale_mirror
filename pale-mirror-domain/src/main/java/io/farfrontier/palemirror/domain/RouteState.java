package io.farfrontier.palemirror.domain;

import java.util.Objects;

/** A canonical strategic route; its physical implementation is observed by an adapter. */
public final class RouteState {
    private final WorldObjectId id;
    private final WorldObjectId origin;
    private final WorldObjectId destination;
    private final ResourceKind resource;
    private final int plannedCapacity;
    private int observedCapacity;
    private RouteStatus status;

    public RouteState(WorldObjectId id, WorldObjectId origin, WorldObjectId destination,
                      ResourceKind resource, int plannedCapacity, RouteStatus status) {
        this(id, origin, destination, resource, plannedCapacity, 0, status);
    }

    public RouteState(WorldObjectId id, WorldObjectId origin, WorldObjectId destination,
                      ResourceKind resource, int plannedCapacity, int observedCapacity, RouteStatus status) {
        this.id = Objects.requireNonNull(id, "id");
        this.origin = Objects.requireNonNull(origin, "origin");
        this.destination = Objects.requireNonNull(destination, "destination");
        this.resource = Objects.requireNonNull(resource, "resource");
        if (plannedCapacity < 0 || observedCapacity < 0) throw new IllegalArgumentException("Route capacity must not be negative");
        this.plannedCapacity = plannedCapacity;
        this.observedCapacity = observedCapacity;
        this.status = Objects.requireNonNull(status, "status");
    }

    public WorldObjectId id() { return id; }
    public WorldObjectId origin() { return origin; }
    public WorldObjectId destination() { return destination; }
    public ResourceKind resource() { return resource; }
    public int plannedCapacity() { return plannedCapacity; }
    public int observedCapacity() { return observedCapacity; }
    public RouteStatus status() { return status; }
    public int transferableCapacity() { return status == RouteStatus.OPERATIONAL ? Math.min(plannedCapacity, observedCapacity) : 0; }

    public boolean observeCapacity(int capacity) {
        if (capacity < 0) throw new IllegalArgumentException("Observed route capacity must not be negative");
        RouteStatus next = capacity == 0 ? RouteStatus.DISRUPTED : RouteStatus.OPERATIONAL;
        boolean changed = observedCapacity != capacity || status != next;
        observedCapacity = capacity;
        status = next;
        return changed;
    }

    public boolean block() {
        if (status == RouteStatus.BLOCKED) return false;
        status = RouteStatus.BLOCKED;
        observedCapacity = 0;
        return true;
    }
}
