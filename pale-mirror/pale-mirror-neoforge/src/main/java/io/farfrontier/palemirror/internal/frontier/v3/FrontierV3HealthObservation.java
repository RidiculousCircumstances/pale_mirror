package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import java.util.Optional;

/** Shared float-to-domain boundary. Invalid/dead health is never a survivor witness. */
final class FrontierV3HealthObservation {
    private FrontierV3HealthObservation() { }

    static Optional<FixedScalar> survivor(float health) {
        if (!Float.isFinite(health) || !(health > 0.0F)) return Optional.empty();
        // Preserve the observed alive/dead distinction at fixed-point precision.
        return Optional.of(new FixedScalar(Math.max(1L, Math.round((double) health * FixedScalar.SCALE))));
    }
}
