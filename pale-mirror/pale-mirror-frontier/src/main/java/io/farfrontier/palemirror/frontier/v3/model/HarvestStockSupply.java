package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** A farmer commits only its current bounded hand-sized part, not the whole renewable field. */
final class HarvestStockSupply implements StockSupplyPort {
    private static final ImmutableKeyOrderView<SubjectId> ORDER = new ImmutableKeyOrderView<>(ResourceSiteState.MAX_SITES);
    private static final ImmutableInputView<List<Commitment>> VIEW = new ImmutableInputView<>();
    private static final ImmutableReferenceIndex.Source<FrontierWorldState, SubjectId,
            ResourceFieldCycle> CYCLES = new ImmutableReferenceIndex.Source<>(state -> state.resourceSites().cycles());
    private static final ImmutableReferenceIndex.Source<FrontierWorldState, SubjectId,
            ResourceSiteDeclaration> DECLARATIONS = new ImmutableReferenceIndex.Source<>(state -> state.resourceSites().declarations());
    private static final ImmutableKeyedInputView<SubjectId, Long> YIELDS =
            new ImmutableKeyedInputView<>(ResourceSiteState.MAX_SITES);
    private static final ImmutableReadProjection<FrontierWorldState, SubjectId,
            ResourceSiteLifecycle, List<Commitment>> SITES = new ImmutableReadProjection<>(ResourceSiteState.MAX_SITES, 2,
            List.of(CYCLES, DECLARATIONS), (state, site) -> site.harvestJobs().isEmpty() ? List.of()
                    : List.of(CYCLES.at(site.siteId()), DECLARATIONS.at(site.siteId())),
            HarvestStockSupply::readSite);
    @Override public Key key() { return Key.FIELD; }
    @Override public long inputDemand(FrontierWorldState state, SubjectId owner, String itemKind, long atTick) { return 0; }
    @Override public List<Commitment> pending(FrontierWorldState state) {
        // One changed crop/job invalidates only its own site's commitments.
        // Preserve the producer's iteration order when flattening the read image.
        var sites = state.resourceSites().sites();
        var commitments = SITES.project(state.bootstrap(), sites, state);
        var order = ORDER.get(sites);
        return VIEW.getPublished(List.of(order, commitments), () -> order.stream()
                .flatMap(id -> commitments.get(id).stream()).toList());
    }
    private static List<Commitment> readSite(FrontierWorldState state, ResourceSiteLifecycle site) {
        if (site.harvestJobs().isEmpty()) return List.of();
        var result = new ArrayList<Commitment>();
        var cycle = state.resourceSites().cycle(site.siteId());
        long unallocated = YIELDS.get(state.bootstrap(), site.siteId(), List.of(cycle), () -> cycle.layout().cells().stream()
                .filter(cell -> !cycle.cell(cell.id()).accounted() && cycle.cell(cell.id()).crop() == ResourceFieldCycle.Crop.MATURE)
                .mapToLong(cell -> FieldAgronomy.yield(state.bootstrap(), state.resourceSite(site.siteId()).kind(), cell.crop())).sum());
        for (var job : site.harvestJobs().values().stream().sorted(Comparator.comparing(ResourceSiteHarvestJob::id)).toList()) {
            if (job.progress().complete()) continue;
            int capacity = job.returningForBatch() ? (job.batchSuccessorSlot().isPresent() ? ActorCarriedResources.MAX_STACK_ITEMS : 0)
                    : ActorCarriedResources.MAX_STACK_ITEMS - job.undeliveredYieldQuantity();
            int remaining = (int) Math.min(capacity, unallocated);
            unallocated -= remaining;
            result.add(new Commitment(job.id(), state.resourceSite(job.siteId()).settlementId(), state.resourceSite(site.siteId()).kind().resourceKind(), remaining, 0));
        }
        return List.copyOf(result);
    }
}
