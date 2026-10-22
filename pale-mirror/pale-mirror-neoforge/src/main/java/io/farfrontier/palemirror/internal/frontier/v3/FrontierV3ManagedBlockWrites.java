package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** Native integration dispatches explicit owners; it never implements their mutation policy. */
final class FrontierV3ManagedBlockWrites {
    static final int MAX_PLANNED_WRITES = 256;
    record PlannedWrite(BlockPos position, BlockState replacement) {
        PlannedWrite {
            position = Objects.requireNonNull(position).immutable();
            Objects.requireNonNull(replacement);
        }
    }
    interface Owner {
        CellMutationKey.OwnerFamily family();
        void preparePlannedWrites(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                List<PlannedWrite> writes);
        void observe(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                BlockPos position, BlockState replacement, boolean committed);
    }
    private static final List<Owner> OWNERS = List.of(new Owner() {
        public CellMutationKey.OwnerFamily family() { return CellMutationKey.OwnerFamily.RESOURCE_SITE; }
        public void preparePlannedWrites(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                List<PlannedWrite> writes) {
            // Field effects retain their individual world-change/custody witnesses in observe.
            // Planned static projection neither acquires field authority nor acknowledges work.
        }
        public void observe(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                BlockPos position, BlockState replacement, boolean committed) {
            FrontierV3ResourceFieldWorldChangeExecutor.observeBlockWrite(level, runtime, position, replacement, committed);
        }
    }, new FrontierV3ExtractionBlockWrites());
    static {
        var keys = new HashSet<CellMutationKey.OwnerFamily>();
        for (var owner : OWNERS) if (!keys.add(owner.family())) throw new IllegalStateException("duplicate native mutation owner");
    }
    private FrontierV3ManagedBlockWrites() { }
    static void preparePlannedWrites(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            List<PlannedWrite> writes) {
        writes = List.copyOf(writes);
        if (writes.isEmpty() || writes.size() > MAX_PLANNED_WRITES)
            throw new IllegalArgumentException("unbounded planned native write window");
        FrontierV3NavigationObservation.preparePlannedWrites(level, runtime, writes);
        for (var owner : OWNERS) owner.preparePlannedWrites(level, runtime, writes);
    }
    static boolean observe(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            BlockPos position, BlockState replacement, boolean committed) {
        if (!FrontierV3NavigationObservation.blockWrite(level, runtime, position, replacement, committed)) return false;
        for (var owner : OWNERS) owner.observe(level, runtime, position, replacement, committed);
        return true;
    }
}
