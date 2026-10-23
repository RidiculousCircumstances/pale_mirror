package io.farfrontier.palemirror.internal.frontier.v3.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import io.farfrontier.palemirror.internal.frontier.v3.FrontierV3PlayerTerrainReadiness;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Only the world-dependent base tick waits; connection and player bookkeeping continue. */
@Mixin(ServerPlayer.class)
abstract class FrontierV3PlayerTerrainTickMixin {
    @WrapWithCondition(method = "doTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;tick()V"), require = 1)
    private boolean frontierV3$completedTerrain(Player player) {
        return FrontierV3PlayerTerrainReadiness.ready((ServerPlayer) player);
    }
}
