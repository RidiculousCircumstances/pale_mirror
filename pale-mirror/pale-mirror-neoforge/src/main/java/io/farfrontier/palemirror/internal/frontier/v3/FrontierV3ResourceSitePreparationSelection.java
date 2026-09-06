package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntent;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.model.FrontierResourceSitePlan;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.ResourceSite;

import java.util.Comparator;
import java.util.Optional;
import java.util.function.Predicate;

/** Pure loaded-world admission policy for one bounded preparation executor pass. */
final class FrontierV3ResourceSitePreparationSelection {
    private FrontierV3ResourceSitePreparationSelection() { }

    /**
     * The queue has a stable order, but an unloaded head must not block an
     * unrelated naturally loaded field. The predicate observes only existing
     * chunk state; selection itself cannot request a chunk load.
     */
    static Optional<PhysicalIntent> nextLoaded(FrontierWorldState state, Predicate<ResourceSite> isLoaded) {
        var sites = FrontierResourceSitePlan.compile(state.bootstrap());
        return state.physicalIntents().values().stream().sorted(Comparator.comparing(PhysicalIntent::id))
                .filter(intent -> intent.kind() == PhysicalIntentKind.RESOURCE_SITE_PREPARATION)
                .filter(intent -> intent.status() == PhysicalIntentStatus.PREPARED || intent.status() == PhysicalIntentStatus.RUNNING)
                .filter(intent -> {
                    ResourceSite site = sites.get(intent.causeSubjectId());
                    return site != null && isLoaded.test(site);
                })
                .findFirst();
    }
}
