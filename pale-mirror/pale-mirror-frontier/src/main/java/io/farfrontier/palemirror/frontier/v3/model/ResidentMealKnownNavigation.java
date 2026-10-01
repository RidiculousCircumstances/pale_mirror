package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation;
import io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
        return clearancePathFrom(state, meal, state.actorLocations().get(meal.residentId()).supportingSurface());
    }

    public static List<SurfaceAnchor> clearancePathFrom(FrontierWorldState state, ResidentMeal meal, SurfaceAnchor start) {
        MovementOrder order = new MovementOrder(meal.residentId(), meal.residentId(),
                FrontierWireTags.tag(meal.phase()), 1L, List.of(meal.clearingSurface()),
                TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.EXACT_STATION);
        return KnownServiceExitNavigation.pathFrom(state, meal.settlementId(), meal.depotId(), order, start);
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
        if (admitted && start.equals(port.serviceSurface())) return List.of(start);
        if (admitted && start.equals(port.exteriorApproach()))
            return serviceLeg(state, meal, depot, port.exteriorApproach());

        List<SurfaceAnchor> prefix = workshopExit(settlement, start);
        SurfaceAnchor outdoorStart = prefix.getLast();
        List<KnownPedestrianRouteKnowledge.Passage> passages = new ArrayList<>();
        passages.add(new KnownPedestrianRouteKnowledge.Passage(depot,
                KnownPedestrianRouteKnowledge.Passage.Reach.PUBLIC_ACCESS));
        if (prefix.size() > 1) {
            SettlementStructure workshop = settlement.structures().stream()
                    .filter(structure -> structure.kind() == StructureKind.WORKSHOP)
                    .filter(structure -> SettlementWorkshopServicePort.forWorkshop(structure)
                            .exteriorApproach().equals(outdoorStart))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("meal start has no declared workshop exit"));
            passages.add(new KnownPedestrianRouteKnowledge.Passage(workshop,
                    KnownPedestrianRouteKnowledge.Passage.Reach.EXTERIOR));
        }
        KnownPedestrianRouteKnowledge routeKnowledge = KnownPedestrianRouteKnowledge.forSettlement(
                state, meal.settlementId(), passages);
        // A distant applicant is not an occupant and does not own the service
        // turn. Route it to a side pocket first, then reconsider the short
        // entrance from there. A legitimate pocket can be farther than three
        // blocks from the exterior station on irregular terrain.
        boolean directService = admitted && (Math.abs(start.x() - port.exteriorApproach().x())
                + Math.abs(start.z() - port.exteriorApproach().z()) <= 3
                || waitingSurfaces(state, meal, port, routeKnowledge).contains(start));
        List<SurfaceAnchor> destinations = directService ? List.of(port.exteriorApproach())
                : waitingSurfaces(state, meal, port, routeKnowledge);
        for (SurfaceAnchor destination : destinations) {
            if (start.equals(destination)) return List.of(start);
            MovementOrder outdoor = new MovementOrder(meal.residentId(), meal.residentId(),
                    FrontierWireTags.tag(meal.phase()), 1L, List.of(destination),
                    TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.EXACT_STATION);
            try {
                List<SurfaceAnchor> route = routeKnowledge.path(outdoorStart, outdoor);
                if (!directService && !port.accessBoundary().allowsWaitingRoute(route)) continue;
                List<SurfaceAnchor> result = new ArrayList<>(prefix);
                result.addAll(route.subList(1, route.size()));
                if (directService) {
                    List<SurfaceAnchor> finalLeg = serviceLeg(state, meal, depot, port.exteriorApproach());
                    result.addAll(finalLeg.subList(1, finalLeg.size()));
                }
                return List.copyOf(result);
            } catch (KnownPedestrianNavigation.RouteUnavailable unavailable) {
                if (directService) throw unavailable;
            }
        }
        throw new KnownPedestrianNavigation.RouteUnavailable("no clear depot waiting surface");
    }

    /** Side pockets keep simultaneous approaches off the single-file exit route. */
    public static boolean atWaitingPocket(FrontierWorldState state, ResidentMeal meal) {
        ActorLocation actor = state.actorLocations().get(meal.residentId());
        if (actor == null) return false;
        Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), meal.settlementId());
        SettlementStructure depot = settlement.structures().stream()
                .filter(value -> value.kind() == StructureKind.DEPOT).findFirst().orElseThrow();
        SettlementDepotServicePort port = SettlementDepotServicePort.forDepot(depot);
        SurfaceAnchor apron = port.facing().step(port.exteriorApproach(), -1);
        SurfaceAnchor current = actor.supportingSurface();
        if (port.accessBoundary().occupied(actor.body())) return false;
        for (int distance = 1; distance <= Math.max(4, (settlement.residents().size() + 1) / 2); distance++) {
            for (SurfaceAnchor side : List.of(port.facing().stepLeft(apron, distance),
                    port.facing().stepRight(apron, distance))) {
                if (current.x() == side.x() && current.z() == side.z()) return true;
            }
        }
        return false;
    }

    private static List<SurfaceAnchor> waitingSurfaces(FrontierWorldState state, ResidentMeal meal,
                                                       SettlementDepotServicePort port,
                                                       KnownPedestrianRouteKnowledge routeKnowledge) {
        Settlement settlement = FrontierWorldStateSupport.settlement(state.bootstrap(), meal.settlementId());
        int ordinal = 0;
        for (int index = 0; index < settlement.residents().size(); index++) {
            if (settlement.residents().get(index).id().equals(meal.residentId())) {
                ordinal = index;
                break;
            }
        }
        SettlementStructure depot = settlement.structures().stream().filter(value -> value.id().equals(port.depotId())).findFirst().orElseThrow();
        List<SurfaceAnchor> candidates = SettlementServiceAccessPoints.forDepot(state, settlement, depot).waitingSurfaces();
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

    private static List<SurfaceAnchor> serviceLeg(FrontierWorldState state, ResidentMeal meal,
                                                   SettlementStructure depot, SurfaceAnchor start) {
        MovementOrder order = new MovementOrder(meal.residentId(), meal.residentId(),
                FrontierWireTags.tag(meal.phase()), 1L,
                List.of(SettlementDepotServicePort.forDepot(depot).serviceSurface()),
                TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.EXACT_STATION);
        return KnownPedestrianRouteKnowledge.path(state, meal.settlementId(), start, order,
                List.of(new KnownPedestrianRouteKnowledge.Passage(depot,
                        KnownPedestrianRouteKnowledge.Passage.Reach.PUBLIC_ACCESS)));
    }
}
