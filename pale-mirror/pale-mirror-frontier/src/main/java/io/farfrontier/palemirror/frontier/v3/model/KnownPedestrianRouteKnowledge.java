package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation;
import io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** One read-only known-geometry policy for pedestrian routes and typed area overlays. */
public final class KnownPedestrianRouteKnowledge {
    private final FrontierBootstrap bootstrap;
    private final Set<BlockPosition> hard;
    private final BoundedPedestrianApproach.SurveyedSurface surveyed;

    /** A task declares a real facility passage, never an arbitrary list of cells to clear. */
    public record Passage(SettlementStructure facility, Reach reach) {
        public enum Reach { EXTERIOR, PUBLIC_ACCESS }

        public Passage {
            Objects.requireNonNull(facility, "pedestrian passage facility");
            Objects.requireNonNull(reach, "pedestrian passage reach");
        }
    }

    private KnownPedestrianRouteKnowledge(FrontierBootstrap bootstrap, Set<BlockPosition> hard,
                                         BoundedPedestrianApproach.SurveyedSurface surveyed) {
        this.bootstrap = bootstrap;
        this.hard = Set.copyOf(hard);
        this.surveyed = surveyed;
    }

    public static List<SurfaceAnchor> path(FrontierWorldState state, SubjectId settlementId,
                                           SurfaceAnchor start, MovementOrder order,
                                           List<Passage> passages) {
        return forSettlement(state, settlementId, passages).path(start, order);
    }

    /** Reuse one immutable view while trying several legal destinations in one planning turn. */
    public static KnownPedestrianRouteKnowledge forSettlement(FrontierWorldState state,
                                                               SubjectId settlementId, List<Passage> passages) {
        Objects.requireNonNull(state, "pedestrian route state");
        passages = List.copyOf(Objects.requireNonNull(passages, "pedestrian route passages"));
        Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), settlementId);
        Set<BlockPosition> hard = occupied(state, settlement, passages);
        return new KnownPedestrianRouteKnowledge(state.bootstrap(), hard,
                KnownPedestrianGround.forSettlement(state, settlementId));
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
            if (passage.reach() == Passage.Reach.PUBLIC_ACCESS) {
                port.exteriorApproach().forEach(surface -> clear(hard, surface));
                FrontierGrayboxPlan.publicAccessSurfaces(structure).forEach(surface -> clear(hard, surface));
            }
        }
        // A witnessed physical change can close even an authored passage. Never clear it
        // together with the planned structure, and never treat another moving actor as a wall.
        hard.addAll(state.physicalDeltas().keySet());
        return hard;
    }

    /** Typed field overlay on the same structural/physical knowledge and route operation. */
    public static KnownPedestrianRouteKnowledge forField(FrontierWorldState state, ResourceSite site,
                                                         ResourceFieldCycle cycle, SettlementDepotServicePort port,
                                                         SurfaceAnchor witnessedStart) {
        Objects.requireNonNull(state, "field route state");
        Objects.requireNonNull(site, "field route site");
        Objects.requireNonNull(cycle, "field route cycle");
        Objects.requireNonNull(port, "field route depot");
        Objects.requireNonNull(witnessedStart, "field route start");
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
        Set<BlockPosition> hard = occupied(state, settlement,
                List.of(new Passage(depot, Passage.Reach.PUBLIC_ACCESS)));
        hard.addAll(site.irrigationSlots());
        for (ResourceFieldLayout.Cell cell : cycle.layout().cells()) {
            ResourceFieldCycle.CellState condition = cycle.cell(cell.id());
            if (condition.crop() == ResourceFieldCycle.Crop.OBSTRUCTED
                    || condition.soil() == ResourceFieldCycle.Soil.OBSTRUCTED)
                hard.add(cell.soil().support());
            if (condition.workAccessBlocked())
                hard.add(cell.workstation().support().offset(0, 2, 0));
        }
        BoundedPedestrianApproach.SurveyedSurface surveyed = ResourceSiteHarvestKnownGeometry.surveyedSupports(state, site);
        SurfaceAnchor knownAtStart = surveyed.at(witnessedStart.x(), witnessedStart.z());
        if (!state.bootstrap().bounds().contains(witnessedStart.support())
                || Math.abs(witnessedStart.y() - knownAtStart.y()) > 1
                    && !port.ownedAccessSurfaces().contains(witnessedStart))
            throw new KnownPedestrianNavigation.RouteUnavailable(
                    "field worker body is not on retained known support");
        if (!witnessedStart.equals(knownAtStart) && !port.ownedAccessSurfaces().contains(witnessedStart)) {
            // A HOT-observed body is stronger than the immutable survey only at this exact column.
            clear(hard, witnessedStart);
        } else if (blocked(witnessedStart, hard)) {
            throw new KnownPedestrianNavigation.RouteUnavailable(
                    "field worker's known support is no longer traversable");
        }
        return new KnownPedestrianRouteKnowledge(state.bootstrap(), hard, surveyed);
    }

    public List<SurfaceAnchor> path(SurfaceAnchor start, MovementOrder order) {
        Objects.requireNonNull(start, "pedestrian route start");
        Objects.requireNonNull(order, "pedestrian route order");
        return KnownPedestrianNavigation.route(bootstrap, start, order, hard, surveyed);
    }

    public SurfaceAnchor supportAt(int x, int z) {
        return surveyed.at(x, z);
    }

    static Set<BlockPosition> staticOccupancy(FrontierBootstrap bootstrap) {
        Set<BlockPosition> occupied = new HashSet<>();
        for (Settlement settlement : bootstrap.settlements())
            occupied.addAll(FrontierSettlementActorSlots.intactStructureOccupancy(
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
