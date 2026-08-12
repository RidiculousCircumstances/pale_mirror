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
        return setBlock(key, position, desired, flags, desired::equals);
    }

    public Result setBlock(SemanticSlotKey key, BlockPos position, BlockState desired, int flags,
                           java.util.function.Predicate<BlockState> postcondition) {
        SemanticSlotRecord slot = ledger.find(key).orElse(null);
        if (slot == null) return Result.blocked("Unknown semantic slot " + key.value());
        if (!level.hasChunkAt(position)) return Result.blocked("Target chunk is not loaded");
        if (parcels.managedAt(level.dimension().location().toString(), position).isEmpty()) {
            return Result.blocked("Position is outside a PM-managed parcel");
        }
        SemanticCellRecord cell = slot.cell(position);
        if (cell == null) return Result.blocked("Position is outside semantic slot " + key.value());
        if (!slot.mutable()) return Result.blocked(slot.conflicted() ? slot.diagnostic() : "Parcel is not PM-managed");
        BlockState current = level.getBlockState(position);
        if (current.equals(desired)) {
            cell.applied(desired);
            return Result.unchanged();
        }
        if (!current.equals(cell.baselineState()) && !current.equals(cell.lastAppliedState())
                && slot.resetPermit().isBlank()) {
            slot.conflict("Unknown change at " + position);
            return Result.blocked(slot.diagnostic());
        }
        level.setBlock(position, desired, flags);
        if (!postcondition.test(level.getBlockState(position))) {
            return Result.blocked("Block postcondition failed at " + position + ": expected " + desired
                    + ", observed " + level.getBlockState(position));
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
}
