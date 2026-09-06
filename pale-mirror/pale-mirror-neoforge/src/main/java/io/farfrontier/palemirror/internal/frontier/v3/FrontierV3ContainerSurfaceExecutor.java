package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.ContainerSurface;
import io.farfrontier.palemirror.frontier.v3.model.ContainerSurfaceActivationStateSupport;
import io.farfrontier.palemirror.frontier.v3.model.ContainerSurfaceStatus;
import io.farfrontier.palemirror.frontier.v3.model.ContainerSurfaceTransition;
import io.farfrontier.palemirror.frontier.v3.model.ExactItemStack;
import io.farfrontier.palemirror.frontier.v3.model.FrontierContainerSocketPlan;
import io.farfrontier.palemirror.frontier.v3.model.GrayboxCell;
import io.farfrontier.palemirror.frontier.v3.model.ProductionTransformationStateSupport;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
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

    /**
     * Read-only loaded-world facts for one exact container socket.  It deliberately exposes no
     * mutable inventory stack and never loads a chunk: operators need to distinguish an absent
     * projection, a foreign socket and a stale owned chest without guessing from CONFLICT.
     */
    record Readiness(String chunk, String freshSocket, String support, String targetBlock, String chest, String slots, String mismatch) { }

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
        // Do not surface a partial COLD hold: the canonical job completes first, then this
        // materializer writes one coherent exact inventory snapshot into the owned chest.
        if (ContainerSurfaceActivationStateSupport.blockedByColdProduction(state, surface.containerId())) return;
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
            // PREPARED is durable before the initial slot write.  An owned empty chest is the
            // exact pre-write recovery state; populate it once.  A nonempty mismatch remains
            // player/world evidence and is never overwritten.
            if (chest.isEmpty()) {
                if (!restorePreparedOwnedEmptyChest(chest, state, surface.containerId())) reportConflict(runtime, surface.containerId());
                else transition(runtime, surface.containerId(), ContainerSurfaceStatus.ACTIVE);
            } else if (!matchesCanonicalSlotsOrPendingProductionOutput(chest, state, surface.containerId())) reportConflict(runtime, surface.containerId());
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
        if (chest == null || !matchesCanonicalSlotsOrPendingProductionOutput(chest, state, surface.containerId())) reportConflict(runtime, surface.containerId());
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

    /** The sole safe PREPARED recovery write: an already-owned chest must still be empty. */
    static boolean restorePreparedOwnedEmptyChest(ChestBlockEntity chest, FrontierWorldState state, SubjectId containerId) {
        return chest.isEmpty() && writeCanonicalSlots(chest, state, containerId);
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

    /**
     * A physical transformation has a deliberately tiny crash window: its exact tagged output
     * can be durable in one named owned slot before the corresponding canonical receipt is
     * replayed.  That is not player drift.  Accept only this exact pending output; every other
     * slot (and every approximate/foreign output) remains strict conflict evidence.
     */
    static boolean matchesCanonicalSlotsOrPendingProductionOutput(ChestBlockEntity chest, FrontierWorldState state, SubjectId containerId) {
        if (state.inventory().containers().get(containerId) == null) return false;
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            ExactItemStack expected = state.inventory().itemAt(containerId, slot).orElse(null);
            if (expected == null ? chest.getItem(slot).isEmpty()
                    : FrontierV3CargoHandoffExecutor.exactMatch(chest.getItem(slot), expected)) continue;
            if (!pendingProductionOutputAt(state, containerId, slot, chest.getItem(slot))) return false;
        }
        return true;
    }

    private static boolean pendingProductionOutputAt(FrontierWorldState state, SubjectId containerId, int slot, net.minecraft.world.item.ItemStack physical) {
        return state.physicalIntents().values().stream()
                .filter(intent -> intent.kind() == PhysicalIntentKind.PRODUCTION_TRANSFORMATION)
                .filter(intent -> intent.status() == PhysicalIntentStatus.RUNNING || intent.status() == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART)
                .anyMatch(intent -> matchesPendingProductionOutput(state, intent, containerId, slot, physical));
    }

    private static boolean matchesPendingProductionOutput(FrontierWorldState state, PhysicalIntent intent, SubjectId containerId, int slot,
                                                          net.minecraft.world.item.ItemStack physical) {
        try {
            ProductionTransformationStateSupport.Target target = ProductionTransformationStateSupport.target(state, intent);
            return target.slot().containerId().equals(containerId) && target.slot().slot() == slot
                    && FrontierV3CargoHandoffExecutor.exactMatch(physical, target.output());
        } catch (IllegalArgumentException ignored) {
            // A broken canonical intent never grants a physical exception to the drift audit.
            return false;
        }
    }

    static boolean reportConflict(FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, SubjectId containerId) {
        return transition(runtime, containerId, ContainerSurfaceStatus.CONFLICT);
    }

    static Readiness readiness(ServerLevel level, FrontierWorldState state, SubjectId containerId) {
        ContainerSurface surface = state.inventory().surfaces().get(containerId);
        if (surface == null) return new Readiness("UNKNOWN_CONTAINER", "", "", "", "", "", "");
        BlockPos target = position(surface);
        if (!level.hasChunkAt(target)) return new Readiness("UNLOADED", "", "", "", "", "", "");
        GrayboxCell support = FrontierContainerSocketPlan.support(state, surface).orElse(null);
        SocketReadiness fresh = socketReadiness(level, FrontierV3GrayboxLedger.get(level), target, support);
        SocketReadiness supportStatus = supportReadiness(level, FrontierV3GrayboxLedger.get(level), target, support);
        ChestBlockEntity owned = activeChest(level, target, containerId);
        ChestBlockEntity chest = level.getBlockEntity(target) instanceof ChestBlockEntity value ? value : null;
        String chestStatus = chest == null ? "NO_CHEST" : owned != null ? "OWNED" : "FOREIGN_OR_UNTAGGED";
        String slots = owned == null ? "UNAVAILABLE" : matchesCanonicalSlots(owned, state, containerId) ? "CURRENT"
                : matchesCanonicalSlotsOrPendingProductionOutput(owned, state, containerId) ? "PENDING_PRODUCTION_OUTPUT" : "MISMATCH";
        String mismatch = owned == null || slots.equals("CURRENT") || slots.equals("PENDING_PRODUCTION_OUTPUT") ? ""
                : firstMismatch(owned, state, containerId);
        return new Readiness("LOADED", fresh.name(), supportStatus.name(),
                BuiltInRegistries.BLOCK.getKey(level.getBlockState(target).getBlock()).toString(), chestStatus, slots, mismatch);
    }

    private static String firstMismatch(ChestBlockEntity chest, FrontierWorldState state, SubjectId containerId) {
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            ExactItemStack expected = state.inventory().itemAt(containerId, slot).orElse(null); ItemStack actual = chest.getItem(slot);
            if (expected == null ? actual.isEmpty() : FrontierV3CargoHandoffExecutor.exactMatch(actual, expected)) continue;
            CustomData data = actual.get(DataComponents.CUSTOM_DATA);
            String actualId = data == null ? "" : data.copyTag().getString(FrontierV3CargoHandoffExecutor.ITEM_ID_KEY);
            return "slot=" + slot + ";expected=" + (expected == null ? "EMPTY" : expected.id().value() + "/" + expected.itemKind() + "/" + expected.count())
                    + ";actual=" + (actual.isEmpty() ? "EMPTY" : BuiltInRegistries.ITEM.getKey(actual.getItem()) + "/" + actual.getCount() + "/" + actualId);
        }
        return "";
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
