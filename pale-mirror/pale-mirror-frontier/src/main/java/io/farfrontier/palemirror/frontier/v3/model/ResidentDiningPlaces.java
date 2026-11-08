package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.buildings.*;
import io.farfrontier.palemirror.frontier.v3.model.seating.*;
import java.util.*;

/** Dining policy and declared-area geometry; no food, actor, movement or table authority. */
public final class ResidentDiningPlaces {
    private ResidentDiningPlaces() { }
    public static List<MealDestination.DiningArea> in(FrontierWorldState state, SubjectId home) {
        return FrontierWorldStateSupport.settlement(state.bootstrap(), home).structures().stream()
                .sorted(Comparator.comparing(SettlementStructure::id))
                .flatMap(s -> s.definition().zones().values().stream().filter(z -> z.use() == BuildingDefinition.ZoneUse.DINING)
                        .sorted(Comparator.comparing(BuildingDefinition.Zone::id)).map(z -> new MealDestination.DiningArea(s.id(), z.id())))
                .toList();
    }
    public static SettlementStructure facility(FrontierWorldState state, SubjectId home, MealDestination.DiningArea place) {
        var structure = FrontierWorldStateSupport.settlement(state.bootstrap(), home).structures().stream()
                .filter(s -> s.id().equals(place.facilityId())).findFirst().orElseThrow(
                        () -> new IllegalArgumentException("dining destination lost its exact home/facility"));
        var zone = structure.definition().zones().get(place.zoneId());
        if (zone == null || zone.use() != BuildingDefinition.ZoneUse.DINING)
            throw new IllegalArgumentException("dining destination lost its declared area");
        return structure;
    }
    public static List<KnownPedestrianRouteKnowledge.SettlementPassage> passages(FrontierWorldState state,
            SubjectId home, MealDestination place) {
        if (place instanceof MealDestination.Here) return List.of();
        return List.of(new KnownPedestrianRouteKnowledge.SettlementPassage(home,
                new KnownPedestrianRouteKnowledge.Passage(facility(state, home, area(state, home, place)), KnownPedestrianRouteKnowledge.Passage.Reach.STATIONS)));
    }
    public static MealDestination.DiningArea area(FrontierWorldState state, SubjectId home, MealDestination place) {
        if (place instanceof MealDestination.DiningArea area) return area;
        if (!(place instanceof MealDestination.Seat seat)) throw new IllegalArgumentException("in-place meal has no dining facility");
        var exact = SeatContacts.place(state, home, seat.id());
        var building = FrontierWorldStateSupport.settlement(state.bootstrap(), home).structures().stream()
                .filter(s -> s.id().equals(exact.id().buildingId())).findFirst().orElseThrow();
        return new MealDestination.DiningArea(exact.id().buildingId(), building.definition().seats().get(exact.id().localId()).zoneId());
    }
    /** Choose a free authored seat at admission. Buildings without seats retain their declared standing dining policy. */
    public static Optional<MealDestination> available(FrontierWorldState state, SubjectId actor, SubjectId home, MealDestination.DiningArea area) {
        var building = facility(state, home, area);
        var declared = building.definition().seats().values().stream().filter(s -> s.zoneId().equals(area.zoneId()))
                .sorted(Comparator.comparing(BuildingSeat::id)).toList();
        if (declared.isEmpty()) return Optional.of(area);
        var knowledge = KnownPedestrianRouteKnowledge.forPublicJourney(state, home, passages(state, home, area)).forActor(state, actor);
        return declared.stream().map(s -> SeatPlace.authored(home, building, s))
                .filter(s -> state.humanPopulation().seats().free(s.id()))
                .filter(s -> knowledge.clearStation(s.approach()) && knowledge.clearStation(s.egress()))
                .map(s -> (MealDestination) new MealDestination.Seat(s.id())).findFirst();
    }
    public static boolean contains(FrontierWorldState state, SubjectId home, MealDestination place, SurfaceAnchor station) {
        if (place instanceof MealDestination.Here) return true; // Portable food follows its independently inspected eater, not its historical origin.
        if (place instanceof MealDestination.Seat seat) {
            var exact = SeatContacts.place(state, home, seat.id());
            return station.equals(exact.approach()) || station.equals(exact.contact());
        }
        var area = (MealDestination.DiningArea) place; var building = facility(state, home, area);
        return BuildingSpatialPlan.of(building).inZone(building.definition().zones().get(area.zoneId()), station.support().offset(0, 1, 0));
    }
    public static List<SurfaceAnchor> stations(FrontierWorldState state, SubjectId home, MealDestination place,
            KnownPedestrianRouteKnowledge knowledge) {
        if (place instanceof MealDestination.Here here) return knowledge.clearStation(here.station()) ? List.of(here.station()) : List.of();
        if (place instanceof MealDestination.Seat seat) {
            var exact = SeatContacts.place(state, home, seat.id());
            return knowledge.clearStation(exact.approach()) ? List.of(exact.approach()) : List.of();
        }
        var area = (MealDestination.DiningArea) place; var building = facility(state, home, area);
        var plan = BuildingSpatialPlan.of(building);
        return plan.authoredFloors().stream().filter(s -> contains(state, home, place, s))
                .filter(s -> FacilityFunctionAvailability.assess(state, new FacilityFunctionAvailability.Requirements(
                        building.id(), Set.of(), Set.of(s)), knowledge).status() == FacilityFunctionAvailability.Status.ELIGIBLE)
                .filter(s -> !plan.circulation().contains(s))
                .sorted(Comparator.comparingInt(SurfaceAnchor::x).thenComparingInt(SurfaceAnchor::y).thenComparingInt(SurfaceAnchor::z))
                .limit(io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder.MAX_LEGAL_STATIONS).toList();
    }
    /** Validate the retained declaration, never rank a replacement from its current position. */
    public static boolean validSelection(FrontierWorldState state, ResidentMeal meal) {
        boolean homeFood = ActivityExecutionCapabilities.permitsHomeFood(state,
                HumanAssignmentProjection.compile(state).assignment(meal.residentId()));
        if (meal.destination() instanceof MealDestination.Here here)
            return !homeFood && meal.portable() && here.station().equals(meal.clearingSurface());
        var area = area(state, meal.settlementId(), meal.destination());
        var building = facility(state, meal.settlementId(), area);
        if (!homeFood) return false;
        if (meal.source() instanceof ResidentFoodSource.Storage source) {
            var record = state.inventory().containers().get(source.containerId());
            return record != null && record.storage().isPresent()
                    && record.storage().orElseThrow().endpoint() instanceof ShipmentEndpoint.Storage endpoint
                    && endpoint.facilityId().filter(area.facilityId()::equals).isPresent();
        }
        return true;
    }
    /** Stable semantic hint for the lease; the navigator can select any legal point in this area. */
    public static SurfaceAnchor hint(FrontierWorldState state, SubjectId home, MealDestination place) {
        if (place instanceof MealDestination.Here here) return here.station();
        if (place instanceof MealDestination.Seat seat) return SeatContacts.place(state, home, seat.id()).approach();
        var area = (MealDestination.DiningArea) place;
        var building = facility(state, home, area);
        var zone = building.definition().zones().get(area.zoneId());
        return building.placement().support(zone.volume().min().offset(0, -1, 0));
    }
    public static boolean pantry(FrontierWorldState state, SubjectId home, SubjectId container) {
        var record = state.inventory().containers().get(container);
        if (record == null || record.storage().isEmpty()) return false;
        var endpoint = record.storage().orElseThrow().endpoint();
        return endpoint instanceof ShipmentEndpoint.Storage storage && storage.facilityId().isPresent()
                && in(state, home).stream().anyMatch(place -> place.facilityId().equals(storage.facilityId().orElseThrow()));
    }
}
