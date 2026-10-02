package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierEvent;

import java.util.Objects;
import java.util.Optional;

/** Read-only common-correlation lookup and canonical-event retention for field harvest traces. */
public final class ResourceSiteHarvestTrace {
    private ResourceSiteHarvestTrace() { }
    public static Optional<RetainedDiagnosticTrace> lookup(FrontierWorldState state, String correlation) {
        Objects.requireNonNull(state, "harvest trace state"); Objects.requireNonNull(correlation, "harvest trace correlation");
        return state.resourceSites().sites().values().stream().flatMap(site -> site.harvestLineages().values().stream())
                .map(lineage -> lineage.causality().trace()).filter(trace -> trace.correlation().equals(correlation)).findFirst();
    }
    public static FrontierWorldState retain(FrontierWorldState state, FrontierEvent event) {
        // The lineage is created by the COLD terminal itself. Earlier crop events cannot
        // attach to a lineage that did not exist yet; retain the actual terminal event as
        // the cold causal boundary once that reducer has published it.
        if (event.payload() instanceof ResourceSiteHarvestReturned returned) {
            return update(state, event.subject(), returned.jobId(), trace -> trace.cold(event));
        }
        if (event.payload() instanceof ResourceSiteHarvestColdTraversalAdvanced advanced) {
            return update(state, event.subject(), advanced.jobId(), trace -> trace.cold(event));
        }
        if (event.payload() instanceof ResourceSiteHarvestColdGoalAdvanced advanced) {
            return update(state, event.subject(), advanced.jobId(), trace -> trace.cold(event));
        }
        if (event.payload() instanceof ResourceSiteHarvestProgressed progressed
                && state.resourceSites().sites().values().stream()
                        .anyMatch(lifecycle -> lifecycle.harvestLineages().values().stream()
                                .anyMatch(lineage -> lineage.predecessorJobId().equals(progressed.jobId())))) {
            return update(state, event.subject(), progressed.jobId(), trace -> trace.cold(event));
        }
        if (event.payload() instanceof PhysicalIntentTransition transition
                && transition.observation().orElse(null) instanceof ResourceSiteHarvestObservation observation) {
            return updateIntent(state, event.subject(), observation.intentId(), trace -> trace.observed(observation.id(), event));
        }
        if (event.payload() instanceof PhysicalIntentTransition transition
                && transition.observation().orElse(null) instanceof ResourceSiteHarvestDeliveryObservation observation) {
            return updateIntent(state, event.subject(), observation.intentId(), trace -> trace.observed(observation.id(), event));
        }
        if (event.payload() instanceof PhysicalIntentTransition transition
                && transition.observation().orElse(null) instanceof ResourceSiteHarvestDeferredObservation observation) {
            return updateIntent(state, event.subject(), observation.intentId(), trace -> trace.observed(observation.id(), event));
        }
        return state;
    }
    private static FrontierWorldState updateIntent(FrontierWorldState state,
            io.farfrontier.palemirror.frontier.v3.api.SubjectId site,
            io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId intent,
            java.util.function.UnaryOperator<RetainedDiagnosticTrace> update) {
        ResourceSiteLifecycle lifecycle = state.resourceSites().sites().get(site);
        if (lifecycle == null) return state;
        var lineage = lifecycle.harvestLineage(intent);
        if (lineage.isEmpty()) return state;
        return state.withResourceSites(state.resourceSites().replace(lifecycle.withHarvestTrace(intent,
                update.apply(lineage.orElseThrow().causality().trace()))));
    }
    private static FrontierWorldState update(FrontierWorldState state,
            io.farfrontier.palemirror.frontier.v3.api.SubjectId site,
            io.farfrontier.palemirror.frontier.v3.api.SubjectId job,
            java.util.function.UnaryOperator<RetainedDiagnosticTrace> update) {
        ResourceSiteLifecycle lifecycle = state.resourceSites().sites().get(site);
        if (lifecycle == null) return state;
        var lineage = lifecycle.harvestLineages().values().stream()
                .filter(value -> value.predecessorJobId().equals(job))
                .reduce((left, right) -> { throw new IllegalArgumentException("field trace has competing exact job histories"); });
        if (lineage.isEmpty()) return state;
        return state.withResourceSites(state.resourceSites().replace(lifecycle.withHarvestTrace(
                lineage.orElseThrow().predecessorIntentId(), update.apply(lineage.orElseThrow().causality().trace()))));
    }
}
