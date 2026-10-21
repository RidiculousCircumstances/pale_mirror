package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianGeometryRevision;
import io.farfrontier.palemirror.frontier.v3.model.geometry.RegionalBlockSet;
import io.farfrontier.palemirror.frontier.v3.model.geometry.RegionalSurfaceMap;
import java.util.*;

/** Bounded derived lineage of explicit geometry views; no world/task mutation or route selection. */
final class KnownPedestrianGeometryScopes {
    private static final int MAX_SCOPES = 256;
    private record Scope(RegionalBlockSet hard, RegionalSurfaceMap overlay,
                         PedestrianGeometryRevision ground, PedestrianGeometryRevision revision) { }
    private record ScopeIdentity(Object epoch, Object view) { }
    /** Reconstructible facts: cache eviction cannot manufacture a terrain change. */
    private record RegionFacts(Object ground, Object hard, Object overlay) { }
    private static Object bootstrap;
    private static RegionalBlockSet obstacles = RegionalBlockSet.copyOf(Set.of());
    private static RegionalSurfaceMap supports = RegionalSurfaceMap.copyOf(Map.of());
    private static RegionalBlockSet withdrawn = RegionalBlockSet.copyOf(Set.of());
    private static RegionalBlockSet unknown = RegionalBlockSet.copyOf(Set.of());
    private static RegionalBlockSet scars = RegionalBlockSet.copyOf(Set.of());
    private static PedestrianGeometryRevision ground = PedestrianGeometryRevision.initial();
    private static final Map<Object, Scope> SCOPES = new LinkedHashMap<>();
    private KnownPedestrianGeometryScopes() { }

