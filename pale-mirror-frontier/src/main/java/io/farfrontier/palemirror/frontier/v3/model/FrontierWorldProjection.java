package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierProjection;
import io.farfrontier.palemirror.frontier.v3.api.Revision;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;

/** Immutable bounded audit surface for the initial live v3 world. */
public record FrontierWorldProjection(
        WorldId worldId, Revision revision, SimInstant instant, String bootstrapHash,
        int settlementCount, int residentCount, int bioformCount, int infectedCellCount, int itemStackCount, int activeProductionJobCount,
        int activeRouteOperationCount, int preparedPhysicalIntentCount
) implements FrontierProjection { }
