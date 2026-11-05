package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Plain native representation of a declared resource. No custody, minting or mutation permission. */
final class FrontierV3FungibleItemStacks {
    static ItemStack stack(String kind, int quantity) {
        var id = ResourceLocation.parse(kind);
        if (!BuiltInRegistries.ITEM.containsKey(id)) throw new IllegalArgumentException("unavailable resource item " + kind);
        var stack = new ItemStack(BuiltInRegistries.ITEM.get(id), quantity);
        if (quantity < 1 || quantity > stack.getMaxStackSize() || stack.isEmpty())
            throw new IllegalArgumentException("resource exceeds its native stack capacity");
        return stack;
    }
    private FrontierV3FungibleItemStacks() { }
}
