package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Durable claim to prepare the fixed physical soil/crop footprint of one unloaded-or-loaded field. */
public record ResourceSitePreparationJob(SubjectId id, SubjectId siteId, PhysicalIntentId intentId) implements ResourceSiteWork {
    public ResourceSitePreparationJob {
        Objects.requireNonNull(id, "resource-site preparation id"); Objects.requireNonNull(siteId, "resource-site preparation site id");
        Objects.requireNonNull(intentId, "resource-site preparation intent id");
        if (!id.value().startsWith("job:site-prepare-") || !siteId.value().startsWith("site:") || !intentId.value().startsWith("intent:site-prepare-")) {
            throw new IllegalArgumentException("resource-site preparation identities must use canonical namespaces");
        }
    }
}
