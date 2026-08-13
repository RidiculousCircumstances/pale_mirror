package io.farfrontier.palemirror.api;

import java.util.Objects;

/** Blueprint placement gated by a canonical project stage rather than initial worldgen. */
public record StagedVisualModule(String stage, VisualModulePlacement module) {
    public StagedVisualModule {
        if (stage == null || stage.isBlank()) throw new IllegalArgumentException("stage is required");
        Objects.requireNonNull(module, "module");
    }
}
