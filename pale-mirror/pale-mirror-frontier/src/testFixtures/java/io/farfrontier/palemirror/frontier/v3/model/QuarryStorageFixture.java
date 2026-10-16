package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.extraction.ExtractionSite;

/** Test-only selection of the single starter store, never a runtime storage policy. */
public final class QuarryStorageFixture {
    private QuarryStorageFixture() { }
    public static SubjectId container(FrontierWorldState state, ExtractionSite site) {
        var stores = state.inventory().containers().values().stream()
                .filter(container -> container.storage().map(policy -> policy.endpoint() instanceof ShipmentEndpoint.ExtractiveSite endpoint
                        && endpoint.siteId().equals(site.id())).orElse(false)).toList();
        if (stores.size() != 1) throw new IllegalArgumentException("fixture requires exactly one starter storage");
        return stores.getFirst().id();
    }
}
