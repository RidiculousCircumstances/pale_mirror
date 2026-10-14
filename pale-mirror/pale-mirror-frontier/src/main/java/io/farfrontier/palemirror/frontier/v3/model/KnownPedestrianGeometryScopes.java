package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianGeometryRevision;
import java.util.*;

/** Bounded derived lineage of explicit geometry views; no world/task mutation or route selection. */
final class KnownPedestrianGeometryScopes {
    private static final int MAX_SCOPES = 256;
    private record Scope(Set<BlockPosition> hard, Map<TerrainColumn, List<SurfaceAnchor>> overlay,
                         PedestrianGeometryRevision ground, PedestrianGeometryRevision revision) { }
    private record ScopeIdentity(Object epoch, Object view) { }
    /** Reconstructible facts: cache eviction cannot manufacture a terrain change. */
    private record RegionFacts(Object ground, Set<BlockPosition> hard,
                               Map<TerrainColumn, List<SurfaceAnchor>> overlay) { }
    private static Object bootstrap;
    private static Set<BlockPosition> obstacles = Set.of();
    private static Map<TerrainColumn, List<SurfaceAnchor>> supports = Map.of();
    private static PedestrianGeometryRevision ground = PedestrianGeometryRevision.initial();
    private static final Map<Object, Scope> SCOPES = new LinkedHashMap<>();
    private KnownPedestrianGeometryScopes() { }

    static synchronized void refresh(Object currentBootstrap, Set<BlockPosition> currentObstacles,
                                    Map<TerrainColumn, List<SurfaceAnchor>> currentSupports) {
        currentObstacles = Set.copyOf(currentObstacles); currentSupports = Map.copyOf(currentSupports);
        if (bootstrap != currentBootstrap) {
            bootstrap = currentBootstrap; SCOPES.clear(); ground = PedestrianGeometryRevision.initial();
        } else {
            var changed = new HashSet<PedestrianGeometryRevision.Region>();
            changedBlocks(obstacles, currentObstacles, changed); changedSupports(supports, currentSupports, changed);
            ground = ground.changed(changed);
        }
        obstacles = Set.copyOf(currentObstacles); supports = Map.copyOf(currentSupports);
    }

    static synchronized PedestrianGeometryRevision revision(Object key, Set<BlockPosition> hard,
                                                            Map<TerrainColumn, List<SurfaceAnchor>> overlay) {
        var prior = SCOPES.get(key);
        PedestrianGeometryRevision revision;
        if (prior != null) {
            var changed = new HashSet<>(ground.changesSince(prior.ground()));
            changedBlocks(prior.hard(), hard, changed); changedSupports(prior.overlay(), overlay, changed);
            if (changed.isEmpty()) return prior.revision();
        }
        // Facts are chunk-indexed once per changed view. A reconstructed view
        // has equal regional tokens even when its bounded cache entry was lost.
        // The bootstrap epoch still fences worlds; current ground tokens fence
        // genuine support/obstacle changes, including changes outside overlays.
        var blocks = new HashMap<PedestrianGeometryRevision.Region, Set<BlockPosition>>();
        for (var position : hard) blocks.computeIfAbsent(PedestrianGeometryRevision.Region.at(position),
                ignored -> new HashSet<>()).add(position);
        var levels = new HashMap<PedestrianGeometryRevision.Region, Map<TerrainColumn, List<SurfaceAnchor>>>();
        overlay.forEach((column, surfaces) -> levels.computeIfAbsent(new PedestrianGeometryRevision.Region(
                Math.floorDiv(column.x(), 16), Math.floorDiv(column.z(), 16)), ignored -> new HashMap<>()).put(column, surfaces));
        var regions = new HashSet<>(ground.revisions().keySet()); regions.addAll(blocks.keySet()); regions.addAll(levels.keySet());
        var tokens = new HashMap<PedestrianGeometryRevision.Region, Object>();
        for (var region : regions) {
            var localBlocks = Set.copyOf(blocks.getOrDefault(region, Set.of()));
            var localLevels = Map.copyOf(levels.getOrDefault(region, Map.of()));
            tokens.put(region, localBlocks.isEmpty() && localLevels.isEmpty()
                    ? ground.at(region.x(), region.z())
                    : new RegionFacts(ground.at(region.x(), region.z()), localBlocks, localLevels));
        }
        // Query identity retains its declared passages. Graphs may be shared across
        // queries only where the complete local facts (including denied passages)
        // are equal, within the same explicit terrain epoch.
        revision = new PedestrianGeometryRevision(new ScopeIdentity(ground.scope(), key),
                ground.geometryScope(), ground.baseline(), tokens);
        if (SCOPES.size() >= MAX_SCOPES && !SCOPES.containsKey(key)) SCOPES.remove(SCOPES.keySet().iterator().next());
        SCOPES.put(key, new Scope(Set.copyOf(hard), Map.copyOf(overlay), ground, revision)); return revision;
    }

    private static void changedBlocks(Set<BlockPosition> before, Set<BlockPosition> after,
                                      Set<PedestrianGeometryRevision.Region> changed) {
        if (before == after) return;
        for (var position : before) if (!after.contains(position)) changed.add(PedestrianGeometryRevision.Region.at(position));
        for (var position : after) if (!before.contains(position)) changed.add(PedestrianGeometryRevision.Region.at(position));
    }
    private static void changedSupports(Map<TerrainColumn, List<SurfaceAnchor>> before, Map<TerrainColumn, List<SurfaceAnchor>> after,
                                        Set<PedestrianGeometryRevision.Region> changed) {
        if (before == after) return;
        for (var entry : before.entrySet()) if (!entry.getValue().equals(after.get(entry.getKey())))
            changed.add(new PedestrianGeometryRevision.Region(Math.floorDiv(entry.getKey().x(), 16), Math.floorDiv(entry.getKey().z(), 16)));
        for (var entry : after.entrySet()) if (!entry.getValue().equals(before.get(entry.getKey())))
            changed.add(new PedestrianGeometryRevision.Region(Math.floorDiv(entry.getKey().x(), 16), Math.floorDiv(entry.getKey().z(), 16)));
    }
}
