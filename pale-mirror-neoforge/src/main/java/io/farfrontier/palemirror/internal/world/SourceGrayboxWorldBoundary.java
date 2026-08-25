package io.farfrontier.palemirror.internal.world;

import io.farfrontier.palemirror.frontier.reference.ReferenceGrayboxLayout;
import net.minecraft.server.level.ServerLevel;

/** Explicit operator activation owns the finite source-graybox arena border. */
final class SourceGrayboxWorldBoundary {
    private SourceGrayboxWorldBoundary() { }

    static void enforce(ServerLevel level) {
        level.getWorldBorder().setCenter(0.0d, 0.0d);
        level.getWorldBorder().setSize(ReferenceGrayboxLayout.WORLD_BLOCKS);
    }
}
