package io.farfrontier.palemirror.domain;

import java.util.Objects;

/** Canonical permission for abstract flow, backed by bounded validation evidence. */
public final class RouteContract {
    private final WorldObjectId id;
    private final WorldObjectId originEndpoint;
    private final WorldObjectId destinationEndpoint;
    private final RouteProvider provider;
    private final ResourceKind resource;
    private final int nominalCapacity;
    private final long currentWindowSteps;
    private final long expiryWindowSteps;
    private int validatedCapacity;
    private long lastSuccessfulValidationStep;
    private String lastObservationId;
    private RouteContractStatus status;

    public RouteContract(WorldObjectId id, WorldObjectId originEndpoint, WorldObjectId destinationEndpoint,
                         RouteProvider provider, ResourceKind resource, int nominalCapacity,
                         long currentWindowSteps, long expiryWindowSteps, RouteContractStatus status) {
        this(id, originEndpoint, destinationEndpoint, provider, resource, nominalCapacity, currentWindowSteps,
                expiryWindowSteps, 0, -1, "", status);
    }

    public RouteContract(WorldObjectId id, WorldObjectId originEndpoint, WorldObjectId destinationEndpoint,
                         RouteProvider provider, ResourceKind resource, int nominalCapacity,
                         long currentWindowSteps, long expiryWindowSteps, int validatedCapacity,
                         long lastSuccessfulValidationStep, String lastObservationId, RouteContractStatus status) {
        this.id = Objects.requireNonNull(id, "id");
        this.originEndpoint = Objects.requireNonNull(originEndpoint, "originEndpoint");
        this.destinationEndpoint = Objects.requireNonNull(destinationEndpoint, "destinationEndpoint");
        this.provider = Objects.requireNonNull(provider, "provider");
        this.resource = Objects.requireNonNull(resource, "resource");
        if (nominalCapacity < 0 || validatedCapacity < 0 || currentWindowSteps < 0
                || expiryWindowSteps < currentWindowSteps || lastSuccessfulValidationStep < -1) {
            throw new IllegalArgumentException("Invalid route contract values");
        }
        this.nominalCapacity = nominalCapacity;
        this.currentWindowSteps = currentWindowSteps;
        this.expiryWindowSteps = expiryWindowSteps;
        this.validatedCapacity = validatedCapacity;
        this.lastSuccessfulValidationStep = lastSuccessfulValidationStep;
        this.lastObservationId = lastObservationId == null ? "" : lastObservationId;
        this.status = Objects.requireNonNull(status, "status");
    }

    public WorldObjectId id() { return id; }
    public WorldObjectId originEndpoint() { return originEndpoint; }
    public WorldObjectId destinationEndpoint() { return destinationEndpoint; }
    public RouteProvider provider() { return provider; }
    public ResourceKind resource() { return resource; }
    public int nominalCapacity() { return nominalCapacity; }
    public long currentWindowSteps() { return currentWindowSteps; }
    public long expiryWindowSteps() { return expiryWindowSteps; }
    public int validatedCapacity() { return validatedCapacity; }
    public long lastSuccessfulValidationStep() { return lastSuccessfulValidationStep; }
    public String lastObservationId() { return lastObservationId; }
    public RouteContractStatus status() { return status; }

    public boolean validate(int capacity, long simulationStep, String observationId) {
        if (capacity < 0 || simulationStep < 0) throw new IllegalArgumentException("Invalid route validation");
        Objects.requireNonNull(observationId, "observationId");
        if (observationId.equals(lastObservationId)) return false;
        if (lastSuccessfulValidationStep > simulationStep) {
            throw new IllegalArgumentException("Route validation must not move backwards in simulation time");
        }
        validatedCapacity = Math.min(nominalCapacity, capacity);
        lastSuccessfulValidationStep = simulationStep;
        lastObservationId = observationId;
        status = validatedCapacity > 0 ? RouteContractStatus.VALIDATED : RouteContractStatus.BLOCKED;
        return true;
    }

    public RouteFreshness freshness(long simulationStep) {
        if (status != RouteContractStatus.VALIDATED || lastSuccessfulValidationStep < 0) return RouteFreshness.EXPIRED;
        long age = Math.max(0, simulationStep - lastSuccessfulValidationStep);
        if (age <= currentWindowSteps) return RouteFreshness.CURRENT;
        return age <= expiryWindowSteps ? RouteFreshness.STALE : RouteFreshness.EXPIRED;
    }

    public RouteHealth health(long simulationStep) {
        return switch (freshness(simulationStep)) {
            case CURRENT -> RouteHealth.HEALTHY;
            case STALE -> RouteHealth.DEGRADED;
            case EXPIRED -> RouteHealth.FAILED;
        };
    }

    public int transferableCapacity(long simulationStep) {
        if (status != RouteContractStatus.VALIDATED) return 0;
        return switch (freshness(simulationStep)) {
            case CURRENT -> validatedCapacity;
            case STALE -> validatedCapacity / 2;
            case EXPIRED -> 0;
        };
    }
}
