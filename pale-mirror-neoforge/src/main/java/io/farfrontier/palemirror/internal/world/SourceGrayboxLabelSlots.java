package io.farfrontier.palemirror.internal.world;

/** Pure horizontal slot policy for collocated source-object information boards. */
final class SourceGrayboxLabelSlots {
    static final int SPACING = 14;

    private SourceGrayboxLabelSlots() { }

    /**
     * Returns a deterministic square-spiral slot.  Moving a co-located board
     * sideways keeps it next to its object; stacking it vertically would turn
     * it into an unreadable sky label.
     */
    static Offset offset(int ordinal) {
        if (ordinal < 0) throw new IllegalArgumentException("label slot ordinal must be non-negative");
        if (ordinal == 0) return new Offset(0, 0);
        int seen = 1;
        for (int ring = 1; ; ring++) {
            for (int x = -ring; x <= ring; x++) {
                if (ordinal == seen++) return scaled(x, -ring);
            }
            for (int z = -ring + 1; z <= ring; z++) {
                if (ordinal == seen++) return scaled(ring, z);
            }
            for (int x = ring - 1; x >= -ring; x--) {
                if (ordinal == seen++) return scaled(x, ring);
            }
            for (int z = ring - 1; z >= -ring + 1; z--) {
                if (ordinal == seen++) return scaled(-ring, z);
            }
        }
    }

    private static Offset scaled(int x, int z) {
        return new Offset(x * SPACING, z * SPACING);
    }

    record Offset(int x, int z) { }
}
