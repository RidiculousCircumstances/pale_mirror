package io.farfrontier.palemirror.internal.economy;

import java.util.Objects;
import java.util.UUID;

import io.farfrontier.palemirror.domain.ResourceKind;
import io.farfrontier.palemirror.domain.WorldObjectId;

/** Durable bridge receipt. It contains no item inventory and never owns canonical stock. */
public final class ResourceTransfer {
    private final String id;
    private final ResourceTransferDirection direction;
    private final UUID playerId;
    private final WorldObjectId communityId;
    private final WorldObjectId siteId;
    private final ResourceKind resource;
    private final int amount;
    private final int inventorySlot;
    private final String mappingHash;
    private final long createdStep;
    private ResourceTransferState state;
    private String diagnostic;

    public ResourceTransfer(String id, ResourceTransferDirection direction, UUID playerId,
                            WorldObjectId communityId, WorldObjectId siteId, ResourceKind resource,
                            int amount, int inventorySlot, String mappingHash, long createdStep,
                            ResourceTransferState state, String diagnostic) {
        this.id = requireText(id, "id");
        this.direction = Objects.requireNonNull(direction, "direction");
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        this.communityId = Objects.requireNonNull(communityId, "communityId");
        this.siteId = Objects.requireNonNull(siteId, "siteId");
        this.resource = Objects.requireNonNull(resource, "resource");
        if (amount <= 0 || inventorySlot < 0 || createdStep < 0) throw new IllegalArgumentException("Invalid resource transfer");
        this.amount = amount;
        this.inventorySlot = inventorySlot;
        this.mappingHash = requireText(mappingHash, "mappingHash");
        this.createdStep = createdStep;
        this.state = Objects.requireNonNull(state, "state");
        this.diagnostic = diagnostic == null ? "" : diagnostic;
    }

    public String id() { return id; }
    public ResourceTransferDirection direction() { return direction; }
    public UUID playerId() { return playerId; }
    public WorldObjectId communityId() { return communityId; }
    public WorldObjectId siteId() { return siteId; }
    public ResourceKind resource() { return resource; }
    public int amount() { return amount; }
    public int inventorySlot() { return inventorySlot; }
    public String mappingHash() { return mappingHash; }
    public long createdStep() { return createdStep; }
    public ResourceTransferState state() { return state; }
    public String diagnostic() { return diagnostic; }
    public void physicalReserved() { transition(ResourceTransferState.PHYSICAL_RESERVED); }
    public void domainApplied() { transition(ResourceTransferState.DOMAIN_APPLIED); }
    public void complete() { transition(ResourceTransferState.COMPLETED); }
    public void cancel(String reason) { state = ResourceTransferState.CANCELLED; diagnostic = requireText(reason, "reason"); }
    public void block(String reason) { state = ResourceTransferState.BLOCKED; diagnostic = requireText(reason, "reason"); }

    private void transition(ResourceTransferState next) {
        if (state.terminal()) throw new IllegalStateException("Terminal resource transfer cannot advance");
        state = Objects.requireNonNull(next, "next");
        diagnostic = "";
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }
}
