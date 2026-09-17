package io.farfrontier.palemirror.internal.frontier.v3.client;

import net.minecraft.core.BlockPos;

import java.util.Objects;

/**
 * The pilot's explicit bridge between a canonical body cell and a client observation.
 * A resource-site crop slot is the canonical standing body cell; its support is the crop cell
 * below.  The pilot may normalize Minecraft's reported block position only against that same
 * independently typed support observation.  It never changes the semantic body target.
 */
record FrontierV3PilotVisitTarget(BlockPos teleportAnchor, BlockPos expectedClientFeet) {
    FrontierV3PilotVisitTarget {
        Objects.requireNonNull(teleportAnchor, "teleport anchor");
        Objects.requireNonNull(expectedClientFeet, "expected client feet");
    }

    static FrontierV3PilotVisitTarget fromSemanticBody(BlockPos body) {
        BlockPos semanticBody = Objects.requireNonNull(body, "semantic body");
        return new FrontierV3PilotVisitTarget(semanticBody, semanticBody);
    }

    static FrontierV3PilotVisitTarget fromClientFeet(BlockPos clientFeet) {
        BlockPos feet = Objects.requireNonNull(clientFeet, "client feet");
        return new FrontierV3PilotVisitTarget(feet, feet);
    }

    /**
     * Converts only a client observation, never the declared semantic target.  The one-cell
     * reported-position quirk is admissible solely when the independently queried supporting
     * surface still proves the canonical body's support.  A player actually one cell low has a
     * different support and is rejected rather than silently shifted into success.
     */
    BlockPos normalizeObservation(BlockPos reportedBody, BlockPos observedSupport) {
        BlockPos reported = Objects.requireNonNull(reportedBody, "reported client body");
        BlockPos support = Objects.requireNonNull(observedSupport, "observed client support");
        if (reported.equals(expectedClientFeet) && support.equals(expectedClientFeet.below())) return expectedClientFeet;
        if (reported.equals(expectedClientFeet.below()) && support.equals(expectedClientFeet.below())) return expectedClientFeet;
        return reported;
    }
}
