package io.farfrontier.palemirror.internal.world;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

/**
 * Read-only player-demand view for source-graybox physical execution.
 *
 * <p>It never issues a ticket or asks Minecraft to load a chunk. The actor
 * coordinator must separately prove that a requested chunk is already loaded
 * before it can reserve an executor lease.</p>
 */
final class SourceGrayboxHotZone {
    static final int HOT_RADIUS_CHUNKS = 1;
    static final int PREPARATION_RADIUS_CHUNKS = HOT_RADIUS_CHUNKS + 1;
    static final int DRAIN_SAFETY_RADIUS_BLOCKS = 64;
    private final List<ServerPlayer> players;

    private SourceGrayboxHotZone(List<ServerPlayer> players) {
        this.players = players;
    }

    static SourceGrayboxHotZone from(ServerLevel level) {
        return new SourceGrayboxHotZone(level.players().stream().filter(player -> !player.isSpectator()).toList());
    }

    boolean hot(BlockPos position) {
        return withinChunkRadius(position, HOT_RADIUS_CHUNKS);
    }

    boolean preparing(BlockPos position) {
        return withinChunkRadius(position, PREPARATION_RADIUS_CHUNKS);
    }

    boolean safeToDrain(BlockPos position) {
        double limit = DRAIN_SAFETY_RADIUS_BLOCKS * (double) DRAIN_SAFETY_RADIUS_BLOCKS;
        return players.stream().noneMatch(player -> player.distanceToSqr(position.getX() + 0.5d, position.getY() + 0.5d, position.getZ() + 0.5d) <= limit);
    }

    private boolean withinChunkRadius(BlockPos position, int radius) {
        ChunkPos target = new ChunkPos(position);
        return players.stream().map(ServerPlayer::chunkPosition).anyMatch(player -> Math.max(Math.abs(player.x - target.x),
                Math.abs(player.z - target.z)) <= radius);
    }
}
