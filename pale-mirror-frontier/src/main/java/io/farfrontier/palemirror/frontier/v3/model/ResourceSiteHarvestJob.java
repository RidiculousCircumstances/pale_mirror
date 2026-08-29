package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** One exact harvest: a named farmer turns all named mature slots into one named 64-wheat output stack. */
public record ResourceSiteHarvestJob(SubjectId id, SubjectId taskId, SubjectId siteId, SubjectId workerId, SubjectId outputItemId,
                                     InventoryCustody.ContainerSlot outputSlot, PhysicalIntentId intentId) implements ResourceSiteWork {
    public ResourceSiteHarvestJob {
        Objects.requireNonNull(id, "resource-site harvest id"); Objects.requireNonNull(taskId, "resource-site harvest task id");
        Objects.requireNonNull(siteId, "resource-site harvest site id");
        Objects.requireNonNull(workerId, "resource-site harvest worker id"); Objects.requireNonNull(outputItemId, "resource-site harvest output item id");
        Objects.requireNonNull(outputSlot, "resource-site harvest output slot"); Objects.requireNonNull(intentId, "resource-site harvest intent id");
        if (!id.value().startsWith("job:site-harvest-") || !taskId.value().startsWith("task:") || !siteId.value().startsWith("site:") || !workerId.value().startsWith("resident:")
                || !outputItemId.value().startsWith("item:site-harvest-") || !intentId.value().startsWith("intent:site-harvest-")) {
            throw new IllegalArgumentException("resource-site harvest identities must use canonical namespaces");
        }
    }
}
