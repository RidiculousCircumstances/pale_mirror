package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianGeometryRevision;
import io.farfrontier.palemirror.frontier.v3.model.geometry.RegionalBlockSet;
import io.farfrontier.palemirror.frontier.v3.model.geometry.RegionalSurfaceMap;
import io.farfrontier.palemirror.frontier.v3.model.geometry.ImmutableRegionMap;
import java.util.*;

/** Bounded derived lineage of explicit geometry views; no world/task mutation or route selection. */
final class KnownPedestrianGeometryScopes {
    private static final int MAX_SCOPES = 256;
    private record Scope(RegionalBlockSet hard, RegionalSurfaceMap overlay, RegionalBlockSet accessRestrictions,
                         PedestrianGeometryRevision ground, PedestrianGeometryRevision revision) { }
    private record ScopeIdentity(Object epoch, Object view) { }
    /** Reconstructible facts: cache eviction cannot manufacture a terrain change. */
    private record RegionFacts(Object ground, Object hard, Object overlay, Object accessRestrictions) { }
    private static final Map<Object, Scope> SCOPES = new LinkedHashMap<>();
    private KnownPedestrianGeometryScopes() { }

    static synchronized PedestrianGeometryRevision revision(Object key, RegionalBlockSet hard,
            Map<TerrainColumn, List<SurfaceAnchor>> overlayMap, RegionalBlockSet accessRestrictions, PedestrianGeometryRevision capturedGround) {
        var overlay = RegionalSurfaceMap.copyOf(overlayMap);
        var ground = capturedGround;
        var prior = SCOPES.get(key);
        if (prior != null && !prior.ground().scope().equals(ground.scope())) prior = null;
        var changed = new HashSet<PedestrianGeometryRevision.Region>();
        if (prior != null) {
            changed.addAll(ground.changesSince(prior.ground()));
            changed.addAll(hard.changesFrom(prior.hard())); changed.addAll(overlay.changesFrom(prior.overlay()));
            changed.addAll(accessRestrictions.changesFrom(prior.accessRestrictions()));
            if (changed.isEmpty()) {
                SCOPES.put(key, new Scope(hard, overlay, accessRestrictions, ground, prior.revision()));
                return prior.revision();
            }
        } else {
            changed.addAll(hard.regions());
            changed.addAll(overlay.regions()); changed.addAll(accessRestrictions.regions());
        }
        // Facts are chunk-indexed once per changed view. A reconstructed view
        // has equal regional tokens even when its bounded cache entry was lost.
        // The bootstrap epoch still fences worlds; current ground tokens fence
        // genuine support/obstacle changes, including changes outside overlays.
        var tokens = ImmutableRegionMap.copyOf(prior == null ? ground.revisions() : prior.revision().revisions());
        var updates = new HashMap<PedestrianGeometryRevision.Region, Optional<Object>>();
        for (var region : changed) {
            var localLevels = overlay.in(region);
            if (hard.in(region).isEmpty() && localLevels.isEmpty() && accessRestrictions.in(region).isEmpty()) {
                updates.put(region, Optional.ofNullable(ground.revisions().get(region)));
            } else updates.put(region, Optional.of(new RegionFacts(ground.at(region.x(), region.z()), hard.token(region), overlay.token(region), accessRestrictions.token(region))));
        }
        // Query identity retains its declared passages. Graphs may be shared across
        // queries only where the complete local facts (including denied passages)
        // are equal, within the same explicit terrain epoch.
        var revision = prior != null ? prior.revision().withRevisions(tokens.with(updates))
                : new PedestrianGeometryRevision(new ScopeIdentity(ground.scope(), key),
                ground.geometryScope(), ground.baseline(), tokens.with(updates));
        if (SCOPES.size() >= MAX_SCOPES && !SCOPES.containsKey(key)) SCOPES.remove(SCOPES.keySet().iterator().next());
        SCOPES.put(key, new Scope(hard, overlay, accessRestrictions, ground, revision)); return revision;
    }

}
