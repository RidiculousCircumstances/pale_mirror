package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Field-owned physical read closure. The shared index selects checks; the
 * existing lifecycle validator remains the sole judge of their meaning. */
final class ResourceSitePhysicalValidation {
    private record Context(Map<PhysicalIntentId, PhysicalIntent> intents,
                           Map<PhysicalObservationId, PhysicalEffectObservation> observations) { }
    private static final ImmutableReferenceIndex.Source<Context, PhysicalIntentId, PhysicalIntent> INTENTS =
            new ImmutableReferenceIndex.Source<>(Context::intents);
    private static final ImmutableReferenceIndex.Source<Context, PhysicalObservationId, PhysicalEffectObservation> OBSERVATIONS =
            new ImmutableReferenceIndex.Source<>(Context::observations);
    private static final ImmutableReferenceIndex<Context, SubjectId, ResourceSiteLifecycle> VALIDATION =
            new ImmutableReferenceIndex<>(ResourceSiteState.MAX_SITES, 2 + ResourceSiteLifecycle.MAX_HARVEST_WORKERS * 5,
                    List.of(INTENTS, OBSERVATIONS), ResourceSitePhysicalValidation::reads,
                    (context, id, lifecycle) -> ResourceSitePhysicalIntentStateSupport.validateLifecycle(
                            context.intents(), context.observations(), lifecycle));

    static void validate(ResourceSiteState sites, Map<PhysicalIntentId, PhysicalIntent> intents,
                         Map<PhysicalObservationId, PhysicalEffectObservation> observations) {
        VALIDATION.validate(sites.declarations(), sites.sites(), new Context(intents, observations));
    }
    private static List<ImmutableReferenceIndex.Read<Context>> reads(Context context, ResourceSiteLifecycle lifecycle) {
        var result = new ArrayList<ImmutableReferenceIndex.Read<Context>>();
        includeIntent(context, result, ResourceSitePhysicalIntentStateSupport.intentId(lifecycle.siteId()));
        for (var job : lifecycle.harvestJobs().values()) {
            includeIntent(context, result, job.intentId());
            job.lastConfirmedBatch().ifPresent(batch -> result.add(OBSERVATIONS.at(batch.receipt().id())));
        }
        for (var lineage : lifecycle.harvestLineages().values()) if (lineage.receiptPending())
            result.add(INTENTS.at(lineage.predecessorIntentId()));
        return List.copyOf(result);
    }
    private static void includeIntent(Context context, List<ImmutableReferenceIndex.Read<Context>> reads, PhysicalIntentId id) {
        reads.add(INTENTS.at(id));
        var intent = context.intents().get(id);
        if (intent != null) intent.postconditionObservationId().ifPresent(observation -> reads.add(OBSERVATIONS.at(observation)));
    }
    private ResourceSitePhysicalValidation() { }
}
