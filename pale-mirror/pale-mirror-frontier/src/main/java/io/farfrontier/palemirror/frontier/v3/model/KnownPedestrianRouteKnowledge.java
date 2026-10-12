package io.farfrontier.palemirror.frontier.v3.model;

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
    private static Set<BlockPosition> cachedStaticOccupancy = Set.of();
    private record ViewKey(SubjectId settlementId, List<Passage> passages) { }
    private static Object viewBootstrap, viewOrgans, viewDeltas, viewTopology, viewSites, viewStorageGeometry, viewDepartureSupports;
    private enum FrontierViewKey { FRONTIER }
    private record FieldScope(SubjectId site, ShipmentEndpoint storage) { }
    private record RefinedScope(Object view, SurfaceAnchor departure) { }
    private record TaskExclusionScope(Object view, Set<SurfaceAnchor> excluded) { }
    private record WitnessedScope(Object view, SurfaceAnchor departure) { }
    private static final java.util.Map<ViewKey, KnownPedestrianRouteKnowledge> VIEWS = new java.util.LinkedHashMap<>();
    private static KnownPedestrianRouteKnowledge frontierView;
    private record FieldView(ResourceSite site, ResourceStorageAccess port, ResourceFieldCycle cycle,
                             Set<BlockPosition> obstructions, KnownPedestrianRouteKnowledge knowledge,
                             java.util.Map<SurfaceAnchor, KnownPedestrianRouteKnowledge> witnessedDepartures) { }
    private static final java.util.Map<SubjectId, FieldView> FIELDS = new java.util.LinkedHashMap<>();
    private final FrontierBootstrap bootstrap;
    private final Set<BlockPosition> hard;
    private final Set<BlockPosition> raisedDepartureSupports;
    private final BoundedPedestrianApproach.SurveyedSurface surveyed;
    private final Object scopeKey;
    private final java.util.Map<TerrainColumn, SurfaceAnchor> overlay;
    private final io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteGeometry geometry;
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
        Set<BlockPosition> hard = null;
        for (var declaration : passages) {
            var settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), declaration.settlementId());
            var authorized = occupied(state, settlement, List.of(declaration.passage()));
            if (hard == null) hard = authorized;
            else hard.retainAll(authorized);
        }
        var result = new KnownPedestrianRouteKnowledge(state.bootstrap(), hard, KnownPedestrianGround.forFrontier(state), key,
                java.util.Map.of(), raisedDepartureSupports(state));
        if (JOURNEYS.size() >= 32) JOURNEYS.clear();
        JOURNEYS.put(key, result); return result;
    }

    private KnownPedestrianRouteKnowledge(FrontierBootstrap bootstrap, Set<BlockPosition> hard,
            BoundedPedestrianApproach.SurveyedSurface surveyed, Object scopeKey,
            java.util.Map<TerrainColumn, SurfaceAnchor> overlay, Set<BlockPosition> raisedDepartureSupports) {
        this.bootstrap = bootstrap;
        this.hard = Set.copyOf(hard);
        this.raisedDepartureSupports = Set.copyOf(raisedDepartureSupports);
        this.surveyed = surveyed;
        this.scopeKey = Objects.requireNonNull(scopeKey); this.overlay = java.util.Map.copyOf(overlay);
        var revision = KnownPedestrianGeometryScopes.revision(scopeKey, this.hard, this.overlay);
        this.geometry = KnownPedestrianNavigation.geometry(bootstrap, this.hard, surveyed, revision, Optional.of(revision));
    }

    public static List<SurfaceAnchor> path(FrontierWorldState state, SubjectId settlementId,
                                           SurfaceAnchor start, MovementOrder order,
                                           List<Passage> passages) {
        return forSettlement(state, settlementId, passages).path(start, order);
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
                KnownPedestrianGround.forFrontier(state), key, java.util.Map.of(), raisedDepartureSupports(state));
        if (VIEWS.size() >= 64) VIEWS.clear();
        VIEWS.put(key, result);
        return result;
    }

    /** Same obstacle policy for cross-settlement approaches; no actor is a hard wall. */
    public static synchronized KnownPedestrianRouteKnowledge forFrontier(FrontierWorldState state) {
        Objects.requireNonNull(state, "frontier pedestrian route state");
        refresh(state);
        if (frontierView == null) {
            var hard = staticOccupancy(state.bootstrap());
            hard.addAll(FrontierGrayboxPlan.intactOrganOccupancy(List.copyOf(state.hiveColony().addedOrgans().values())));
            hard.addAll(KnownSiteGeometry.forState(state).obstacles());
            hard.addAll(state.physicalDeltas().keySet());
            declaredStorageObstacles(state, hard);
            frontierView = new KnownPedestrianRouteKnowledge(state.bootstrap(), hard, KnownPedestrianGround.forFrontier(state),
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
                || viewSites != sites || viewStorageGeometry != storage || viewDepartureSupports != departures) {
            VIEWS.clear();
            FIELDS.clear();
            var obstacles = new HashSet<BlockPosition>(sites.obstacles());
            obstacles.addAll(storage); obstacles.addAll(state.physicalDeltas().keySet());
            obstacles.addAll(FrontierGrayboxPlan.intactOrganOccupancy(List.copyOf(state.hiveColony().addedOrgans().values())));
            var supports = new java.util.LinkedHashMap<TerrainColumn, SurfaceAnchor>();
            for (var position : FrontierRouteNetwork.footprint(state.bootstrap(), state.routeTopology()).surfaceCells()) {
                var column = new TerrainColumn(position.x(), position.z()); var current = supports.get(column);
                if (current == null || current.y() < position.y()) supports.put(column, new SurfaceAnchor(position));
            }
            supports.putAll(sites.supports());
            KnownPedestrianGeometryScopes.refresh(state.bootstrap(), obstacles, supports);
            JOURNEYS.clear();
            frontierView = null;
            viewBootstrap = state.bootstrap(); viewOrgans = state.hiveColony().addedOrgans();
            viewDeltas = state.physicalDeltas(); viewTopology = state.routeTopology();
            viewSites = sites; viewStorageGeometry = storage; viewDepartureSupports = departures;
        }
    }

    public boolean traversable(List<SurfaceAnchor> remainingPath) {
        return remainingPath.stream().allMatch(surface -> bootstrap.bounds().contains(surface.support()) && !blocked(surface, hard));
    }

    public List<SurfaceAnchor> plannedPath(SurfaceAnchor start, MovementOrder order) {
        return KnownPedestrianNavigation.plannedRoute(geometryFrom(start), start, order);
    }
    public List<SurfaceAnchor> retainedPath(SurfaceAnchor start, MovementOrder order) {
        return KnownPedestrianNavigation.retainedRoute(geometryFrom(start), start, order);
    }
    public List<SurfaceAnchor> plannedBetween(SurfaceAnchor start, SurfaceAnchor target) {
        return KnownPedestrianNavigation.plannedBetween(geometryFrom(start), start, target);
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
        var geometry = geometryFrom(origin);
        var scope = LocalNavigationEnvelope.around(origin.standingBody(), origin.standingBody());
        var queue = new java.util.ArrayDeque<SurfaceAnchor>();
        var routes = new java.util.LinkedHashMap<SurfaceAnchor, List<SurfaceAnchor>>();
        queue.add(origin); routes.put(origin, List.of(origin));
        while (!queue.isEmpty()) {
            var current = queue.removeFirst();
            for (int[] offset : List.of(new int[]{0, -1}, new int[]{-1, 0}, new int[]{1, 0}, new int[]{0, 1})) {
                var next = geometry.supportAt(current.x() + offset[0], current.z() + offset[1]);
                if (next != null && scope.contains(next.support()) && geometry.bounds().contains(next.support())
                        && !geometry.blocked(next) && Math.abs((long) next.y() - current.y()) <= 1 && !routes.containsKey(next)) {
                    var route = new java.util.ArrayList<>(routes.get(current)); route.add(next);
                    routes.put(next, List.copyOf(route));
                    queue.addLast(next);
                }
            }
        }
        return java.util.Collections.unmodifiableMap(routes);
    }
    /** Projection may leave an unavailable retained birth cell; this does not authorize HOT locomotion. */
    public void requireLocalPlacementRoute(io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteReceipt receipt) {
        var route = receipt.route(); var origin = route.getFirst();
        var scope = LocalNavigationEnvelope.around(origin.standingBody(), origin.standingBody());
        if (route.stream().anyMatch(surface -> !scope.contains(surface.support())))
            throw new IllegalArgumentException("placement access leaves its bounded local envelope");
        // The retained origin is historical projection knowledge, not a claim that its
        // current body volume is clear. Every proposed actual placement/connecting cell is clear.
        KnownPedestrianNavigation.requireRoute(geometryFrom(origin), route.subList(1, route.size()));
    }
    public List<SurfaceAnchor> plannedPath(SurfaceAnchor start, MovementOrder order, List<SurfaceAnchor> hint) {
        return KnownPedestrianNavigation.plannedRoute(geometryFrom(start), start, order, hint);
    }
    public Optional<io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult> planningEvidence(SurfaceAnchor start, SurfaceAnchor target) {
        return io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRoutePlanning.peek(geometryFrom(start), start, target);
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
        var known = surveyed.at(start.x(), start.z());
        if (start.equals(known)) return geometry;
        // A known container remains a transit obstacle, but an actor physically
        // pushed onto it can depart from that retained contact. Only this origin
        // is refined; no destination, body clearance or foreign scar is opened.
        boolean raisedContact = known != null && start.y() == known.y() + 1
                && raisedDepartureSupports.contains(start.support())
                && !hard.contains(start.support().offset(0, 1, 0)) && !hard.contains(start.support().offset(0, 2, 0));
        if (known == null || !bootstrap.bounds().contains(start.support()) || blocked(start, hard) && !raisedContact
                || Math.abs((long) start.y() - known.y()) > 1L)
            throw new KnownPedestrianNavigation.RouteUnavailable("retained departure is not on compatible known support: " + start);
        var previous = departures.get(start);
        if (previous != null) return previous;
        var refinedOverlay = new java.util.LinkedHashMap<>(overlay);
        refinedOverlay.put(new TerrainColumn(start.x(), start.z()), start);
        var revision = KnownPedestrianGeometryScopes.revision(new RefinedScope(scopeKey, start), hard, refinedOverlay);
        var refined = new io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteGeometry() {
            @Override public WorldBounds bounds() { return geometry.bounds(); }
            @Override public Object version() { return revision; }
            @Override public Optional<io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianGeometryRevision> regionalRevision() {
                return Optional.of(revision);
            }
            @Override public SurfaceAnchor supportAt(int x, int z) { return x == start.x() && z == start.z() ? start : surveyed.at(x, z); }
            @Override public boolean blocked(SurfaceAnchor surface) {
                return !(raisedContact && surface.equals(start)) && geometry.blocked(surface);
            }
        };
        if (departures.size() >= 32) departures.remove(departures.keySet().iterator().next());
        departures.put(start, refined); return refined;
    }

    private static Set<BlockPosition> occupied(FrontierWorldState state, Settlement settlement,
                                               List<Passage> passages) {
        Set<BlockPosition> hard = staticOccupancy(state.bootstrap());
        hard.addAll(KnownSiteGeometry.forState(state).obstacles());
        hard.addAll(FrontierGrayboxPlan.intactOrganOccupancy(
                List.copyOf(state.hiveColony().addedOrgans().values())));
        for (Passage passage : passages) {
            SettlementStructure structure = settlement.structures().stream()
                    .filter(candidate -> candidate.id().equals(passage.facility().id()))
                    .reduce((left, right) -> { throw new IllegalArgumentException("duplicate pedestrian passage facility"); })
                    .orElseThrow(() -> new IllegalArgumentException("pedestrian passage has no local facility"));
            if (!structure.equals(passage.facility()))
                throw new IllegalArgumentException("pedestrian passage differs from its declared facility");
            FacilityTraversalPort port = FrontierTraversalPlan.facilityPort(structure)
                    .orElseThrow(() -> new IllegalArgumentException("pedestrian passage has no declared traversal port"));
            clear(hard, port.exteriorApproach().getFirst());
            if (passage.reach() == Passage.Reach.PUBLIC_ACCESS || passage.reach() == Passage.Reach.STATIONS) {
                port.exteriorApproach().forEach(surface -> clear(hard, surface));
                FrontierGrayboxPlan.publicAccessSurfaces(structure).forEach(surface -> clear(hard, surface));
            }
            if (passage.reach() == Passage.Reach.STATIONS) {
                port.ingressSurfaces().forEach(surface -> clear(hard, surface));
                port.stations().forEach(surface -> clear(hard, surface));
            }
        }
        // A witnessed physical change can close even an authored passage. Never clear it
        // together with the planned structure, and never treat another moving actor as a wall.
        hard.addAll(state.physicalDeltas().keySet());
        declaredStorageObstacles(state, hard);
        return hard;
    }
    private static void declaredStorageObstacles(FrontierWorldState state, Set<BlockPosition> hard) {
        hard.addAll(KnownStorageGeometry.forState(state));
    }
    private static Set<BlockPosition> raisedDepartureSupports(FrontierWorldState state) {
        return KnownStorageGeometry.fixedSupports(state).stream().filter(position -> !state.physicalDeltas().containsKey(position))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
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
                Set<BlockPosition> hard = occupied(state, settlement, passages);
                hard.addAll(site.irrigationSlots());
                hard.addAll(obstructions);
                var knowledge = new KnownPedestrianRouteKnowledge(state.bootstrap(), hard,
                        ResourceSiteHarvestKnownGeometry.surveyedSupports(state, site), new FieldScope(site.id(), port.endpoint()),
                        site.cropSlots().stream().collect(java.util.stream.Collectors.toMap(
                                crop -> new TerrainColumn(crop.x(), crop.z()), crop -> new SurfaceAnchor(crop.offset(0, -1, 0)))),
                        raisedDepartureSupports(state));
                cached = new FieldView(site, port, cycle, obstructions, knowledge, new java.util.LinkedHashMap<>());
            }
            if (FIELDS.size() >= 64 && !FIELDS.containsKey(site.id())) FIELDS.clear();
            FIELDS.put(site.id(), cached);
        }
        var knowledge = cached.knowledge();
        SurfaceAnchor knownAtStart = knowledge.surveyed.at(witnessedStart.x(), witnessedStart.z());
        if (!state.bootstrap().bounds().contains(witnessedStart.support())
                || Math.abs(witnessedStart.y() - knownAtStart.y()) > 1
                    && !port.ownedAccessSurfaces().contains(witnessedStart))
            throw new KnownPedestrianNavigation.RouteUnavailable(
                    "field worker body is not on retained known support");
        if (!witnessedStart.equals(knownAtStart) && !port.ownedAccessSurfaces().contains(witnessedStart)) {
            // Preserve the existing exact witnessed-column exception, never a remote passage.
            var departure = cached.witnessedDepartures().get(witnessedStart);
            if (departure != null) return departure;
            var hard = new HashSet<>(knowledge.hard);
            clear(hard, witnessedStart);
            departure = new KnownPedestrianRouteKnowledge(state.bootstrap(), hard, knowledge.surveyed,
                    new WitnessedScope(knowledge.scopeKey, witnessedStart), knowledge.overlay, knowledge.raisedDepartureSupports);
            if (cached.witnessedDepartures().size() >= 32)
                cached.witnessedDepartures().remove(cached.witnessedDepartures().keySet().iterator().next());
            cached.witnessedDepartures().put(witnessedStart, departure);
            return departure;
        }
        if (blocked(witnessedStart, knowledge.hard))
            throw new KnownPedestrianNavigation.RouteUnavailable(
                    "field worker's known support is no longer traversable");
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
        var constrained = new HashSet<>(hard);
        excluded.forEach(surface -> constrained.add(surface.support()));
        var allowedDepartures = new HashSet<>(raisedDepartureSupports);
        excluded.forEach(surface -> allowedDepartures.remove(surface.support()));
        return new KnownPedestrianRouteKnowledge(bootstrap, constrained, surveyed, new TaskExclusionScope(scopeKey, excluded), overlay,
                allowedDepartures);
    }

    /** Explicit task exclusions for bounded alternative selection, not caller-owned terrain rules. */
    public List<SurfaceAnchor> pathAvoiding(SurfaceAnchor start, MovementOrder order, Set<SurfaceAnchor> excluded) {
        excluded = Set.copyOf(Objects.requireNonNull(excluded, "excluded task surfaces"));
        if (excluded.size() > io.farfrontier.palemirror.frontier.v3.model.navigation.TraversalRejoin.MAX_SURFACES)
            throw new IllegalArgumentException("task surface exclusions exceed the bounded path profile");
        if (excluded.isEmpty()) return path(start, order);
        var constrained = new HashSet<>(hard);
        excluded.forEach(surface -> constrained.add(surface.support()));
        return KnownPedestrianNavigation.route(bootstrap, start, order, constrained, surveyed);
    }

    public SurfaceAnchor supportAt(int x, int z) {
        return surveyed.at(x, z);
    }
    /** Read-only known geometry; callers may choose semantic goals, not invent traversability. */
    public io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteGeometry geometry() { return geometry; }

    static synchronized Set<BlockPosition> staticOccupancy(FrontierBootstrap bootstrap) {
        if (cachedBootstrap != bootstrap) {
            cachedStaticOccupancy = Set.copyOf(compileStaticOccupancy(bootstrap));
            cachedBootstrap = bootstrap;
        }
        // Passages and dynamic overlays mutate their own view, never the cached base.
        return new HashSet<>(cachedStaticOccupancy);
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

    private static void clear(Set<BlockPosition> occupied, SurfaceAnchor surface) {
        occupied.remove(surface.support());
        occupied.remove(surface.support().offset(0, 1, 0));
        occupied.remove(surface.support().offset(0, 2, 0));
    }
}
