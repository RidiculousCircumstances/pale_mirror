package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.LinkedHashMap;
import java.util.Map;

/** Composes authored solid surfaces before natural terrain; no task owns a private road datum. */
public final class KnownPedestrianGround {
    private record Roads(RouteTopology topology, ChunkSurfaceIndex surfaces) { }
    // Rebuildable knowledge cache: one current bootstrap identity/road revision, never world state.
    private static FrontierBootstrap cachedBootstrap;
    private static Roads cachedRoads;
    private static ChunkSurfaceIndex bootstrapRoads;
    private static ChunkSurfaceIndex frontierLocal;
    private static final Map<SubjectId, ChunkSurfaceIndex> LOCAL = new LinkedHashMap<>();
    private static final ImmutableInputView<io.farfrontier.palemirror.frontier.v3.model.geometry.RegionalBlockSet> UNKNOWN = new ImmutableInputView<>();
    private KnownPedestrianGround() { }

    public static BoundedPedestrianApproach.SurveyedSurface forSettlement(FrontierWorldState state,
                                                                       SubjectId settlementId) {
        return restricted(state, RetainedNavigationGeometry.overlay(layered(state.bootstrap(), roads(state.bootstrap(), state.routeTopology()),
                local(state.bootstrap(), settlementId), KnownSiteGeometry.forState(state)), state.navigationGeometry(), KnownSiteGeometry.forState(state).retainedBlocks()));
    }

    /** Immutable admission/hydration datum, using the same authored-ground policy. */
    public static BoundedPedestrianApproach.SurveyedSurface forBootstrap(FrontierBootstrap bootstrap) {
        return frontierSurvey(bootstrap, bootstrapRoads(bootstrap));
    }

    /** Authored work-site admission uses current roads without inventing a staging floor. */
    public static BoundedPedestrianApproach.SurveyedSurface forBootstrap(FrontierBootstrap bootstrap, RouteTopology topology) {
        return frontierSurvey(bootstrap, roads(bootstrap, topology));
    }

    /** Cross-settlement movement uses all authored local floors, not one origin's datum. */
    public static BoundedPedestrianApproach.SurveyedSurface forFrontier(FrontierWorldState state) {
        return restricted(state, RetainedNavigationGeometry.overlay(layered(state.bootstrap(), roads(state.bootstrap(), state.routeTopology()),
                frontierLocal(state.bootstrap()), KnownSiteGeometry.forState(state)), state.navigationGeometry(), KnownSiteGeometry.forState(state).retainedBlocks()));
    }

    private static BoundedPedestrianApproach.SurveyedSurface restricted(FrontierWorldState state,
            BoundedPedestrianApproach.SurveyedSurface source) {
        var unknown = UNKNOWN.get(java.util.List.of(state.physicalDeltas()), () ->
                io.farfrontier.palemirror.frontier.v3.model.geometry.RegionalBlockSet.copyOf(state.physicalDeltas().keySet()));
        return new BoundedPedestrianApproach.SurveyedSurface() {
            @Override public SurfaceAnchor at(int x, int z) { return source.at(x, z); }
            @Override public java.util.List<SurfaceAnchor> surfacesAt(int x, int z) { return source.surfacesAt(x, z); }
            @Override public boolean excluded(SurfaceAnchor surface) { return source.excluded(surface); }
            @Override public boolean blockKnown(BlockPosition position) { return !unknown.contains(position) && source.blockKnown(position); }
            @Override public java.util.Optional<io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationCell> collisionAt(BlockPosition position) { return source.collisionAt(position); }
            @Override public boolean detailedCollisionAt(int x, int z) { return source.detailedCollisionAt(x, z); }
            @Override public boolean clearanceKnown(SurfaceAnchor surface) {
                for (int y = 0; y <= 2; y++) if (!blockKnown(surface.support().offset(0, y, 0))) return false;
                return source.clearanceKnown(surface);
            }
        };
    }

    private static BoundedPedestrianApproach.SurveyedSurface frontierSurvey(FrontierBootstrap bootstrap, ChunkSurfaceIndex roads) {
        return layered(bootstrap, roads, frontierLocal(bootstrap),
                new KnownSiteGeometry.View(Map.of(), java.util.Set.of(), java.util.Set.of()));
    }

