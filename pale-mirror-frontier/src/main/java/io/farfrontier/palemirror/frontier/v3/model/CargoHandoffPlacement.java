package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** One exact item placement observed while completing a cargo hand-off. */
public record CargoHandoffPlacement(SubjectId itemId, InventoryCustody.ContainerSlot receiverSlot) {
    public CargoHandoffPlacement {
        Objects.requireNonNull(itemId, "handoff item id");
        Objects.requireNonNull(receiverSlot, "handoff receiver slot");
    }
}
