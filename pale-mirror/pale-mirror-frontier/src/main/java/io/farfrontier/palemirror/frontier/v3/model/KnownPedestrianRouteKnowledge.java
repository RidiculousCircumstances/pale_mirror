package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation;
import io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder;

import java.util.HashSet;
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
    private static Object viewBootstrap, viewOrgans, viewDeltas, viewTopology;
    private static Object geometryVersion = new Object();
    private static final java.util.Map<ViewKey, KnownPedestrianRouteKnowledge> VIEWS = new java.util.LinkedHashMap<>();
    private static KnownPedestrianRouteKnowledge frontierView;
    private record FieldView(ResourceSite site, SettlementDepotServicePort port, ResourceFieldCycle cycle,
                             Set<BlockPosition> obstructions, KnownPedestrianRouteKnowledge knowledge,
                             java.util.Map<SurfaceAnchor, KnownPedestrianRouteKnowledge> witnessedDepartures) { }
    private static final java.util.Map<SubjectId, FieldView> FIELDS = new java.util.LinkedHashMap<>();
    private final FrontierBootstrap bootstrap;
    private final Set<BlockPosition> hard;
    private final BoundedPedestrianApproach.SurveyedSurface surveyed;
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
        var result = new KnownPedestrianRouteKnowledge(state.bootstrap(), hard, KnownPedestrianGround.forFrontier(state));
        if (JOURNEYS.size() >= 32) JOURNEYS.clear();
        JOURNEYS.put(key, result); return result;
    }

    private KnownPedestrianRouteKnowledge(FrontierBootstrap bootstrap, Set<BlockPosition> hard,
                                         BoundedPedestrianApproach.SurveyedSurface surveyed) {
        this.bootstrap = bootstrap;
        this.hard = Set.copyOf(hard);
        this.surveyed = surveyed;
        this.geometry = KnownPedestrianNavigation.geometry(bootstrap, this.hard, surveyed, geometryVersion);
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
                KnownPedestrianGround.forFrontier(state));
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
            hard.addAll(state.physicalDeltas().keySet());
            frontierView = new KnownPedestrianRouteKnowledge(state.bootstrap(), hard, KnownPedestrianGround.forFrontier(state));
        }
        return frontierView;
    }

    private static void refresh(FrontierWorldState state) {
        if (viewBootstrap != state.bootstrap() || viewOrgans != state.hiveColony().addedOrgans()
                || viewDeltas != state.physicalDeltas() || viewTopology != state.routeTopology()) {
            VIEWS.clear();
            FIELDS.clear();
            geometryVersion = new Object();
            JOURNEYS.clear();
            frontierView = null;
            viewBootstrap = state.bootstrap(); viewOrgans = state.hiveColony().addedOrgans();
            viewDeltas = state.physicalDeltas(); viewTopology = state.routeTopology();
        }
    }

    public boolean traversable(List<SurfaceAnchor> remainingPath) {
        return remainingPath.stream().allMatch(surface -> bootstrap.bounds().contains(surface.support()) && !blocked(surface, hard));
    }

    public List<SurfaceAnchor> plannedPath(SurfaceAnchor start, MovementOrder order) {
        return KnownPedestrianNavigation.plannedRoute(geometryFrom(start), start, order);
    }
    /** Connected known supports inside the existing one-cell local collision latitude.
     * No task goal, route clock, arrival or physical actor position is changed. */
    public List<SurfaceAnchor> localPlacement(SurfaceAnchor origin) {
        var geometry = geometryFrom(origin);
        var scope = LocalNavigationEnvelope.around(origin.standingBody(), origin.standingBody());
        var queue = new java.util.ArrayDeque<SurfaceAnchor>();
        var seen = new java.util.LinkedHashSet<SurfaceAnchor>();
        queue.add(origin); seen.add(origin);
        while (!queue.isEmpty()) {
            var current = queue.removeFirst();
            for (int[] offset : List.of(new int[]{0, -1}, new int[]{-1, 0}, new int[]{1, 0}, new int[]{0, 1})) {
                var next = geometry.supportAt(current.x() + offset[0], current.z() + offset[1]);
                if (next != null && scope.contains(next.support()) && geometry.bounds().contains(next.support())
                        && !geometry.blocked(next) && Math.abs((long) next.y() - current.y()) <= 1 && seen.add(next))
                    queue.addLast(next);
            }
        }
        return List.copyOf(seen);
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

    /** The exact retained departure may refine its one column; it cannot invent remote terrain. */
    private synchronized io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteGeometry geometryFrom(SurfaceAnchor start) {
        var known = surveyed.at(start.x(), start.z());
        if (start.equals(known)) return geometry;
        if (known == null || !bootstrap.bounds().contains(start.support()) || blocked(start, hard)
                || Math.abs((long) start.y() - known.y()) > 1L)
            throw new KnownPedestrianNavigation.RouteUnavailable("retained departure is not on compatible known support: " + start);
        var previous = departures.get(start);
        if (previous != null) return previous;
        var refined = new io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteGeometry() {
            @Override public WorldBounds bounds() { return geometry.bounds(); }
            @Override public Object version() { return geometry.version(); }
            @Override public SurfaceAnchor supportAt(int x, int z) { return x == start.x() && z == start.z() ? start : surveyed.at(x, z); }
            @Override public boolean blocked(SurfaceAnchor surface) { return geometry.blocked(surface); }
        };
        if (departures.size() >= 32) departures.remove(departures.keySet().iterator().next());
        departures.put(start, refined); return refined;
    }

    private static Set<BlockPosition> occupied(FrontierWorldState state, Settlement settlement,
                                               List<Passage> passages) {
        Set<BlockPosition> hard = staticOccupancy(state.bootstrap());
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
        return hard;
    }

    /** Typed field overlay on the same structural/physical knowledge and route operation. */
    public static synchronized KnownPedestrianRouteKnowledge forField(FrontierWorldState state, ResourceSite site,
                                                         ResourceFieldCycle cycle, SettlementDepotServicePort port,
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
        SettlementStructure depot = settlement.structures().stream()
                .filter(structure -> structure.id().equals(port.depotId()))
                .reduce((left, right) -> { throw new IllegalArgumentException("duplicate field depot passage"); })
                .orElseThrow(() -> new IllegalArgumentException("field route has no declared depot passage"));
        if (!SettlementDepotServicePort.forDepot(depot).equals(port))
            throw new IllegalArgumentException("field route depot passage differs from its plan");
        FieldView cached = FIELDS.get(site.id());
        if (cached == null || cached.cycle() != cycle || !cached.site().equals(site) || !cached.port().equals(port)) {
            Set<BlockPosition> obstructions = fieldObstructions(cycle);
            if (cached != null && cached.site().equals(site) && cached.port().equals(port)
                    && cached.cycle().layout() == cycle.layout() && cached.obstructions().equals(obstructions)) {
                cached = new FieldView(site, port, cycle, cached.obstructions(), cached.knowledge(), cached.witnessedDepartures());
            } else {
                Set<BlockPosition> hard = occupied(state, settlement,
                        List.of(new Passage(depot, Passage.Reach.PUBLIC_ACCESS)));
                hard.addAll(site.irrigationSlots());
                hard.addAll(obstructions);
                var knowledge = new KnownPedestrianRouteKnowledge(state.bootstrap(), hard,
                        ResourceSiteHarvestKnownGeometry.surveyedSupports(state, site));
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
            departure = new KnownPedestrianRouteKnowledge(state.bootstrap(), hard, knowledge.surveyed);
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
