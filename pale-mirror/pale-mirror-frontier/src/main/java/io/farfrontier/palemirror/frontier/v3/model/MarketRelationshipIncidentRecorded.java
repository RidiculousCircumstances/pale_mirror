package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;

/** Typed WAL fact recording the first relationship failure of one accepted market order. */
public record MarketRelationshipIncidentRecorded(SubjectId orderId, RelationshipIncident incident) implements FrontierPayload {
    public MarketRelationshipIncidentRecorded {
        Objects.requireNonNull(orderId, "relationship incident order"); Objects.requireNonNull(incident, "relationship incident");
        if (!orderId.equals(incident.ownerId())) throw new IllegalArgumentException("relationship incident owner must be its order");
    }
    @Override public String type() { return "frontier.market_relationship_incident_recorded"; }
}
