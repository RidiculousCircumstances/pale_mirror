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
import io.farfrontier.palemirror.frontier.v3.model.FrontierContainerSocketPlan;
import io.farfrontier.palemirror.frontier.v3.model.GrayboxCell;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
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
    enum SocketReadiness { DEFERRED, READY, CONFLICT }
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
            SocketReadiness readiness = socketReadiness(level, FrontierV3GrayboxLedger.get(level), target,
                    FrontierContainerSocketPlan.support(state, surface).orElse(null));
            if (readiness == SocketReadiness.DEFERRED) return;
            if (readiness == SocketReadiness.CONFLICT) {
                transition(runtime, surface.containerId(), ContainerSurfaceStatus.CONFLICT);
                return;
            }
            if (!transition(runtime, surface.containerId(), ContainerSurfaceStatus.PREPARED)) return;
            ChestBlockEntity chest = claimFreshChest(level, target, surface.containerId());
            if (chest == null || !writeCanonicalSlots(chest, state, surface.containerId())) {
                transition(runtime, surface.containerId(), ContainerSurfaceStatus.CONFLICT);
                return;
            }
            transition(runtime, surface.containerId(), ContainerSurfaceStatus.ACTIVE);
            return;
        }
        // PREPARED can survive a restart both before and after the physical chest write.  Its
        // support may still be one materializer turn behind, so wait for an absent owned
        // foundation; then either claim an empty socket or inspect the already-owned chest.
        SocketReadiness readiness = supportReadiness(level, FrontierV3GrayboxLedger.get(level), target,
                FrontierContainerSocketPlan.support(state, surface).orElse(null));
        if (readiness == SocketReadiness.DEFERRED) return;
        if (readiness == SocketReadiness.CONFLICT) {
            reportConflict(runtime, surface.containerId());
            return;
        }
        ChestBlockEntity chest = activeChest(level, target, surface.containerId());
        if (chest != null) {
            if (!matchesCanonicalSlots(chest, state, surface.containerId())) reportConflict(runtime, surface.containerId());
            else transition(runtime, surface.containerId(), ContainerSurfaceStatus.ACTIVE);
            return;
        }
        chest = claimFreshChest(level, target, surface.containerId());
        if (chest == null || !writeCanonicalSlots(chest, state, surface.containerId())) reportConflict(runtime, surface.containerId());
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
        if (!hasReadySocket(level, FrontierV3GrayboxLedger.get(level), position(surface),
                FrontierContainerSocketPlan.support(state, surface).orElse(null))) {
            reportConflict(runtime, surface.containerId());
            return;
        }
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

    /**
     * Separates a not-yet-projected owned socket from a player/world obstruction.  Only the
     * latter is terminal conflict evidence; the former must wait without changing canonical
     * container state.
     */
    static SocketReadiness socketReadiness(ServerLevel level, FrontierV3GrayboxLedger ledger, BlockPos target, GrayboxCell support) {
        // A destroyed/temporarily unavailable canonical facility has no socket to materialize;
        // it is capacity loss, not evidence that the player obstructed a future chest.
        if (!level.getBlockState(target).isAir()) return SocketReadiness.CONFLICT;
        return supportReadiness(level, ledger, target, support);
    }

    /** Checks only the owned foundation, so PREPARED recovery can inspect an already-owned chest. */
    static SocketReadiness supportReadiness(ServerLevel level, FrontierV3GrayboxLedger ledger, BlockPos target, GrayboxCell support) {
        if (support == null) return SocketReadiness.DEFERRED;
        BlockPos supportPosition = target.below();
        FrontierV3GrayboxLedger.Claim claim = ledger.claim(supportPosition);
        if (claim == null && level.getBlockState(supportPosition).isAir()) return SocketReadiness.DEFERRED;
        return matchesReadySocket(level, claim, support, supportPosition) ? SocketReadiness.READY : SocketReadiness.CONFLICT;
    }

    private static boolean hasReadySocket(ServerLevel level, FrontierV3GrayboxLedger ledger, BlockPos target, GrayboxCell support) {
        return support != null && matchesReadySocket(level, ledger.claim(target.below()), support, target.below());
    }

    private static boolean matchesReadySocket(ServerLevel level, FrontierV3GrayboxLedger.Claim claim, GrayboxCell support, BlockPos position) {
        return claim != null && !claim.conflicted() && claim.owner().equals(support.ownerId().value())
                && claim.material().equals(support.material().name()) && claim.semanticPart().equals(support.semanticPart().name())
                && level.getBlockState(position).equals(FrontierV3GrayboxExecutor.material(support.material()));
    }

    private static boolean transition(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, SubjectId containerId,
                                      ContainerSurfaceStatus status) {
        io.farfrontier.palemirror.frontier.v3.api.FrontierCanonicalState<?> checkpoint = runtime.canonicalState().orElseThrow(() -> new IllegalStateException("v3 runtime is inactive"));
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
        return runtime.decodedState().orElse(null);
    }
}
