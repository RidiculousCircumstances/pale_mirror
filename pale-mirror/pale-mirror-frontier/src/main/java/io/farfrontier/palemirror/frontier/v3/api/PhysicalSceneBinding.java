package io.farfrontier.palemirror.frontier.v3.api;

import java.util.Objects;

/** Producer-declared durable scene identity; never recovered from an intent's spelling. */
public record PhysicalSceneBinding(SceneLeaseId leaseId, long revision) {
    public PhysicalSceneBinding {
        Objects.requireNonNull(leaseId, "physical scene lease");
        if (revision < 0) throw new IllegalArgumentException("physical scene revision must be non-negative");
    }
}
