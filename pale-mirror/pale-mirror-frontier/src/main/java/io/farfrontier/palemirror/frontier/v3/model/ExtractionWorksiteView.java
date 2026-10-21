package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.extraction.ExtractionDeposit;
import io.farfrontier.palemirror.frontier.v3.model.geometry.RegionalBlockMap;
import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianGeometryRevision.Region;
import java.util.*;

/** Derived current worksite facts; unchanged declarations and regions retain their identity. */
public final class ExtractionWorksiteView {
    private record Site(ExtractionDeposit deposit, RegionalBlockMap<WorksiteBlock> blocks) { }
    private FrontierBootstrap bootstrap;
    private final Map<SubjectId, Site> sites = new HashMap<>();
    private RegionalBlockMap<WorksiteBlock> result = RegionalBlockMap.copyOf(Map.of());

    public RegionalBlockMap<WorksiteBlock> current(FrontierWorldState state) {
        if (bootstrap != state.bootstrap()) { sites.clear(); result = RegionalBlockMap.copyOf(Map.of()); bootstrap = state.bootstrap(); }
        var changed = new HashSet<Region>();
        for (var id : new ArrayList<>(sites.keySet())) if (!state.extractionSites().deposits().containsKey(id))
            changed.addAll(sites.remove(id).blocks().regions());
        for (var deposit : state.extractionSites().deposits().values()) {
            var previous = sites.get(deposit.site().id());
            if (previous != null && previous.deposit() == deposit) continue;
            var blocks = update(previous, deposit);
            changed.addAll(blocks.changesFrom(previous == null ? RegionalBlockMap.copyOf(Map.of()) : previous.blocks()));
            sites.put(deposit.site().id(), new Site(deposit, blocks));
        }
        var replacements = new HashMap<Region, Map<BlockPosition, WorksiteBlock>>();
        for (var region : changed) {
            var cells = new HashMap<BlockPosition, WorksiteBlock>();
            for (var site : sites.values()) site.blocks().in(region).forEach((position, block) -> {
                if (cells.putIfAbsent(position, block) != null) throw new IllegalStateException("overlapping worksite declarations");
            });
            replacements.put(region, cells);
        }
        return result = result.replace(replacements);
    }

    private static RegionalBlockMap<WorksiteBlock> update(Site previous, ExtractionDeposit deposit) {
        if (previous == null || previous.deposit().site().layout() != deposit.site().layout()) {
            var all = new HashMap<BlockPosition, WorksiteBlock>();
            for (var cell : ExtractionWorksiteBlocks.declared(deposit))
                all.put(cell.position(), ExtractionWorksiteBlocks.current(deposit, cell));
            return RegionalBlockMap.copyOf(all);
        }
        var changes = new HashMap<BlockPosition, WorksiteBlock>();
        if (previous.deposit().cells() != deposit.cells()) for (var entry : deposit.cells().entrySet()) {
            if (entry.getValue().equals(previous.deposit().cells().get(entry.getKey()))) continue;
            var position = deposit.site().layout().require(entry.getKey()).source();
            changes.put(position, ExtractionWorksiteBlocks.current(deposit, previous.blocks().get(position)));
        }
        if (previous.deposit().geometry() != deposit.geometry()) {
            var positions = new HashSet<>(deposit.geometry().keySet()); positions.addAll(previous.deposit().geometry().keySet());
            for (var position : positions) if (!Objects.equals(deposit.geometry().get(position), previous.deposit().geometry().get(position))) {
                var declared = previous.blocks().get(position);
                var base = deposit.site().layout().fixedBlocks().get(position);
                if (base == null) base = deposit.site().layout().terrainSupports().get(position);
                if (declared.key().role() == WorksiteBlock.Role.CONTAINER_SOCKET)
                    base = new io.farfrontier.palemirror.frontier.v3.model.extraction.BlockExtraction.Block("minecraft:air", Map.of());
                changes.put(position, ExtractionWorksiteBlocks.current(deposit,
                        new WorksiteBlock(declared.key(), position, 1, Objects.requireNonNull(base))));
            }
        }
        return previous.blocks().with(changes);
    }
}
