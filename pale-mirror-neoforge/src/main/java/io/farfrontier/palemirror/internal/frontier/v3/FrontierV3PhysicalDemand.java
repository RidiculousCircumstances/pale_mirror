package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Read-only current-player demand gate for physical effects that mutate the loaded world. */
final class FrontierV3PhysicalDemand {
    static final int EFFECT_RADIUS_BLOCKS = 64;

    private FrontierV3PhysicalDemand() { }

    static boolean exists(ServerLevel level, BlockPos position) {
        return level.hasChunkAt(position) && level.players().stream().filter(player -> !player.isSpectator())
                // A GameTest mock is a server-side fixture actor, not an ordinary connected
                // player and must not grant materialization/effect demand to unrelated tests.
                .filter(player -> !"test-mock-player".equals(player.getGameProfile().getName()))
                .anyMatch(player -> player.blockPosition().closerThan(position, EFFECT_RADIUS_BLOCKS));
    }
}
