package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.server.level.ServerPlayer;

/** Native player physics requires completed terrain, never a synchronous chunk demand.
 * Player tickets, packet handling and inventory updates remain Minecraft-owned.
 */
public final class FrontierV3PlayerTerrainReadiness {
    private FrontierV3PlayerTerrainReadiness() { }

    public static boolean ready(ServerPlayer player) {
        var level = player.serverLevel();
        if (!FrontierV3PhysicalWorld.isPhysical(level)) return true;
        // Matches Entity.touchingUnloadedChunk's collision/fluid halo, but checks
        // completed LevelChunks: hasChunksAt can merely see pending holders.
        return FrontierV3LoadedWorld.ready(level, player.getBoundingBox().inflate(1.0D));
    }
}
