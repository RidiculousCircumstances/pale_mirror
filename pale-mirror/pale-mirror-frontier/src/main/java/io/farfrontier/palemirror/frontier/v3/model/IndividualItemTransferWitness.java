package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;

/** Exact object and addresses frozen before a native handoff, independently of any work family. */
public record IndividualItemTransferWitness(ExactItemStack item, InventoryCustody.ContainerSlot endpoint,
        Optional<InventoryCustody.ContainerSlot> attached, long sourceEpoch) {
    public IndividualItemTransferWitness {
        Objects.requireNonNull(item); Objects.requireNonNull(endpoint); Objects.requireNonNull(attached);
        if (item.count() != 1 || !item.condition().durable() || sourceEpoch < 1)
            throw new IllegalArgumentException("individual transfer requires a durable exact preimage");
    }
    public static IndividualItemTransferWitness capture(FrontierWorldState state, ActorContainerItemOrder order, long bodyEpoch) {
        var exact = (ActorContainerItemOrder.Portion.Exact) order.portion();
        long epoch = order.direction() == ActorContainerItemOrder.Direction.TAKE
                ? containerEpoch(state, order.exactSlot().containerId())
                : exact.attachedSlot().map(slot -> containerEpoch(state, slot.containerId())).orElse(bodyEpoch);
        return new IndividualItemTransferWitness(exact.item(), order.exactSlot(), exact.attachedSlot(), epoch);
    }
    private static long containerEpoch(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.SubjectId container) {
        return state.replicaCustody().custodyByScope().get(ReferenceContainerCustody.scopeId(container)).authorityEpoch();
    }
}
