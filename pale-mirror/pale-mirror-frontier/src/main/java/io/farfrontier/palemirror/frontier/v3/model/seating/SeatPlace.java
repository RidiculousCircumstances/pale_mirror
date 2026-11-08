package io.farfrontier.palemirror.frontier.v3.model.seating;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.buildings.*;
import java.util.*;

/** Exact authored furniture identity and transformed contact geometry. No occupancy inference. */
public record SeatPlace(Id id, SubjectId home, BuildingSeat.Kind kind, String chairBlock,
        BlockPosition chair, Optional<SeatTable> table, SurfaceAnchor approach, SurfaceAnchor egress, FacilityFacing facing) {
    public record Id(SubjectId buildingId, String localId) {
        public Id {
            Objects.requireNonNull(buildingId);
            if (localId == null || !localId.matches("[a-z0-9_./-]{1,96}"))
                throw new IllegalArgumentException("invalid local seat identity");
        }
    }
    public SeatPlace(Id id, SubjectId home, BuildingSeat.Kind kind, String chairBlock, String tableBlock,
            BlockPosition chair, BlockPosition table, SurfaceAnchor approach, SurfaceAnchor egress, FacilityFacing facing) {
        this(id, home, kind, chairBlock, chair, Optional.of(new SeatTable(tableBlock, table)), approach, egress, facing);
    }
    public SeatPlace {
        Objects.requireNonNull(id); Objects.requireNonNull(home); Objects.requireNonNull(kind);
        Objects.requireNonNull(chairBlock); Objects.requireNonNull(chair);
        Objects.requireNonNull(table); Objects.requireNonNull(approach); Objects.requireNonNull(egress); Objects.requireNonNull(facing);
    }
    public static SeatPlace authored(SubjectId home, SettlementStructure building, BuildingSeat declaration) {
        var p = building.placement();
        if (!declaration.equals(building.definition().seats().get(declaration.id())))
            throw new IllegalArgumentException("foreign authored seat declaration");
        return new SeatPlace(new Id(building.id(), declaration.id()), home, declaration.kind(),
                declaration.chairBlock(), p.world(declaration.chair()),
                declaration.table().map(table -> new SeatTable(table.block(), p.world(table.position()))),
                p.support(declaration.approach()), p.support(declaration.egress()), p.direction(declaration.facing()));
    }
    public SurfaceAnchor contact() { return new SurfaceAnchor(chair); }
}
