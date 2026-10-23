package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Admission only. Capturing and confirming the actual edit uses the shared click path. */
final class FrontierV3ExactContainerIngress {
    private FrontierV3ExactContainerIngress() { }
    static boolean admissible(FrontierWorldState state, ServerPlayer player, ItemStack incoming) {
        if (incoming.isEmpty()) return true;
        var id = FrontierV3ExactItemPresentation.itemId(incoming);
        if (id.isEmpty()) return plainSupported(incoming);
        var exact = state.inventory().items().get(id.orElseThrow());
        return exact != null && exact.custody().equals(new InventoryCustody.Player(player.getUUID()))
                && FrontierV3ExactItemPresentation.exactMatch(incoming, observedCondition(incoming, exact));
    }
    static ExactItemStack observedCondition(ItemStack stack, ExactItemStack exact) {
        // The player is the physical custodian: ordinary use/repair may change damage,
        // but never the declared item identity or its maximum lifetime.
        return exact.condition().durable() && stack.getMaxDamage() == exact.condition().maximumDamage()
                ? exact.withCondition(new ItemCondition(stack.getMaxDamage(), stack.getDamageValue())) : exact;
    }
    static boolean plainSupported(ItemStack stack) {
        var plain = new ItemStack(stack.getItem(), stack.getCount());
        if (plain.isDamageableItem()) {
            if (stack.getCount() != 1 || stack.getDamageValue() < 0 || stack.getDamageValue() >= plain.getMaxDamage()) return false;
            plain.setDamageValue(stack.getDamageValue());
        }
        return ItemStack.isSameItemSameComponents(stack, plain);
    }
}
