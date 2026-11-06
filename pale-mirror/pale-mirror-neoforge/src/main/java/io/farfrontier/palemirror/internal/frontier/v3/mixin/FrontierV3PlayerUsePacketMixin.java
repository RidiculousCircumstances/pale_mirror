package io.farfrontier.palemirror.internal.frontier.v3.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Defer before the packet acknowledges prediction or sends any pre-action block correction. */
@Mixin(ServerGamePacketListenerImpl.class)
abstract class FrontierV3PlayerUsePacketMixin {
    @Shadow public ServerPlayer player;
    @WrapMethod(method="handleUseItemOn")
    private void frontierV3$admitUsePacket(ServerboundUseItemOnPacket packet, Operation<Void> original) {
        // The native handler owns Netty-to-server scheduling. Its scheduled re-entry
        // reaches this boundary again on the owning thread before touching the world.
        if(!player.server.isSameThread()) { original.call(packet);return; }
        io.farfrontier.palemirror.internal.frontier.v3.FrontierV3PlayerUsePacket.handle(player,packet,() -> original.call(packet));
    }
}
