package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.geometry.*;
import java.util.*;

/** Agriculture's suitability predicate over shared geometry and reachable-area discovery. No mutation or forced chunks. */
final class FieldDevelopmentLand {
    static List<FieldDevelopmentCell> read(FrontierWorldState state, SubjectId home, List<ResourceSite> fields, int limit) {
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
