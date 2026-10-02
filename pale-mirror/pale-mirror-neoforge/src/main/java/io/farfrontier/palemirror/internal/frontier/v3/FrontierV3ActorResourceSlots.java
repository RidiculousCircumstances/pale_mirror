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

    static ItemStack get(Mob actor, ActorItemSlot slot) {
        return switch (slot) {
            case ActorItemSlot.Hand hand -> actor.getItemBySlot(equipment(hand));
            case ActorItemSlot.Pocket pocket -> inventory(actor).getItem(pocket.index());
        };
    }

    static void set(Mob actor, ActorItemSlot slot, ItemStack value) {
        switch (slot) {
            case ActorItemSlot.Hand hand -> actor.setItemSlot(equipment(hand), value);
            case ActorItemSlot.Pocket pocket -> inventory(actor).setItem(pocket.index(), value);
        }
    }

    static PhysicalStackAddress address(SubjectId actorId, Mob body, ActorItemSlot slot) {
        return switch (slot) {
            case ActorItemSlot.Hand hand -> new PhysicalStackAddress.ActorHand(actorId, body.getUUID(), hand.hand());
            case ActorItemSlot.Pocket pocket -> new PhysicalStackAddress.ActorPocket(actorId, body.getUUID(), pocket.index());
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
