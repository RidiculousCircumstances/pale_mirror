package io.farfrontier.palemirror.internal.settlement;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import io.farfrontier.palemirror.domain.WorldObjectId;
import io.farfrontier.palemirror.internal.economy.SettlementDepotState;
import io.farfrontier.palemirror.internal.world.MutableCell;
import net.minecraft.core.BlockPos;

public final class RefugeeCampRecord {
    private final String populationGroupId;
    private final WorldObjectId communityId;
    private final WorldObjectId siteId;
    private final String dimensionId;
    private final BlockPos anchor;
    private final List<MutableCell> cells;
    private final List<UUID> representativeIds;
    private SettlementDepotState state;
    private String diagnostic;

    public RefugeeCampRecord(String populationGroupId, WorldObjectId communityId, WorldObjectId siteId,
                             String dimensionId, BlockPos anchor, List<MutableCell> cells,
                             List<UUID> representativeIds, SettlementDepotState state, String diagnostic) {
        this.populationGroupId = Objects.requireNonNull(populationGroupId, "populationGroupId");
        this.communityId = Objects.requireNonNull(communityId, "communityId");
        this.siteId = Objects.requireNonNull(siteId, "siteId");
        this.dimensionId = Objects.requireNonNull(dimensionId, "dimensionId");
        this.anchor = Objects.requireNonNull(anchor, "anchor").immutable();
        this.cells = List.copyOf(cells);
        this.representativeIds = List.copyOf(representativeIds);
        this.state = Objects.requireNonNull(state, "state");
        this.diagnostic = diagnostic == null ? "" : diagnostic;
    }
    public String populationGroupId() { return populationGroupId; }
    public WorldObjectId communityId() { return communityId; }
    public WorldObjectId siteId() { return siteId; }
    public String dimensionId() { return dimensionId; }
    public BlockPos anchor() { return anchor; }
    public List<MutableCell> cells() { return cells; }
    public List<UUID> representativeIds() { return representativeIds; }
    public SettlementDepotState state() { return state; }
    public String diagnostic() { return diagnostic; }
    public void start() { if (state != SettlementDepotState.PLANNED) throw new IllegalStateException("Camp is not planned"); state = SettlementDepotState.RUNNING; }
    public void activate() { if (state != SettlementDepotState.RUNNING) throw new IllegalStateException("Camp is not running"); state = SettlementDepotState.ACTIVE; diagnostic = ""; }
    public void block(String reason) { state = SettlementDepotState.BLOCKED; diagnostic = Objects.requireNonNull(reason); }
}
