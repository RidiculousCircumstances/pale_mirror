package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Logistics supplies positive retained access evidence. Unknown/pending navigation is not a price signal. */
final class TransportMarketAccess {
    static boolean confirmed(FrontierWorldState state, SubjectId sourceContainer, String item) {
        if (ReferenceContainerCustody.blocksCanonicalUse(state, sourceContainer)) return false;
        for (var mission : state.shipments().missions().values()) {
            if (!mission.sender().containerId().equals(sourceContainer) || mission.stage() == TransportMission.Stage.COMPLETE
                    || mission.supplies().isEmpty() || mission.supplies().orElseThrow().needsReplan()
                    || mission.shipmentIds().stream().map(state.shipments().shipments()::get).noneMatch(s -> s.itemKind().equals(item))) continue;
            // Validate a retained calculation against current geometry; never start a search from a price review.
            try { mission.supplies().orElseThrow().journey().require(state, mission); return true; }
            catch (IllegalArgumentException staleGeometry) { /* No positive access evidence for this quotation. */ }
        }
        return false;
    }
    private TransportMarketAccess() { }
}
