package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import java.util.*;

/** Configuration changes placement eligibility, never existing stock/title or a retained operation's endpoint. */
public record ResourceStoragePolicyChanged(SubjectId containerId, ResourceStoragePolicy expected,
        ResourceStoragePolicy replacement) implements FrontierPayload {
    public ResourceStoragePolicyChanged {
        Objects.requireNonNull(containerId); Objects.requireNonNull(expected); Objects.requireNonNull(replacement);
        if (!expected.endpoint().equals(replacement.endpoint())) throw new IllegalArgumentException("storage policy cannot move its physical endpoint");
    }
    @Override public String type() { return "frontier.resource_storage_policy_changed"; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }
    public FrontierWorldState apply(FrontierWorldState state, SubjectId owner) {
        var inventory = state.inventory(); var current = inventory.containers().get(containerId);
        if (current == null || !current.ownerId().equals(owner) || !current.storage().equals(Optional.of(expected)))
            throw new IllegalArgumentException("storage policy has a stale predecessor or foreign owner");
        if (ContainerPhysicalAuthorityComposition.pending(state, containerId)
                || ContainerStorageDemandSources.demands(state).stream().anyMatch(demand -> demand.container().equals(containerId)))
            throw new IllegalArgumentException("storage policy retains an admitted physical effect or receiving promise");
        var records = new LinkedHashMap<>(inventory.containers()); records.put(containerId, current.withStorage(replacement));
        return state.withInventory(new ExactInventory(records, inventory.items(), inventory.cargo(), inventory.playerItems(),
                inventory.worldCarrierItems(), inventory.conflicts(), inventory.surfaces(), inventory.economics(), inventory.fungibleResources()));
    }
}
