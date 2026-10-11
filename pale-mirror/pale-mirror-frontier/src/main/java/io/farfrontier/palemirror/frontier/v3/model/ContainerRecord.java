package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;
import java.util.Optional;

/** Exact physical storage surface; Minecraft containers later represent these same slots. */
public record ContainerRecord(SubjectId id, SubjectId ownerId, int slotCount,
                              ContainerPurpose purpose,
                              Optional<ProductionStationSpec> productionStation,
                              Optional<ResourceStoragePolicy> storage) {
    public ContainerRecord {
        Objects.requireNonNull(id, "container id"); Objects.requireNonNull(ownerId, "container owner");
        productionStation = Objects.requireNonNull(productionStation, "production station declaration");
        Objects.requireNonNull(purpose, "container purpose");
        storage = Objects.requireNonNull(storage, "explicit optional warehouse capability");
        if (storage.isPresent() && productionStation.isPresent())
            throw new IllegalArgumentException("production station cannot be an ordinary warehouse");
        storage.ifPresent(policy -> {
            if (!policy.endpoint().containerId().equals(id) || !policy.endpoint().settlementId().equals(ownerId))
                throw new IllegalArgumentException("storage declaration has foreign identity");
        });
        if ((purpose == ContainerPurpose.PRODUCTION_STATION) != productionStation.isPresent())
            throw new IllegalArgumentException("container purpose does not match its station declaration");
        if ((purpose == ContainerPurpose.RESOURCE_STORAGE)
                != storage.map(policy -> policy.endpoint() instanceof ShipmentEndpoint.Storage).orElse(false))
            throw new IllegalArgumentException("resource storage requires its complete ordinary access declaration");
        if (slotCount <= 0 || slotCount > 54) throw new IllegalArgumentException("container slot count must be 1..54");
        productionStation.ifPresent(station -> {
            if (!station.containerId().equals(id) || station.inputSlot() >= slotCount || station.outputSlot() >= slotCount)
                throw new IllegalArgumentException("production station does not own its declared bounded container");
        });
    }

    public ContainerRecord(SubjectId id, SubjectId ownerId, int slotCount, ContainerPurpose purpose,
                           Optional<ProductionStationSpec> productionStation) {
        this(id, ownerId, slotCount, purpose, productionStation, Optional.empty());
    }

    public ContainerRecord withStorage(ResourceStoragePolicy policy) {
        return new ContainerRecord(id, ownerId, slotCount, purpose, productionStation, Optional.of(policy));
    }

    public ContainerRecord(SubjectId id, SubjectId ownerId, int slotCount) {
        this(id, ownerId, slotCount, ContainerPurpose.UNSCOPED, Optional.empty());
    }

    public ContainerRecord(SubjectId id, SubjectId ownerId, int slotCount, ContainerPurpose purpose) {
        this(id, ownerId, slotCount, purpose, Optional.empty());
    }
}
