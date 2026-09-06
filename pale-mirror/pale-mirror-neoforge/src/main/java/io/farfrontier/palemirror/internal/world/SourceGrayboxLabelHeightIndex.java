package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxLayout;
import java.util.Arrays;
import java.util.List;

/**
 * Immutable-per-publication roof index for local source-graybox boards.
 *
 * <p>Board placement never owns or changes a claim.  It needs only the
 * greatest already-owned block height at a candidate x/z column.  Building
 * this bounded arena index once avoids copying and scanning the complete
 * presentation ledger again for every board.</p>
 */
final class SourceGrayboxLabelHeightIndex {
    private static final int MIN_X = ReferenceGrayboxLayout.MIN_X;
    private static final int MIN_Z = ReferenceGrayboxLayout.MIN_Z;
    private static final int WIDTH = ReferenceGrayboxLayout.ARENA_BLOCKS_X;
    private static final int DEPTH = ReferenceGrayboxLayout.ARENA_BLOCKS_Z;
    private static final int MAX_X_EXCLUSIVE = MIN_X + WIDTH;
    private static final int MAX_Z_EXCLUSIVE = MIN_Z + DEPTH;
    private static final int GROUND_Y = ReferenceGrayboxLayout.GROUND_Y;
    private static final short NO_CLAIM = Short.MIN_VALUE;
    private final short[] highestRelativeRoof = new short[WIDTH * DEPTH];

    private SourceGrayboxLabelHeightIndex() {
        Arrays.fill(highestRelativeRoof, NO_CLAIM);
    }

    static SourceGrayboxLabelHeightIndex from(List<Footprint> footprints) {
        SourceGrayboxLabelHeightIndex result = new SourceGrayboxLabelHeightIndex();
        footprints.forEach(result::add);
        return result;
    }

    /** Returns the first legal board floor over the source-owned roof at this column. */
    int baseline(int x, int z, int clearance) {
        if (clearance < 0) throw new IllegalArgumentException("source graybox label clearance is invalid");
        if (x < MIN_X || x >= MAX_X_EXCLUSIVE || z < MIN_Z || z >= MAX_Z_EXCLUSIVE) return GROUND_Y + clearance;
        int relativeRoof = highestRelativeRoof[index(x, z)];
        return (relativeRoof == NO_CLAIM ? GROUND_Y : GROUND_Y + relativeRoof) + clearance;
    }

    private void add(Footprint footprint) {
        int endX = Math.addExact(footprint.x(), footprint.width());
        int endZ = Math.addExact(footprint.z(), footprint.depth());
        int top = Math.addExact(footprint.y(), footprint.height() - 1);
        int relativeTop = Math.subtractExact(top, GROUND_Y);
        if (relativeTop <= NO_CLAIM || relativeTop > Short.MAX_VALUE) {
            throw new IllegalStateException("source graybox label roof exceeds its bounded vertical range");
        }
        int startX = Math.max(MIN_X, footprint.x());
        int finishX = Math.min(MAX_X_EXCLUSIVE, endX);
        int startZ = Math.max(MIN_Z, footprint.z());
        int finishZ = Math.min(MAX_Z_EXCLUSIVE, endZ);
        for (int z = startZ; z < finishZ; z++) for (int x = startX; x < finishX; x++) {
            int index = index(x, z);
            if (relativeTop > highestRelativeRoof[index]) highestRelativeRoof[index] = (short) relativeTop;
        }
    }

    private static int index(int x, int z) {
        return (z - MIN_Z) * WIDTH + (x - MIN_X);
    }

    record Footprint(int x, int y, int z, int width, int depth, int height) {
        Footprint {
            if (width < 1 || depth < 1 || height < 1) {
                throw new IllegalArgumentException("source graybox label footprint dimensions are invalid");
            }
        }
    }
}
