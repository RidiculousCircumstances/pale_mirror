package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;

/** One observed farmer-cargo hand write through the Minecraft body owner. */
final class FrontierV3VillagerHandMutation {
    private FrontierV3VillagerHandMutation() { }

    static boolean setOffhand(Mob worker, ItemStack expected) {
        worker.setItemInHand(InteractionHand.OFF_HAND, expected);
        ItemStack actual = worker.getOffhandItem();
        return expected.isEmpty() ? actual.isEmpty()
                : actual.getCount() == expected.getCount()
                && ItemStack.isSameItemSameComponents(actual, expected);
    }
}
