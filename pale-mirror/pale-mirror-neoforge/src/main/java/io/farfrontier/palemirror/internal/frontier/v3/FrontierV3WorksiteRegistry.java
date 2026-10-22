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
    private static Map<Long, List<WorksiteBlock>> chunks = Map.of();
    private static List<List<WorksiteBlock>> versions = List.of();
    private static final FrontierV3WorksiteCurrentIndex CURRENT = new FrontierV3WorksiteCurrentIndex();
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
    static Map<Long, List<WorksiteBlock>> currentChunks(FrontierWorldState state) {
        return CURRENT.update(OWNERS.stream().map(owner -> owner.currentBlocks(state)).toList());
    }
    static Optional<WorksiteBlock> point(FrontierWorldState state, BlockPos position) {
        var address = new BlockPosition(position.getX(), position.getY(), position.getZ());
        WorksiteBlock found = null;
        for (var owner : OWNERS) {
            var cell = owner.currentBlocks(state).get(address);
            if (cell == null) continue;
            if (cell.key().family() != owner.family()) throw new IllegalStateException("worksite provider returned a foreign declaration");
            if (found != null) throw new IllegalStateException("overlapping worksite declarations");
            found = cell;
        }
        return Optional.ofNullable(found);
    }
    private static void index(FrontierWorldState state) {
        var current = OWNERS.stream().map(owner -> owner.declarations(state)).toList();
        boolean same = bootstrap == state.bootstrap() && current.size() == versions.size();
        for (int i = 0; same && i < current.size(); i++) same = current.get(i) == versions.get(i);
        if (same) return;
        var nextPoints = new HashMap<Long, WorksiteBlock>(); var nextChunks = new TreeMap<Long, List<WorksiteBlock>>();
        for (int i = 0; i < OWNERS.size(); i++) for (var cell : current.get(i)) {
            var owner = OWNERS.get(i);
            if (cell.key().family() != owner.family()) throw new IllegalStateException("worksite provider returned a foreign declaration");
            var position = FrontierV3WorksiteProjection.position(cell);
            if (nextPoints.putIfAbsent(position.asLong(), cell) != null) throw new IllegalStateException("overlapping worksite declarations");
            nextChunks.computeIfAbsent(ChunkPos.asLong(position.getX() >> 4, position.getZ() >> 4), ignored -> new ArrayList<>()).add(cell);
        }
        var frozen = new LinkedHashMap<Long, List<WorksiteBlock>>(); nextChunks.forEach((key, cells) -> frozen.put(key, List.copyOf(cells)));
        chunks = Collections.unmodifiableMap(frozen); bootstrap = state.bootstrap(); versions = current;
    }
}
