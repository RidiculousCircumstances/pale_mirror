package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;

/** Read-only site geometry capabilities. Navigation consumes facts, never a site's jobs or commodity. */
final class KnownSiteGeometry {
    enum Owner { EXTRACTION }
    interface Provider {
        Owner owner();
        Object version(FrontierWorldState state);
        View project(FrontierWorldState state);
    }
    record View(Map<TerrainColumn, SurfaceAnchor> supports, Set<BlockPosition> obstacles) {
        View {
            supports = Map.copyOf(supports); obstacles = Set.copyOf(obstacles);
            if (supports.entrySet().stream().anyMatch(entry -> entry.getKey().x() != entry.getValue().x()
                    || entry.getKey().z() != entry.getValue().z()))
                throw new IllegalArgumentException("site geometry column differs from its declared support");
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
    private KnownSiteGeometry() { }
    static synchronized View forState(FrontierWorldState state) {
        var versions = PROVIDERS.stream().map(provider -> provider.version(state)).toList();
        boolean same = cachedBootstrap == state.bootstrap() && cachedView != null && versions.size() == cachedVersions.size();
        for (int i = 0; same && i < versions.size(); i++) same = versions.get(i) == cachedVersions.get(i);
        if (same) return cachedView;
        var supports = new LinkedHashMap<TerrainColumn, SurfaceAnchor>();
        var obstacles = new HashSet<BlockPosition>();
        for (var provider : PROVIDERS) {
            var contribution = provider.project(state);
            for (var entry : contribution.supports().entrySet())
                if (supports.putIfAbsent(entry.getKey(), entry.getValue()) != null)
                    throw new IllegalArgumentException("two geometry owners declared one surface column");
            obstacles.addAll(contribution.obstacles());
        }
        cachedView = new View(supports, obstacles); cachedBootstrap = state.bootstrap(); cachedVersions = versions;
        return cachedView;
    }
}
