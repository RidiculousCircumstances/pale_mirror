package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Read-only current-player demand gate for physical effects that mutate the loaded world. */
final class FrontierV3PhysicalDemand {
    static final int EFFECT_RADIUS_BLOCKS = 64;

    private FrontierV3PhysicalDemand() { }

    static boolean exists(ServerLevel level, BlockPos position) {
        return readiness(level, position).runnable();
    }

    /**
     * Read-only explanation of the ordinary-player gate.  Executors use the same value that an
     * operator sees; the diagnostic cannot load a chunk or turn demand on.
     */
    static Readiness readiness(ServerLevel level, BlockPos position) {
        boolean loaded = level.hasChunkAt(position);
        boolean playerNearby = level.players().stream().filter(player -> !player.isSpectator())
                // A GameTest mock is a server-side fixture actor, not an ordinary connected
                // player and must not grant materialization/effect demand to unrelated tests.
                .filter(player -> !"test-mock-player".equals(player.getGameProfile().getName()))
                .anyMatch(player -> player.blockPosition().closerThan(position, EFFECT_RADIUS_BLOCKS));
        return new Readiness(loaded, playerNearby);
    }

    record Readiness(boolean chunkLoaded, boolean ordinaryPlayerNearby) {
        boolean runnable() { return chunkLoaded && ordinaryPlayerNearby; }
    }
}
