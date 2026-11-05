package io.farfrontier.palemirror.internal.frontier.v3.mixin;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** The vanilla deprecated helper omits NeoForge's mock-channel negotiation before login. */
@Mixin(GameTestHelper.class)
abstract class FrontierV3GameTestConnectionMixin {
    @ModifyArg(method = "makeMockServerPlayerInLevel", index = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/players/PlayerList;placeNewPlayer(Lnet/minecraft/network/Connection;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/server/network/CommonListenerCookie;)V"))
    private Connection frontierV3$negotiateMockChannels(Connection connection) {
        NetworkRegistry.configureMockConnection(connection);
        return connection;
    }
}
