package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import java.util.List;

/** Static intent translation only; registered mutation owners retain all knowledge/custody policy. */
final class FrontierV3GrayboxPreparation {
    private FrontierV3GrayboxPreparation() { }
    /** One bounded distinct window; a circular cursor must not duplicate planned writes. */
    static List<GrayboxCell> window(int limit, java.util.function.Supplier<java.util.Optional<GrayboxCell>> next) {
        var selected = new java.util.LinkedHashSet<GrayboxCell>();
        for (int count = 0; count < limit; count++) {
            var cell = next.get().orElse(null);
            if (cell == null || !selected.add(cell)) break;
        }
        return List.copyOf(selected);
    }
    static boolean prepare(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            FrontierWorldState state, List<GrayboxCell> cells) {
        var writes = cells.stream().filter(cell -> !state.physicalDeltas().containsKey(cell.position()))
                .map(cell -> new FrontierV3ManagedBlockWrites.PlannedWrite(
                        new BlockPos(cell.position().x(), cell.position().y(), cell.position().z()),
                        FrontierV3GrayboxExecutor.material(cell.material()))).toList();
        return writes.isEmpty() || FrontierV3ManagedBlockWrites.preparePlannedWrites(level, runtime, writes);
    }
}
