package io.farfrontier.palemirror.frontier.v3.model.buildings;

import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.FacilityFacing;
import java.util.Objects;
import java.util.Optional;
import io.farfrontier.palemirror.frontier.v3.model.seating.SeatTable;

/** An authored seat and its nearby interaction points; not occupancy or navigation authority. */
public record BuildingSeat(String id, String zoneId, Kind kind, String chairBlock, BlockPosition chair, Optional<SeatTable> table,
        BlockPosition approach, BlockPosition egress,
        FacilityFacing facing) {
    public enum Kind {
        HANDCRAFTED_CHAIR(1, "handcrafted:spruce_chair", "handcrafted:spruce_table"),
        HANDCRAFTED_BENCH(2, "handcrafted:spruce_bench", "handcrafted:spruce_table");
        private final int tag; private final String chair, table;
        Kind(int tag, String chair, String table) { this.tag = tag; this.chair = chair; this.table = table; }
        public boolean supports(String chair, Optional<SeatTable> table) {
            return this.chair.equals(chair) && table.map(t -> this.table.equals(t.block())).orElse(true);
        }
        public int wireTag() { return tag; }
        public static Kind fromWireTag(int tag) {
            for (var value : values()) if (value.tag == tag) return value;
            throw new IllegalArgumentException("unknown declared seat kind");
        }
    }
    public BuildingSeat(String id, String zoneId, Kind kind, String chairBlock, String tableBlock,
            BlockPosition chair, BlockPosition table, BlockPosition approach, BlockPosition egress, FacilityFacing facing) {
        this(id, zoneId, kind, chairBlock, chair, Optional.of(new SeatTable(tableBlock, table)), approach, egress, facing);
    }
    public BuildingSeat {
        if (id == null || !id.matches("[a-z0-9_./-]{1,96}")
                || zoneId == null || !zoneId.matches("[a-z0-9_./-]{1,96}"))
            throw new IllegalArgumentException("invalid seat identity");
        Objects.requireNonNull(kind); Objects.requireNonNull(chair); Objects.requireNonNull(table);
        Objects.requireNonNull(approach); Objects.requireNonNull(egress); Objects.requireNonNull(facing);
        if (!kind.supports(chairBlock, table)
                || table.filter(t -> chair.y() != t.position().y()
                        || !t.position().equals(chair.offset(facing.x(), 0, facing.z()))).isPresent()
                || !nearby(chair, approach) || !nearby(chair, egress))
            throw new IllegalArgumentException("seat lacks its explicit supported furniture/contact geometry");
    }
    private static boolean nearby(BlockPosition chair, BlockPosition floor) {
        return floor.y() == chair.y() - 1
                && Math.abs(floor.x() - chair.x()) + Math.abs(floor.z() - chair.z()) == 1;
    }
}
