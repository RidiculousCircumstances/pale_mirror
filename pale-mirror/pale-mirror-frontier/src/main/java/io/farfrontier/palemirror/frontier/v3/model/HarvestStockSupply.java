package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;

/** A farmer commits only its current bounded hand-sized part, not the whole renewable field. */
final class HarvestStockSupply implements StockSupplyPort {
    private static final ImmutableInputView<List<Commitment>> VIEW = new ImmutableInputView<>();
    @Override public Key key() { return Key.FIELD; }
    @Override public long inputDemand(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.SubjectId owner, String itemKind) { return 0; }
    @Override public List<Commitment> pending(FrontierWorldState state) {
        // Reservations/prices change many immutable trade drafts, but cannot
        // change crops or harvest jobs. Reuse only the exact field-owner inputs.
        return VIEW.get(List.of(state.resourceSites(), state.bootstrap()), () -> read(state));
    }
    private static List<Commitment> read(FrontierWorldState state) {
        var result = new ArrayList<Commitment>();
        for (var site : state.resourceSites().sites().values()) {
            if (site.harvestJobs().isEmpty()) continue;
            var cycle = state.resourceSites().cycle(site.siteId());
            long unallocated = cycle.layout().cells().stream().filter(cell -> !cycle.cell(cell.id()).accounted()
                    && cycle.cell(cell.id()).crop() == ResourceFieldCycle.Crop.MATURE)
                    .mapToLong(cell -> FieldAgronomy.yield(state.bootstrap(), cell.crop())).sum();
            for (var job : site.harvestJobs().values().stream().sorted(Comparator.comparing(ResourceSiteHarvestJob::id)).toList()) {
            if (job.progress().complete()) continue;
            int capacity = job.returningForBatch() ? (job.batchSuccessorSlot().isPresent() ? ActorCarriedResources.MAX_STACK_ITEMS : 0)
                    : ActorCarriedResources.MAX_STACK_ITEMS - job.undeliveredYieldQuantity();
            int remaining = (int) Math.min(capacity, unallocated);
            unallocated -= remaining;
            result.add(new Commitment(job.id(), state.resourceSite(job.siteId()).settlementId(), "minecraft:wheat", remaining, 0));
            }
        }
        return List.copyOf(result);
    }
}
