package io.farfrontier.palemirror.internal.frontier.v3.mixin;

import io.farfrontier.palemirror.internal.frontier.v3.FrontierV3ServerLifecycle;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Server-thread transaction boundary for a managed depot's familiar vanilla chest UI. */
@Mixin(AbstractContainerMenu.class)
abstract class FrontierV3DepotMenuClickMixin {
    @Unique private boolean frontierV3$managedDepotClick;

    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void frontierV3$beforeDepotClick(int slot, int button, ClickType kind,
                                              Player player, CallbackInfo callback) {
        frontierV3$managedDepotClick = false;
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        var disposition = FrontierV3ServerLifecycle.beforeDepotMenuClick(serverPlayer,
                (AbstractContainerMenu) (Object) this, slot, button, kind);
        if (disposition == FrontierV3ServerLifecycle.DepotClickDisposition.REJECTED) {
            callback.cancel(); return;
        }
        frontierV3$managedDepotClick = disposition == FrontierV3ServerLifecycle.DepotClickDisposition.ACCEPTED;
    }

    @Inject(method = "clicked", at = @At("RETURN"))
    private void frontierV3$afterDepotClick(int slot, int button, ClickType kind,
                                             Player player, CallbackInfo callback) {
        if (frontierV3$managedDepotClick && player instanceof ServerPlayer serverPlayer)
            FrontierV3ServerLifecycle.afterDepotMenuClick(serverPlayer, (AbstractContainerMenu) (Object) this);
        frontierV3$managedDepotClick = false;
    }
}