    private static BoundedPedestrianApproach.SurveyedSurface layered(FrontierBootstrap bootstrap,
            ChunkSurfaceIndex roads, ChunkSurfaceIndex local, KnownSiteGeometry.View sites) {
        // Sparse site columns need the same direct chunk index as roads. MapN's
        // clustered probing of coordinate hashes made absent-column queries costly
        // for every body-volume sample in long-distance region construction.
        var siteIndex = io.farfrontier.palemirror.frontier.v3.model.geometry.RegionalSurfaceMap.copyOf(sites.supports());
        return new BoundedPedestrianApproach.SurveyedSurface() {
            @Override public boolean excluded(SurfaceAnchor surface) { return sites.withdrawnSupports().contains(surface.support()); }
            @Override public boolean blockKnown(BlockPosition position) { return !sites.unknown().contains(position); }
            @Override public SurfaceAnchor at(int x, int z) {
                var levels = surfacesAt(x, z); return levels.isEmpty() ? null : levels.getLast();
            }
            @Override public java.util.List<SurfaceAnchor> surfacesAt(int x, int z) {
                var natural = SurfaceAnchor.at(x, bootstrap.terrain().supportYAt(x, z), z);
                var road = roads.surfacesAt(x, z); var floor = local.surfacesAt(x, z);
                var site = siteIndex.getOrDefault(new TerrainColumn(x, z), java.util.List.of());
                if (road.isEmpty() && floor.isEmpty() && site.isEmpty())
                    return sites.withdrawnSupports().contains(natural.support()) ? java.util.List.of() : java.util.List.of(natural);
                var result = new java.util.TreeSet<SurfaceAnchor>(java.util.Comparator.comparingInt(SurfaceAnchor::y));
                result.add(natural); result.addAll(road); result.addAll(floor); result.addAll(site);
                result.removeIf(surface -> sites.withdrawnSupports().contains(surface.support()));
                return java.util.List.copyOf(result);
            }
        };
    }

    private static synchronized ChunkSurfaceIndex frontierLocal(FrontierBootstrap bootstrap) {
        useBootstrap(bootstrap);
        if (frontierLocal == null) frontierLocal = ChunkSurfaceIndex.of(bootstrap.settlements().stream()
                .flatMap(settlement -> SettlementPedestrianGround.localSupports(bootstrap, settlement.id()).values().stream()).toList());
        return frontierLocal;
    }

    private static synchronized ChunkSurfaceIndex bootstrapRoads(FrontierBootstrap bootstrap) {
        useBootstrap(bootstrap);
        if (bootstrapRoads == null) bootstrapRoads = ChunkSurfaceIndex.of(FrontierRouteNetwork.footprint(bootstrap,
                RouteTopology.initial()).surfaceCells().stream().map(SurfaceAnchor::new).toList());
        return bootstrapRoads;
    }

    private static synchronized ChunkSurfaceIndex roads(FrontierBootstrap bootstrap,
                                                                       RouteTopology topology) {
        useBootstrap(bootstrap);
        Roads cached = cachedRoads;
        if (cached != null && cached.topology() == topology) return cached.surfaces();
        ChunkSurfaceIndex immutable = ChunkSurfaceIndex.of(FrontierRouteNetwork.footprint(bootstrap, topology)
                .surfaceCells().stream().map(SurfaceAnchor::new).toList());
        cachedRoads = new Roads(topology, immutable);
        return immutable;
    }

    private static synchronized ChunkSurfaceIndex local(FrontierBootstrap bootstrap, SubjectId settlementId) {
        useBootstrap(bootstrap);
        return LOCAL.computeIfAbsent(settlementId, id -> ChunkSurfaceIndex.of(
                SettlementPedestrianGround.localSupports(bootstrap, id).values()));
    }

    private static void useBootstrap(FrontierBootstrap bootstrap) {
        if (cachedBootstrap == bootstrap) return;
        cachedBootstrap = bootstrap;
        cachedRoads = null;
        bootstrapRoads = null;
        frontierLocal = null;
        LOCAL.clear();
    }
}
