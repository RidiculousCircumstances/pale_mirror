package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.extraction.ExtractionDeposit;
import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianGeometryRevision.Region;
import java.util.*;

/** Immutable region declarations are cheap; exact block facts materialize only on demand. */
public final class ExtractionWorksiteView {
    private record Site(ExtractionDeposit deposit, Map<Region, List<WorksiteBlock>> regions) { }
    private FrontierBootstrap bootstrap;
    private final Map<SubjectId, Site> sites = new TreeMap<>();
    private WorksiteRegionIndex index = new WorksiteRegionIndex();

    public Map<Region, List<WorksiteBlock>> current(FrontierWorldState state) {
        if (bootstrap != state.bootstrap()) { sites.clear(); index = new WorksiteRegionIndex(); bootstrap = state.bootstrap(); }
        sites.keySet().retainAll(state.extractionSites().deposits().keySet());
        for (var deposit : state.extractionSites().deposits().values()) {
            var previous = sites.get(deposit.site().id());
            if (previous == null || previous.deposit() != deposit)
                sites.put(deposit.site().id(), new Site(deposit, update(previous, deposit)));
        }
        return index.update(sites.values().stream().map(Site::regions).toList());
    }
    private static Map<Region, List<WorksiteBlock>> update(Site previous, ExtractionDeposit deposit) {
        var layout = deposit.site().layout();
        var changed = new HashSet<Region>();
        if (previous == null) changed.addAll(layout.worksitePositions().keySet());
        else {
            var old = previous.deposit(); var prior = old.site().layout();
            if (prior != layout) {
                changedRegions(prior.fixedBlocks(), layout.fixedBlocks(), changed);
                changedRegions(prior.terrainSupports(), layout.terrainSupports(), changed);
                changedRegions(prior.infrastructureIds(), layout.infrastructureIds(), changed);
                changedRegions(prior.terrainIds(), layout.terrainIds(), changed);
                changedRegions(prior.cellsByPosition(), layout.cellsByPosition(), changed);
                if (!prior.container().equals(layout.container())) {
                    changed.add(Region.at(prior.container())); changed.add(Region.at(layout.container()));
                }
            }
            if (old.cells() != deposit.cells()) {
                old.cells().forEach((id, cell) -> {
                    if (!cell.equals(deposit.cells().get(id))) changed.add(Region.at(prior.require(id).source()));
                });
                deposit.cells().forEach((id, cell) -> {
                    if (!cell.equals(old.cells().get(id))) changed.add(Region.at(layout.require(id).source()));
                });
            }
            changedRegions(old.geometry(), deposit.geometry(), changed);
        }
        if (changed.isEmpty()) return previous.regions();
        var next = new HashMap<>(previous == null ? Map.<Region, List<WorksiteBlock>>of() : previous.regions());
        for (var region : changed) {
            var positions = layout.worksitePositions().get(region);
            if (positions == null) next.remove(region);
            else next.put(region, new DeferredImmutableList<>(() -> WorksiteRegionIndex.ordered(positions.stream()
                    .map(position -> ExtractionWorksiteBlocks.declaredAt(deposit, position).orElseThrow())
                    .map(declared -> ExtractionWorksiteBlocks.current(deposit, declared)).toList())));
        }
        return Collections.unmodifiableMap(next);
    }
    private static void changedRegions(Map<BlockPosition, ?> before, Map<BlockPosition, ?> after, Set<Region> changed) {
        if (before == after) return;
        before.forEach((position, value) -> { if (!value.equals(after.get(position))) changed.add(Region.at(position)); });
        after.forEach((position, value) -> { if (!value.equals(before.get(position))) changed.add(Region.at(position)); });
    }
}
