package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import java.util.*;

/** Planner declares one empty storage capability; physical adoption/materialization still proves its own effects. */
public record ResourceStorageDeclared(ContainerRecord container, BlockPosition position) implements FrontierPayload {
    public ResourceStorageDeclared {
        Objects.requireNonNull(container); Objects.requireNonNull(position);
        if (container.purpose() != ContainerPurpose.RESOURCE_STORAGE || container.storage().isEmpty())
            throw new IllegalArgumentException("storage declaration needs its complete capability");
    }
    @Override public String type() { return "frontier.resource_storage_declared"; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }
    public FrontierWorldState apply(FrontierWorldState state, SubjectId owner) {
        FrontierWorldStateSupport.settlement(state.bootstrap(), owner);
        var inventory = state.inventory();
        if (!container.ownerId().equals(owner) || inventory.containers().size() >= 4096
                || inventory.containers().containsKey(container.id()) || !state.bootstrap().bounds().contains(position)
                || inventory.surfaces().values().stream().filter(ContainerSurface::fixed)
                    .filter(surface -> surface.status() != ContainerSurfaceStatus.RETIRED).anyMatch(surface -> surface.position().equals(position)))
            throw new IllegalArgumentException("storage declaration has foreign ownership, occupied identity/socket or excessive capacity");
        var records = new LinkedHashMap<>(inventory.containers()); records.put(container.id(), container);
        var surfaces = new LinkedHashMap<>(inventory.surfaces());
        surfaces.put(container.id(), new ContainerSurface(container.id(), position, ContainerSurfaceStatus.UNMATERIALIZED));
        var next = state.withInventory(new ExactInventory(records, inventory.items(), inventory.cargo(), inventory.playerItems(),
                inventory.worldCarrierItems(), inventory.conflicts(), surfaces, inventory.economics(), inventory.fungibleResources()));
        ShipmentEndpointComposition.validate(next, container.storage().orElseThrow().endpoint());
        return next;
    }
}
