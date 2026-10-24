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
        boolean preparePlannedWrites(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                List<PlannedWrite> writes);
        boolean observe(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                BlockPos position, BlockState replacement, boolean committed);
    }
    private static final List<Owner> OWNERS = List.of(new Owner() {
        public CellMutationKey.OwnerFamily family() { return CellMutationKey.OwnerFamily.RESOURCE_SITE; }
        public boolean preparePlannedWrites(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                List<PlannedWrite> writes) {
            // Field effects retain their individual world-change/custody witnesses in observe.
            // Planned static projection neither acquires field authority nor acknowledges work.
            return true;
        }
        public boolean observe(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                BlockPos position, BlockState replacement, boolean committed) {
            FrontierV3ResourceFieldWorldChangeExecutor.observeBlockWrite(level, runtime, position, replacement, committed);
            return true;
        }
    }, new FrontierV3ExtractionBlockWrites());
    static {
        var keys = new HashSet<CellMutationKey.OwnerFamily>();
        for (var owner : OWNERS) if (!keys.add(owner.family())) throw new IllegalStateException("duplicate native mutation owner");
    }
    private FrontierV3ManagedBlockWrites() { }
    /** Declare all planned block writes before the common executor consumes its attempt. */
    static FrontierV3NativeEffect effect(ServerLevel level, List<PlannedWrite> writes,
            java.util.function.BooleanSupplier mutation, Runnable resume) {
        return effect(level, () -> FrontierV3ServerLifecycle.runtimeForNativeMutation(level.getServer()),
                writes, mutation, () -> !FrontierV3PhysicalWorld.isPhysical(level), resume);
    }
    static FrontierV3NativeEffect effect(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            List<PlannedWrite> writes, java.util.function.BooleanSupplier mutation, Runnable resume) {
        Objects.requireNonNull(runtime);
        return effect(level, () -> runtime, writes, mutation, () -> false, resume);
    }
    private static FrontierV3NativeEffect effect(ServerLevel level,
            java.util.function.Supplier<FrontierV3ServerRuntime<FrontierWorldState, ?>> runtimeSource,
            List<PlannedWrite> writes, java.util.function.BooleanSupplier mutation,
            java.util.function.BooleanSupplier unmanaged, Runnable resume) {
        var retained = List.copyOf(writes);
        if (retained.isEmpty() || retained.size() > MAX_PLANNED_WRITES)
            throw new IllegalArgumentException("native block effect needs a bounded declared write set");
        return FrontierV3NativeEffect.observed(mutation).preparedBy(() -> {
            if (unmanaged.getAsBoolean()) return FrontierV3NativeEffect.Admission.READY;
            var runtime = runtimeSource.get();
            return runtime == null ? FrontierV3NativeEffect.Admission.waiting("canonical-runtime")
                    : prepareEffect(level, runtime, retained, resume);
        });
    }
    static boolean preparePlannedWrites(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            List<PlannedWrite> writes) {
        return preparePlannedWrites(level, runtime, writes, () -> { });
    }
    /** The producer owns durable intent and revalidates it on continuation. This
     * integration layer owns only ordering of knowledge fences and native effects. */
    static boolean preparePlannedWrites(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            List<PlannedWrite> writes, Runnable resume) {
        return prepareEffect(level, runtime, writes, resume).ready();
    }
    private static FrontierV3NativeEffect.Admission prepareEffect(ServerLevel level,
            FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, List<PlannedWrite> writes, Runnable resume) {
        writes = List.copyOf(writes);
        if (writes.isEmpty() || writes.size() > MAX_PLANNED_WRITES)
            throw new IllegalArgumentException("unbounded planned native write window");
        if (!FrontierV3NavigationObservation.preparePlannedWrites(level, runtime, writes, resume))
            return FrontierV3NativeEffect.Admission.waiting("navigation-knowledge");
        for (var owner : OWNERS) if (!owner.preparePlannedWrites(level, runtime, writes))
            return FrontierV3NativeEffect.Admission.waiting("block-owner:" + owner.family());
        return FrontierV3NativeEffect.Admission.READY;
    }
    static boolean observe(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            BlockPos position, BlockState replacement, boolean committed) {
        if (!FrontierV3NavigationObservation.blockWrite(level, runtime, position, replacement, committed)) return false;
        for (var owner : OWNERS) if (!owner.observe(level, runtime, position, replacement, committed)) return false;
        return true;
    }
}
