package io.farfrontier.palemirror.internal.economy;

import io.farfrontier.palemirror.api.SemanticSlotKey;
import io.farfrontier.palemirror.domain.WorldObjectId;
import java.util.Objects;
import net.minecraft.core.BlockPos;

/** Stable depot identity. Shared semantic/job ledgers own its provenance and execution state. */
public record SettlementDepotRecord(WorldObjectId siteId, WorldObjectId communityId, String dimensionId,
                                    BlockPos anchor, SemanticSlotKey semanticSlot) {
    public SettlementDepotRecord {
        Objects.requireNonNull(siteId, "siteId"); Objects.requireNonNull(communityId, "communityId");
        Objects.requireNonNull(dimensionId, "dimensionId"); anchor = Objects.requireNonNull(anchor, "anchor").immutable();
        Objects.requireNonNull(semanticSlot, "semanticSlot");
    }
    public BlockPos interactionPosition() { return anchor.above(); }
}
