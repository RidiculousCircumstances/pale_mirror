package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

/** One durable physical operation that is exclusively bound to one resource site. */
public sealed interface ResourceSiteWork permits ResourceSitePreparationJob, ResourceSiteHarvestJob {
    SubjectId id();
    SubjectId siteId();
    PhysicalIntentId intentId();
}
