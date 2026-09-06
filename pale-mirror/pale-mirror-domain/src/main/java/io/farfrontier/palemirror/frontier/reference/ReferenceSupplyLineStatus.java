package io.farfrontier.palemirror.frontier.reference;

import java.util.List;
import java.util.Objects;

/** Immutable result of source frontier pathfinding for one campaign/day. */
public record ReferenceSupplyLineStatus(
        int campaignId,
        String sourceSector,
        String targetSector,
        List<String> sectors,
        boolean connected,
        double risk,
        double readiness,
        String reason
) {
    public ReferenceSupplyLineStatus {
        sourceSector = Objects.requireNonNull(sourceSector, "sourceSector");
        targetSector = Objects.requireNonNull(targetSector, "targetSector");
        sectors = List.copyOf(Objects.requireNonNull(sectors, "sectors"));
        reason = Objects.requireNonNull(reason, "reason");
    }
}
