package io.farfrontier.palemirror.internal.frontier.v3.mixin;

import io.farfrontier.palemirror.frontier.v3.model.GroundResourcePlayerWithdrawal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import io.farfrontier.palemirror.internal.frontier.v3.FrontierV3PilotCrashHooks;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.llamalad7.mixinextras.sugar.Local;

/**
 * Pilot-only observation after the actual native ground pickup, before its canonical
 * stock-exit confirmation. No player-inventory matching or fabricated player account.
 */
@Mixin(targets = "io.farfrontier.palemirror.internal.frontier.v3.FrontierV3GroundPlayerPickup")
abstract class FrontierV3FungiblePlayerCrashWindowMixin {
    @Inject(method = "add(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/item/ItemEntity;"
            + "Lnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/item/ItemStack;"
            + "Lio/farfrontier/palemirror/internal/frontier/v3/FrontierV3ServerRuntime;)Z", at = @At(value = "INVOKE",
            target = "Lio/farfrontier/palemirror/internal/frontier/v3/FrontierV3GroundPlayerPickup;reconcile"
                    + "(Lnet/minecraft/server/level/ServerLevel;Lio/farfrontier/palemirror/internal/frontier/v3/FrontierV3ServerRuntime;"
                    + "Lio/farfrontier/palemirror/frontier/v3/model/GroundResourcePlayerWithdrawal;)Z"), require = 1)
    private static void stopAfterVisiblePlayerDeparture(ServerLevel level, ItemEntity entity, Inventory inventory, ItemStack stack, @Coerce Object runtime,
                                                        CallbackInfoReturnable<Boolean> ignored,
                                                        @Local GroundResourcePlayerWithdrawal receipt) {
        FrontierV3PilotCrashHooks.afterVisibleFungiblePlayerDeparture(level, runtime, receipt.sourceAccountId());
    }
}
