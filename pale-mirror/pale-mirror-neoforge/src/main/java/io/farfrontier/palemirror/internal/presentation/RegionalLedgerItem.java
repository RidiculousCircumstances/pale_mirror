package io.farfrontier.palemirror.internal.presentation;

import io.farfrontier.palemirror.internal.PaleMirrorRuntime;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.WrittenBookItem;
import net.minecraft.world.level.Level;

/** A native vanilla book screen backed by a fresh read-only PM projection on every use. */
public final class RegionalLedgerItem extends WrittenBookItem {
    public RegionalLedgerItem(Properties properties) { super(properties); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            PaleMirrorRuntime.forServer(serverPlayer.getServer()).refreshRegionalLedger(serverPlayer, stack);
            serverPlayer.openItemGui(stack, hand);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
