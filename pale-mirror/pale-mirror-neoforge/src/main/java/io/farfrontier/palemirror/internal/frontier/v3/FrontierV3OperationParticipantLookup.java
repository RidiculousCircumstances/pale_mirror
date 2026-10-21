package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import java.util.*;

/** Resolves explicitly declared native addresses, never business owners or replacement
 * objects. No chunk forcing and no acceptance of a different native incarnation. */
final class FrontierV3OperationParticipantLookup {
    private FrontierV3OperationParticipantLookup() { }
    static List<FrontierV3MinecraftOperation.Participant> current(ServerLevel level,
            Set<FrontierV3OperationReceipt.Participant> declared) {
        var ports = new ArrayList<FrontierV3MinecraftOperation.Participant>();
        for (var identity : declared) {
            var port = current(level, identity);
            if (port == null || !identity.equals(port.identity())) return List.of();
            ports.add(port);
        }
        return List.copyOf(ports);
    }
    private static FrontierV3MinecraftOperation.Participant current(ServerLevel level, FrontierV3OperationReceipt.Participant identity) {
        return switch (identity.medium()) {
            case PLAYER -> {
                var player = level.getServer().getPlayerList().getPlayer(UUID.fromString(identity.identity()));
                yield player == null ? null : FrontierV3OperationPlayers.current(player);
            }
            case ENTITIES -> {
                var entity = level.getEntity(UUID.fromString(identity.identity()));
                yield entity == null || entity.isRemoved() ? null : FrontierV3OperationEntityParticipant.nativeObject(entity);
            }
            case ENTITY_COLUMN -> FrontierV3OperationEntityColumns.current(level, new ChunkPos(Long.parseLong(identity.identity()))).orElse(null);
            case TERRAIN -> {
                var position = BlockPos.of(Long.parseLong(identity.identity()));
                var block = level.hasChunkAt(position) ? level.getBlockEntity(position) : null;
                yield block == null || block.isRemoved() ? null : FrontierV3OperationBlockEntityParticipant.current(block);
            }
            case CHUNK_BLOCKS -> {
                var position = BlockPos.of(Long.parseLong(identity.identity()));
                yield level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4) == null ? null
                        : FrontierV3OperationChunks.current(level, position);
            }
        };
    }
}
