package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;
import java.util.UUID;

/** Exactly one custody location for a stack; it cannot be counted by two inventories. */
public sealed interface InventoryCustody permits InventoryCustody.ContainerSlot, InventoryCustody.Cargo, InventoryCustody.Player, InventoryCustody.WorldCarrier, InventoryCustody.Actor {
    public record ContainerSlot(SubjectId containerId, int slot) implements InventoryCustody {
        public ContainerSlot { Objects.requireNonNull(containerId, "container id"); if (slot < 0) throw new IllegalArgumentException("slot cannot be negative"); }
    }
    public record Cargo(SubjectId cargoId) implements InventoryCustody { public Cargo { Objects.requireNonNull(cargoId, "cargo id"); } }
    public record Player(UUID playerId) implements InventoryCustody { public Player { Objects.requireNonNull(playerId, "player id"); } }
    /** A loaded Minecraft carrier with one retained UUID; it is never an untracked item entity. */
    public record WorldCarrier(UUID carrierId) implements InventoryCustody { public WorldCarrier { Objects.requireNonNull(carrierId, "world carrier id"); } }
    /** Exact worn/carried equipment held by one canonical resident or bioform. */
    public record Actor(SubjectId actorId) implements InventoryCustody {
        public Actor {
            Objects.requireNonNull(actorId, "equipment actor id");
            if (!actorId.value().startsWith("resident:") && !actorId.value().startsWith("bioform:")) {
                throw new IllegalArgumentException("actor custody must name one canonical actor");
            }
        }
    }
}
