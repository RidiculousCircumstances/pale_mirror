package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.ExactItemStack;
import io.farfrontier.palemirror.frontier.v3.model.InventoryCustody;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import java.util.Objects;

/**
 * Physical half of an actor's exact container/hand transfer. The owning process must first
 * establish the actor, source/destination, station and durable intent; this class neither
 * selects a job nor changes canonical custody. A successful return is only an observation
 * for that owner's receipt, never permission to infer a new item or economic owner.
 */
final class FrontierV3ActorItemTransfer {
    private FrontierV3ActorItemTransfer() { }

    static boolean take(ChestBlockEntity chest, Villager actor, ExactItemStack expected,
                        InventoryCustody.ContainerSlot source, EquipmentSlot hand) {
        Objects.requireNonNull(chest, "source chest");
        Objects.requireNonNull(actor, "item actor");
        Objects.requireNonNull(expected, "expected item");
        Objects.requireNonNull(source, "source slot");
        requireHand(hand);
        if (source.slot() >= chest.getContainerSize()
                || !FrontierV3CargoHandoffExecutor.exactMatch(chest.getItem(source.slot()), expected)
                || !actor.getItemBySlot(hand).isEmpty()) return false;
        ItemStack stack = chest.getItem(source.slot());
        chest.setItem(source.slot(), ItemStack.EMPTY);
        chest.setChanged();
        actor.setItemSlot(hand, stack);
        return chest.getItem(source.slot()).isEmpty()
                && FrontierV3CargoHandoffExecutor.exactMatch(actor.getItemBySlot(hand), expected);
    }

    static boolean place(ChestBlockEntity chest, Villager actor, ExactItemStack expected,
                         InventoryCustody.ContainerSlot destination, EquipmentSlot hand) {
        Objects.requireNonNull(chest, "destination chest");
        Objects.requireNonNull(actor, "item actor");
        Objects.requireNonNull(expected, "expected item");
        Objects.requireNonNull(destination, "destination slot");
        requireHand(hand);
        if (destination.slot() >= chest.getContainerSize()
                || !chest.getItem(destination.slot()).isEmpty()
                || !FrontierV3CargoHandoffExecutor.exactMatch(actor.getItemBySlot(hand), expected)) return false;
        ItemStack stack = actor.getItemBySlot(hand);
        actor.setItemSlot(hand, ItemStack.EMPTY);
        chest.setItem(destination.slot(), stack);
        chest.setChanged();
        return actor.getItemBySlot(hand).isEmpty()
                && FrontierV3CargoHandoffExecutor.exactMatch(chest.getItem(destination.slot()), expected);
    }

    private static void requireHand(EquipmentSlot hand) {
        if (hand != EquipmentSlot.MAINHAND && hand != EquipmentSlot.OFFHAND) {
            throw new IllegalArgumentException("item transfer requires one declared actor hand");
        }
    }
}
