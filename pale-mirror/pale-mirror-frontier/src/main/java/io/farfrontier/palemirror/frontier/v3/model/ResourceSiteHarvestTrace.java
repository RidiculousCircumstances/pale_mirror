package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierEvent;

import java.util.Objects;
import java.util.Optional;

/** Read-only common-correlation lookup and canonical-event retention for field harvest traces. */
public final class ResourceSiteHarvestTrace {
    private ResourceSiteHarvestTrace() { }
    public static Optional<RetainedDiagnosticTrace> lookup(FrontierWorldState state, String correlation) {
        Objects.requireNonNull(state, "harvest trace state"); Objects.requireNonNull(correlation, "harvest trace correlation");
        return state.resourceSites().sites().values().stream().map(ResourceSiteLifecycle::harvestLineage).flatMap(Optional::stream)
                .map(lineage -> lineage.causality().trace()).filter(trace -> trace.correlation().equals(correlation)).findFirst();
    }
    public static FrontierWorldState retain(FrontierWorldState state, FrontierEvent event) {
        // The lineage is created by the COLD terminal itself. Earlier crop events cannot
        // attach to a lineage that did not exist yet; retain the actual terminal event as
        // the cold causal boundary once that reducer has published it.
        if (event.payload() instanceof ResourceSiteHarvestReturned returned) {
            return update(state, "resource-site-harvest:" + returned.jobId().value(), trace -> trace.cold(event));
        }
        if (event.payload() instanceof ResourceSiteHarvestColdTraversalAdvanced advanced) {
            return update(state, "resource-site-harvest:" + advanced.jobId().value(), trace -> trace.cold(event));
        }
        if (event.payload() instanceof ResourceSiteHarvestColdGoalAdvanced advanced) {
            return update(state, "resource-site-harvest:" + advanced.jobId().value(), trace -> trace.cold(event));
        }
        if (event.payload() instanceof ResourceSiteHarvestProgressed progressed
                && state.resourceSites().sites().values().stream()
                        .anyMatch(lifecycle -> lifecycle.harvestLineage().isPresent()
                                && lifecycle.harvestLineage().orElseThrow().predecessorJobId().equals(progressed.jobId()))) {
            return update(state, "resource-site-harvest:" + progressed.jobId().value(), trace -> trace.cold(event));
        }
        if (event.payload() instanceof PhysicalIntentTransition transition
                && transition.observation().orElse(null) instanceof ResourceSiteHarvestObservation observation) {
            return updateIntent(state, observation.intentId(), trace -> trace.observed(observation.id(), event));
        }
        if (event.payload() instanceof PhysicalIntentTransition transition
                && transition.observation().orElse(null) instanceof ResourceSiteHarvestDeliveryObservation observation) {
            return updateIntent(state, observation.intentId(), trace -> trace.observed(observation.id(), event));
        }
        if (event.payload() instanceof PhysicalIntentTransition transition
                && transition.observation().orElse(null) instanceof ResourceSiteHarvestDeferredObservation observation) {
            return updateIntent(state, observation.intentId(), trace -> trace.observed(observation.id(), event));
        }
        return state;
    }
    private static FrontierWorldState updateIntent(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId intent,
                                                   java.util.function.UnaryOperator<RetainedDiagnosticTrace> update) {
        for (ResourceSiteLifecycle lifecycle : state.resourceSites().sites().values()) {
            if (lifecycle.harvestLineage().isPresent() && lifecycle.harvestLineage().orElseThrow().predecessorIntentId().equals(intent)) {
                return state.withResourceSites(state.resourceSites().replace(lifecycle.withHarvestTrace(update.apply(lifecycle.harvestLineage().orElseThrow().causality().trace()))));
            }
        }
        return state;
    }
    private static FrontierWorldState update(FrontierWorldState state, String correlation, java.util.function.UnaryOperator<RetainedDiagnosticTrace> update) {
        for (ResourceSiteLifecycle lifecycle : state.resourceSites().sites().values()) {
            if (lifecycle.harvestLineage().isPresent() && lifecycle.harvestLineage().orElseThrow().causality().trace().correlation().equals(correlation)) {
                return state.withResourceSites(state.resourceSites().replace(lifecycle.withHarvestTrace(update.apply(lifecycle.harvestLineage().orElseThrow().causality().trace()))));
            }
        }
        return state;
    }
}
