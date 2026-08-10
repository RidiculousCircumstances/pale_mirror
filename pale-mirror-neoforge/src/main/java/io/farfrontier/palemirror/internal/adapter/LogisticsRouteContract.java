package io.farfrontier.palemirror.internal.adapter;

import java.util.Objects;

import io.farfrontier.palemirror.domain.WorldObjectId;
import net.minecraft.core.BlockPos;

/** PM-owned semantic route identity and the narrow physical facts an adapter may inspect. */
public record LogisticsRouteContract(WorldObjectId routeId, BlockPos originAnchor, BlockPos destinationAnchor,
                                     String originStationName, String destinationStationName) {
    public LogisticsRouteContract {
        Objects.requireNonNull(routeId, "routeId");
        originAnchor = Objects.requireNonNull(originAnchor, "originAnchor").immutable();
        destinationAnchor = Objects.requireNonNull(destinationAnchor, "destinationAnchor").immutable();
        if (originStationName == null || originStationName.isBlank()
                || destinationStationName == null || destinationStationName.isBlank()) {
            throw new IllegalArgumentException("Logistics station names must be present");
        }
    }
}
