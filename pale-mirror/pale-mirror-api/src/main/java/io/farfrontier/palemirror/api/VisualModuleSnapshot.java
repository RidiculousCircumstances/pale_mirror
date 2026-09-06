package io.farfrontier.palemirror.api;

import java.util.List;
import java.util.Objects;

/** Curated immutable blueprint compiled by Visuals for one-shot genesis or guarded staged construction. */
public record VisualModuleSnapshot(String templateId, VisualBounds footprint,
                                   List<VisualBlockPlacement> blocks) {
    public VisualModuleSnapshot {
        if (templateId == null || templateId.isBlank()) throw new IllegalArgumentException("templateId must not be blank");
        Objects.requireNonNull(footprint, "footprint");
        blocks = List.copyOf(blocks);
        if (blocks.isEmpty()) throw new IllegalArgumentException("A blueprint snapshot must contain blocks");
    }
}
