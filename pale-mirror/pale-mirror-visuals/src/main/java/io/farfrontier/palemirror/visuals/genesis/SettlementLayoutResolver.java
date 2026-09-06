package io.farfrontier.palemirror.visuals.genesis;

import io.farfrontier.palemirror.api.AuthoredSettlementSitePlan;
import io.farfrontier.palemirror.api.VisualPoint;

/** Bounded terrain-aware compilation boundary for one accepted community center. */
@FunctionalInterface
public interface SettlementLayoutResolver {
    AuthoredSettlementSitePlan resolve(String source, VisualPoint anchor, FrontierClimate climate,
                                       int freightDirection, TerrainCandidate terrain);
}
