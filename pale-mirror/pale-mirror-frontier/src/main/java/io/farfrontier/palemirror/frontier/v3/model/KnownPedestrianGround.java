package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.LinkedHashMap;
import java.util.Map;

/** Composes authored solid surfaces before natural terrain; no task owns a private road datum. */
public final class KnownPedestrianGround {
    private record Roads(RouteTopology topology, ChunkSurfaceIndex surfaces) {}

    // Rebuildable knowledge cache: one current bootstrap identity/road revision, never world state.
    private static FrontierBootstrap cachedBootstrap;
    private static Roads cachedRoads;
    private static ChunkSurfaceIndex bootstrapRoads;
    private static ChunkSurfaceIndex frontierLocal;
    private static final Map<SubjectId, ChunkSurfaceIndex> LOCAL = new LinkedHashMap<>();
    private static final ImmutableResultView<
                    String,
                    java.util.Set<
                            io.farfrontier.palemirror.frontier.v3.model.navigation
                                    .PedestrianGeometryRevision.Region>>
            SURVEYS = new ImmutableResultView<>(4);
    private static final ImmutableInputView<BoundedPedestrianApproach.SurveyedSurface>
            FRONTIER_VIEWS = new ImmutableInputView<>();
    private static final ImmutableInputView<BoundedPedestrianApproach.SurveyedSurface>
            BOOTSTRAP_VIEWS = new ImmutableInputView<>();
    private static final KnownSiteGeometry.View EMPTY_SITES =
            new KnownSiteGeometry.View(Map.of(), java.util.Set.of(), java.util.Set.of());
    private static final ImmutableInputView<
                    io.farfrontier.palemirror.frontier.v3.model.geometry.RegionalBlockSet>
            UNKNOWN = new ImmutableInputView<>();

    private KnownPedestrianGround() {}

    public static BoundedPedestrianApproach.SurveyedSurface forSettlement(
            FrontierWorldState state, SubjectId settlementId) {
        return forSettlement(state, settlementId, KnownSiteGeometry.forState(state));
    }

    static BoundedPedestrianApproach.SurveyedSurface forSettlement(
            FrontierWorldState state, SubjectId settlementId, KnownSiteGeometry.View sites) {
        return KnownSpatialGeometry.forState(state)
                .overlay(
                        restricted(
                                state,
                                RetainedNavigationGeometry.overlay(
                                        layered(
                                                state.bootstrap(),
                                                roads(state.bootstrap(), state.routeTopology()),
                                                local(state.bootstrap(), settlementId),
                                                sites),
                                        state.navigationGeometry(),
                                        sites.retainedBlocks())));
    }

    /** Immutable admission/hydration datum, using the same authored-ground policy. */
    public static BoundedPedestrianApproach.SurveyedSurface forBootstrap(
            FrontierBootstrap bootstrap) {
        return frontierSurvey(bootstrap, bootstrapRoads(bootstrap));
    }

    /** Authored work-site admission uses current roads without inventing a staging floor. */
    public static BoundedPedestrianApproach.SurveyedSurface forBootstrap(
            FrontierBootstrap bootstrap, RouteTopology topology) {
        return frontierSurvey(bootstrap, roads(bootstrap, topology));
    }

    /** Cross-settlement movement uses all authored local floors, not one origin's datum. */
    public static BoundedPedestrianApproach.SurveyedSurface forFrontier(FrontierWorldState state) {
        return forFrontier(state, KnownSiteGeometry.forState(state));
    }

    static BoundedPedestrianApproach.SurveyedSurface forFrontier(
            FrontierWorldState state, KnownSiteGeometry.View sites) {
        var spatial = KnownSpatialGeometry.forState(state);
        return FRONTIER_VIEWS.get(
                java.util.List.of(
                        state.bootstrap(),
                        state.routeTopology(),
                        sites,
                        state.navigationGeometry(),
                        state.physicalDeltas(),
                        spatial),
                () ->
                        spatial.overlay(
                                restricted(
                                        state,
                                        RetainedNavigationGeometry.overlay(
                                                layered(
                                                        state.bootstrap(),
                                                        roads(
                                                                state.bootstrap(),
                                                                state.routeTopology()),
                                                        frontierLocal(state.bootstrap()),
                                                        sites),
                                                state.navigationGeometry(),
                                                sites.retainedBlocks()))));
    }

