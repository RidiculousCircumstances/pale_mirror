package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation;
import io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Ephemeral COLD route to a resident's declared depot service station. */
public final class ResidentMealKnownNavigation {
    private ResidentMealKnownNavigation() { }

    public static List<SurfaceAnchor> path(FrontierWorldState state, ResidentMeal meal) {
        Objects.requireNonNull(state, "meal navigation state");
        Objects.requireNonNull(meal, "meal navigation owner");
        ActorLocation actor = state.actorLocations().get(meal.residentId());
        if (actor == null || actor.condition().status() != ActorLifeStatus.ALIVE)
            throw new IllegalArgumentException("meal navigation has no living resident body");
        return pathFrom(state, meal, actor.supportingSurface());
    }

    /** The same retained body walks away from the shared socket before the next user enters. */
    public static List<SurfaceAnchor> returnPath(FrontierWorldState state, ResidentMeal meal) {
        Objects.requireNonNull(state, "meal clearing state");
        Objects.requireNonNull(meal, "meal clearing owner");
        ActorLocation actor = state.actorLocations().get(meal.residentId());
        if (actor == null || actor.condition().status() != ActorLifeStatus.ALIVE)
            throw new IllegalArgumentException("meal clearing has no living resident body");
        SurfaceAnchor start = actor.supportingSurface();
        if (start.equals(meal.clearingSurface())) return List.of(start);
        Set<BlockPosition> occupied = new HashSet<>();
        for (Settlement candidate : state.bootstrap().settlements())
            occupied.addAll(FrontierSettlementActorSlots.intactStructureOccupancy(
                    state.bootstrap().terrain(), candidate.structures()));
        occupied.addAll(FrontierGrayboxPlan.intactOrganOccupancy(state.bootstrap().hive().organs()));
        occupied.addAll(state.physicalDeltas().keySet());
        clear(occupied, start);
        Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), meal.settlementId());
        SettlementStructure depot = settlement.structures().stream()
                .filter(structure -> structure.kind() == StructureKind.DEPOT).findFirst().orElseThrow();
        SettlementDepotServicePort port = SettlementDepotServicePort.forDepot(depot);
        clear(occupied, port.serviceSurface());
        clear(occupied, port.exteriorApproach());
        state.actorLocations().forEach((id, location) -> {
            if (!id.equals(meal.residentId()) && location.condition().status() == ActorLifeStatus.ALIVE)
                occupied.add(location.supportingSurface().support().offset(0, 1, 0));
        });
        Map<TerrainColumn, SurfaceAnchor> known = SettlementPedestrianGround.localSupports(
                state.bootstrap(), meal.settlementId());
        MovementOrder order = new MovementOrder(meal.residentId(), meal.residentId(),
                FrontierWireTags.tag(meal.phase()), 1L, List.of(meal.clearingSurface()),
                TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.EXACT_STATION);
        return KnownPedestrianNavigation.route(state.bootstrap(), start, order, occupied,
                (x, z) -> SettlementPedestrianGround.surveyedSupport(state.bootstrap(), known, x, z));
    }

    /** A HOT release can resume from its last witnessed body without replaying an old path. */
    public static List<SurfaceAnchor> pathFrom(FrontierWorldState state, ResidentMeal meal, SurfaceAnchor start) {
        Objects.requireNonNull(state, "meal navigation state");
        Objects.requireNonNull(meal, "meal navigation owner");
        Objects.requireNonNull(start, "meal navigation start");
        Settlement settlement = state.bootstrap().settlements().stream()
                .filter(value -> value.id().equals(meal.settlementId())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("meal navigation has no retained settlement"));
        SettlementStructure depot = settlement.structures().stream()
                .filter(value -> value.kind() == StructureKind.DEPOT).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("meal navigation has no retained depot"));
        SettlementDepotServicePort port = SettlementDepotServicePort.forDepot(depot);
        if (!meal.depotId().equals(FrontierWorldState.depotId(settlement.id())))
            throw new IllegalArgumentException("meal navigation has a foreign depot identity");
        boolean admitted = ServiceAccessCoordinator.depotAvailableForMeal(state, meal.depotId(), meal.residentId());
        if (start.equals(port.serviceSurface())) return List.of(start);
        if (admitted && start.equals(port.exteriorApproach())) return List.of(start, port.serviceSurface());
        if (!admitted && port.accessBoundary().occupied(start.standingBody())) return List.of(start);

        List<SurfaceAnchor> prefix = workshopExit(settlement, start);
        SurfaceAnchor outdoorStart = prefix.getLast();
        Set<BlockPosition> occupied = new HashSet<>();
        for (Settlement candidate : state.bootstrap().settlements())
            occupied.addAll(FrontierSettlementActorSlots.intactStructureOccupancy(state.bootstrap().terrain(), candidate.structures()));
        occupied.addAll(FrontierGrayboxPlan.intactOrganOccupancy(state.bootstrap().hive().organs()));
        occupied.addAll(state.physicalDeltas().keySet());
        clear(occupied, port.exteriorApproach());
        // Every declared depot station is a walkable PUBLIC_ACCESS_SURFACE. The
        // structure occupancy set also contains its supporting block, so clearing
        // only the center service station strands a worker released at a side
        // station one step from the chest.
        port.ownedAccessSurfaces().forEach(surface -> clear(occupied, surface));
        // Only a declared workshop exit may clear interior occupancy. An arbitrary
        // start inside another structure is not permission to walk through its wall.
        if (prefix.size() > 1) prefix.forEach(surface -> clear(occupied, surface));
        state.actorLocations().forEach((id, location) -> {
            if (!id.equals(meal.residentId()) && location.condition().status() == ActorLifeStatus.ALIVE)
                occupied.add(location.supportingSurface().support().offset(0, 1, 0));
        });
        Map<TerrainColumn, SurfaceAnchor> known = SettlementPedestrianGround.localSupports(state.bootstrap(), meal.settlementId());
        // A distant applicant is not an occupant and does not own the service
        // turn. Route it to a side pocket first, then reconsider the short
        // entrance from there. A legitimate pocket can be farther than three
        // blocks from the exterior station on irregular terrain.
        boolean directService = admitted && (Math.abs(start.x() - port.exteriorApproach().x())
                + Math.abs(start.z() - port.exteriorApproach().z()) <= 3
                || waitingSurfaces(state, meal, port, known).contains(start));
        List<SurfaceAnchor> destinations = directService ? List.of(port.exteriorApproach())
                : waitingSurfaces(state, meal, port, known);
        for (SurfaceAnchor destination : destinations) {
            if (!directService && occupied.contains(destination.support().offset(0, 1, 0))) continue;
            if (start.equals(destination)) return List.of(start);
            MovementOrder outdoor = new MovementOrder(meal.residentId(), meal.residentId(),
                    FrontierWireTags.tag(meal.phase()), 1L, List.of(destination),
                    TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.EXACT_STATION);
            try {
                List<SurfaceAnchor> route = KnownPedestrianNavigation.route(state.bootstrap(), outdoorStart, outdoor,
                        occupied, (x, z) -> SettlementPedestrianGround.surveyedSupport(state.bootstrap(), known, x, z));
                if (!directService && route.stream().anyMatch(surface ->
                        port.accessBoundary().occupied(surface.standingBody()))) continue;
                List<SurfaceAnchor> result = new ArrayList<>(prefix);
                result.addAll(route.subList(1, route.size()));
                if (directService) result.add(port.serviceSurface());
                return List.copyOf(result);
            } catch (KnownPedestrianNavigation.RouteUnavailable unavailable) {
                if (directService) throw unavailable;
            }
        }
        throw new KnownPedestrianNavigation.RouteUnavailable("no clear depot waiting surface");
    }

    /** Side pockets keep simultaneous approaches off the single-file exit route. */
    private static List<SurfaceAnchor> waitingSurfaces(FrontierWorldState state, ResidentMeal meal,
                                                       SettlementDepotServicePort port,
                                                       Map<TerrainColumn, SurfaceAnchor> known) {
        SurfaceAnchor apron = port.facing().step(port.exteriorApproach(), -1);
        Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), meal.settlementId());
        int ordinal = 0;
        for (int index = 0; index < settlement.residents().size(); index++) {
            if (settlement.residents().get(index).id().equals(meal.residentId())) {
                ordinal = index;
                break;
            }
        }
        List<SurfaceAnchor> candidates = new ArrayList<>();
        for (int distance = 1; distance <= Math.max(4, (settlement.residents().size() + 1) / 2); distance++) {
            for (SurfaceAnchor side : List.of(port.facing().stepLeft(apron, distance),
                    port.facing().stepRight(apron, distance))) {
                SurfaceAnchor supported = SettlementPedestrianGround.surveyedSupport(state.bootstrap(), known,
                        side.x(), side.z());
                if (!port.accessBoundary().occupied(supported.standingBody())) candidates.add(supported);
            }
        }
        if (candidates.isEmpty()) return List.of();
        List<SurfaceAnchor> preferred = new ArrayList<>(candidates.size());
        for (int index = 0; index < candidates.size(); index++)
            preferred.add(candidates.get((ordinal + index) % candidates.size()));
        return List.copyOf(preferred);
    }

    private static List<SurfaceAnchor> workshopExit(Settlement settlement, SurfaceAnchor start) {
        for (SettlementStructure structure : settlement.structures()) {
            if (structure.kind() != StructureKind.WORKSHOP) continue;
            SettlementWorkshopServicePort port = SettlementWorkshopServicePort.forWorkshop(structure);
            List<SurfaceAnchor> exit = List.of(port.workStation(), port.inputStation(),
                    port.interiorSurface(), port.throatSurface(), port.approachSurface(), port.exteriorApproach());
            int index = exit.indexOf(start);
            if (index >= 0) return exit.subList(index, exit.size());
        }
        return List.of(start);
    }

    private static void clear(Set<BlockPosition> occupied, SurfaceAnchor surface) {
        occupied.remove(surface.support());
        occupied.remove(surface.support().offset(0, 1, 0));
        occupied.remove(surface.support().offset(0, 2, 0));
    }
}
