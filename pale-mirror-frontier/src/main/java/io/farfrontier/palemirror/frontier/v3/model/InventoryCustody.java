package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;
import java.util.UUID;

/** Exactly one custody location for a stack; it cannot be counted by two inventories. */
public sealed interface InventoryCustody permits InventoryCustody.ContainerSlot, InventoryCustody.Cargo, InventoryCustody.Player {
    record ContainerSlot(SubjectId containerId, int slot) implements InventoryCustody {
        public ContainerSlot { Objects.requireNonNull(containerId, "container id"); if (slot < 0) throw new IllegalArgumentException("slot cannot be negative"); }
    }
    record Cargo(SubjectId cargoId) implements InventoryCustody { public Cargo { Objects.requireNonNull(cargoId, "cargo id"); } }
    record Player(UUID playerId) implements InventoryCustody { public Player { Objects.requireNonNull(playerId, "player id"); } }
}
