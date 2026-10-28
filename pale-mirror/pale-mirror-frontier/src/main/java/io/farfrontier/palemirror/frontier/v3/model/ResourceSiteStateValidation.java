package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Complete field-register invariants, updated from exact immutable row dependencies. */
final class ResourceSiteStateValidation {
    private record Inputs(Map<SubjectId, ResourceFieldCycle> cycles) { }
    private static final ImmutableKeyOrderView<SubjectId> SITE_KEYS = new ImmutableKeyOrderView<>(ResourceSiteState.MAX_SITES);
    private static final ImmutableKeyOrderView<SubjectId> CYCLE_KEYS = new ImmutableKeyOrderView<>(ResourceSiteState.MAX_SITES);
    private static final ImmutableKeyOrderView<SubjectId> DECLARATION_KEYS = new ImmutableKeyOrderView<>(ResourceSiteState.MAX_SITES);
    private static final ImmutableInvariantCheck<Void> MEMBERSHIP = new ImmutableInvariantCheck<>();
    private static final ImmutableInvariantCheck<Void> DECLARATIONS = new ImmutableInvariantCheck<>();
    private static final ImmutableGroupView<SubjectId, ResourceSiteLifecycle, SubjectId> JOB_IDENTITIES = new ImmutableGroupView<>();
    private static final ImmutableReferenceIndex.Source<Inputs, SubjectId, ResourceFieldCycle> CYCLES =
            new ImmutableReferenceIndex.Source<>(Inputs::cycles);
    private static final ImmutableReferenceIndex<Inputs, SubjectId, ResourceSiteLifecycle> SITES = new ImmutableReferenceIndex<>(
            ResourceSiteState.MAX_SITES, 1, List.of(CYCLES), (input, site) -> List.of(CYCLES.at(site.siteId())),
            (input, key, site) -> validateSite(key, site, input.cycles().get(key)));
    private ResourceSiteStateValidation() { }

    static void validate(Map<SubjectId, ResourceSiteDeclaration> declarations, Map<SubjectId, ResourceSiteLifecycle> sites,
            Map<SubjectId, ResourceFieldCycle> cycles, Map<CellMutationKey, ResourceFieldCellObserved> pendingWorldChanges,
            Map<CellMutationKey, ResourceFieldForeignChangeHeld> pendingForeignChanges) {
        if (sites.size() > ResourceSiteState.MAX_SITES) throw new IllegalArgumentException("every resource-site lifecycle needs one exact cell cycle");
        MEMBERSHIP.validate(() -> {
            if (!sites.keySet().equals(cycles.keySet()) || !sites.keySet().equals(declarations.keySet()))
                throw new IllegalArgumentException("every resource-site lifecycle needs one exact cell cycle");
        }, SITE_KEYS.get(sites), CYCLE_KEYS.get(cycles), DECLARATION_KEYS.get(declarations));
        DECLARATIONS.validate(() -> {
            if (declarations.entrySet().stream().anyMatch(entry -> !entry.getKey().equals(entry.getValue().id())))
                throw new IllegalArgumentException("resource-site declaration key differs from its exact owner");
        }, declarations);
        var declaredSites = sites.keySet();
        if (pendingWorldChanges.keySet().stream().anyMatch(key -> !declaredSites.contains(key.owner())))
            throw new IllegalArgumentException("world field change has no declared site owner");
        if (pendingForeignChanges.keySet().stream().anyMatch(key -> !declaredSites.contains(key.owner()))
                || pendingForeignChanges.keySet().stream().anyMatch(pendingWorldChanges::containsKey))
            throw new IllegalArgumentException("foreign field change has no unique declared site owner");
        if (!JOB_IDENTITIES.uniqueMemberships(ResourceSiteStateValidation.class, sites, site -> site.harvestJobs().keySet()))
            throw new IllegalArgumentException("duplicate field job identity");
        SITES.validate(ResourceSiteStateValidation.class, sites, new Inputs(cycles));
        for (var entry : pendingWorldChanges.entrySet()) {
            SubjectId siteId = entry.getKey().owner();
            ResourceFieldCellObserved observation = entry.getValue();
            ResourceFieldCycle cycle = cycles.get(siteId);
            if (!entry.getKey().equals(ResourceSiteState.mutationKey(observation.siteId(), observation.cellId())) || observation.source() != ResourceFieldCellObserved.Source.WORLD
                    || observation.change() == ResourceFieldCellObserved.Change.UNCHANGED
                    || observation.epoch() != cycle.epoch() || observation.layoutRevision() != cycle.layout().revision()
                    || !ResourceFieldPhysicalSurface.Condition.of(cycle.cell(observation.cellId())).equals(observation.before())
                    && !ResourceFieldPhysicalSurface.Condition.of(cycle.cell(observation.cellId())).equals(observation.after())
                    || cycle.pendingPlayerBreaks().containsKey(observation.cellId()))
                throw new IllegalArgumentException("world field hold has a stale or competing canonical predecessor");
        }
        for (var entry : pendingForeignChanges.entrySet()) {
            SubjectId siteId = entry.getKey().owner();
            ResourceFieldForeignChangeHeld held = entry.getValue();
            ResourceFieldCycle cycle = cycles.get(siteId);
            if (!entry.getKey().equals(ResourceSiteState.mutationKey(held.siteId(), held.cellId())) || held.epoch() != cycle.epoch()
                    || held.layoutRevision() != cycle.layout().revision()
                    || !held.before().equals(cycle.cell(held.cellId()))
                    && !ResourceSiteState.foreignTransition(held.before(), cycle.cell(held.cellId()))
                    || cycle.pendingPlayerBreaks().containsKey(held.cellId()))
                throw new IllegalArgumentException("foreign field hold has a stale or competing canonical predecessor");
        }
    }

    private static void validateSite(SubjectId key, ResourceSiteLifecycle lifecycle, ResourceFieldCycle cycle) {
        if (!key.equals(lifecycle.siteId())) throw new IllegalArgumentException("resource-site map key must match lifecycle identity");
        if (!key.equals(cycle.siteId()))
            throw new IllegalArgumentException("resource-site cell cycle declares another site owner");
        if (cycle.epoch() != lifecycle.growthEpoch())
            throw new IllegalArgumentException("resource-site cell cycle must share its lifecycle epoch");
        for (ResourceSiteHarvestJob job : lifecycle.harvestJobs().values()) {
            if (job.progress().totalCropSlots() != cycle.layout().cells().size())
                throw new IllegalArgumentException("field worker must retain its exact admitted layout size");
            if (job.navigationBlock().isPresent()
                    && job.navigationBlock().orElseThrow().layoutRevision() != cycle.layout().revision())
                throw new IllegalArgumentException("farmer's blocked goal has a foreign field layout revision");
            if (!job.progress().complete() && !job.returningForBatch()) {
                ResourceFieldLayout.CellId selected = cycle.layout().cells().get(job.progress().nextCropSlotIndex()).id();
                if (!job.target().current(cycle)
                        || !job.target().cellId().equals(selected))
                    throw new IllegalArgumentException("field target has a stale generation or disagrees with its work pool");
            }
        }
    }
}
