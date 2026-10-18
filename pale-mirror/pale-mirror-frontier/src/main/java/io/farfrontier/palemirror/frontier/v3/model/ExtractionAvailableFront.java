package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.extraction.*;
import io.farfrontier.palemirror.frontier.v3.model.geometry.*;
import java.util.*;

/** Mining-owned constraints around generic adjacent block cuts; no actor or physical effect writer. */
final class ExtractionAvailableFront {
    private final FrontierWorldState state;
    private final ExtractionDeposit deposit;
    private final Map<TerrainColumn, SurfaceAnchor> current;
    private final Set<BlockPosition> occupied;
    private final AdjacentExcavationPlanner.FrontSelection terraces;

    ExtractionAvailableFront(FrontierWorldState state, ExtractionDeposit deposit) {
        this.state = state; this.deposit = deposit;
        var geometry = KnownPedestrianRouteKnowledge.forFrontier(state).geometry();
        current = new ExtractionWorkingSurfaces(state, deposit).availableFront().entrySet().stream()
                .filter(entry -> !geometry.blocked(entry.getValue()))
                .collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
        var held = new HashSet<BlockPosition>();
        state.actorLocations().values().forEach(actor -> held.add(actor.supportingSurface().support()));
        state.extractionSites().work().values().stream().filter(job -> job.siteId().equals(deposit.site().id()))
                .flatMap(job -> job.target().stream()).forEach(target -> held.add(
                        deposit.site().layout().require(target.key().cell()).workstation().support()));
        // Unfinished declared work also retains its position even before a worker claims it.
        deposit.site().layout().cells().stream().filter(cell -> deposit.cells().get(cell.id()).disposition()
                == ExtractionDeposit.Disposition.PRESENT).forEach(cell -> held.add(cell.workstation().support()));
        occupied = Set.copyOf(held);
        var rules = state.bootstrap().ruleset().extraction();
        terraces = rules.terraces().map(value -> value.selection(deposit.site().layout().storagePort().y(),
                current.values(), ignored -> true)).orElseGet(AdjacentExcavationPlanner::adjacentSelection);
    }
    Collection<SurfaceAnchor> stations() { return current.values(); }
    AdjacentExcavationPlanner.FrontSelection selection() {
        return new AdjacentExcavationPlanner.FrontSelection() {
            @Override public List<SurfaceAnchor> order(Collection<SurfaceAnchor> surfaces) { return terraces.order(surfaces); }
            @Override public List<Integer> floorOffsets(SurfaceAnchor station, int dx, int dz) { return terraces.floorOffsets(station, dx, dz); }
            @Override public int clearanceTopY(SurfaceAnchor station, SurfaceAnchor floor) { return terraces.clearanceTopY(station, floor); }
            @Override public boolean retainsColumn(SurfaceAnchor surface) {
                return deposit.site().layout().fixedBlocks().containsKey(surface.support());
            }
        };
    }
    boolean executable(AdjacentExcavationPlanner.Column<BlockExtraction.Block> column) {
        var rules = state.bootstrap().ruleset().extraction();
        boolean work = false;
        for (int height = 1; height <= column.clearance().size(); height++) {
            var block = column.clearance().get(height - 1).block();
            var position = column.floor().support().offset(0, height, 0);
            if (block.kind().equals("minecraft:air")) continue;
            if (rules.definitionFor(block).isEmpty() || occupied.contains(position)
                    || !BlockInteractionReach.VANILLA_ADULT_PEDESTRIAN.contains(column.station(), position)) return false;
            work = true;
        }
        return work && WorkingSurfaceTransition.preservesAccess(column.floor(), current, ignored -> true, occupied);
    }
}
