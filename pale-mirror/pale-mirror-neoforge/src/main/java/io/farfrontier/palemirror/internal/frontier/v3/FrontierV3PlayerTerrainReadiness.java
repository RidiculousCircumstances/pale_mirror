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

    /** Preserve absMoveTo's interpolation bookkeeping without invoking setPosRaw's
     * NeoForge chunk demand. Real moves, corrections and teleports keep native authority.
     */
    public static boolean resetUnchangedPosition(ServerPlayer player, double x, double y, double z,
            float yaw, float pitch) {
        if (player.getX() != x || player.getY() != y || player.getZ() != z || ready(player)) return false;
        player.xo = x; player.yo = y; player.zo = z;
        player.absRotateTo(yaw, pitch);
        return true;
    }
}
