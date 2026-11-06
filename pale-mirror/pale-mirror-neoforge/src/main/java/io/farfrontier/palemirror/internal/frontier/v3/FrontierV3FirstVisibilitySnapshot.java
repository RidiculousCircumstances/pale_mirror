package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.world.level.ChunkPos;
import io.farfrontier.palemirror.frontier.v3.model.GrayboxCell;

/** Bounded read-only first-visibility evidence, including its first unresolved declared cell. */
record FrontierV3FirstVisibilitySnapshot(ChunkPos chunk, String status, long revision, int cells, GrayboxCell unresolved) {
    FrontierV3FirstVisibilitySnapshot(ChunkPos chunk, String status, long revision, int cells) { this(chunk,status,revision,cells,null); }
    static FrontierV3FirstVisibilitySnapshot unavailable() { return new FrontierV3FirstVisibilitySnapshot(null, "INVALID", -1L, 0); }
    static FrontierV3FirstVisibilitySnapshot unobserved(ChunkPos chunk) { return new FrontierV3FirstVisibilitySnapshot(chunk, "UNOBSERVED", -1L, 0); }
}