    /**
     * Explicit public floor datum for retained two-dimensional consumers, validated against current
     * multi-level facts.
     */
    public static SurfaceAnchor publicDatumAt(FrontierWorldState state, int x, int z) {
        var expected = forBootstrap(state.bootstrap(), state.routeTopology()).at(x, z);
        if (expected == null) return null;
        var current = forFrontier(state);
        return current.surfacesAt(x, z).contains(expected)
                        && current.blockKnown(expected.support())
                        && current.clearanceKnown(expected)
                ? expected
                : null;
    }

    private static BoundedPedestrianApproach.SurveyedSurface restricted(
            FrontierWorldState state, BoundedPedestrianApproach.SurveyedSurface source) {
        var unknown = PhysicalDeltaGeometry.unknown(state);
        return new BoundedPedestrianApproach.SurveyedSurface() {
            @Override
            public java.util.OptionalInt uniformSupport(WorldBounds area) {
                return io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationRegionCoverage
                                .intersects(unknown.regions(), area)
                        ? java.util.OptionalInt.empty()
                        : source.uniformSupport(area);
            }

            @Override
            public SurfaceAnchor at(int x, int z) {
                return source.at(x, z);
            }

            @Override
            public java.util.List<SurfaceAnchor> surfacesAt(int x, int z) {
                return source.surfacesAt(x, z);
            }

            @Override
            public boolean excluded(SurfaceAnchor surface) {
                return source.excluded(surface);
            }

            @Override
            public boolean blockKnown(BlockPosition position) {
                return !unknown.contains(position) && source.blockKnown(position);
            }

            @Override
            public java.util.Optional<
                            io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationCell>
                    collisionAt(BlockPosition position) {
                return source.collisionAt(position);
            }

            @Override
            public boolean detailedCollisionAt(int x, int z) {
                return source.detailedCollisionAt(x, z);
            }

            @Override
            public boolean clearanceKnown(SurfaceAnchor surface) {
                for (int y = 0; y <= 2; y++)
                    if (!blockKnown(surface.support().offset(0, y, 0))) return false;
                return source.clearanceKnown(surface);
            }
        };
    }

    private static BoundedPedestrianApproach.SurveyedSurface frontierSurvey(
            FrontierBootstrap bootstrap, ChunkSurfaceIndex roads) {
        return BOOTSTRAP_VIEWS.get(
                java.util.List.of(bootstrap, roads),
                () -> layered(bootstrap, roads, frontierLocal(bootstrap), EMPTY_SITES));
    }

