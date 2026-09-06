package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Strict idempotency identities for NeoForge observations submitted to the v3 kernel. */
final class FrontierV3CommandIds {
    private FrontierV3CommandIds() { }

    static CommandId externalExplosionObservation(String effectId, long packedPosition) {
        return observation("external-explosion", effectId, packedPosition);
    }

    static CommandId managedExplosionObservation(PhysicalIntentId intentId, long packedPosition) {
        return observation("managed-explosion", intentId.value(), packedPosition);
    }

    static CommandId resourceSiteExplosionConflict(String effectId, SubjectId siteId) {
        return new CommandId("executor:resource-site-explosion-" + effectId.replace(':', '-') + "-" + siteId.value().replace(':', '-'));
    }

    /** A canonical revision identifies one serialized scene transition without expanding its opaque lease ID. */
    static CommandId scene(String phase, long revision) {
        return revision("executor:", phase, revision);
    }

    /** A canonical revision identifies one serialized explosion transition without expanding its opaque intent ID. */
    static CommandId explosion(String phase, long revision) {
        return revision("executor:explosion-", phase, revision);
    }

    /**
     * A physical observation is serialized through the canonical lane, so its checkpoint
     * revision is its bounded idempotency key. Domain IDs remain in the payload and trace;
     * they must not be expanded into the restricted Minecraft command identifier grammar.
     */
    static CommandId physical(String phase, long revision) {
        return revision("executor:", phase, revision);
    }

    private static CommandId observation(String kind, String sourceId, long packedPosition) {
        return new CommandId("executor:" + kind + "-" + sourceId.replace(':', '-') + "-p" + Long.toUnsignedString(packedPosition));
    }

    private static CommandId revision(String prefix, String phase, long revision) {
        Objects.requireNonNull(phase, "command phase");
        if (revision < 0L) throw new IllegalArgumentException("command revision must be non-negative");
        return new CommandId(prefix + phase + "-r" + revision);
    }
}
