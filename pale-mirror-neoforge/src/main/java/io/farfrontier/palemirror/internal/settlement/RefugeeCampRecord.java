package io.farfrontier.palemirror.internal.settlement;

import io.farfrontier.palemirror.api.SemanticSlotKey;
import io.farfrontier.palemirror.domain.WorldObjectId;
import java.util.Objects;
import net.minecraft.core.BlockPos;

/** Identity and placement only; cells and execution state belong to the shared semantic/job ledgers. */
public record RefugeeCampRecord(String populationGroupId, WorldObjectId communityId, WorldObjectId siteId,
                                String dimensionId, BlockPos anchor, SemanticSlotKey semanticSlot) {
    public RefugeeCampRecord {
        Objects.requireNonNull(populationGroupId, "populationGroupId");
        Objects.requireNonNull(communityId, "communityId");
        Objects.requireNonNull(siteId, "siteId");
        Objects.requireNonNull(dimensionId, "dimensionId");
        anchor = Objects.requireNonNull(anchor, "anchor").immutable();
        Objects.requireNonNull(semanticSlot, "semanticSlot");
    }
}
