package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import java.util.*;

/** Closed owner composition and immutable authored point index, shared by all native consumers. */
final class FrontierV3WorksiteRegistry {
    static final List<FrontierV3WorksiteProjectionOwner> OWNERS = List.of(new FrontierV3ExtractionWorksiteOwner());
    private static final Map<CellMutationKey.OwnerFamily, FrontierV3WorksiteProjectionOwner> BY_FAMILY;
    private static FrontierBootstrap bootstrap;
    private static Map<Long, WorksiteBlock> points = Map.of();
    private static Map<Long, List<WorksiteBlock>> chunks = Map.of();
    static {
        var owners = new EnumMap<CellMutationKey.OwnerFamily, FrontierV3WorksiteProjectionOwner>(CellMutationKey.OwnerFamily.class);
        for (var owner : OWNERS) if (owners.putIfAbsent(owner.family(), owner) != null) throw new IllegalStateException("duplicate worksite owner");
        BY_FAMILY = Map.copyOf(owners);
    }
    private FrontierV3WorksiteRegistry() { }
    static FrontierV3WorksiteProjectionOwner owner(CellMutationKey.OwnerFamily family) {
        return Objects.requireNonNull(BY_FAMILY.get(family), "unregistered worksite family");
    }
    static Map<Long, List<WorksiteBlock>> chunks(FrontierWorldState state) { index(state); return chunks; }
    static Optional<WorksiteBlock> point(FrontierWorldState state, BlockPos position) {
        index(state); return Optional.ofNullable(points.get(position.asLong()));
    }
    private static void index(FrontierWorldState state) {
        if (bootstrap == state.bootstrap()) return;
        var nextPoints = new HashMap<Long, WorksiteBlock>(); var nextChunks = new TreeMap<Long, List<WorksiteBlock>>();
        for (var owner : OWNERS) for (var cell : owner.declarations(state)) {
            if (cell.key().family() != owner.family()) throw new IllegalStateException("worksite provider returned a foreign declaration");
            var position = FrontierV3WorksiteProjection.position(cell);
            if (nextPoints.putIfAbsent(position.asLong(), cell) != null) throw new IllegalStateException("overlapping worksite declarations");
            nextChunks.computeIfAbsent(ChunkPos.asLong(position.getX() >> 4, position.getZ() >> 4), ignored -> new ArrayList<>()).add(cell);
        }
        var frozen = new LinkedHashMap<Long, List<WorksiteBlock>>(); nextChunks.forEach((key, cells) -> frozen.put(key, List.copyOf(cells)));
        points = Map.copyOf(nextPoints); chunks = Collections.unmodifiableMap(frozen); bootstrap = state.bootstrap();
    }
}
