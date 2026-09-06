package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Read-only aggregate of ordinary current observers for one naturally loaded scene anchor.
 *
 * <p>This is deliberately neither a lease nor canonical state. It gives physical scene
 * executors one stable answer to the only question player presence may answer: whether the
 * existing canonical owner is currently eligible to use its already-admitted HOT authority.
 * Individual observers are never projected into members, schedules, progress or lease IDs.</p>
 */
final class FrontierV3SceneDemand {
    static final int RADIUS_BLOCKS = 96;

    private FrontierV3SceneDemand() { }

    static Snapshot observe(ServerLevel level, BlockPosition anchor) {
        Objects.requireNonNull(level, "scene demand level");
        Objects.requireNonNull(anchor, "scene demand anchor");
        BlockPos position = new BlockPos(anchor.x(), anchor.y(), anchor.z());
        if (!level.hasChunkAt(position)) return Snapshot.unloaded();
        LinkedHashSet<UUID> observers = level.players().stream()
                .filter(player -> !player.isSpectator())
                .filter(player -> player.blockPosition().closerThan(position, RADIUS_BLOCKS))
                .map(net.minecraft.world.entity.player.Player::getUUID)
                .sorted(Comparator.comparing(UUID::toString))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return new Snapshot(true, observers);
    }

    /**
     * Read-only safety-distance query for a retained body set.  This deliberately lives beside
     * aggregate scene demand so individual scene executors cannot reintroduce their own player
     * scans, radii or spectator policy while deciding whether a shared HOT lease may drain.
     */
    static boolean observerWithin(ServerLevel level, List<BlockPos> positions, int radiusBlocks) {
        Objects.requireNonNull(level, "scene observer level");
        List<BlockPos> retainedPositions = List.copyOf(Objects.requireNonNull(positions, "scene observer positions"));
        if (radiusBlocks < 1 || retainedPositions.isEmpty()) return false;
        return level.players().stream().filter(player -> !player.isSpectator())
                .anyMatch(player -> retainedPositions.stream().anyMatch(position -> player.blockPosition().closerThan(position, radiusBlocks)));
    }

    /** Immutable bounded input to HOT admission and drain policy; it grants no ownership. */
    record Snapshot(boolean chunkLoaded, Set<UUID> observerIds) {
        Snapshot {
            observerIds = Set.copyOf(Objects.requireNonNull(observerIds, "scene observer IDs"));
            if (observerIds.size() > 256) throw new IllegalArgumentException("scene observer demand exceeds bounded input");
        }

        static Snapshot unloaded() { return new Snapshot(false, Set.of()); }
        boolean active() { return chunkLoaded && !observerIds.isEmpty(); }
    }
}
