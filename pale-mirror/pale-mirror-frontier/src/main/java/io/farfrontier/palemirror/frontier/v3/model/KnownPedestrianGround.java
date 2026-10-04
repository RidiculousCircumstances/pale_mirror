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
    private KnownPedestrianGround() { }

    public static BoundedPedestrianApproach.SurveyedSurface forSettlement(FrontierWorldState state,
                                                                       SubjectId settlementId) {
        ChunkSurfaceIndex roads = roads(state.bootstrap(), state.routeTopology());
        ChunkSurfaceIndex local = local(state.bootstrap(), settlementId);
        var terrain = state.bootstrap().terrain();
        return (x, z) -> {
            SurfaceAnchor road = roads.at(x, z), settlement = local.at(x, z);
            // Both solid authored floors are physically projected. Only their upper exposed
            // collision surface is standable; selecting the lower puts feet inside the upper.
            if (road != null && settlement != null) return road.y() >= settlement.y() ? road : settlement;
            if (road != null) return road;
            return settlement != null ? settlement : SurfaceAnchor.at(x,
                    terrain.supportYAt(x, z), z);
        };
    }

    /** Immutable admission/hydration datum, using the same authored-ground policy. */
    public static BoundedPedestrianApproach.SurveyedSurface forBootstrap(FrontierBootstrap bootstrap) {
        return frontierSurvey(bootstrap, bootstrapRoads(bootstrap));
    }

    /** Cross-settlement movement uses all authored local floors, not one origin's datum. */
    public static BoundedPedestrianApproach.SurveyedSurface forFrontier(FrontierWorldState state) {
        return frontierSurvey(state.bootstrap(), roads(state.bootstrap(), state.routeTopology()));
    }

    private static BoundedPedestrianApproach.SurveyedSurface frontierSurvey(FrontierBootstrap bootstrap, ChunkSurfaceIndex roads) {
        var local = frontierLocal(bootstrap);
        var terrain = bootstrap.terrain();
        return (x, z) -> {
            var road = roads.at(x, z); var floor = local.at(x, z);
            if (road != null && floor != null) return road.y() >= floor.y() ? road : floor;
            return road != null ? road : floor != null ? floor : SurfaceAnchor.at(x, terrain.supportYAt(x, z), z);
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
