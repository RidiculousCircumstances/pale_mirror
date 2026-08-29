package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

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

    private static CommandId observation(String kind, String sourceId, long packedPosition) {
        return new CommandId("executor:" + kind + "-" + sourceId.replace(':', '-') + "-p" + Long.toUnsignedString(packedPosition));
    }
}
