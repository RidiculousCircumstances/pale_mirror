package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.execution.ActorHotObservation;
import java.util.Objects;

/** Retained native equipment preimage. Work owners own its lifetime, shared inventory owns the transfer. */
public record WorkEquipmentStep(ActorHotObservation observation, ExactItemStack item,
                                InventoryCustody.ContainerSlot slot, long containerEpoch) {
    public WorkEquipmentStep {
        Objects.requireNonNull(observation); Objects.requireNonNull(item); Objects.requireNonNull(slot);
        if (containerEpoch < 1 || item.count() != 1 || !item.condition().durable())
            throw new IllegalArgumentException("equipment step lacks exact individual state or container epoch");
    }
}
