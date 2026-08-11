package io.farfrontier.palemirror.internal.presentation;

import io.farfrontier.palemirror.internal.PaleMirrorRuntime;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;

/** A server-issued, single-use physical proposal for a PM-owned refugee site. */
public final class RefugeeAnchorItem extends Item {
    public static final String PERMIT_KEY = "pale_mirror_refugee_anchor_permit";

    public RefugeeAnchorItem(Properties properties) { super(properties.stacksTo(1)); }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getPlayer() instanceof ServerPlayer player)) return InteractionResult.SUCCESS;
        ItemStack stack = context.getItemInHand();
        boolean placed = PaleMirrorRuntime.forServer(player.getServer()).placeRefugeeAnchor(player,
                context.getClickedPos().relative(context.getClickedFace()), stack);
        return placed ? InteractionResult.CONSUME : InteractionResult.FAIL;
    }

    public static void bind(ItemStack stack, String permitId) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString(PERMIT_KEY, permitId));
    }

    public static String permitId(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? "" : data.copyTag().getString(PERMIT_KEY);
    }
}
