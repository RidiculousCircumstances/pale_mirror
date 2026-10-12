package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianGeometryRevision;
import java.util.*;

/** Bounded derived lineage of explicit geometry views; no world/task mutation or route selection. */
final class KnownPedestrianGeometryScopes {
    private static final int MAX_SCOPES = 256;
    private record Scope(Set<BlockPosition> hard, Map<TerrainColumn, SurfaceAnchor> overlay,
                         PedestrianGeometryRevision ground, PedestrianGeometryRevision revision) { }
    private record ScopeIdentity(Object epoch, Object view) { }
    private static Object bootstrap;
    private static Set<BlockPosition> obstacles = Set.of();
    private static Map<TerrainColumn, SurfaceAnchor> supports = Map.of();
    private static PedestrianGeometryRevision ground = PedestrianGeometryRevision.initial();
    private static final Map<Object, Scope> SCOPES = new LinkedHashMap<>();
    private KnownPedestrianGeometryScopes() { }

    static synchronized void refresh(Object currentBootstrap, Set<BlockPosition> currentObstacles,
                                    Map<TerrainColumn, SurfaceAnchor> currentSupports) {
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
                                                            Map<TerrainColumn, SurfaceAnchor> overlay) {
        var prior = SCOPES.get(key);
        PedestrianGeometryRevision revision;
        if (prior == null) revision = PedestrianGeometryRevision.initial(new ScopeIdentity(ground.scope(), key));
        else {
            var changed = new HashSet<>(ground.changesSince(prior.ground()));
            changedBlocks(prior.hard(), hard, changed); changedSupports(prior.overlay(), overlay, changed);
            revision = prior.revision().changed(changed);
        }
        if (SCOPES.size() >= MAX_SCOPES && !SCOPES.containsKey(key)) SCOPES.remove(SCOPES.keySet().iterator().next());
        SCOPES.put(key, new Scope(Set.copyOf(hard), Map.copyOf(overlay), ground, revision)); return revision;
    }

    private static void changedBlocks(Set<BlockPosition> before, Set<BlockPosition> after,
                                      Set<PedestrianGeometryRevision.Region> changed) {
        for (var position : before) if (!after.contains(position)) changed.add(PedestrianGeometryRevision.Region.at(position));
        for (var position : after) if (!before.contains(position)) changed.add(PedestrianGeometryRevision.Region.at(position));
    }
    private static void changedSupports(Map<TerrainColumn, SurfaceAnchor> before, Map<TerrainColumn, SurfaceAnchor> after,
                                        Set<PedestrianGeometryRevision.Region> changed) {
        for (var entry : before.entrySet()) if (!entry.getValue().equals(after.get(entry.getKey())))
            changed.add(PedestrianGeometryRevision.Region.at(entry.getValue().support()));
        for (var entry : after.entrySet()) if (!entry.getValue().equals(before.get(entry.getKey())))
            changed.add(PedestrianGeometryRevision.Region.at(entry.getValue().support()));
    }
}
