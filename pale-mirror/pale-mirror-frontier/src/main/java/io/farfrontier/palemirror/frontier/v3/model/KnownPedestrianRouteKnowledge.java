package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.navigation.UnitNavigationProfile;
import io.farfrontier.palemirror.frontier.v3.model.geometry.RegionalBlockSet;
import io.farfrontier.palemirror.frontier.v3.model.geometry.RegionalSurfaceMap;
import io.farfrontier.palemirror.frontier.v3.model.geometry.RegionalSurfaceContributions;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation;
import io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder;

import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** One read-only known-geometry policy for pedestrian routes and typed area overlays. */
public final class KnownPedestrianRouteKnowledge {
    // Bootstrap geometry is immutable. Keep one identity-keyed entry, not an unbounded
    // world registry or a record-keyed map that hashes the entire bootstrap on each lookup.
    private static FrontierBootstrap cachedBootstrap;
    private static RegionalBlockSet cachedStaticOccupancy = RegionalBlockSet.copyOf(Set.of());
    private static KnownPedestrianObstacleIndex obstacleIndex = new KnownPedestrianObstacleIndex();
    private record ViewKey(SubjectId settlementId, List<Passage> passages) { }
    private static Object viewBootstrap, viewOrgans, viewDeltas, viewTopology, viewSites, viewStorageGeometry, viewDepartureSupports;
    private static io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationGeometryState viewNavigation;
    private enum FrontierViewKey { FRONTIER }
    private record FieldScope(SubjectId site, ShipmentEndpoint storage) { }
    private record RefinedScope(Object view, SurfaceAnchor departure) { }
    private record TaskExclusionScope(Object view, Set<SurfaceAnchor> excluded) { }
    private record WitnessedScope(Object view, SurfaceAnchor departure) { }
    private static final java.util.Map<ViewKey, KnownPedestrianRouteKnowledge> VIEWS = new java.util.LinkedHashMap<>();
    private static KnownPedestrianRouteKnowledge frontierView;
    private static final ImmutableInputView<java.util.Map<TerrainColumn, List<SurfaceAnchor>>> ROAD_SUPPORTS = new ImmutableInputView<>();
    private static final ImmutableInputView<Set<BlockPosition>> RAISED_SUPPORTS = new ImmutableInputView<>();
    private enum GroundSource { ROADS, SITES }
    private static RegionalSurfaceContributions<GroundSource> groundSupports = new RegionalSurfaceContributions<>();
    private record FieldView(ResourceSite site, ResourceStorageAccess port, ResourceFieldCycle cycle,
                             Set<BlockPosition> obstructions, KnownPedestrianRouteKnowledge knowledge,
                             java.util.Map<SurfaceAnchor, KnownPedestrianRouteKnowledge> witnessedDepartures) { }
    private static final java.util.Map<SubjectId, FieldView> FIELDS = new java.util.LinkedHashMap<>();
    private final FrontierBootstrap bootstrap;
    private final RegionalBlockSet hard;
    private final Set<BlockPosition> raisedDepartureSupports;
    private final BoundedPedestrianApproach.SurveyedSurface surveyed;
    private final Object scopeKey;
    private final io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianGeometryRevision capturedGround;
    private final java.util.Map<TerrainColumn, List<SurfaceAnchor>> overlay;
    private final io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteGeometry geometry;
    private final io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteGeometry terrainGeometry;
    private final UnitNavigationProfile profile;
    private final java.util.Map<UnitNavigationProfile, KnownPedestrianRouteKnowledge> profiles = new java.util.LinkedHashMap<>();
    private final java.util.Map<SurfaceAnchor, io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteGeometry> departures = new java.util.LinkedHashMap<>();
    private record JourneyKey(List<SettlementPassage> passages) { }
    private static final java.util.Map<JourneyKey, KnownPedestrianRouteKnowledge> JOURNEYS = new java.util.LinkedHashMap<>();

    /** A task declares a real facility passage, never an arbitrary list of cells to clear. */
    public record Passage(SettlementStructure facility, Reach reach) {
        public enum Reach { EXTERIOR, PUBLIC_ACCESS, STATIONS }

        public Passage {
            Objects.requireNonNull(facility, "pedestrian passage facility");
            Objects.requireNonNull(reach, "pedestrian passage reach");
        }
        // Identity is cheap to hash; equality still checks the complete declaration.
        @Override public int hashCode() { return 31 * facility.id().hashCode() + reach.hashCode(); }
    }

    public record SettlementPassage(SubjectId settlementId, Passage passage) {
        public SettlementPassage { Objects.requireNonNull(settlementId); Objects.requireNonNull(passage); }
    }

