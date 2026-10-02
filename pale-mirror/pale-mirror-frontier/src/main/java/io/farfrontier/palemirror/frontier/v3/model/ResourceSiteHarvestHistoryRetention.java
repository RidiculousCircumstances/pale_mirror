package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Set;
import java.util.stream.Collectors;

/** Field-owned retirement proof. Receipt resolution alone does not release scene custody. */
public final class ResourceSiteHarvestHistoryRetention {
    private ResourceSiteHarvestHistoryRetention() { }
    public static Set<PhysicalIntentId> reclaimable(FrontierWorldState state,
                                                    ResourceSiteLifecycle lifecycle, SubjectId worker) {
        return lifecycle.harvestLineages().values().stream()
                .filter(history -> history.workerId().equals(worker) && history.outputReceiptResolved()
                        && history.successorJobId().isEmpty())
                .filter(history -> {
                    var intent = state.physicalIntents().get(history.predecessorIntentId());
                    return intent == null || intent.status() == PhysicalIntentStatus.CONFIRMED;
                })
                .filter(history -> state.sceneLeases().values().stream()
                        .filter(FrontierSceneBehaviors::isResourceSiteHarvest)
                        .noneMatch(lease -> lease.status() != SceneLeaseStatus.CLOSED
                                && FrontierSceneBehaviors.resourceSiteHarvest(lease).siteId().equals(lifecycle.siteId())
                                && FrontierSceneBehaviors.resourceSiteHarvest(lease).jobId().equals(history.predecessorJobId())))
                .map(ResourceSiteHarvestLineage::predecessorIntentId).collect(Collectors.toUnmodifiableSet());
    }
}
