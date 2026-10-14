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
    private static PedestrianGeometryRevision ground = PedestrianGeometryRevision.initial();
    private static final Map<Object, Scope> SCOPES = new LinkedHashMap<>();
    private KnownPedestrianGeometryScopes() { }

    static synchronized void refresh(Object currentBootstrap, Set<BlockPosition> obstacleInput,
                                    Map<TerrainColumn, List<SurfaceAnchor>> supportInput) {
        var currentObstacles = RegionalBlockSet.copyOf(obstacleInput);
        var currentSupports = RegionalSurfaceMap.copyOf(supportInput);
        if (bootstrap != currentBootstrap) {
            bootstrap = currentBootstrap; SCOPES.clear(); ground = PedestrianGeometryRevision.initial();
        } else {
            var changed = new HashSet<>(currentObstacles.changesFrom(obstacles));
            changed.addAll(currentSupports.changesFrom(supports));
            ground = ground.changed(changed);
        }
        obstacles = currentObstacles; supports = currentSupports;
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
