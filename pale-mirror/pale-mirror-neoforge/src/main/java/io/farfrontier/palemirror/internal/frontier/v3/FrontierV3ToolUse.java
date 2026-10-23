package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.tooling.ToolUse;
import net.minecraft.world.item.ItemStack;

/** Item-side mutation port. The enclosing common operation owns durability and replay permission. */
final class FrontierV3ToolUse {
    private FrontierV3ToolUse() { }
    static boolean before(ItemStack stack, ToolUse use) { return FrontierV3ExactItemPresentation.exactMatch(stack, use.before()); }
    static boolean after(ItemStack stack, ToolUse use) { return FrontierV3ExactItemPresentation.exactMatch(stack, use.after()); }
    static boolean apply(ItemStack stack, ToolUse use) {
        if (!before(stack, use)) return false;
        stack.setDamageValue(use.after().condition().damage());
        return after(stack, use);
    }
}
