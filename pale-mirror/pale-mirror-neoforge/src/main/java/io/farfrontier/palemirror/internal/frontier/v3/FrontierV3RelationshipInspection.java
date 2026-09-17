package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.FrontierDomainRelationships;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;

import java.util.Objects;

/** Read-only bridge that adds fenced Minecraft carrier evidence to canonical relationship inspection. */
final class FrontierV3RelationshipInspection {
    private FrontierV3RelationshipInspection() { }

    static FrontierDomainRelationships.View inspect(FrontierWorldState state, long revision, FrontierV3AmbientCarrierLedger carriers) {
        Objects.requireNonNull(state, "relationship state"); Objects.requireNonNull(carriers, "carrier evidence");
        return FrontierDomainRelationships.withCarrierEvidence(FrontierDomainRelationships.view(state, revision), carriers.relationshipEvidence());
    }
}
