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
        // Ring r contains 8r positions and ends at ordinal 4r(r + 1).
        // Deriving r and its edge directly keeps a dense conflict layout O(1)
        // per candidate; walking every earlier ring made a safe lateral search
        // capable of monopolising the server tick.
        int ring = (int) Math.ceil((Math.sqrt(1.0d + ordinal) - 1.0d) / 2.0d);
        int first = 1 + 4 * ring * (ring - 1);
        int offset = ordinal - first;
        int top = ring * 2 + 1;
        if (offset < top) return scaled(-ring + offset, -ring);
        offset -= top;
        int right = ring * 2;
        if (offset < right) return scaled(ring, -ring + 1 + offset);
        offset -= right;
        if (offset < right) return scaled(ring - 1 - offset, ring);
        offset -= right;
        return scaled(-ring, ring - 1 - offset);
    }

    private static Offset scaled(int x, int z) {
        return new Offset(x * SPACING, z * SPACING);
    }

    record Offset(int x, int z) { }
}
