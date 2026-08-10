package io.farfrontier.palemirror.internal.economy;

import java.util.List;
import java.util.Objects;

import io.farfrontier.palemirror.domain.WorldObjectId;
import io.farfrontier.palemirror.internal.world.MutableCell;
import net.minecraft.core.BlockPos;

/** Persisted PM-owned depot placement and provenance mask. */
public final class SettlementDepotRecord {
    private final WorldObjectId siteId;
    private final WorldObjectId communityId;
    private final String dimensionId;
    private final BlockPos anchor;
    private final List<MutableCell> cells;
    private SettlementDepotState state;
    private String diagnostic;

    public SettlementDepotRecord(WorldObjectId siteId, WorldObjectId communityId, String dimensionId,
                                 BlockPos anchor, List<MutableCell> cells, SettlementDepotState state,
                                 String diagnostic) {
        this.siteId = Objects.requireNonNull(siteId, "siteId");
        this.communityId = Objects.requireNonNull(communityId, "communityId");
        this.dimensionId = Objects.requireNonNull(dimensionId, "dimensionId");
        this.anchor = Objects.requireNonNull(anchor, "anchor").immutable();
        this.cells = List.copyOf(cells);
        this.state = Objects.requireNonNull(state, "state");
        this.diagnostic = diagnostic == null ? "" : diagnostic;
    }

    public WorldObjectId siteId() { return siteId; }
    public WorldObjectId communityId() { return communityId; }
    public String dimensionId() { return dimensionId; }
    public BlockPos anchor() { return anchor; }
    public BlockPos interactionPosition() { return anchor.above(); }
    public List<MutableCell> cells() { return cells; }
    public SettlementDepotState state() { return state; }
    public String diagnostic() { return diagnostic; }
    public void start() { if (state != SettlementDepotState.PLANNED) throw new IllegalStateException("Depot is not planned"); state = SettlementDepotState.RUNNING; }
    public void activate() { if (state != SettlementDepotState.RUNNING) throw new IllegalStateException("Depot is not running"); state = SettlementDepotState.ACTIVE; diagnostic = ""; }
    public void block(String reason) { state = SettlementDepotState.BLOCKED; diagnostic = Objects.requireNonNull(reason, "reason"); }
}
