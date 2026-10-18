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
        var accepted = new HashMap<SurfaceAnchor, FieldDevelopmentCell>();
        var result = ReachableSurfaceArea.select(geometry, starts, starts.getFirst(), rules.searchRadius(), limit,
                Math.min(65536, Math.multiplyExact(rules.maximumCells(), 4)), surface -> {
                    var soil = knowledge.at(surface.support()); var crop = knowledge.at(surface.support().offset(0, 1, 0));
                    if (soil.isEmpty() || crop.isEmpty() || !geometry.clearStation(surface)) return false;
                    try { accepted.put(surface, new FieldDevelopmentCell(surface, soil.orElseThrow(), crop.orElseThrow())); return true; }
                    catch (IllegalArgumentException unsuitable) { return false; }
                });
        return result.selected().stream().map(accepted::get).toList();
    }
    private FieldDevelopmentLand() { }
}
