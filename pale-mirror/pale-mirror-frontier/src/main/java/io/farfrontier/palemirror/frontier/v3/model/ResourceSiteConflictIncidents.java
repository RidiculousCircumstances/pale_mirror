package io.farfrontier.palemirror.frontier.v3.model;

/** Resource-site adapter to the reusable immutable conflict-incident core. */
public final class ResourceSiteConflictIncidents {
    private ResourceSiteConflictIncidents() { }

    public static ConflictIncident first(ResourceSiteLifecycle lifecycle, ResourceSiteConflictObserved conflict) {
        String site = lifecycle.siteId().value();
        String identity = "incident:resource-site:" + site.substring("site:".length());
        String expected = "phase=" + lifecycle.phase() + ",epoch=" + lifecycle.growthEpoch() + ",stage=" + lifecycle.growthStage();
        String observed = "position=" + conflict.position().x() + "," + conflict.position().y() + "," + conflict.position().z()
                + ",reason=" + conflict.reason() + ",source=" + conflict.source();
        String disposition = ResourceSiteConflictDisposition.requiresRecoveryInspection(conflict.reason())
                ? ResourceSiteConflictPolicy.RECOVERY_INSPECTION_REQUIRED.name()
                : ResourceSiteConflictPolicy.TERMINAL_REPAIR_REQUIRED.name();
        return new ConflictIncident(identity, conflict.source().category(), conflict.reason().name(), lifecycle.siteId(), lifecycle.siteId(),
                conflict.source().name(), expected, observed, expected, "phase=CONFLICT," + expected, disposition, "conflict:" + identity);
    }
}
