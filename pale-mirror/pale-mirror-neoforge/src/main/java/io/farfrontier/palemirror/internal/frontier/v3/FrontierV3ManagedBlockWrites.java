package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** Native integration dispatches explicit owners; it never implements their mutation policy. */
final class FrontierV3ManagedBlockWrites {
    interface Owner {
        CellMutationKey.OwnerFamily family();
        void observe(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                BlockPos position, BlockState replacement, boolean committed);
    }
    private static final List<Owner> OWNERS = List.of(new Owner() {
        public CellMutationKey.OwnerFamily family() { return CellMutationKey.OwnerFamily.RESOURCE_SITE; }
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
    static void observe(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            BlockPos position, BlockState replacement, boolean committed) {
        for (var owner : OWNERS) owner.observe(level, runtime, position, replacement, committed);
    }
}
