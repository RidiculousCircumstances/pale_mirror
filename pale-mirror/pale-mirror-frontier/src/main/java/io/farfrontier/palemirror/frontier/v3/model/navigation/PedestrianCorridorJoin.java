package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;

/** Cooperative proof validation and two local connections to a previously proven corridor. */
final class PedestrianCorridorJoin {
    private final PedestrianCorridorCache.Corridor corridor;
    private PedestrianRouteGeometry geometry;
    private final PedestrianRegionCache cache;
    private final SurfaceAnchor start, target;
    private final BiConsumer<PedestrianRegion.Tile, Object> observed;
    private final Iterator<Map.Entry<PedestrianRegion.Tile, Object>> validation;
    private PedestrianRegion first, last;
    private PedestrianRegion.Refinement prefix, suffix;
    private final PedestrianPathComposition.LoopErasure assembled = new PedestrianPathComposition.LoopErasure();
    private List<SurfaceAnchor> result;
    private boolean finished;
    private int part, cursor;
    private final PedestrianRouteExtent extent;

    PedestrianCorridorJoin(PedestrianCorridorCache.Corridor corridor, PedestrianRouteGeometry geometry,
                          PedestrianRegionCache cache, SurfaceAnchor start, SurfaceAnchor target,
                          BiConsumer<PedestrianRegion.Tile, Object> observed, PedestrianRouteExtent extent) {
        this.corridor = corridor; this.geometry = geometry; this.cache = cache;
        this.start = start; this.target = target; this.observed = observed;
        this.extent = extent;
        validation = corridor.dependencies().entrySet().iterator();
    }
    void rebase(PedestrianRouteGeometry geometry) { this.geometry = geometry; }

    int advance(int budget, BooleanSupplier mayContinue) {
        int work = 0;
        while (!finished && work < budget && mayContinue.getAsBoolean()) {
            if (validation.hasNext()) {
                var entry = validation.next(); work++;
                Object current = revision(entry.getKey()); observed.accept(entry.getKey(), current);
                if (!Objects.equals(entry.getValue(), current)) finished = true;
                continue;
            }
            if (first == null || last == null) {
                var tile = PedestrianRegion.Tile.at(first == null ? start : target);
                observed.accept(tile, revision(tile));
                var lookup = cache.region(geometry, tile, budget - work, mayContinue); work += lookup.work();
                if (lookup.limited()) { finished = true; continue; }
                if (lookup.region() == null) break;
                if (first == null) first = lookup.region(); else last = lookup.region();
                continue;
            }
            if (prefix == null) {
                work++;
                var head = corridor.route().getFirst(); var tail = corridor.route().getLast();
                if (first.component(start) < 0 || first.component(start) != first.component(head)
                        || last.component(target) < 0 || last.component(target) != last.component(tail)) {
                    finished = true; continue;
                }
                prefix = first.refine(start, head); suffix = last.refine(tail, target); continue;
            }
            if (prefix.result() == null) { work += prefix.advance(Math.min(256, budget - work)); continue; }
            if (suffix.result() == null) { work += suffix.advance(Math.min(256, budget - work)); continue; }
            work++;
            if (part == 3) { result = assembled.result(); finished = true; continue; }
            var source = part == 0 ? prefix.result() : part == 1 ? corridor.route() : suffix.result();
            if (cursor == source.size()) { part++; cursor = 1; continue; }
            if (assembled.size() == HierarchicalPedestrianSearch.MAX_ROUTE_SURFACES) { finished = true; continue; }
            if (assembled.appendStep(source.get(cursor))) cursor++;
            if (!extent.equals(PedestrianRouteExtent.COMPLETE) && assembled.size() == extent.maximumSurfaces()) {
                result = assembled.result(); finished = true;
            }
        }
        return work;
    }
    private Object revision(PedestrianRegion.Tile tile) {
        return geometry.regionalRevision().orElseThrow().at(tile.x(), tile.z());
    }
    boolean finished() { return finished; }
    List<SurfaceAnchor> result() { return result; }
}
