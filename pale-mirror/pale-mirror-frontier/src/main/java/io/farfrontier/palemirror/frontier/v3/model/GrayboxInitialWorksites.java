package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.geometry.RegionalBlockMap;
import java.util.HashMap;

/** Authored starting access infrastructure only, not later excavation/development. */
public final class GrayboxInitialWorksites {
    private GrayboxInitialWorksites() { }
    public static RegionalBlockMap<WorksiteBlock> compile(FrontierBootstrap bootstrap) {
        var cells = new HashMap<BlockPosition, WorksiteBlock>();
        for (var deposit : GrayboxQuarryPlan.compile(bootstrap).sites().deposits().values()) {
            for (var position : deposit.site().layout().fixedBlocks().keySet()) {
                var cell = ExtractionWorksiteBlocks.declaredAt(deposit, position).orElseThrow();
                // The container owner prepares its socket through the runtime hand-off.
                if (cell.key().role() == WorksiteBlock.Role.CONTAINER_SOCKET) continue;
                var previous = cells.putIfAbsent(position, cell);
                if (previous != null && !previous.equals(cell))
                    throw new IllegalArgumentException("competing initial worksite declarations at " + position);
            }
        }
        return RegionalBlockMap.copyOf(cells);
    }
}
