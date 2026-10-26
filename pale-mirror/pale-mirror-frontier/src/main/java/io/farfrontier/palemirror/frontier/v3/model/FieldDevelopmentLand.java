package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.geometry.*;
import java.util.*;

/** Agriculture's suitability predicate over shared geometry and reachable-area discovery. No mutation or forced chunks. */
final class FieldDevelopmentLand {
    /** Opaque reachability/suitability result tied to one exact immutable world input. */
    static final class Survey {
        private final FrontierWorldState state; private final SubjectId home;
        private final List<ResourceSite> fields; private final List<FieldDevelopmentCell> cells;
        private Survey(FrontierWorldState state, SubjectId home, List<ResourceSite> fields, List<FieldDevelopmentCell> cells) {
            this.state = state; this.home = home; this.fields = List.copyOf(fields); this.cells = List.copyOf(cells);
        }
        List<FieldDevelopmentCell> cells() { return cells; }
        Survey select(List<FieldDevelopmentCell> selected) {
            if (!new HashSet<>(cells).containsAll(selected) || new HashSet<>(selected).size() != selected.size())
                throw new IllegalArgumentException("land selection contains unsurveyed or duplicate cells");
            return new Survey(state, home, fields, selected);
        }
        void require(FrontierWorldState current, SubjectId owner, List<ResourceSite> sourceFields) {
            if (state != current || !home.equals(owner) || !fields.equals(sourceFields))
                throw new IllegalArgumentException("land admission has stale or foreign survey evidence");
        }
    }
    static Survey survey(FrontierWorldState state, SubjectId home, List<ResourceSite> fields, int limit) {
        if (fields.stream().map(ResourceSite::id).distinct().count() != fields.size()
                || fields.stream().anyMatch(field -> !field.settlementId().equals(home)
                    || !field.equals(state.resourceSiteDescriptors().get(field.id()))))
            throw new IllegalArgumentException("field survey has stale or foreign declared sources");
        return new Survey(state, home, fields, discover(state, home, fields, limit));
    }
    static List<FieldDevelopmentCell> read(FrontierWorldState state, SubjectId home, List<ResourceSite> fields, int limit) {
        return survey(state, home, fields, limit).cells();
    }
    private static List<FieldDevelopmentCell> discover(FrontierWorldState state, SubjectId home, List<ResourceSite> fields, int limit) {
        if (limit <= 0 || fields.isEmpty()) return List.of();
        var rules = state.bootstrap().ruleset().fields().cultivation().orElseThrow();
        var knowledge = new DevelopmentGroundKnowledge(state);
        var geometry = KnownPedestrianRouteKnowledge.forSettlement(state, home, List.of()).geometry();
        var starts = fields.stream().flatMap(field -> field.layout().cells().stream())
                .map(ResourceFieldLayout.Cell::workstation).toList();
        // Suitability is cheaper than a body-clearance flood. An entirely rocky
        // search area needs no reachability calculation. This is only a negative
        // filter: every admitted candidate still passes the shared walking graph.
        var accepted = new HashMap<SurfaceAnchor, FieldDevelopmentCell>();
        var origin = starts.getFirst();
        boolean nearby = false;
        for (var start : starts) {
            for (int[] offset : new int[][]{{-1, 0}, {0, -1}, {0, 1}, {1, 0}})
                for (var surface : geometry.surfacesAt(start.x() + offset[0], start.z() + offset[1]))
                    if (candidate(knowledge, surface).isPresent()) { nearby = true; break; }
            if (nearby) break;
        }
        // Preserve the cheap early exit for fertile fields; a complete suitability
        // scan is useful only when the immediate frontier offers no land.
        if (!nearby) {
            int samples = 0;
            survey:
            for (int x = origin.x() - rules.searchRadius(); x <= origin.x() + rules.searchRadius(); x++)
                for (int z = origin.z() - rules.searchRadius(); z <= origin.z() + rules.searchRadius(); z++) {
                    if (++samples > 65536) { nearby = true; break survey; }
                    for (var surface : geometry.surfacesAt(x, z)) {
                        if (++samples > 65536) { nearby = true; break survey; }
                        candidate(knowledge, surface).ifPresent(cell -> accepted.put(surface, cell));
                    }
                }
            // Exhaustion is unknown, not proof of unsuitable land.
            if (!nearby && accepted.isEmpty()) return List.of();
        }
        int selectedLimit = nearby ? limit : Math.min(limit, accepted.size());
        var result = ReachableSurfaceArea.select(geometry, starts, origin, rules.searchRadius(), selectedLimit,
                Math.min(65536, Math.multiplyExact(rules.maximumCells(), 4)), surface -> {
                    var cell = candidate(knowledge, surface);
                    if (cell.isEmpty() || !geometry.clearStation(surface)) return false;
                    accepted.put(surface, cell.orElseThrow()); return true;
                });
        return result.selected().stream().map(accepted::get).toList();
    }
    private static Optional<FieldDevelopmentCell> candidate(DevelopmentGroundKnowledge knowledge, SurfaceAnchor surface) {
        var soil = knowledge.at(surface.support()); var crop = knowledge.at(surface.support().offset(0, 1, 0));
        if (soil.isPresent() && crop.isPresent() && FieldDevelopmentCell.suitable(soil.orElseThrow(), crop.orElseThrow())) {
            return Optional.of(new FieldDevelopmentCell(surface, soil.orElseThrow(), crop.orElseThrow()));
        }
        return Optional.empty();
    }
    private FieldDevelopmentLand() { }
}
