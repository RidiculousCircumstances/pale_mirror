package io.farfrontier.palemirror.internal.world;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.border.WorldBorder;

/** Owns the finite physical arena selected by the explicit graybox activation. */
final class FrontierGrayboxWorldBoundary {
    static final double CENTER = 0.0D;
    static final double SIZE = 1_024.0D;

    private FrontierGrayboxWorldBoundary() { }

    static void enforce(ServerLevel level) {
        WorldBorder border = level.getWorldBorder();
        border.setCenter(CENTER, CENTER);
        border.setSize(SIZE);
    }
}
