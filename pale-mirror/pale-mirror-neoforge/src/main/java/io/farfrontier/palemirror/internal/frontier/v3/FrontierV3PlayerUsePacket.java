package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.server.level.ServerPlayer;

/** Owns the lifetime of one ordinary use packet, including its predicted client state.
 * Minecraft acknowledges and reconciles an admitted packet inside the original handler.
 * A cancelled unattempted packet is explicitly reconciled once; it is never replayed. */
public final class FrontierV3PlayerUsePacket {
    private FrontierV3PlayerUsePacket() { }
    public static void handle(ServerPlayer player, ServerboundUseItemOnPacket packet, Runnable nativeHandler) {
        var level=player.serverLevel();var hit=packet.getHitResult();
        FrontierV3PlayerNativeInputs.usePacket(player,level,packet.getHand(),hit,nativeHandler,() -> {
            if(player.isRemoved() || player.serverLevel()!=level || !player.connection.isAcceptingMessages())return;
            // Ack is cumulative, so cancellation of an older packet cannot regress it.
            player.connection.ackBlockChangesUpTo(packet.getSequence());
            player.connection.send(new ClientboundBlockUpdatePacket(level,hit.getBlockPos()));
            player.connection.send(new ClientboundBlockUpdatePacket(level,hit.getBlockPos().relative(hit.getDirection())));
            player.containerMenu.broadcastChanges();
        });
    }
}
