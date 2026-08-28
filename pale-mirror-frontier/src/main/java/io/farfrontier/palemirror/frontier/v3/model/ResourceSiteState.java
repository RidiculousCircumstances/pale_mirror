package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Bounded canonical register for every deterministic renewable site in one Frontier v3 world. */
public record ResourceSiteState(Map<SubjectId, ResourceSiteLifecycle> sites) {
    public ResourceSiteState {
        sites = Map.copyOf(Objects.requireNonNull(sites, "resource-site lifecycles"));
        for (Map.Entry<SubjectId, ResourceSiteLifecycle> entry : sites.entrySet()) {
            if (!entry.getKey().equals(entry.getValue().siteId())) throw new IllegalArgumentException("resource-site map key must match lifecycle identity");
        }
    }

    public static ResourceSiteState initial(FrontierBootstrap bootstrap) {
        Map<SubjectId, ResourceSiteLifecycle> values = new LinkedHashMap<>();
        FrontierResourceSitePlan.compile(bootstrap).keySet().forEach(site -> values.put(site, ResourceSiteLifecycle.unprepared(site)));
        return new ResourceSiteState(values);
    }

    void validate(FrontierBootstrap bootstrap) {
        Map<SubjectId, ResourceSite> expected = FrontierResourceSitePlan.compile(bootstrap);
        if (!expected.keySet().equals(sites.keySet())) throw new IllegalArgumentException("resource-site state must retain every and only bootstrap resource site");
        for (ResourceSite site : expected.values()) {
            ResourceSiteLifecycle lifecycle = sites.get(site.id());
            Settlement settlement = FrontierWorldStateSupport.settlement(bootstrap, site.settlementId());
            SettlementStructure facility = FrontierWorldStateSupport.structure(settlement, site.facilityId());
            if (facility.kind() != StructureKind.FARM || site.kind() != ResourceSiteKind.WHEAT_FIELD) {
                throw new IllegalArgumentException("resource-site lifecycle must bind an owning farm");
            }
        }
    }

    public ResourceSiteLifecycle site(SubjectId id) {
        ResourceSiteLifecycle lifecycle = sites.get(Objects.requireNonNull(id, "resource-site id"));
        if (lifecycle == null) throw new IllegalArgumentException("unknown resource site: " + id.value());
        return lifecycle;
    }

    public ResourceSiteState replace(ResourceSiteLifecycle lifecycle) {
        Objects.requireNonNull(lifecycle, "resource-site lifecycle");
        if (!sites.containsKey(lifecycle.siteId())) throw new IllegalArgumentException("unknown resource site: " + lifecycle.siteId().value());
        Map<SubjectId, ResourceSiteLifecycle> next = new LinkedHashMap<>(sites); next.put(lifecycle.siteId(), lifecycle);
        return new ResourceSiteState(next);
    }
}
