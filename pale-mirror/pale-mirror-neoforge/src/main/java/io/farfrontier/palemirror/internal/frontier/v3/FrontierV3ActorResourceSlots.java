package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;

/** Minecraft storage adapter. Neither resource kinds nor owning activities select behavior here. */
final class FrontierV3ActorResourceSlots {
    private FrontierV3ActorResourceSlots() { }

    static java.util.List<ActorItemSlot> supportedSlots(Mob actor) {
        return UnitInventoryPresentation.slots().stream()
                .filter(slot -> !(slot instanceof ActorItemSlot.Pocket) || actor instanceof Villager).toList();
    }

    static ItemStack get(Mob actor, ActorItemSlot slot) {
        return switch (slot) {
            case ActorItemSlot.Hand hand -> actor.getItemBySlot(equipment(hand));
            case ActorItemSlot.Pocket pocket -> inventory(actor).getItem(pocket.index());
            case ActorItemSlot.AttachedStorage ignored -> throw new IllegalArgumentException("attached storage requires its declared physical-container port");
        };
    }

    static void set(Mob actor, ActorItemSlot slot, ItemStack value) {
        switch (slot) {
            case ActorItemSlot.Hand hand -> actor.setItemSlot(equipment(hand), value);
            case ActorItemSlot.Pocket pocket -> inventory(actor).setItem(pocket.index(), value);
            case ActorItemSlot.AttachedStorage ignored -> throw new IllegalArgumentException("attached storage requires its declared physical-container port");
        }
    }

    static PhysicalStackAddress address(SubjectId actorId, Mob body, ActorItemSlot slot) {
        return switch (slot) {
            case ActorItemSlot.Hand hand -> new PhysicalStackAddress.ActorHand(actorId, body.getUUID(), hand.hand());
            case ActorItemSlot.Pocket pocket -> new PhysicalStackAddress.ActorPocket(actorId, body.getUUID(), pocket.index());
            case ActorItemSlot.AttachedStorage ignored -> throw new IllegalArgumentException("attached storage address is selected from current container bindings");
        };
    }

    private static net.minecraft.world.SimpleContainer inventory(Mob actor) {
        if (!(actor instanceof Villager villager))
            throw new IllegalArgumentException("actor body has no supported pocket inventory");
        return villager.getInventory();
    }

    private static EquipmentSlot equipment(ActorItemSlot.Hand hand) {
        return hand.hand() == ActorContainerItemOrder.Hand.MAIN ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
    }
}