    private static BoundedPedestrianApproach.SurveyedSurface layered(
            FrontierBootstrap bootstrap,
            ChunkSurfaceIndex roads,
            ChunkSurfaceIndex local,
            KnownSiteGeometry.View sites) {
        // Sparse site columns need the same direct chunk index as roads. MapN's
        // clustered probing of coordinate hashes made absent-column queries costly
        // for every body-volume sample in long-distance region construction.
        var siteIndex =
                io.farfrontier.palemirror.frontier.v3.model.geometry.RegionalSurfaceMap.copyOf(
                        sites.supports());
        var surveyRegions =
                SURVEYS.get(
                        java.util.List.of(bootstrap.terrain()),
                        "survey-regions",
                        () ->
                                bootstrap.terrain().surveyedSupportY().keySet().stream()
                                        .map(
                                                column ->
                                                        new io.farfrontier.palemirror.frontier.v3
                                                                .model.navigation
                                                                .PedestrianGeometryRevision.Region(
                                                                column.x() >> 4, column.z() >> 4))
                                        .collect(java.util.stream.Collectors.toUnmodifiableSet()));
        var supports = siteIndex.regions();
        var withdrawals =
                io.farfrontier.palemirror.frontier.v3.model.geometry.RegionalBlockSet.copyOf(
                                sites.withdrawnSupports())
                        .regions();
        var missing =
                io.farfrontier.palemirror.frontier.v3.model.geometry.RegionalBlockSet.copyOf(
                                sites.unknown())
                        .regions();
        return new BoundedPedestrianApproach.SurveyedSurface() {
            @Override
            public java.util.OptionalInt uniformSupport(WorldBounds area) {
                if (area.minX() < bootstrap.bounds().minX()
                        || area.minZ() < bootstrap.bounds().minZ()
                        || area.maxXExclusive() > bootstrap.bounds().maxXExclusive()
                        || area.maxZExclusive() > bootstrap.bounds().maxZExclusive()
                        || roads.intersects(area)
                        || local.intersects(area)) return java.util.OptionalInt.empty();
                for (var regions : java.util.List.of(surveyRegions, supports, withdrawals, missing))
                    if (io.farfrontier.palemirror.frontier.v3.model.geometry
                            .NavigationRegionCoverage.intersects(regions, area))
                        return java.util.OptionalInt.empty();
                return java.util.OptionalInt.of(bootstrap.terrain().baselineSupportY());
            }

            @Override
            public boolean excluded(SurfaceAnchor surface) {
                return sites.withdrawnSupports().contains(surface.support());
            }

            @Override
            public boolean blockKnown(BlockPosition position) {
                return !sites.unknown().contains(position);
            }

            @Override
            public SurfaceAnchor at(int x, int z) {
                var levels = surfacesAt(x, z);
                return levels.isEmpty() ? null : levels.getLast();
            }

            @Override
            public java.util.List<SurfaceAnchor> surfacesAt(int x, int z) {
                var natural = SurfaceAnchor.at(x, bootstrap.terrain().supportYAt(x, z), z);
                var road = roads.surfacesAt(x, z);
                var floor = local.surfacesAt(x, z);
                var site = siteIndex.getOrDefault(new TerrainColumn(x, z), java.util.List.of());
                if (road.isEmpty() && floor.isEmpty() && site.isEmpty())
                    return sites.withdrawnSupports().contains(natural.support())
                            ? java.util.List.of()
                            : java.util.List.of(natural);
                var result =
                        new java.util.TreeSet<SurfaceAnchor>(
                                java.util.Comparator.comparingInt(SurfaceAnchor::y));
                result.add(natural);
                result.addAll(road);
                result.addAll(floor);
                result.addAll(site);
                result.removeIf(surface -> sites.withdrawnSupports().contains(surface.support()));
                return java.util.List.copyOf(result);
            }
        };
    }

    private static synchronized ChunkSurfaceIndex frontierLocal(FrontierBootstrap bootstrap) {
        useBootstrap(bootstrap);
        if (frontierLocal == null)
            frontierLocal =
                    ChunkSurfaceIndex.of(
                            bootstrap.settlements().stream()
                                    .flatMap(
                                            settlement ->
                                                    SettlementPedestrianGround.localSupports(
                                                            bootstrap, settlement.id())
                                                            .values()
                                                            .stream())
                                    .toList());
        return frontierLocal;
    }

    private static synchronized ChunkSurfaceIndex bootstrapRoads(FrontierBootstrap bootstrap) {
        useBootstrap(bootstrap);
        if (bootstrapRoads == null)
            bootstrapRoads =
                    ChunkSurfaceIndex.of(
                            FrontierRouteNetwork.footprint(bootstrap, RouteTopology.initial())
                                    .surfaceCells()
                                    .stream()
                                    .map(SurfaceAnchor::new)
                                    .toList());
        return bootstrapRoads;
    }

    private static synchronized ChunkSurfaceIndex roads(
            FrontierBootstrap bootstrap, RouteTopology topology) {
        useBootstrap(bootstrap);
        Roads cached = cachedRoads;
        if (cached != null && cached.topology() == topology) return cached.surfaces();
        ChunkSurfaceIndex immutable =
                ChunkSurfaceIndex.of(
                        FrontierRouteNetwork.footprint(bootstrap, topology).surfaceCells().stream()
                                .map(SurfaceAnchor::new)
                                .toList());
        cachedRoads = new Roads(topology, immutable);
        return immutable;
    }

    private static synchronized ChunkSurfaceIndex local(
            FrontierBootstrap bootstrap, SubjectId settlementId) {
        useBootstrap(bootstrap);
        return LOCAL.computeIfAbsent(
                settlementId,
                id ->
                        ChunkSurfaceIndex.of(
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
