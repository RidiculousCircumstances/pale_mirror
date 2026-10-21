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
        if (id.isEmpty()) return ItemStack.isSameItemSameComponents(incoming, new ItemStack(incoming.getItem(), incoming.getCount()));
        var exact = state.inventory().items().get(id.orElseThrow());
        return exact != null && exact.custody().equals(new InventoryCustody.Player(player.getUUID()))
                && FrontierV3ExactItemPresentation.exactMatch(incoming, exact);
    }
}
