package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Map;
import java.util.Objects;

/** Exact append-only geometry predecessor and admitted bare-soil image, not a tillage receipt. */
public record FieldLayoutExtension(long predecessorRevision, long predecessorNextCellId,
                                   int predecessorCellCount, String predecessorFingerprint,
                                   Map<ResourceFieldLayout.CellId, ResourceFieldCycle.Soil> ground) {
    public FieldLayoutExtension {
        Objects.requireNonNull(predecessorFingerprint);
        ground = Map.copyOf(ground);
        if (predecessorRevision < 1 || predecessorNextCellId < 1 || predecessorCellCount < 0
                || predecessorCellCount > ResourceFieldLayout.MAX_CELLS
                || !predecessorFingerprint.matches("[0-9a-f]{64}") || ground.isEmpty()
                || ground.size() > ResourceFieldLayout.MAX_CELLS
                || ground.values().stream().anyMatch(soil -> !soil.cultivable()))
            throw new IllegalArgumentException("invalid field land admission");
    }
    public static FieldLayoutExtension from(ResourceFieldLayout before,
            Map<ResourceFieldLayout.CellId, ResourceFieldCycle.Soil> ground) {
        return new FieldLayoutExtension(before.revision(), before.nextCellId(), before.cells().size(),
                before.fingerprint(), ground);
    }
}
