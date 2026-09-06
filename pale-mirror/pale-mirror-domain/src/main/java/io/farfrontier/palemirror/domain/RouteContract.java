package io.farfrontier.palemirror.domain;

import java.util.Objects;

/**
 * Canonical permission for abstract flow, backed by physical validation evidence.
 * Service observations may expire; authored topology remains valid until contradicted.
 */
public final class RouteContract {
    private final WorldObjectId id;
    private final WorldObjectId originEndpoint;
    private final WorldObjectId destinationEndpoint;
    private final RouteProvider provider;
    private final ResourceKind resource;
    private final int nominalCapacity;
    private final int allocationWeight;
    private final long currentWindowSteps;
    private final long expiryWindowSteps;
    private int validatedCapacity;
    private long lastSuccessfulValidationStep;
    private String lastObservationId;
    private RouteContractStatus status;

    /**
     * Creates the canonical baseline represented by an immutable authored-world
     * manifest. Chunk generation may materialize this topology later; loaded-world
     * reconciliation is responsible for explicitly blocking it when the physical
     * graph is absent or damaged.
     */
    public static RouteContract authoredVanillaMinecart(WorldObjectId id, WorldObjectId originEndpoint,
                                                         WorldObjectId destinationEndpoint,
                                                         ResourceKind resource, int nominalCapacity,
                                                         long currentWindowSteps, long expiryWindowSteps,
                                                         long genesisStep, String topologyObservationId) {
        Objects.requireNonNull(topologyObservationId, "topologyObservationId");
        if (topologyObservationId.isBlank()) {
            throw new IllegalArgumentException("Authored topology observation id must not be blank");
        }
        return new RouteContract(id, originEndpoint, destinationEndpoint, RouteProvider.VANILLA_MINECART,
                resource, nominalCapacity, currentWindowSteps, expiryWindowSteps, nominalCapacity,
                genesisStep, topologyObservationId, RouteContractStatus.VALIDATED);
    }

    public RouteContract(WorldObjectId id, WorldObjectId originEndpoint, WorldObjectId destinationEndpoint,
                         RouteProvider provider, ResourceKind resource, int nominalCapacity,
                         long currentWindowSteps, long expiryWindowSteps, RouteContractStatus status) {
        this(id, originEndpoint, destinationEndpoint, provider, resource, nominalCapacity, currentWindowSteps,
                expiryWindowSteps, 100, 0, -1, "", status);
    }

    public RouteContract(WorldObjectId id, WorldObjectId originEndpoint, WorldObjectId destinationEndpoint,
                         RouteProvider provider, ResourceKind resource, int nominalCapacity,
                         long currentWindowSteps, long expiryWindowSteps, int validatedCapacity,
                         long lastSuccessfulValidationStep, String lastObservationId, RouteContractStatus status) {
        this(id, originEndpoint, destinationEndpoint, provider, resource, nominalCapacity, currentWindowSteps,
                expiryWindowSteps, 100, validatedCapacity, lastSuccessfulValidationStep, lastObservationId, status);
    }

    public RouteContract(WorldObjectId id, WorldObjectId originEndpoint, WorldObjectId destinationEndpoint,
                         RouteProvider provider, ResourceKind resource, int nominalCapacity,
                         long currentWindowSteps, long expiryWindowSteps, int allocationWeight,
                         int validatedCapacity, long lastSuccessfulValidationStep,
                         String lastObservationId, RouteContractStatus status) {
        this.id = Objects.requireNonNull(id, "id");
        this.originEndpoint = Objects.requireNonNull(originEndpoint, "originEndpoint");
        this.destinationEndpoint = Objects.requireNonNull(destinationEndpoint, "destinationEndpoint");
        this.provider = Objects.requireNonNull(provider, "provider");
        this.resource = Objects.requireNonNull(resource, "resource");
        if (nominalCapacity < 0 || allocationWeight < 1 || validatedCapacity < 0 || currentWindowSteps < 0
                || expiryWindowSteps < currentWindowSteps || lastSuccessfulValidationStep < -1) {
            throw new IllegalArgumentException("Invalid route contract values");
        }
        this.nominalCapacity = nominalCapacity;
        this.allocationWeight = allocationWeight;
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
    public int allocationWeight() { return allocationWeight; }
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
        if (provider.hasPersistentTopologyEvidence()) return RouteFreshness.CURRENT;
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
