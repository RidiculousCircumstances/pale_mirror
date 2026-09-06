package io.farfrontier.palemirror.internal.settlement;

import io.farfrontier.palemirror.api.SemanticSlotKey;
import io.farfrontier.palemirror.domain.PopulationDisposition;
import io.farfrontier.palemirror.domain.WorldObjectId;
import java.util.Objects;
import net.minecraft.core.BlockPos;

/** Identity, placement and the minimal durable population lifecycle of one camp. */
public final class RefugeeCampRecord {
    private final String populationGroupId;
    private final WorldObjectId communityId;
    private final WorldObjectId siteId;
    private final String dimensionId;
    private final BlockPos anchor;
    private final SemanticSlotKey semanticSlot;
    private boolean populationDeparted;
    private boolean retired;

    public RefugeeCampRecord(String populationGroupId, WorldObjectId communityId, WorldObjectId siteId,
                             String dimensionId, BlockPos anchor, SemanticSlotKey semanticSlot) {
        this(populationGroupId, communityId, siteId, dimensionId, anchor, semanticSlot, false, false);
    }

    public RefugeeCampRecord(String populationGroupId, WorldObjectId communityId, WorldObjectId siteId,
                             String dimensionId, BlockPos anchor, SemanticSlotKey semanticSlot,
                             boolean populationDeparted, boolean retired) {
        this.populationGroupId = Objects.requireNonNull(populationGroupId, "populationGroupId");
        this.communityId = Objects.requireNonNull(communityId, "communityId");
        this.siteId = Objects.requireNonNull(siteId, "siteId");
        this.dimensionId = Objects.requireNonNull(dimensionId, "dimensionId");
        this.anchor = Objects.requireNonNull(anchor, "anchor").immutable();
        this.semanticSlot = Objects.requireNonNull(semanticSlot, "semanticSlot");
        if (retired && !populationDeparted) {
            throw new IllegalArgumentException("A refugee camp cannot retire before its population departs");
        }
        this.populationDeparted = populationDeparted;
        this.retired = retired;
    }

    public String populationGroupId() { return populationGroupId; }
    public WorldObjectId communityId() { return communityId; }
    public WorldObjectId siteId() { return siteId; }
    public String dimensionId() { return dimensionId; }
    public BlockPos anchor() { return anchor; }
    public SemanticSlotKey semanticSlot() { return semanticSlot; }
    public boolean populationDeparted() { return populationDeparted; }
    public boolean retired() { return retired; }

    public boolean observe(PopulationDisposition disposition) {
        Objects.requireNonNull(disposition, "disposition");
        if (disposition != PopulationDisposition.RESIDENT) {
            if (populationDeparted) return false;
            populationDeparted = true;
            return true;
        }
        if (!populationDeparted || retired) return false;
        retired = true;
        return true;
    }
}
