package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.internal.integration.vanilla.VanillaMinecartRailAdapter;
import java.util.ArrayDeque;
import java.util.HashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/** Event-driven bounded graph observation; it never scans an entire chunk volume. */
final class VanillaRailTopologyRuntime {
    private static final int LOCAL_RADIUS = 2;
    private static final int CHUNK_GRAPH_BUDGET = 8_192;

    private VanillaRailTopologyRuntime() { }

    static boolean refresh(ServerLevel level, VanillaMinecartRailAdapter adapter,
                           VanillaMinecartRouteRecord record, int verificationBudget) {
        boolean changed = false;
        BlockPos lastObserved = null;
        BlockPos dirty;
        int localBudget = 8;
        while (localBudget-- > 0 && (dirty = record.pollDirtyTopologyCell()) != null) {
            lastObserved = dirty;
            changed |= scanLocal(level, adapter, record, dirty);
        }
        ChunkPos chunk = record.pollLoadedDirtyTopologyChunk(value -> {
            ChunkPos candidate = new ChunkPos(value);
            return level.hasChunk(candidate.x, candidate.z);
        });
        if (chunk != null) {
            changed |= scanChunkGraph(level, adapter, record, chunk);
            lastObserved = new BlockPos(chunk.getMiddleBlockX(), record.start().getY(), chunk.getMiddleBlockZ());
        }
        for (BlockPos position : record.topologyVerificationSlice(verificationBudget)) {
            if (!level.hasChunkAt(position)) continue;
            var shape = adapter.observedShape(level, position);
            if (record.observeRail(position, shape)) {
                changed = true;
                if (shape == null) lastObserved = position;
            }
        }
        if (!changed) return false;
        var path = record.connectedPath();
        if (path.isPresent()) changed |= record.acceptTopology(path.orElseThrow());
        else {
            BlockPos issue = record.firstMissingAcceptedRail();
            if (issue == null) issue = lastObserved == null ? record.topologyIssue() : lastObserved;
            changed |= record.disconnectTopology(issue);
        }
        return changed;
    }

    private static boolean scanLocal(ServerLevel level, VanillaMinecartRailAdapter adapter,
                                     VanillaMinecartRouteRecord record, BlockPos center) {
        boolean changed = false;
        for (int x = -LOCAL_RADIUS; x <= LOCAL_RADIUS; x++)
            for (int z = -LOCAL_RADIUS; z <= LOCAL_RADIUS; z++)
                for (int y = -2; y <= 2; y++) {
                    BlockPos position = center.offset(x, y, z);
                    if (!record.containsTopologyPosition(position) || !level.hasChunkAt(position)) continue;
                    changed |= record.observeRail(position, adapter.observedShape(level, position));
                }
        return changed;
    }

    private static boolean scanChunkGraph(ServerLevel level, VanillaMinecartRailAdapter adapter,
                                          VanillaMinecartRouteRecord record, ChunkPos chunk) {
        boolean changed = false;
        ArrayDeque<BlockPos> pending = new ArrayDeque<>();
        HashSet<Long> visited = new HashSet<>();
        record.plannedPath().stream().filter(position -> new ChunkPos(position).equals(chunk)).forEach(pending::add);
        record.observedRailShapes().keySet().stream().map(BlockPos::of)
                .filter(position -> new ChunkPos(position).equals(chunk)).forEach(pending::add);
        int budget = CHUNK_GRAPH_BUDGET;
        while (!pending.isEmpty() && budget-- > 0) {
            BlockPos position = pending.removeFirst();
            if (!new ChunkPos(position).equals(chunk) || !record.containsTopologyPosition(position)
                    || !visited.add(position.asLong())) continue;
            var shape = adapter.observedShape(level, position);
            changed |= record.observeRail(position, shape);
            if (shape == null) continue;
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos horizontal = position.relative(direction);
                pending.add(horizontal.below()); pending.add(horizontal); pending.add(horizontal.above());
            }
        }
        return changed;
    }
}
