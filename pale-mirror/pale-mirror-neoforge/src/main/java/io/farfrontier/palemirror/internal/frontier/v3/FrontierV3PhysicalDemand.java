package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.List;

/** Read-only current-player demand gate for physical effects that mutate the loaded world. */
final class FrontierV3PhysicalDemand {
    static final int EFFECT_RADIUS_BLOCKS = 64;
    /**
     * Read-only presentation envelope for an exact container surface.  It is wider than the
     * physical interaction radius: a player can make a surface present without becoming an
     * eligible physical interaction observer.  Neither value grants custody.
     */
    static final int PRESENTATION_RADIUS_BLOCKS = 96;

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
        List<net.minecraft.server.level.ServerPlayer> ordinaryPlayers = level.players().stream().filter(player -> !player.isSpectator())
                // A GameTest mock is a server-side fixture actor, not an ordinary connected
                // player and must not grant materialization/effect demand to unrelated tests.
                .filter(player -> !"test-mock-player".equals(player.getGameProfile().getName()))
                .toList();
        int eligibleObservers = (int) ordinaryPlayers.stream()
                .filter(player -> player.blockPosition().closerThan(position, EFFECT_RADIUS_BLOCKS)).count();
        int presentationObservers = (int) ordinaryPlayers.stream()
                .filter(player -> player.blockPosition().closerThan(position, PRESENTATION_RADIUS_BLOCKS)).count();
        return new Readiness(loaded, eligibleObservers > 0, presentationObservers > 0, eligibleObservers, presentationObservers);
    }

    record Readiness(boolean chunkLoaded, boolean ordinaryPlayerNearby, boolean presentationDemand,
                     int eligibleObserverCount, int presentationObserverCount) {
        boolean runnable() { return chunkLoaded && ordinaryPlayerNearby; }
    }
}
