package io.farfrontier.palemirror.frontier.v3.model.buildings;

import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.FacilityFacing;
import java.util.Objects;

/** An authored seat and its nearby interaction points; not occupancy or navigation authority. */
public record BuildingSeat(String id, String zoneId, Kind kind, String chairBlock, String tableBlock,
        BlockPosition chair, BlockPosition table, BlockPosition approach, BlockPosition egress,
        FacilityFacing facing) {
    public enum Kind {
        HANDCRAFTED_CHAIR(1, "handcrafted:spruce_chair", "handcrafted:spruce_table"),
        HANDCRAFTED_BENCH(2, "handcrafted:spruce_bench", "handcrafted:spruce_table");
        private final int tag; private final String chair, table;
        Kind(int tag, String chair, String table) { this.tag = tag; this.chair = chair; this.table = table; }
        public boolean supports(String chair, String table) { return this.chair.equals(chair) && this.table.equals(table); }
        public int wireTag() { return tag; }
        public static Kind fromWireTag(int tag) {
            for (var value : values()) if (value.tag == tag) return value;
            throw new IllegalArgumentException("unknown declared seat kind");
        }
    }
    public BuildingSeat {
        if (id == null || !id.matches("[a-z0-9_./-]{1,96}")
                || zoneId == null || !zoneId.matches("[a-z0-9_./-]{1,96}"))
            throw new IllegalArgumentException("invalid seat identity");
        Objects.requireNonNull(kind); Objects.requireNonNull(chair); Objects.requireNonNull(table);
        Objects.requireNonNull(approach); Objects.requireNonNull(egress); Objects.requireNonNull(facing);
        if (!kind.supports(chairBlock, tableBlock)
                || chair.y() != table.y() || !table.equals(chair.offset(facing.x(), 0, facing.z()))
                || !nearby(chair, approach) || !nearby(chair, egress))
            throw new IllegalArgumentException("seat lacks its explicit supported furniture/contact geometry");
    }
    private static boolean nearby(BlockPosition chair, BlockPosition floor) {
        return floor.y() == chair.y() - 1
                && Math.abs(floor.x() - chair.x()) + Math.abs(floor.z() - chair.z()) == 1;
    }
}
