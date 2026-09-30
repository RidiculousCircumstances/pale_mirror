package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation;
import io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** One read-only known-geometry policy for settlement pedestrian routes. */
public final class KnownSettlementPedestrianRoute {
    private final FrontierBootstrap bootstrap;
    private final Set<BlockPosition> hard;
    private final Map<TerrainColumn, SurfaceAnchor> known;

    /** A task declares a real facility passage, never an arbitrary list of cells to clear. */
    public record Passage(SubjectId facilityId, Kind kind) {
        public enum Kind { DEPOT_ACCESS, WORKSHOP_EXTERIOR }

        public Passage {
            Objects.requireNonNull(facilityId, "pedestrian passage facility");
            Objects.requireNonNull(kind, "pedestrian passage kind");
        }
    }

    private KnownSettlementPedestrianRoute(FrontierBootstrap bootstrap, Set<BlockPosition> hard,
                                           Map<TerrainColumn, SurfaceAnchor> known) {
        this.bootstrap = bootstrap;
        this.hard = Set.copyOf(hard);
        this.known = Map.copyOf(known);
    }

    public static List<SurfaceAnchor> path(FrontierWorldState state, SubjectId settlementId,
                                           SurfaceAnchor start, MovementOrder order,
                                           List<Passage> passages) {
        return forSettlement(state, settlementId, passages).path(start, order);
    }

    /** Reuse one immutable view while trying several legal destinations in one planning turn. */
    public static KnownSettlementPedestrianRoute forSettlement(FrontierWorldState state,
                                                                SubjectId settlementId, List<Passage> passages) {
        Objects.requireNonNull(state, "pedestrian route state");
        passages = List.copyOf(Objects.requireNonNull(passages, "pedestrian route passages"));
        Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), settlementId);
        Set<BlockPosition> hard = new HashSet<>();
        for (Settlement candidate : state.bootstrap().settlements())
            hard.addAll(FrontierSettlementActorSlots.intactStructureOccupancy(
                    state.bootstrap().terrain(), candidate.structures()));
        hard.addAll(FrontierGrayboxPlan.intactOrganOccupancy(state.bootstrap().hive().organs()));
        for (Passage passage : passages) {
            SettlementStructure structure = settlement.structures().stream()
                    .filter(candidate -> candidate.id().equals(passage.facilityId()))
                    .reduce((left, right) -> { throw new IllegalArgumentException("duplicate pedestrian passage facility"); })
                    .orElseThrow(() -> new IllegalArgumentException("pedestrian passage has no local facility"));
            if (passage.kind() == Passage.Kind.DEPOT_ACCESS) {
                if (structure.kind() != StructureKind.DEPOT)
                    throw new IllegalArgumentException("pedestrian depot passage names another facility kind");
                SettlementDepotServicePort port = SettlementDepotServicePort.forDepot(structure);
                clear(hard, port.exteriorApproach());
                port.ownedAccessSurfaces().forEach(surface -> clear(hard, surface));
            } else {
                if (structure.kind() != StructureKind.WORKSHOP)
                    throw new IllegalArgumentException("pedestrian workshop passage names another facility kind");
                clear(hard, SettlementWorkshopServicePort.forWorkshop(structure).exteriorApproach());
            }
        }
        // A witnessed physical change can close even an authored passage. Never clear it
        // together with the planned structure, and never treat another moving actor as a wall.
        hard.addAll(state.physicalDeltas().keySet());
        Map<TerrainColumn, SurfaceAnchor> known = SettlementPedestrianGround.localSupports(
                state.bootstrap(), settlementId);
        return new KnownSettlementPedestrianRoute(state.bootstrap(), hard, known);
    }

    public List<SurfaceAnchor> path(SurfaceAnchor start, MovementOrder order) {
        Objects.requireNonNull(start, "pedestrian route start");
        Objects.requireNonNull(order, "pedestrian route order");
        return KnownPedestrianNavigation.route(bootstrap, start, order, hard,
                (x, z) -> SettlementPedestrianGround.surveyedSupport(bootstrap, known, x, z));
    }

    public SurfaceAnchor supportAt(int x, int z) {
        return SettlementPedestrianGround.surveyedSupport(bootstrap, known, x, z);
    }

    private static void clear(Set<BlockPosition> occupied, SurfaceAnchor surface) {
        occupied.remove(surface.support());
        occupied.remove(surface.support().offset(0, 1, 0));
        occupied.remove(surface.support().offset(0, 2, 0));
    }
}
