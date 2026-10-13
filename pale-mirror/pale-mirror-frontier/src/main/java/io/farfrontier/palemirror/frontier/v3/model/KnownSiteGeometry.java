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
    record View(Map<TerrainColumn, List<SurfaceAnchor>> supports, Set<BlockPosition> obstacles,
                Set<BlockPosition> withdrawnSupports) {
        View {
            supports = Map.copyOf(supports); obstacles = Set.copyOf(obstacles);
            withdrawnSupports = Set.copyOf(withdrawnSupports);
            if (supports.entrySet().stream().anyMatch(entry -> entry.getValue().isEmpty()
                    || entry.getValue().stream().anyMatch(surface -> entry.getKey().x() != surface.x()
                        || entry.getKey().z() != surface.z())))
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
        var supports = new LinkedHashMap<TerrainColumn, List<SurfaceAnchor>>();
        var obstacles = new HashSet<BlockPosition>();
        var withdrawn = new HashSet<BlockPosition>();
        for (var provider : PROVIDERS) {
            var contribution = provider.project(state);
            for (var entry : contribution.supports().entrySet()) {
                var prior = supports.get(entry.getKey());
                if (prior == null) { supports.put(entry.getKey(), entry.getValue()); continue; }
                if (!Collections.disjoint(prior, entry.getValue()))
                    throw new IllegalArgumentException("two geometry owners declared one exact support");
                supports.put(entry.getKey(), java.util.stream.Stream.concat(prior.stream(), entry.getValue().stream())
                        .sorted(Comparator.comparingInt(SurfaceAnchor::y)).toList());
            }
            obstacles.addAll(contribution.obstacles());
            withdrawn.addAll(contribution.withdrawnSupports());
        }
        // Retain each independent immutable component. A changed obstacle must not make
        // every unchanged road/support column look like a new ground declaration.
        var projected = new View(cachedBootstrap == state.bootstrap() && cachedView != null
                    && supports.equals(cachedView.supports()) ? cachedView.supports() : supports,
                cachedBootstrap == state.bootstrap() && cachedView != null
                    && obstacles.equals(cachedView.obstacles()) ? cachedView.obstacles() : obstacles,
                cachedBootstrap == state.bootstrap() && cachedView != null
                    && withdrawn.equals(cachedView.withdrawnSupports()) ? cachedView.withdrawnSupports() : withdrawn);
        if (cachedBootstrap != state.bootstrap() || !projected.equals(cachedView)) cachedView = projected;
        cachedBootstrap = state.bootstrap(); cachedVersions = versions;
        return cachedView;
    }
}
