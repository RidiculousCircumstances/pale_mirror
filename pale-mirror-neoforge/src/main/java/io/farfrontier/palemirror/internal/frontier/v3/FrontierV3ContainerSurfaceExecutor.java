package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.ContainerSurface;
import io.farfrontier.palemirror.frontier.v3.model.ContainerSurfaceStatus;
import io.farfrontier.palemirror.frontier.v3.model.ContainerSurfaceTransition;
import io.farfrontier.palemirror.frontier.v3.model.ExactItemStack;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldStateCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import java.util.Comparator;

/**
 * Materializes one exact inventory surface at a time in naturally loaded chunks.
 *
 * <p>This executor owns the physical chest lifecycle, while operation executors only use an
 * already {@linkplain ContainerSurfaceStatus#ACTIVE active} container. Once preparation was
 * durably recorded, recovery inspects the owned chest against the canonical slots; it never
 * reconstructs a missing or altered surface.</p>
 */
final class FrontierV3ContainerSurfaceExecutor {
    private FrontierV3ContainerSurfaceExecutor() { }

    static void tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        FrontierWorldState state = state(runtime);
        if (state == null) return;
        state.inventory().surfaces().values().stream()
                .filter(surface -> surface.status() == ContainerSurfaceStatus.UNMATERIALIZED
                        || surface.status() == ContainerSurfaceStatus.PREPARED)
                .sorted(Comparator.comparing(ContainerSurface::containerId))
                .filter(surface -> level.hasChunkAt(position(surface)))
                .findFirst().ifPresent(surface -> executeLifecycle(level, runtime, state, surface));
        auditOneActiveSurface(level, runtime, state);
    }

    private static void executeLifecycle(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                         FrontierWorldState state, ContainerSurface surface) {
        BlockPos target = position(surface);
        if (surface.status() == ContainerSurfaceStatus.UNMATERIALIZED) {
            if (!transition(runtime, surface.containerId(), ContainerSurfaceStatus.PREPARED)) return;
            ChestBlockEntity chest = claimFreshChest(level, target, surface.containerId());
            if (chest == null || !writeCanonicalSlots(chest, state, surface.containerId())) {
                transition(runtime, surface.containerId(), ContainerSurfaceStatus.CONFLICT);
                return;
            }
            transition(runtime, surface.containerId(), ContainerSurfaceStatus.ACTIVE);
            return;
        }
        ChestBlockEntity chest = activeChest(level, target, surface.containerId());
        if (chest == null || !matchesCanonicalSlots(chest, state, surface.containerId())) reportConflict(runtime, surface.containerId());
        else transition(runtime, surface.containerId(), ContainerSurfaceStatus.ACTIVE);
    }

    /** Bounded fair drift inspection for retained active surfaces; it does not mutate blocks. */
    private static void auditOneActiveSurface(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                              FrontierWorldState state) {
        var active = state.inventory().surfaces().values().stream()
                .filter(surface -> surface.status() == ContainerSurfaceStatus.ACTIVE)
                .sorted(Comparator.comparing(ContainerSurface::containerId)).toList();
        if (active.isEmpty()) return;
        ContainerSurface surface = active.get((int) Math.floorMod(level.getGameTime(), active.size()));
        if (!level.hasChunkAt(position(surface))) return;
        ChestBlockEntity chest = activeChest(level, position(surface), surface.containerId());
        if (chest == null || !matchesCanonicalSlots(chest, state, surface.containerId())) reportConflict(runtime, surface.containerId());
    }

    /** Creates a fresh owned chest only after durable PREPARED state exists. */
    static ChestBlockEntity claimFreshChest(ServerLevel level, BlockPos target, SubjectId containerId) {
        if (!level.getBlockState(target).isAir() || level.getBlockState(target.below()).isAir()) return null;
        if (!level.setBlock(target, Blocks.CHEST.defaultBlockState(), 3)) return null;
        if (!(level.getBlockEntity(target) instanceof ChestBlockEntity chest)) return null;
        if (!chest.getPersistentData().getString(FrontierV3CargoHandoffExecutor.CONTAINER_ID_KEY).isBlank() || !chest.isEmpty()) return null;
        chest.getPersistentData().putString(FrontierV3CargoHandoffExecutor.CONTAINER_ID_KEY, containerId.value());
        chest.setChanged();
        return chest;
    }

    static ChestBlockEntity activeChest(ServerLevel level, BlockPos target, SubjectId containerId) {
        if (!(level.getBlockEntity(target) instanceof ChestBlockEntity chest)) return null;
        return containerId.value().equals(chest.getPersistentData().getString(FrontierV3CargoHandoffExecutor.CONTAINER_ID_KEY)) ? chest : null;
    }

    static boolean writeCanonicalSlots(ChestBlockEntity chest, FrontierWorldState state, SubjectId containerId) {
        if (state.inventory().containers().get(containerId) == null || !chest.isEmpty()) return false;
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            ExactItemStack item = state.inventory().itemAt(containerId, slot).orElse(null);
            if (item != null) chest.setItem(slot, FrontierV3CargoHandoffExecutor.materializedStack(item));
        }
        chest.setChanged();
        return matchesCanonicalSlots(chest, state, containerId);
    }

    static boolean matchesCanonicalSlots(ChestBlockEntity chest, FrontierWorldState state, SubjectId containerId) {
        if (state.inventory().containers().get(containerId) == null) return false;
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            ExactItemStack expected = state.inventory().itemAt(containerId, slot).orElse(null);
            if (expected == null ? !chest.getItem(slot).isEmpty()
                    : !FrontierV3CargoHandoffExecutor.exactMatch(chest.getItem(slot), expected)) return false;
        }
        return true;
    }

    static boolean reportConflict(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, SubjectId containerId) {
        return transition(runtime, containerId, ContainerSurfaceStatus.CONFLICT);
    }

    private static boolean transition(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, SubjectId containerId,
                                      ContainerSurfaceStatus status) {
        CheckpointImage checkpoint = runtime.checkpointImage().orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        CommandId commandId = new CommandId("executor:surface-" + status.name().toLowerCase(java.util.Locale.ROOT)
                + "-" + containerId.value().replace(':', '-'));
        CommandResult result = runtime.submit(new FrontierCommand(1, commandId, checkpoint.worldId(), checkpoint.revision(), checkpoint.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(commandId),
                new ContainerSurfaceTransition(containerId, status))).orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
        return result instanceof CommandResult.Accepted;
    }

    private static BlockPos position(ContainerSurface surface) {
        return new BlockPos(surface.position().x(), surface.position().y(), surface.position().z());
    }

    private static FrontierWorldState state(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        return runtime.checkpointImage().map(image -> new FrontierWorldStateCodec().decode(image.canonicalState())).orElse(null);
    }
}
