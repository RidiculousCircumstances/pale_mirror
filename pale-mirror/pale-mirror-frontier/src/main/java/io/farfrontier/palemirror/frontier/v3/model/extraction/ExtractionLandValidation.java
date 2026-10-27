package io.farfrontier.palemirror.frontier.v3.model.extraction;

import io.farfrontier.palemirror.frontier.v3.model.WorldBounds;

/** The immutable layout carries exact extrema of every source/fixed/substrate
 * block. Bounds checking needs no global cache or repeated voxel traversal. */
final class ExtractionLandValidation {
    private ExtractionLandValidation() { }
    static void require(ExtractionLayout layout, WorldBounds bounds) {
        if (!layout.horizontalExtent().within(bounds))
            throw new IllegalArgumentException("extraction layout leaves declared world bounds");
    }
}
