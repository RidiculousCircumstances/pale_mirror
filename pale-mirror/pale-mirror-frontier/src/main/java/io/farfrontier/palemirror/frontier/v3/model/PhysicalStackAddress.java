package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;
import java.util.UUID;

/** A current Vanilla stack address; it is evidence only and never an economic owner. */
public sealed interface PhysicalStackAddress permits PhysicalStackAddress.ContainerSlot, PhysicalStackAddress.PlayerSlot,
        PhysicalStackAddress.HopperSlot, PhysicalStackAddress.WorldEntity, PhysicalStackAddress.ActorHand {
    record ContainerSlot(InventoryCustody.ContainerSlot slot) implements PhysicalStackAddress {
        public ContainerSlot { Objects.requireNonNull(slot, "physical container slot"); }
    }
    record PlayerSlot(UUID playerId, int slot) implements PhysicalStackAddress {
        public PlayerSlot { Objects.requireNonNull(playerId, "physical player"); if (slot < 0 || slot > 255) throw new IllegalArgumentException("physical player slot"); }
    }
    record HopperSlot(BlockPosition position, int slot) implements PhysicalStackAddress {
        public HopperSlot { Objects.requireNonNull(position, "physical hopper position"); if (slot < 0 || slot > 4) throw new IllegalArgumentException("physical hopper slot"); }
    }
    record WorldEntity(UUID entityId) implements PhysicalStackAddress {
        public WorldEntity { Objects.requireNonNull(entityId, "physical item entity"); }
    }
    /** One visible OFFHAND stack of the named canonical actor's exact current body.
     * Main-hand equipment has a separate exact-item owner and must not be overwritten by crop cargo. */
    record ActorHand(SubjectId actorId, UUID entityId) implements PhysicalStackAddress {
        public ActorHand {
            Objects.requireNonNull(actorId, "physical hand actor");
            Objects.requireNonNull(entityId, "physical hand body");
        }
    }
}
