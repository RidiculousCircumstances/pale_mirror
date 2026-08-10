package io.farfrontier.palemirror.internal.adapter;

import io.farfrontier.palemirror.domain.ThreatTier;
import io.farfrontier.palemirror.internal.world.MutableCell;

/** Pure source-owned block selection; generic materialization retains all write safety checks. */
@FunctionalInterface
public interface SourceOverlayPalette {
    String desiredBlock(MutableCell cell, ThreatTier tier);

    static SourceOverlayPalette baselineOnly() { return (cell, tier) -> cell.baselineBlock(); }
}
