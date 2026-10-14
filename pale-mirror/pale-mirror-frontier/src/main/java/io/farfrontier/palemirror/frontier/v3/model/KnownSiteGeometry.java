package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;
import io.farfrontier.palemirror.frontier.v3.model.geometry.*;

/** Read-only site geometry capabilities. Navigation consumes facts, never a site's jobs or commodity. */
final class KnownSiteGeometry {
    enum Owner { EXTRACTION }
    interface Provider {
        Owner owner();
        Object version(FrontierWorldState state);
        View project(FrontierWorldState state);
    }
    record View(Map<TerrainColumn, List<SurfaceAnchor>> supports, Set<BlockPosition> obstacles,
                Set<BlockPosition> withdrawnSupports) {
        View {
            supports = RegionalSurfaceMap.copyOf(supports); obstacles = RegionalBlockSet.copyOf(obstacles);
            withdrawnSupports = RegionalBlockSet.copyOf(withdrawnSupports);
        }
    }
    private static final List<Provider> PROVIDERS = List.of(new ExtractionKnownGeometry());
    static {
        if (!PROVIDERS.stream().map(Provider::owner).collect(java.util.stream.Collectors.toSet())
                .equals(EnumSet.allOf(Owner.class)) || PROVIDERS.size() != Owner.values().length)
            throw new IllegalArgumentException("missing or duplicate known-site geometry provider");
    }
    private static FrontierBootstrap cachedBootstrap;
    private static List<Object> cachedVersions = List.of();
    private static View cachedView;
    private static RegionalSurfaceContributions<Owner> surfaceParts = new RegionalSurfaceContributions<>(true);
    private static RegionalBlockContributions<Owner> obstacleParts = new RegionalBlockContributions<>();
    private static RegionalBlockContributions<Owner> withdrawnParts = new RegionalBlockContributions<>();
    private KnownSiteGeometry() { }
    static synchronized View forState(FrontierWorldState state) {
        var versions = PROVIDERS.stream().map(provider -> provider.version(state)).toList();
        boolean same = cachedBootstrap == state.bootstrap() && cachedView != null && versions.size() == cachedVersions.size();
        for (int i = 0; same && i < versions.size(); i++) same = versions.get(i) == cachedVersions.get(i);
        if (same) return cachedView;
        if (cachedBootstrap != state.bootstrap()) {
            surfaceParts = new RegionalSurfaceContributions<>(true); obstacleParts = new RegionalBlockContributions<>();
            withdrawnParts = new RegionalBlockContributions<>();
        }
        for (int i = 0; i < PROVIDERS.size(); i++) {
            var provider = PROVIDERS.get(i);
            if (cachedBootstrap == state.bootstrap() && cachedView != null && versions.get(i) == cachedVersions.get(i)) continue;
            var contribution = provider.project(state);
            surfaceParts.replace(provider.owner(), RegionalSurfaceMap.copyOf(contribution.supports()));
            obstacleParts.replace(provider.owner(), RegionalBlockSet.copyOf(contribution.obstacles()));
            withdrawnParts.replace(provider.owner(), RegionalBlockSet.copyOf(contribution.withdrawnSupports()));
        }
        // Retain each independent immutable component. A changed obstacle must not make
        // every unchanged road/support column look like a new ground declaration.
        if (cachedBootstrap != state.bootstrap() || cachedView == null || cachedView.supports() != surfaceParts.snapshot()
                || cachedView.obstacles() != obstacleParts.snapshot() || cachedView.withdrawnSupports() != withdrawnParts.snapshot())
            cachedView = new View(surfaceParts.snapshot(), obstacleParts.snapshot(), withdrawnParts.snapshot());
        cachedBootstrap = state.bootstrap(); cachedVersions = versions;
        return cachedView;
    }
}