    private static io.farfrontier.palemirror.frontier.v3.model.geometry.RegionalBlockMap<io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationBlockState>
            priorShapeSources = io.farfrontier.palemirror.frontier.v3.model.geometry.RegionalBlockMap.copyOf(Map.of());
    static synchronized void observeShapes(io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationGeometryState prior,
            io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationGeometryState current,
            Map<BlockPosition, io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationBlockState> sourceBlocks) {
        var cells = io.farfrontier.palemirror.frontier.v3.model.geometry.RegionalBlockMap.copyOf(current.cells());
        var previous = io.farfrontier.palemirror.frontier.v3.model.geometry.RegionalBlockMap.copyOf(
                prior == null ? java.util.Map.<BlockPosition, io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationObservation>of() : prior.cells());
        var positions = new HashSet<BlockPosition>();
        var sources = io.farfrontier.palemirror.frontier.v3.model.geometry.RegionalBlockMap.copyOf(sourceBlocks);
        for (var region : sources.changesFrom(priorShapeSources)) {
            addChangedPositions(positions, sources.in(region), priorShapeSources.in(region));
        }
        priorShapeSources = sources;
        if (prior == null || !current.definitions().equals(prior.definitions()))
            sourceBlocks.forEach((position, block) -> {
                if (prior == null || !Objects.equals(current.definitions().get(block), prior.definitions().get(block))) positions.add(position);
            });
        for (var region : cells.changesFrom(previous)) {
            addChangedPositions(positions, cells.in(region), previous.in(region));
        }
        // A changed neighbour can alter a contextual shape which itself extends
        // across the next chunk boundary. Include the exact dependent origin
        // before applying the ordinary shape-protrusion halo.
        for (var position : new ArrayList<>(positions))
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                var dependent = position.offset(dx, dy, dz);
                var evidence = cells.get(dependent);
                if (evidence != null && evidence.dependencies().containsKey(position)) positions.add(dependent);
            }
        var regions = new HashSet<PedestrianGeometryRevision.Region>();
        for (var position : positions) {
            // Shapes may protrude into the neighbouring cell and depend on neighbouring states.
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
                regions.add(PedestrianGeometryRevision.Region.at(position.offset(dx, 0, dz)));
        }
        ground = ground.changed(regions);
    }

    /** A regional index narrows comparison, not the invalidation footprint. */
    private static <T> void addChangedPositions(Set<BlockPosition> changed, Map<BlockPosition, T> current,
                                               Map<BlockPosition, T> prior) {
        current.forEach((position, value) -> { if (!Objects.equals(value, prior.get(position))) changed.add(position); });
        prior.keySet().forEach(position -> { if (!current.containsKey(position)) changed.add(position); });
    }

    static synchronized void refresh(Object currentBootstrap, Set<BlockPosition> obstacleInput,
                                    Map<TerrainColumn, List<SurfaceAnchor>> supportInput) {
        refresh(currentBootstrap, obstacleInput, supportInput, Set.of(), Set.of(), Set.of());
    }
    static synchronized void refresh(Object currentBootstrap, Set<BlockPosition> obstacleInput,
            Map<TerrainColumn, List<SurfaceAnchor>> supportInput, Set<BlockPosition> withdrawnInput,
            Set<BlockPosition> unknownInput, Set<BlockPosition> scarInput) {
        var currentObstacles = RegionalBlockSet.copyOf(obstacleInput);
        var currentSupports = RegionalSurfaceMap.copyOf(supportInput);
        var currentWithdrawn = RegionalBlockSet.copyOf(withdrawnInput);
        var currentUnknown = RegionalBlockSet.copyOf(unknownInput);
        var currentScars = RegionalBlockSet.copyOf(scarInput);
        if (bootstrap != currentBootstrap) {
            bootstrap = currentBootstrap; SCOPES.clear(); ground = PedestrianGeometryRevision.initial();
        } else {
            var changed = new HashSet<>(currentObstacles.changesFrom(obstacles));
            changed.addAll(currentSupports.changesFrom(supports));
            // A change of meaning matters even when the union of occupied cells
            // stays identical (unknown -> known solid, or support withdrawal).
            changed.addAll(currentWithdrawn.changesFrom(withdrawn));
            changed.addAll(currentUnknown.changesFrom(unknown));
            changed.addAll(currentScars.changesFrom(scars));
            ground = ground.changed(changed);
        }
        obstacles = currentObstacles; supports = currentSupports;
        withdrawn = currentWithdrawn; unknown = currentUnknown; scars = currentScars;
    }

    static synchronized PedestrianGeometryRevision revision(Object key, Set<BlockPosition> hard,
                                                            Map<TerrainColumn, List<SurfaceAnchor>> overlay) {
        return revision(key, RegionalBlockSet.copyOf(hard), overlay, ground);
    }
    static synchronized PedestrianGeometryRevision groundRevision() { return ground; }
    static synchronized PedestrianGeometryRevision revision(Object key, RegionalBlockSet hard,
            Map<TerrainColumn, List<SurfaceAnchor>> overlayMap, PedestrianGeometryRevision capturedGround) {
        var overlay = RegionalSurfaceMap.copyOf(overlayMap);
        var ground = capturedGround;
        var prior = SCOPES.get(key);
        if (prior != null && !prior.ground().scope().equals(ground.scope())) prior = null;
        var changed = new HashSet<PedestrianGeometryRevision.Region>();
        if (prior != null) {
            changed.addAll(ground.changesSince(prior.ground()));
            changed.addAll(hard.changesFrom(prior.hard())); changed.addAll(overlay.changesFrom(prior.overlay()));
            if (changed.isEmpty()) {
                SCOPES.put(key, new Scope(hard, overlay, ground, prior.revision()));
                return prior.revision();
            }
        } else {
            changed.addAll(ground.revisions().keySet()); changed.addAll(hard.regions());
            changed.addAll(overlay.regions());
        }
        // Facts are chunk-indexed once per changed view. A reconstructed view
        // has equal regional tokens even when its bounded cache entry was lost.
        // The bootstrap epoch still fences worlds; current ground tokens fence
        // genuine support/obstacle changes, including changes outside overlays.
        var tokens = prior == null ? new HashMap<PedestrianGeometryRevision.Region, Object>()
                : new HashMap<>(prior.revision().revisions());
        for (var region : changed) {
            var localLevels = overlay.in(region);
            tokens.put(region, hard.in(region).isEmpty() && localLevels.isEmpty()
                    ? ground.at(region.x(), region.z())
                    : new RegionFacts(ground.at(region.x(), region.z()), hard.token(region), overlay.token(region)));
        }
        // Query identity retains its declared passages. Graphs may be shared across
        // queries only where the complete local facts (including denied passages)
        // are equal, within the same explicit terrain epoch.
        var revision = new PedestrianGeometryRevision(new ScopeIdentity(ground.scope(), key),
                ground.geometryScope(), ground.baseline(), tokens);
        if (SCOPES.size() >= MAX_SCOPES && !SCOPES.containsKey(key)) SCOPES.remove(SCOPES.keySet().iterator().next());
        SCOPES.put(key, new Scope(hard, overlay, ground, revision)); return revision;
    }

}
