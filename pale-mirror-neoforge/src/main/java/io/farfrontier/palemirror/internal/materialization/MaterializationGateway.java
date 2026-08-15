package io.farfrontier.palemirror.internal.materialization;

import io.farfrontier.palemirror.api.GuardedWorldAccess;
import io.farfrontier.palemirror.api.SemanticSlotKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/** Core-owned fail-closed executor gateway. It never loads a chunk. */
public final class MaterializationGateway implements GuardedWorldAccess {
    private final ServerLevel level;
    private final SemanticSlotLedger ledger;
    private final ParcelLedger parcels;

    public MaterializationGateway(ServerLevel level, SemanticSlotLedger ledger, ParcelLedger parcels) {
        this.level = level;
        this.ledger = ledger;
        this.parcels = parcels;
    }

    @Override
    public Result setBlock(SemanticSlotKey key, BlockPos position, BlockState desired, int flags) {
        return setBlock(key, position, desired, flags, observed -> equivalent(desired, observed));
    }

    public Result setBlock(SemanticSlotKey key, BlockPos position, BlockState desired, int flags,
                           java.util.function.Predicate<BlockState> postcondition) {
        SemanticSlotRecord slot = ledger.find(key).orElse(null);
        if (slot == null) return Result.blocked("Unknown semantic slot " + key.value());
        if (!level.hasChunkAt(position)) return Result.blocked("Target chunk is not loaded");
        ParcelRecord parcel = parcels.find(slot.parcelId()).orElse(null);
        if (parcel == null || !parcel.kind().pmManaged()
                || !parcel.dimensionId().equals(level.dimension().location().toString()) || !parcel.contains(position)) {
            return Result.blocked("Position is outside semantic slot parcel " + slot.parcelId());
        }
        if (parcel.kind() != slot.parcelKind()) {
            return Result.blocked("Semantic slot parcel kind changed for " + key.value());
        }
        SemanticCellRecord cell = slot.cell(position);
        if (cell == null) return Result.blocked("Position is outside semantic slot " + key.value());
        if (!slot.mutable()) return Result.blocked(slot.conflicted() ? slot.diagnostic() : "Parcel is not PM-managed");
        BlockState current = level.getBlockState(position);
        if (equivalent(current, desired)) {
            cell.applied(desired);
            return Result.unchanged();
        }
        if (!equivalent(current, cell.baselineState()) && !equivalent(current, cell.lastAppliedState())
                && slot.resetPermit().isBlank()) {
            slot.conflict("Unknown change at " + position);
            return Result.blocked(slot.diagnostic());
        }
        if (!level.setBlock(position, desired, flags)) {
            return Result.blocked("Minecraft rejected block mutation at " + position);
        }
        if (!postcondition.test(level.getBlockState(position))) {
            BlockState observed = level.getBlockState(position);
            boolean rolledBack = level.setBlock(position, current, flags) && level.getBlockState(position).equals(current);
            String diagnostic = "Block postcondition failed at " + position + ": expected " + desired
                    + ", observed " + observed + "; rollback=" + (rolledBack ? "restored" : "failed");
            if (!rolledBack) slot.conflict(diagnostic);
            return Result.blocked(diagnostic);
        }
        cell.applied(level.getBlockState(position));
        return Result.applied();
    }

    /** Called only after every cell in the authorized slot has passed its postcondition. */
    public void completeReset(SemanticSlotKey key) {
        SemanticSlotRecord slot = ledger.find(key).orElseThrow(() -> new IllegalArgumentException("Unknown semantic slot"));
        if (!slot.resetPermit().isBlank()) slot.completeReset();
    }

    public static String identity(BlockState state) { return state.toString(); }

    /**
     * Fence and wall connections are derived by Minecraft from neighbouring
     * blocks. They are not authored semantic state and therefore cannot make
     * an otherwise successful PM write fail its postcondition.
     */
    static boolean equivalent(BlockState expected, BlockState observed) {
        if (expected.equals(observed)) return true;
        if (expected.getBlock() != observed.getBlock()) return false;
        return expected.getBlock() instanceof net.minecraft.world.level.block.FenceBlock
                || expected.getBlock() instanceof net.minecraft.world.level.block.WallBlock
                || expected.getBlock() instanceof net.minecraft.world.level.block.IronBarsBlock;
    }
}
