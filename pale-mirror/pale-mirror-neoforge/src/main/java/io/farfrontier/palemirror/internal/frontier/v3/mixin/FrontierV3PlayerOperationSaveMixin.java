package io.farfrontier.palemirror.internal.frontier.v3.mixin;

import io.farfrontier.palemirror.internal.frontier.v3.FrontierV3OperationPlayers;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.PlayerDataStorage;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.io.File;

@Mixin(PlayerDataStorage.class)
abstract class FrontierV3PlayerOperationSaveMixin {
    @Shadow @Final private File playerDir;
    @Redirect(method = "save", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;saveWithoutId(Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/nbt/CompoundTag;"))
    private CompoundTag frontierV3$receiptSnapshot(Player player, CompoundTag destination) {
        return FrontierV3OperationPlayers.snapshot(player, player.saveWithoutId(destination));
    }
    @Inject(method = "save", at = @At("RETURN"))
    private void frontierV3$actualPlayerSave(Player player, CallbackInfo callback) {
        FrontierV3OperationPlayers.saved(player, playerDir.toPath());
    }
}
