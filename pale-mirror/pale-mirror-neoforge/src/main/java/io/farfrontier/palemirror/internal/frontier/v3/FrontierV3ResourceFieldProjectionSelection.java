package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldCycle;
import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldLayout;
import io.farfrontier.palemirror.frontier.v3.model.ResourceFieldPhysicalSurface;
import java.util.ArrayList;
import java.util.List;

/** Fair bounded discovery; unchanged cells do not consume a physical write slot. */
final class FrontierV3ResourceFieldProjectionSelection {
    private static final int MAX_CELL_PROBES_PER_TURN = 64;
    record Selection(List<ResourceFieldLayout.CellId> cells, int nextIndex, int probes) {
        Selection { cells = List.copyOf(cells); }
    }
    private FrontierV3ResourceFieldProjectionSelection() { }

    static Selection select(ResourceFieldCycle cycle, FrontierV3ResourceFieldWitness witness, int start) {
        if (!witness.matchesCycle(cycle))
            throw new IllegalArgumentException("projection discovery has a foreign field witness");
        var layout = cycle.layout().cells();
        int index = Math.floorMod(start, layout.size()), probes = 0;
        var selected = new ArrayList<ResourceFieldLayout.CellId>();
        int limit = Math.min(MAX_CELL_PROBES_PER_TURN, layout.size());
        while (probes < limit && selected.size() < FrontierV3ResourceSiteExecutor.projectionWriteBudget()) {
            var id = layout.get(index).id();
            var retained = witness.cell(id);
            var canonical = cycle.cell(id);
            boolean ownedGrowth = canonical.soil() == ResourceFieldCycle.Soil.FARMLAND
                    && (canonical.crop() == ResourceFieldCycle.Crop.GROWING
                        || canonical.crop() == ResourceFieldCycle.Crop.MATURE);
            if (ownedGrowth && !cycle.pendingPlayerBreaks().containsKey(id) && retained.foreign().isEmpty()
                    && (retained.pending().isPresent()
                        ? retained.pending().orElseThrow().canonicalSource().isPresent()
                        : !retained.committed().equals(ResourceFieldPhysicalSurface.Condition.of(canonical))))
                selected.add(id);
            probes++; index = (index + 1) % layout.size();
        }
        return new Selection(selected, index, probes);
    }
}