    /** Public entrances survive a container transaction. They grant walking, not stock or private workstation access. */
    public static KnownPedestrianRouteKnowledge forPublicJourney(FrontierWorldState state, SubjectId home,
                                                                 List<SettlementPassage> additional) {
        var settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), home);
        var passages = new ArrayList<SettlementPassage>();
        settlement.structures().stream().filter(facility -> FrontierTraversalPlan.facilityPort(facility).isPresent())
                .forEach(facility -> passages.add(new SettlementPassage(home, new Passage(facility, Passage.Reach.PUBLIC_ACCESS))));
        passages.addAll(additional);
        return passages.isEmpty() ? forFrontier(state) : forJourney(state, passages.stream().distinct().toList());
    }

    /** Cross-settlement tasks declare their authorized ports, not their own obstacle rules. */
    public static synchronized KnownPedestrianRouteKnowledge forJourney(FrontierWorldState state, List<SettlementPassage> passages) {
        passages = List.copyOf(passages);
        if (passages.isEmpty() || passages.size() > 8)
            throw new IllegalArgumentException("journey needs bounded declared facility passages");
        refresh(state);
        JourneyKey key = new JourneyKey(passages);
        var cached = JOURNEYS.get(key);
        if (cached != null) return cached;
        var cleared = new HashSet<BlockPosition>();
        for (var declaration : passages) {
            var settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), declaration.settlementId());
            cleared.addAll(passageClearance(settlement, List.of(declaration.passage())));
        }
        var hard = obstacleIndex.withPassages(cleared);
        var result = new KnownPedestrianRouteKnowledge(state.bootstrap(), hard,
                accessSurvey(state, KnownPedestrianGround.forFrontier(state), cleared), key,
                java.util.Map.of(), raisedDepartureSupports(state));
        if (JOURNEYS.size() >= 32) JOURNEYS.clear();
        JOURNEYS.put(key, result); return result;
    }

    private KnownPedestrianRouteKnowledge(FrontierBootstrap bootstrap, Set<BlockPosition> hard,
            BoundedPedestrianApproach.SurveyedSurface surveyed, Object scopeKey,
            java.util.Map<TerrainColumn, List<SurfaceAnchor>> overlay, Set<BlockPosition> raisedDepartureSupports) {
        this(bootstrap, hard, surveyed, scopeKey, overlay, raisedDepartureSupports, KnownPedestrianGeometryScopes.groundRevision());
    }
    private KnownPedestrianRouteKnowledge(FrontierBootstrap bootstrap, Set<BlockPosition> hard,
            BoundedPedestrianApproach.SurveyedSurface surveyed, Object scopeKey,
            java.util.Map<TerrainColumn, List<SurfaceAnchor>> overlay, Set<BlockPosition> raisedDepartureSupports,
            io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianGeometryRevision capturedGround) {
        this.bootstrap = bootstrap;
        this.hard = RegionalBlockSet.copyOf(hard);
        this.raisedDepartureSupports = Set.copyOf(raisedDepartureSupports);
        this.surveyed = surveyed;
        this.scopeKey = Objects.requireNonNull(scopeKey); this.overlay = RegionalSurfaceMap.copyOf(overlay);
        this.capturedGround = capturedGround;
        var revision = KnownPedestrianGeometryScopes.revision(scopeKey, this.hard, this.overlay, capturedGround);
        this.geometry = KnownPedestrianNavigation.geometry(bootstrap, this.hard, surveyed, revision, Optional.of(revision));
        this.terrainGeometry = geometry; this.profile = null; // Unbound terrain knowledge grants no actor profile.
    }

    private KnownPedestrianRouteKnowledge(KnownPedestrianRouteKnowledge source,
            UnitNavigationProfile profile) {
        this.bootstrap = source.bootstrap; this.hard = source.hard; this.raisedDepartureSupports = source.raisedDepartureSupports;
        this.surveyed = source.surveyed; this.scopeKey = source.scopeKey; this.overlay = source.overlay;
        this.capturedGround = source.capturedGround;
        this.terrainGeometry = source.terrainGeometry; this.profile = Objects.requireNonNull(profile);
        this.geometry = profiled(terrainGeometry);
    }
    /** Bind the exact producer declaration, never infer an envelope from activity or kind. */
    public KnownPedestrianRouteKnowledge forActor(FrontierWorldState state, SubjectId actorId) {
        var actor = Objects.requireNonNull(state.actorLocations().get(actorId), "navigation actor declaration");
        return forProfile(actor.navigationProfile());
    }
    public synchronized KnownPedestrianRouteKnowledge forProfile(UnitNavigationProfile requested) {
        Objects.requireNonNull(requested);
        if (!requested.capabilities().contains(TraversalCapability.PEDESTRIAN))
            throw new IllegalArgumentException("known pedestrian geometry requires an explicitly declared walking capability");
        if (requested.equals(profile)) return this;
        if (profiles.size() >= 16) profiles.clear();
        return profiles.computeIfAbsent(requested, value -> new KnownPedestrianRouteKnowledge(this, value));
    }
    public KnownPedestrianRouteKnowledge forActors(FrontierWorldState state, List<SubjectId> actorIds) {
        if (actorIds.isEmpty() || actorIds.size() > 32 || actorIds.stream().distinct().count() != actorIds.size())
            throw new IllegalArgumentException("navigation group needs a bounded exact roster");
        return forProfile(UnitNavigationProfile.enclosing(actorIds.stream()
                .map(id -> Objects.requireNonNull(state.actorLocations().get(id), "navigation group actor").navigationProfile()).toList()));
    }
    private io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteGeometry profiled(
            io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteGeometry terrain) {
        return profile == null ? terrain : new io.farfrontier.palemirror.frontier.v3.model.navigation.ProfiledPedestrianGeometry(terrain, profile, hard::contains);
    }

    public static List<SurfaceAnchor> path(FrontierWorldState state, SubjectId settlementId,
                                           SurfaceAnchor start, MovementOrder order,
                                           List<Passage> passages) {
        return forSettlement(state, settlementId, passages).forActor(state, order.actorId()).path(start, order);
    }

    /** Reuse one immutable view while trying several legal destinations in one planning turn. */
    public static synchronized KnownPedestrianRouteKnowledge forSettlement(FrontierWorldState state,
                                                               SubjectId settlementId, List<Passage> passages) {
        Objects.requireNonNull(state, "pedestrian route state");
        passages = List.copyOf(Objects.requireNonNull(passages, "pedestrian route passages"));
        refresh(state);
        ViewKey key = new ViewKey(settlementId, passages);
        KnownPedestrianRouteKnowledge prior = VIEWS.get(key);
        if (prior != null) return prior;
        Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), settlementId);
        Set<BlockPosition> hard = occupied(state, settlement, passages);
        KnownPedestrianRouteKnowledge result = new KnownPedestrianRouteKnowledge(state.bootstrap(), hard,
                accessSurvey(state, KnownPedestrianGround.forFrontier(state), passageClearance(settlement, passages)),
                key, java.util.Map.of(), raisedDepartureSupports(state));
        if (VIEWS.size() >= 64) VIEWS.clear();
        VIEWS.put(key, result);
        return result;
    }

    /** Same obstacle policy for cross-settlement approaches; no actor is a hard wall. */
    public static synchronized KnownPedestrianRouteKnowledge forFrontier(FrontierWorldState state) {
        Objects.requireNonNull(state, "frontier pedestrian route state");
        refresh(state);
        if (frontierView == null) {
            var hard = obstacleIndex.complete();
            frontierView = new KnownPedestrianRouteKnowledge(state.bootstrap(), hard,
                    accessSurvey(state, KnownPedestrianGround.forFrontier(state), Set.of()),
                    FrontierViewKey.FRONTIER, java.util.Map.of(), raisedDepartureSupports(state));
        }
        return frontierView;
    }

    private static void refresh(FrontierWorldState state) {
        var sites = KnownSiteGeometry.forState(state);
        var storage = KnownStorageGeometry.forState(state);
        var departures = KnownStorageGeometry.fixedSupports(state);
        if (viewBootstrap != state.bootstrap() || viewOrgans != state.hiveColony().addedOrgans()
                || viewDeltas != state.physicalDeltas() || viewTopology != state.routeTopology()
                || viewSites != sites || viewStorageGeometry != storage || viewDepartureSupports != departures
                || viewNavigation != state.navigationGeometry()) {
            if (viewBootstrap != state.bootstrap()) {
                obstacleIndex = new KnownPedestrianObstacleIndex(); groundSupports = new RegionalSurfaceContributions<>();
            }
            obstacleIndex.refresh(state, sites, storage, () -> retainedStaticOccupancy(state.bootstrap()));
            VIEWS.clear();
            FIELDS.clear();
            var roadCells = FrontierRouteNetwork.surfaceCells(state.bootstrap(), state.routeTopology());
            var roads = ROAD_SUPPORTS.get(List.of(state.bootstrap(), roadCells), () -> {
                var supports = new java.util.LinkedHashMap<TerrainColumn, List<SurfaceAnchor>>();
                for (var position : roadCells) {
                    var column = new TerrainColumn(position.x(), position.z());
                    var levels = new ArrayList<>(supports.getOrDefault(column, List.of()));
                    var surface = new SurfaceAnchor(position);
                    if (!levels.contains(surface)) levels.add(surface);
                    levels.sort(java.util.Comparator.comparingInt(SurfaceAnchor::y)); supports.put(column, List.copyOf(levels));
                }
                return RegionalSurfaceMap.copyOf(supports);
            });
            groundSupports.replace(GroundSource.ROADS, RegionalSurfaceMap.copyOf(roads));
            var supports = groundSupports.replace(GroundSource.SITES, RegionalSurfaceMap.copyOf(sites.supports()));
            KnownPedestrianGeometryScopes.refresh(state.bootstrap(), obstacleIndex.groundChanges(), supports,
                    sites.withdrawnSupports(), sites.unknown(), state.physicalDeltas().keySet());
            KnownPedestrianGeometryScopes.observeShapes(viewNavigation, state.navigationGeometry(), sites.retainedBlocks());
            viewNavigation = state.navigationGeometry();
            JOURNEYS.clear();
            frontierView = null;
            viewBootstrap = state.bootstrap(); viewOrgans = state.hiveColony().addedOrgans();
            viewDeltas = state.physicalDeltas(); viewTopology = state.routeTopology();
            viewSites = sites; viewStorageGeometry = storage; viewDepartureSupports = departures;
        }
    }

    public boolean traversable(List<SurfaceAnchor> remainingPath) {
        return remainingPath.stream().allMatch(surface -> bootstrap.bounds().contains(surface.support())
                && geometry.knowsSupport(surface) && !geometry.blocked(surface));
    }
    /** Transit knowledge does not imply that a complete body can centre at a service station. */
    public boolean clearStation(SurfaceAnchor surface) {
        return geometry.clearStation(surface);
    }
    public int formationSpacing(int requested) {
        return profile == null ? requested : Math.max(requested, (int) Math.ceil(profile.width()));
    }

    public List<SurfaceAnchor> plannedPath(SurfaceAnchor start, MovementOrder order) {
        return KnownPedestrianNavigation.plannedRoute(geometryFrom(start), start, order);
    }
    public List<SurfaceAnchor> plannedSegment(SurfaceAnchor start, MovementOrder order) {
        return KnownPedestrianNavigation.plannedSegment(geometryFrom(start), start, order);
    }
    public List<SurfaceAnchor> plannedSegment(SurfaceAnchor start, MovementOrder order, List<SurfaceAnchor> hint) {
        return KnownPedestrianNavigation.plannedSegment(geometryFrom(start), start, order, hint);
    }
    public List<SurfaceAnchor> retainedPath(SurfaceAnchor start, MovementOrder order) {
        return KnownPedestrianNavigation.retainedRoute(geometryFrom(start), start, order);
    }
    public List<SurfaceAnchor> plannedBetween(SurfaceAnchor start, SurfaceAnchor target) {
        return KnownPedestrianNavigation.plannedBetween(geometryFrom(start), start, target);
    }
    public io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteEstimate estimateBetween(
            SurfaceAnchor start, SurfaceAnchor target) {
        return KnownPedestrianNavigation.estimateBetween(geometryFrom(start), start, target);
    }
    public io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianAccessSelection accessSelection(SurfaceAnchor origin) {
        return new io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianAccessSelection(geometryFrom(origin), origin);
    }
    /** Geometry-only admission, without inventing an actor assignment or movement authority. */
    public boolean reachable(SurfaceAnchor start, SurfaceAnchor target) {
        try {
            return io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRoutePlanning
                    .calculate(geometryFrom(start), start, target).status()
                    == io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult.Status.FOUND;
        } catch (KnownPedestrianNavigation.RouteUnavailable unavailable) { return false; }
    }
    /** Connected known supports inside the existing one-cell local collision latitude.
     * No task goal, route clock, arrival or physical actor position is changed. */
    public List<SurfaceAnchor> localPlacement(SurfaceAnchor origin) {
        return List.copyOf(localPlacementRoutes(origin).keySet());
    }
    /** Bounded local access proofs; this small placement envelope is not a world route search. */
    public java.util.Map<SurfaceAnchor, List<SurfaceAnchor>> localPlacementRoutes(SurfaceAnchor origin) {
        return localRoutes(origin, LocalNavigationEnvelope.around(origin.standingBody(), origin.standingBody()), true);
    }
    /** Small exact known-geometry escape proofs; no cooperative/global route queue or movement authority. */
    public java.util.Map<SurfaceAnchor, List<SurfaceAnchor>> localMovementRoutes(SurfaceAnchor origin) {
        return localRoutes(origin, LocalNavigationEnvelope.localLeg(List.of(origin), origin), false);
    }
    /** A bounded miss with incomplete collision facts is not proof of disconnection. */
    public List<SurfaceAnchor> localMovementTo(SurfaceAnchor origin, java.util.function.Predicate<SurfaceAnchor> destination) {
        var result = scanLocal(origin, LocalNavigationEnvelope.localLeg(List.of(origin), origin), false);
        return result.routes().entrySet().stream().filter(entry -> destination.test(entry.getKey()))
                .min(java.util.Comparator.<java.util.Map.Entry<SurfaceAnchor, List<SurfaceAnchor>>>comparingInt(entry -> entry.getValue().size())
                        .thenComparingInt(entry -> entry.getKey().x()).thenComparingInt(entry -> entry.getKey().z())
                        .thenComparingInt(entry -> entry.getKey().y()))
                .map(java.util.Map.Entry::getValue).orElseThrow(() -> new KnownPedestrianNavigation.RouteUnavailable(
                        result.unknown() ? io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult.Status.UNKNOWN_GEOMETRY
                                : io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult.Status.NO_PATH,
                        "retained departure has no bounded known continuation"));
    }
    private record LocalRoutes(java.util.Map<SurfaceAnchor, List<SurfaceAnchor>> routes, boolean unknown) { }
    private java.util.Map<SurfaceAnchor, List<SurfaceAnchor>> localRoutes(SurfaceAnchor origin, LocalNavigationEnvelope scope,
            boolean placement) {
        return scanLocal(origin, scope, placement).routes();
    }
    private LocalRoutes scanLocal(SurfaceAnchor origin, LocalNavigationEnvelope scope, boolean placement) {
        var geometry = geometryFrom(origin);
        boolean unknown = !geometry.clearanceKnown(origin);
        var queue = new java.util.ArrayDeque<SurfaceAnchor>();
        var routes = new java.util.LinkedHashMap<SurfaceAnchor, List<SurfaceAnchor>>();
        queue.add(origin); routes.put(origin, List.of(origin));
        while (!queue.isEmpty()) {
            var current = queue.removeFirst();
            for (int[] offset : List.of(new int[]{0, -1}, new int[]{-1, 0}, new int[]{1, 0}, new int[]{0, 1})) {
                var probe = current.support().offset(offset[0], 0, offset[1]);
                if (!scope.contains(probe) || !geometry.bounds().contains(probe)) continue;
                var levels = geometry.surfacesAt(probe.x(), probe.z());
                if (levels.isEmpty()) unknown = true;
                for (var next : levels) {
                    if (!geometry.clearanceKnown(next)) unknown = true;
                    if (!scope.contains(next.support()) || !geometry.bounds().contains(next.support())
                            || geometry.blocked(next) || routes.containsKey(next)) continue;
                    boolean connected = placement && current.equals(origin) ? geometry.canProjectPlacement(current, next)
                            : geometry.canTraverse(current, next);
                    if (connected) {
                        var route = new java.util.ArrayList<>(routes.get(current)); route.add(next);
                        routes.put(next, List.copyOf(route)); queue.addLast(next);
                    }
                }
            }
        }
        return new LocalRoutes(java.util.Collections.unmodifiableMap(routes), unknown);
    }
    /** Projection may leave an unavailable retained birth cell; this does not authorize HOT locomotion. */
    public void requireLocalPlacementRoute(io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteReceipt receipt) {
        var route = receipt.route(); var origin = route.getFirst();
        var scope = LocalNavigationEnvelope.around(origin.standingBody(), origin.standingBody());
        if (route.stream().anyMatch(surface -> !scope.contains(surface.support())))
            throw new IllegalArgumentException("placement access leaves its bounded local envelope");
        // The retained origin is historical projection knowledge, not a claim that its
        // current body volume is clear. Every proposed actual placement/connecting cell is clear.
        var geometry = geometryFrom(origin);
        if (route.size() < 2 || !geometry.canProjectPlacement(origin, route.get(1)))
            throw new IllegalArgumentException("placement lacks an exact adjacent clear contact");
        KnownPedestrianNavigation.requireRoute(geometry, route.subList(1, route.size()));
    }
    public List<SurfaceAnchor> plannedPath(SurfaceAnchor start, MovementOrder order, List<SurfaceAnchor> hint) {
        return KnownPedestrianNavigation.plannedRoute(geometryFrom(start), start, order, hint);
    }
    public Optional<io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult> planningEvidence(SurfaceAnchor start, SurfaceAnchor target) {
        return io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRoutePlanning.peek(geometryFrom(start), start, target);
    }
    public Optional<io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult> segmentEvidence(SurfaceAnchor start, SurfaceAnchor target) {
        return io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRoutePlanning.peek(geometryFrom(start), start, target,
                io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteExtent.MOVEMENT_SEGMENT);
    }

    public void requireRoute(List<SurfaceAnchor> route) {
        if (route.isEmpty()) throw new IllegalArgumentException("accepted route is empty");
        KnownPedestrianNavigation.requireRoute(geometryFrom(route.getFirst()), route);
    }

    /** Verify selected area access through the same retained-departure geometry as its producer. */
    public void requireAccess(io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianAccessReceipt access,
            SurfaceAnchor origin, Set<SurfaceAnchor> destinations) {
        access.require(geometryFrom(origin), origin, destinations);
    }

    /** The exact retained departure may refine its one column; it cannot invent remote terrain. */
    private synchronized io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteGeometry geometryFrom(SurfaceAnchor start) {
        // Surveyed existence and permission are independent. A captured chest
        // top is a known support but remains excluded from ordinary transit;
        // its retained occupant still needs the exact departure permission.
        if (geometry.knowsSupport(start) && !geometry.supportExcluded(start)) return geometry;
        if (!geometry.clearanceKnown(start)) throw new KnownPedestrianNavigation.RouteUnavailable(
                io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult.Status.UNKNOWN_GEOMETRY,
                "retained departure lacks current block facts: " + start);
        // A known container remains a transit obstacle, but an actor physically
        // pushed onto it can depart from that retained contact. Only this origin
        // is refined; no destination, body clearance or foreign scar is opened.
        boolean raisedContact = surveyed.surfacesAt(start.x(), start.z()).stream().anyMatch(surface -> start.y() == surface.y() + 1)
                && raisedDepartureSupports.contains(start.support())
                && !hard.contains(start.support().offset(0, 1, 0)) && !hard.contains(start.support().offset(0, 2, 0));
        if (!bootstrap.bounds().contains(start.support()) || surveyed.excluded(start) && !raisedContact || blocked(start, hard) && !raisedContact)
            throw new KnownPedestrianNavigation.RouteUnavailable(
                    io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult.Status.NO_PATH,
                    "retained departure violates known hard scope: " + start);
        if (surveyed.surfacesAt(start.x(), start.z()).stream().noneMatch(surface -> Math.abs((long) start.y() - surface.y()) <= 1L))
            throw new KnownPedestrianNavigation.RouteUnavailable(
                    io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult.Status.UNKNOWN_GEOMETRY,
                    "retained departure is not on compatible known support: " + start);
        var previous = departures.get(start);
        if (previous != null) return previous;
        var refinedOverlay = new java.util.LinkedHashMap<>(overlay);
        refinedOverlay.put(new TerrainColumn(start.x(), start.z()), List.of(start));
        var revision = KnownPedestrianGeometryScopes.revision(new RefinedScope(scopeKey, start), hard, refinedOverlay, capturedGround);
        var refined = new io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteGeometry() {
            @Override public WorldBounds bounds() { return terrainGeometry.bounds(); }
            @Override public Object version() { return revision; }
            @Override public Optional<io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianGeometryRevision> regionalRevision() {
                return Optional.of(revision);
            }
            @Override public SurfaceAnchor supportAt(int x, int z) { return x == start.x() && z == start.z() ? start : surveyed.at(x, z); }
            @Override public List<SurfaceAnchor> surfacesAt(int x, int z) {
                var levels = surveyed.surfacesAt(x, z);
                if (x != start.x() || z != start.z() || levels.contains(start)) return levels;
                return java.util.stream.Stream.concat(levels.stream(), java.util.stream.Stream.of(start))
                        .sorted(java.util.Comparator.comparingInt(SurfaceAnchor::y)).toList();
            }
            @Override public boolean blocked(SurfaceAnchor surface) {
                return !(raisedContact && surface.equals(start)) && terrainGeometry.blocked(surface);
            }
            @Override public boolean supportExcluded(SurfaceAnchor surface) {
                return !(raisedContact && surface.equals(start)) && terrainGeometry.supportExcluded(surface);
            }
            @Override public boolean clearBody(SurfaceAnchor surface) { return terrainGeometry.clearBody(surface); }
            @Override public boolean clearanceKnown(SurfaceAnchor surface) { return terrainGeometry.clearanceKnown(surface); }
            @Override public boolean blockKnown(BlockPosition position) { return terrainGeometry.blockKnown(position); }
            @Override public java.util.Optional<io.farfrontier.palemirror.frontier.v3.model.geometry.NavigationCollisionView> collisions() { return terrainGeometry.collisions(); }
            @Override public boolean supportAvailable(SurfaceAnchor surface) {
                return raisedContact && surface.equals(start) || terrainGeometry.supportAvailable(surface);
            }
        };
        if (departures.size() >= 32) departures.remove(departures.keySet().iterator().next());
        var result = profiled(refined); departures.put(start, result); return result;
    }

    private static RegionalBlockSet occupied(FrontierWorldState state, Settlement settlement,
                                               List<Passage> passages) {
        return obstacleIndex.withPassages(passageClearance(settlement, passages));
    }
    private static Set<BlockPosition> passageClearance(Settlement settlement, List<Passage> passages) {
        Set<BlockPosition> cleared = new HashSet<>();
        for (Passage passage : passages) {
            SettlementStructure structure = settlement.structures().stream()
                    .filter(candidate -> candidate.id().equals(passage.facility().id()))
                    .reduce((left, right) -> { throw new IllegalArgumentException("duplicate pedestrian passage facility"); })
                    .orElseThrow(() -> new IllegalArgumentException("pedestrian passage has no local facility"));
            if (!structure.equals(passage.facility()))
                throw new IllegalArgumentException("pedestrian passage differs from its declared facility");
            FacilityTraversalPort port = FrontierTraversalPlan.facilityPort(structure)
                    .orElseThrow(() -> new IllegalArgumentException("pedestrian passage has no declared traversal port"));
            clearance(cleared, port.exteriorApproach().getFirst());
            if (passage.reach() == Passage.Reach.PUBLIC_ACCESS || passage.reach() == Passage.Reach.STATIONS) {
                port.exteriorApproach().forEach(surface -> clearance(cleared, surface));
                FrontierGrayboxPlan.publicAccessSurfaces(structure).forEach(surface -> clearance(cleared, surface));
            }
            if (passage.reach() == Passage.Reach.STATIONS) {
                port.ingressSurfaces().forEach(surface -> clearance(cleared, surface));
                port.stations().forEach(surface -> clearance(cleared, surface));
            }
        }
        // A witnessed physical change can close even an authored passage. Never clear it
        // together with the planned structure, and never treat another moving actor as a wall.
        return Set.copyOf(cleared);
    }
    private static Set<BlockPosition> raisedDepartureSupports(FrontierWorldState state) {
        var fixed = KnownStorageGeometry.fixedSupports(state);
        return RAISED_SUPPORTS.get(List.of(state.bootstrap(), fixed, state.physicalDeltas()), () -> fixed.stream()
                .filter(position -> !state.physicalDeltas().containsKey(position))
                .collect(java.util.stream.Collectors.toUnmodifiableSet()));
    }
    /** Facility access and container tops are explicit contact permissions, not
     * collision volumes. Opening a passage never opens a physical container. */
    private static BoundedPedestrianApproach.SurveyedSurface accessSurvey(FrontierWorldState state,
            BoundedPedestrianApproach.SurveyedSurface ground, Set<BlockPosition> cleared) {
        var denied = RegionalBlockSet.copyOf(retainedStaticOccupancy(state.bootstrap()))
                .overlay(cleared, KnownStorageGeometry.fixedSupports(state));
        return BoundedPedestrianApproach.excluding(ground, denied);
    }

    /** Typed field overlay on the same structural/physical knowledge and route operation. */
    public static synchronized KnownPedestrianRouteKnowledge forField(FrontierWorldState state, ResourceSite site,
                                                         ResourceFieldCycle cycle, ResourceStorageAccess port,
                                                         SurfaceAnchor witnessedStart) {
        Objects.requireNonNull(state, "field route state");
        Objects.requireNonNull(site, "field route site");
        Objects.requireNonNull(cycle, "field route cycle");
        Objects.requireNonNull(port, "field route depot");
        Objects.requireNonNull(witnessedStart, "field route start");
        refresh(state);
        if (!site.equals(state.resourceSite(site.id())) || cycle != state.resourceSites().cycle(site.id()))
            throw new IllegalArgumentException("field route overlay is not the current canonical field");
        if (!port.settlementId().equals(site.settlementId()))
            throw new IllegalArgumentException("field route has a foreign depot passage");
        Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), site.settlementId());
        if (!ResourceStorageAccess.at(state, port.containerId()).equals(port))
            throw new IllegalArgumentException("field route storage passage differs from its declaration");
        FieldView cached = FIELDS.get(site.id());
        if (cached == null || cached.cycle() != cycle || !cached.site().equals(site) || !cached.port().equals(port)) {
            Set<BlockPosition> obstructions = fieldObstructions(cycle);
            if (cached != null && cached.site().equals(site) && cached.port().equals(port)
                    && cached.cycle().layout() == cycle.layout() && cached.obstructions().equals(obstructions)) {
                cached = new FieldView(site, port, cycle, cached.obstructions(), cached.knowledge(), cached.witnessedDepartures());
            } else {
                var passages = ShipmentEndpointComposition.passages(state, port.endpoint()).stream()
                        .map(SettlementPassage::passage).toList();
                var hard = occupied(state, settlement, passages).overlay(Set.of(), Set.copyOf(site.irrigationSlots()))
                        .overlay(Set.of(), obstructions);
                var knowledge = new KnownPedestrianRouteKnowledge(state.bootstrap(), hard,
                        BoundedPedestrianApproach.excluding(BoundedPedestrianApproach.excluding(
                                accessSurvey(state, ResourceSiteHarvestKnownGeometry.surveyedSupports(state, site),
                                        passageClearance(settlement, passages)), Set.copyOf(site.irrigationSlots())),
                                obstructions), new FieldScope(site.id(), port.endpoint()),
                        site.cropSlots().stream().collect(java.util.stream.Collectors.toMap(
                                crop -> new TerrainColumn(crop.x(), crop.z()), crop -> List.of(new SurfaceAnchor(crop.offset(0, -1, 0))))),
                        raisedDepartureSupports(state));
                cached = new FieldView(site, port, cycle, obstructions, knowledge, new java.util.LinkedHashMap<>());
            }
            if (FIELDS.size() >= 64 && !FIELDS.containsKey(site.id())) FIELDS.clear();
            FIELDS.put(site.id(), cached);
        }
        var knowledge = cached.knowledge();
        boolean compatibleStart = knowledge.surveyed.surfacesAt(witnessedStart.x(), witnessedStart.z()).stream()
                .anyMatch(surface -> Math.abs((long) witnessedStart.y() - surface.y()) <= 1L);
        if (!state.bootstrap().bounds().contains(witnessedStart.support())
                || !knowledge.geometry.knowsSupport(witnessedStart) && !compatibleStart
                    && !port.ownedAccessSurfaces().contains(witnessedStart))
            throw new KnownPedestrianNavigation.RouteUnavailable(
                    "field worker body is not on retained known support");
        if (!knowledge.geometry.knowsSupport(witnessedStart) && !port.ownedAccessSurfaces().contains(witnessedStart)) {
            // Preserve the existing exact witnessed-column exception, never a remote passage.
            var departure = cached.witnessedDepartures().get(witnessedStart);
            if (departure != null) return departure;
            var cleared = new HashSet<BlockPosition>(); clearance(cleared, witnessedStart);
            var hard = knowledge.hard.overlay(cleared, Set.of());
            departure = new KnownPedestrianRouteKnowledge(state.bootstrap(), hard, knowledge.surveyed,
                    new WitnessedScope(knowledge.scopeKey, witnessedStart), knowledge.overlay, knowledge.raisedDepartureSupports, knowledge.capturedGround);
            if (cached.witnessedDepartures().size() >= 32)
                cached.witnessedDepartures().remove(cached.witnessedDepartures().keySet().iterator().next());
            cached.witnessedDepartures().put(witnessedStart, departure);
            return departure;
        }
        // This factory supplies knowledge, not actor clearance admission. The shared
        // profiled route query distinguishes UNKNOWN_GEOMETRY from a proven obstacle.
        // In particular, a pending neighbouring block observation is not NO_PATH.
        return knowledge;
    }

    /** Only obstacle facts affect geometry; growth, yield and worker progress do not. */
    private static Set<BlockPosition> fieldObstructions(ResourceFieldCycle cycle) {
        Set<BlockPosition> hard = new HashSet<>();
        for (ResourceFieldLayout.Cell cell : cycle.layout().cells()) {
            ResourceFieldCycle.CellState condition = cycle.cell(cell.id());
            if (condition.crop() == ResourceFieldCycle.Crop.OBSTRUCTED
                    || condition.soil() == ResourceFieldCycle.Soil.OBSTRUCTED)
                hard.add(cell.soil().support());
            if (condition.workAccessBlocked())
                hard.add(cell.workstation().support().offset(0, 2, 0));
        }
        return Set.copyOf(hard);
    }

    public List<SurfaceAnchor> path(SurfaceAnchor start, MovementOrder order) {
        Objects.requireNonNull(start, "pedestrian route start");
        Objects.requireNonNull(order, "pedestrian route order");
        return KnownPedestrianNavigation.route(geometryFrom(start), start, order);
    }

    /** Explicit task exclusions for bounded alternative selection, not caller-owned terrain rules. */
    public KnownPedestrianRouteKnowledge withTaskExclusions(Set<SurfaceAnchor> excluded) {
        excluded = Set.copyOf(Objects.requireNonNull(excluded, "excluded task surfaces"));
        if (excluded.size() > io.farfrontier.palemirror.frontier.v3.model.navigation.TraversalRejoin.MAX_SURFACES)
            throw new IllegalArgumentException("task surface exclusions exceed the bounded path profile");
        if (excluded.isEmpty()) return this;
        var forbidden = excluded.stream().map(SurfaceAnchor::support).collect(java.util.stream.Collectors.toSet());
        var allowedDepartures = new HashSet<>(raisedDepartureSupports);
        excluded.forEach(surface -> allowedDepartures.remove(surface.support()));
        var constrainedKnowledge = new KnownPedestrianRouteKnowledge(bootstrap, hard, BoundedPedestrianApproach.excluding(surveyed, forbidden),
                new TaskExclusionScope(scopeKey, excluded), overlay, allowedDepartures, capturedGround);
        return profile == null ? constrainedKnowledge : constrainedKnowledge.forProfile(profile);
    }

    /** Explicit task exclusions for bounded alternative selection, not caller-owned terrain rules. */
    public List<SurfaceAnchor> pathAvoiding(SurfaceAnchor start, MovementOrder order, Set<SurfaceAnchor> excluded) {
        excluded = Set.copyOf(Objects.requireNonNull(excluded, "excluded task surfaces"));
        if (excluded.size() > io.farfrontier.palemirror.frontier.v3.model.navigation.TraversalRejoin.MAX_SURFACES)
            throw new IllegalArgumentException("task surface exclusions exceed the bounded path profile");
        if (excluded.isEmpty()) return path(start, order);
        return withTaskExclusions(excluded).path(start, order);
    }

    public SurfaceAnchor supportAt(int x, int z) {
        return surveyed.at(x, z);
    }
    public List<SurfaceAnchor> surfacesAt(int x, int z) { return geometry.surfacesAt(x, z); }
    /** Read-only known geometry; callers may choose semantic goals, not invent traversability. */
    public io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteGeometry geometry() { return geometry; }

    static synchronized Set<BlockPosition> staticOccupancy(FrontierBootstrap bootstrap) {
        return new HashSet<>(retainedStaticOccupancy(bootstrap));
    }
    private static Set<BlockPosition> retainedStaticOccupancy(FrontierBootstrap bootstrap) {
        if (cachedBootstrap != bootstrap) {
            cachedStaticOccupancy = RegionalBlockSet.copyOf(compileStaticOccupancy(bootstrap));
            cachedBootstrap = bootstrap;
        }
        // Passages and dynamic overlays mutate their own view, never the cached base.
        return cachedStaticOccupancy;
    }

    private static Set<BlockPosition> compileStaticOccupancy(FrontierBootstrap bootstrap) {
        Set<BlockPosition> occupied = new HashSet<>();
        for (Settlement settlement : bootstrap.settlements())
            occupied.addAll(FrontierGrayboxPlan.intactStructurePedestrianObstacles(
                    bootstrap.terrain(), settlement.structures()));
        occupied.addAll(FrontierGrayboxPlan.intactOrganOccupancy(bootstrap.hive().organs()));
        return occupied;
    }

    private static boolean blocked(SurfaceAnchor surface, Set<BlockPosition> occupied) {
        return occupied.contains(surface.support()) || occupied.contains(surface.support().offset(0, 1, 0))
                || occupied.contains(surface.support().offset(0, 2, 0));
    }

    private static void clearance(Set<BlockPosition> cleared, SurfaceAnchor surface) {
        cleared.add(surface.support());
        cleared.add(surface.support().offset(0, 1, 0));
        cleared.add(surface.support().offset(0, 2, 0));
    }
}
