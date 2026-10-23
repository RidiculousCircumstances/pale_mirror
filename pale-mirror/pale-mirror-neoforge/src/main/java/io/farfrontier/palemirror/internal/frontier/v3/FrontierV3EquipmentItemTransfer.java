package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;

/** One physical exact-item handoff adapter shared by work families; it never selects a job or tool. */
record FrontierV3EquipmentItemTransfer(Container container, Villager actor, ExactItemStack item,
                                      InventoryCustody.ContainerSlot slot, ActorContainerItemOrder.Direction direction) {
    boolean before() {
        return direction == ActorContainerItemOrder.Direction.TAKE
                ? actor.getMainHandItem().isEmpty() && FrontierV3ExactItemPresentation.exactMatch(container.getItem(slot.slot()), item)
                : container.getItem(slot.slot()).isEmpty() && FrontierV3ExactItemPresentation.exactMatch(actor.getMainHandItem(), item);
    }
    boolean apply() {
        return direction == ActorContainerItemOrder.Direction.TAKE
                ? FrontierV3ActorItemTransfer.take(container, actor, item, slot, EquipmentSlot.MAINHAND)
                : FrontierV3ActorItemTransfer.place(container, actor, item, slot, EquipmentSlot.MAINHAND);
    }
    boolean after() {
        return direction == ActorContainerItemOrder.Direction.TAKE
                ? container.getItem(slot.slot()).isEmpty() && FrontierV3ExactItemPresentation.exactMatch(actor.getMainHandItem(), item)
                : actor.getMainHandItem().isEmpty() && FrontierV3ExactItemPresentation.exactMatch(container.getItem(slot.slot()), item);
    }
}
