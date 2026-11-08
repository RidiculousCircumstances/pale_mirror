package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.seating.*;
import java.util.*;

/** Read-only exact furniture access demand; common movement owns any yielding journey. */
public final class SeatAccessClearance {
    private static final ImmutableGroupView<SubjectId, SeatUse, SurfaceAnchor> REQUESTS = new ImmutableGroupView<>();
    private static final ImmutableGroupView<SubjectId, ActorLocation, SurfaceAnchor> BODIES = new ImmutableGroupView<>();
    private static final ImmutableInputView<Set<SurfaceAnchor>> FURNITURE_CONTACTS = new ImmutableInputView<>();
    private SeatAccessClearance() { }
    public static boolean furnitureContact(FrontierWorldState state, SurfaceAnchor surface) {
        return FURNITURE_CONTACTS.get(List.of(state.bootstrap()), () -> {
            var contacts = new HashSet<SurfaceAnchor>();
            for (var home : state.bootstrap().settlements()) for (var building : home.structures())
                for (var declaration : building.definition().seats().values()) {
                    var place = SeatPlace.authored(home.id(), building, declaration);
                    contacts.add(place.approach()); contacts.add(place.egress());
                    contacts.add(new SurfaceAnchor(place.chair()));
                    place.table().ifPresent(table -> contacts.add(new SurfaceAnchor(table.position())));
                }
            return Set.copyOf(contacts);
        }).contains(surface);
    }
    public static SeatPlace place(FrontierBootstrap bootstrap, SubjectId home, SeatPlace.Id id) {
        var building = FrontierWorldStateSupport.settlement(bootstrap, home).structures().stream()
                .filter(b -> b.id().equals(id.buildingId())).findFirst().orElseThrow(() -> new IllegalArgumentException("seat clearance lost its authored home/building"));
        var declaration = building.definition().seats().get(id.localId());
        if (declaration == null) throw new IllegalArgumentException("seat clearance lost its authored seat");
        return SeatPlace.authored(home, building, declaration);
    }
    public static boolean parkingFloor(FrontierWorldState state, SeatPlace place, SurfaceAnchor surface) {
        var building = FrontierWorldStateSupport.settlement(state.bootstrap(), place.home()).structures().stream()
                .filter(b -> b.id().equals(place.id().buildingId())).findFirst().orElseThrow();
        var plan = io.farfrontier.palemirror.frontier.v3.model.buildings.BuildingSpatialPlan.of(building);
        return plan.blocks().containsKey(surface.support())
                ? plan.authoredFloors().contains(surface) && !plan.workstations().contains(surface) && !plan.circulation().contains(surface)
                : PedestrianLocalDeparture.publicPosition(state, surface);
    }
    private static Set<SurfaceAnchor> contacts(SeatUse use) {
        return use.phase() == SeatUse.Phase.RESERVED && use.pendingBody().isEmpty()
                ? Set.copyOf(List.of(use.place().approach(), use.place().egress())) : Set.of();
    }
    public static Optional<SeatPlace> requestedAt(FrontierWorldState state, SubjectId actor) {
        var location = state.actorLocations().get(actor);
        if (location == null) return Optional.empty();
        return REQUESTS.rows(state.bootstrap(), state.humanPopulation().seats().uses(), SeatAccessClearance::contacts, location.supportingSurface()).stream()
                .filter(use -> !use.execution().actorId().equals(actor))
                .filter(use -> {
                    var execution = state.actorExecutions().actors().get(use.execution().actorId());
                    return execution != null && execution.current().equals(Optional.of(use.execution()));
                })
                .map(SeatUse::place).findFirst();
    }
    public static List<SubjectId> occupants(FrontierWorldState state, SeatUse use) {
        return BODIES.keys(state.bootstrap(), state.actorLocations(), location -> location.condition().status() == ActorLifeStatus.ALIVE
                ? Set.of(location.supportingSurface()) : Set.of(), use.place().approach()).stream()
                .filter(actor -> !actor.equals(use.execution().actorId())).toList();
    }
}
