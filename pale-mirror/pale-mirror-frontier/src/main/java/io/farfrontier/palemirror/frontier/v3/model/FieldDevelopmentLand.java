package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.geometry.*;
import java.util.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianGeometryRevision;
import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianGeometryRevision.Region;
import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteGeometry;

/** Agriculture's suitability predicate over shared geometry and reachable-area discovery. No mutation or forced chunks. */
final class FieldDevelopmentLand {
    private record Query(SubjectId home, int limit) { }
    private record Facts(FrontierBootstrap bootstrap, ImmutableDependencies fields,
            Object geometryVersion,
            Optional<PedestrianGeometryRevision> regional,
            Set<Region> regions,
            KnownBlockReadWitness<NavigationBlockState> blocks, List<FieldDevelopmentCell> cells) { }
    private static final int MAX_ENTRIES = 64, MAX_QUERY_READS = 16_384, MAX_RETAINED_READS = 131_072;
    private static final Map<Query, Facts> LAND = new LinkedHashMap<>(16, .75f, true);
    private static int retainedReads;
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
        if (limit <= 0 || fields.isEmpty()) return new Survey(state, home, fields, List.of());
        var knowledge = new DevelopmentGroundKnowledge(state);
        var geometry = KnownPedestrianRouteKnowledge.forSettlement(state, home, List.of()).geometry();
        var query = new Query(home, limit);
        Facts retained;
        synchronized (LAND) { retained = LAND.get(query); }
        List<FieldDevelopmentCell> cells;
        var currentRegional = geometry.regionalRevision();
        boolean sameGeometry = retained != null && (currentRegional.isPresent() && retained.regional().isPresent()
                ? currentRegional.orElseThrow().matches(retained.regional().orElseThrow(), retained.regions())
                : Objects.equals(geometry.version(), retained.geometryVersion()));
        if (retained != null && retained.bootstrap() == state.bootstrap()
                && retained.fields().matches(fields.toArray()) && sameGeometry && retained.blocks().matches(knowledge)) {
            // Only selection facts are reused. Refresh each admitted sample's
            // revision from this exact state; old observation metadata cannot
            // become permission for new development.
            cells = retained.cells().stream().map(cell -> {
                var fresh = candidate(knowledge, cell.support()).orElseThrow();
                return fresh.equals(cell) ? cell : fresh;
            }).toList();
        } else {
            var reads = new KnownBlockReadWitness.Recorder<NavigationBlockState>(knowledge, MAX_QUERY_READS);
            cells = discover(state, fields, limit, reads, geometry);
            var complete = reads.completed();
            if (complete.isPresent()) {
                var facts = new Facts(state.bootstrap(), ImmutableDependencies.of(fields.toArray()), geometry.version(),
                        currentRegional, regions(state, fields), complete.orElseThrow(), cells);
                synchronized (LAND) {
                    var old = LAND.put(query, facts);
                    retainedReads += facts.blocks().size() - (old == null ? 0 : old.blocks().size());
                    while (LAND.size() > MAX_ENTRIES || retainedReads > MAX_RETAINED_READS) {
                        var iterator = LAND.entrySet().iterator();
                        retainedReads -= iterator.next().getValue().blocks().size(); iterator.remove();
                    }
                }
            }
        }
        // Reuse only immutable facts. The admission proof is freshly tied to this
        // exact world state; cached land never grants stale development authority.
        return new Survey(state, home, fields, cells);
    }
    static List<FieldDevelopmentCell> read(FrontierWorldState state, SubjectId home, List<ResourceSite> fields, int limit) {
        return survey(state, home, fields, limit).cells();
    }
    private static List<FieldDevelopmentCell> discover(FrontierWorldState state, List<ResourceSite> fields, int limit,
            KnownBlockGeometry<NavigationBlockState> knowledge, PedestrianRouteGeometry geometry) {
        var rules = state.bootstrap().ruleset().fields().cultivation().orElseThrow();
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
    private static Optional<FieldDevelopmentCell> candidate(KnownBlockGeometry<NavigationBlockState> knowledge, SurfaceAnchor surface) {
        var soil = knowledge.at(surface.support()); var crop = knowledge.at(surface.support().offset(0, 1, 0));
        if (soil.isPresent() && crop.isPresent() && FieldDevelopmentCell.suitable(soil.orElseThrow(), crop.orElseThrow())) {
            return Optional.of(new FieldDevelopmentCell(surface, soil.orElseThrow(), crop.orElseThrow()));
        }
        return Optional.empty();
    }
    /** Complete horizontal read footprint of the shared area search, including
     * every initial station and the adjacent support/body-clearance columns.
     * Regional tokens already include the geometry provider's shape dependencies. */
    private static Set<Region>
            regions(FrontierWorldState state, List<ResourceSite> fields) {
        var starts = fields.stream().flatMap(field -> field.layout().cells().stream())
                .map(ResourceFieldLayout.Cell::workstation).toList();
        var origin = starts.getFirst();
        int radius = state.bootstrap().ruleset().fields().cultivation().orElseThrow().searchRadius() + 1;
        var regions = new HashSet<Region>();
        for (int x = Math.floorDiv(origin.x() - radius, 16); x <= Math.floorDiv(origin.x() + radius, 16); x++)
            for (int z = Math.floorDiv(origin.z() - radius, 16); z <= Math.floorDiv(origin.z() + radius, 16); z++)
                regions.add(new Region(x, z));
        for (var start : starts) for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
            regions.add(new Region(Math.floorDiv(start.x() + dx, 16), Math.floorDiv(start.z() + dz, 16)));
        return Set.copyOf(regions);
    }
    private FieldDevelopmentLand() { }
}
