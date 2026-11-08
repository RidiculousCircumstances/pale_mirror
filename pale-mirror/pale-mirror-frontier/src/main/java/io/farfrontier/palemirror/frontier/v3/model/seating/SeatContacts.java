package io.farfrontier.palemirror.frontier.v3.model.seating;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import java.util.*;

/** Shared furniture reservation and local pose owner. Activities own their purpose and duration. */
public final class SeatContacts {
    private record PlaceKey(SubjectId home, SeatPlace.Id id) { }
    private static final ImmutableInputView<Map<PlaceKey, SeatPlace>> AUTHORED = new ImmutableInputView<>();
    /** Reservation cleanup contributed to the common fatality transaction; never a corpse/body write. */
    public static SeatState atFatality(SeatState seats, ActorExecutionId execution) {
        var use = seats.uses().get(execution.actorId());
        if (use == null) return seats;
        if (!use.execution().equals(execution)) return seats; // Another retained activity owns its independently declared use.
        return use.pendingBody().isPresent() ? seats : new SeatState(ImmutableLookupMap.withoutEntry(seats.uses(), execution.actorId()));
    }
    /** A composed owner's exact settled fatality effect retires its pending seat reference, without touching a native corpse. */
    public static SeatState retireDeadComposed(FrontierWorldState state, ActorActuationId expected) {
        ActorBodyAuthority.requireRetiredDeath(state, expected.body());
        var seats = state.humanPopulation().seats(); var use = seats.uses().get(expected.body().actorId());
        if (use == null) return seats;
        if (!use.execution().equals(expected.execution()) || !use.pendingBody().equals(Optional.of(expected))
                || !use.pendingMode().equals(Optional.of(SeatUse.ExecutionMode.COMPOSED_ACTIVITY_EFFECT))
                || !use.pendingAction().equals(Optional.of(SeatUse.Action.EXIT)))
            throw new IllegalArgumentException("fatality seat closure lacks its exact delegated effect");
        return new SeatState(ImmutableLookupMap.withoutEntry(seats.uses(), expected.body().actorId()));
    }
    public static Optional<SubjectId> pendingOwner(FrontierWorldState state, SubjectId actor) {
        return Optional.ofNullable(state.humanPopulation().seats().uses().get(actor))
                .filter(use -> use.pendingBody().isPresent()).map(use -> use.execution().activityOwnerId());
    }
    public static SeatPlace place(FrontierWorldState state, SubjectId home, SeatPlace.Id id) {
        var places = AUTHORED.get(List.of(state.bootstrap()), () -> {
            var result = new HashMap<PlaceKey, SeatPlace>();
            for (var settlement : state.bootstrap().settlements()) for (var building : settlement.structures())
                for (var declaration : building.definition().seats().values()) {
                    var place = SeatPlace.authored(settlement.id(), building, declaration);
                    if (result.putIfAbsent(new PlaceKey(settlement.id(), place.id()), place) != null)
                        throw new IllegalArgumentException("duplicate authored seat identity");
                }
            return Map.copyOf(result);
        });
        var place = places.get(new PlaceKey(home, id));
        if (place == null) throw new IllegalArgumentException("seat lost its declared home/building/annotation");
        return place;
    }
    public static boolean present(FrontierWorldState state, SeatPlace place) {
        return place.equals(place(state, place.home(), place.id()));
    }
    public static List<KnownPedestrianRouteKnowledge.SettlementPassage> passages(FrontierWorldState state, SeatPlace place) {
        var building = FrontierWorldStateSupport.settlement(state.bootstrap(), place.home()).structures().stream()
                .filter(s -> s.id().equals(place.id().buildingId())).findFirst().orElseThrow();
        return List.of(new KnownPedestrianRouteKnowledge.SettlementPassage(place.home(),
                new KnownPedestrianRouteKnowledge.Passage(building, KnownPedestrianRouteKnowledge.Passage.Reach.STATIONS)));
    }
    /** Current common block evidence; an unavailable observation never restores pristine furniture. */
    public enum FurnitureKnowledge { PRESENT, MISSING, UNKNOWN }
    public static FurnitureKnowledge furnitureKnowledge(FrontierWorldState state, SeatPlace place) {
        var chair = furnitureCell(state, place.chair(), place.chairBlock(), Optional.of(place.facing()));
        var table = furnitureCell(state, place.table(), place.tableBlock(), Optional.empty());
        if (chair == FurnitureKnowledge.MISSING || table == FurnitureKnowledge.MISSING) return FurnitureKnowledge.MISSING;
        return chair == FurnitureKnowledge.UNKNOWN || table == FurnitureKnowledge.UNKNOWN ? FurnitureKnowledge.UNKNOWN : FurnitureKnowledge.PRESENT;
    }
    private static FurnitureKnowledge furnitureCell(FrontierWorldState state, BlockPosition at, String block, Optional<FacilityFacing> facing) {
        var delta = state.physicalDeltas().get(at);
        if (delta != null) return delta.kind() == PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS ? FurnitureKnowledge.MISSING : FurnitureKnowledge.UNKNOWN;
        var observation = state.navigationGeometry().cells().get(at);
        if (observation == null) return FurnitureKnowledge.PRESENT; // Exact authored genesis evidence, with no retained override.
        var actual = observation.cell().flatMap(cell -> cell.observedState());
        if (actual.isEmpty()) return FurnitureKnowledge.UNKNOWN;
        var value = actual.orElseThrow();
        return value.kind().equals(block) && (facing.isEmpty() || facing.orElseThrow().name().toLowerCase(Locale.ROOT).equals(value.properties().get("facing")))
                ? FurnitureKnowledge.PRESENT : FurnitureKnowledge.MISSING;
    }
    public static boolean approachClear(FrontierWorldState state, SeatUse use) {

        var knowledge = KnownPedestrianRouteKnowledge.forPublicJourney(state, use.place().home(), passages(state, use.place()))
                .forActor(state, use.execution().actorId());
        return knowledge.clearStation(use.place().approach()) && knowledge.clearStation(use.place().egress());
    }
    /** At most five known local exits; no route discovery or remote movement. */
    public static Optional<SurfaceAnchor> coldExit(FrontierWorldState state, SeatUse use) {
        var knowledge = KnownPedestrianRouteKnowledge.forPublicJourney(state, use.place().home(),
                passages(state, use.place()))
                .forActor(state, use.execution().actorId());
        var actual = state.actorLocations().get(use.execution().actorId()).body();
        var contact = use.place().contact().standingBody();
        var physicalCell = new BodyPosition(use.place().chair().x(), use.place().chair().y(), use.place().chair().z());
        if (!actual.equals(contact) && !actual.equals(physicalCell)) {
            // A settled native departure can already have vacated the chair.
            // Retire only its logical occupancy at that exact current supported
            // body; never pull it back to a preferred exit or invent a route.
            return knowledge.clearStation(actual.supportingSurface())
                    ? Optional.of(actual.supportingSurface()) : Optional.empty();
        }
        var exits = new LinkedHashSet<SurfaceAnchor>(); exits.add(use.place().egress());
        for (var facing : FacilityFacing.values()) exits.add(new SurfaceAnchor(use.place().chair().offset(facing.x(), -1, facing.z())));
        return exits.stream().filter(knowledge::clearStation).findFirst();
    }
    public static FrontierWorldState apply(FrontierWorldState state, SubjectId subject, SeatEvent event, long tick) {
        var seats = state.humanPopulation().seats();
        if (!subject.equals(event.subject())) throw new IllegalArgumentException("foreign seat event subject");
        if (event instanceof SeatEvent.Reserved reserved) {
            var use = reserved.use(); state.actorExecutions().requireCurrent(use.execution());
            var person = state.humanPopulation().residents().get(subject);
            if (person == null || !person.settlementId().equals(use.place().home())
                    || seats.uses().containsKey(subject) || !seats.free(use.place().id())
                    || use.phase() != SeatUse.Phase.RESERVED || use.revision() != 1 || use.changedAt() != tick
                    || use.pendingBody().isPresent() || !present(state, use.place()) || !approachClear(state, use)
                    || !ActorExecutionComposition.CAPABILITIES.require(use.execution().activityKind()).permitsSeat(state, use.execution(), use))
                throw new IllegalArgumentException("invalid seat reservation or activity permission");
            return update(state, seats.with(use));
        }
        long revision = switch (event) {
            case SeatEvent.Prepared e -> e.revision();
            case SeatEvent.Checkpointed e -> e.revision();
            case SeatEvent.ApplicationCheckpointed e -> e.revision();
            case SeatEvent.Changed e -> e.revision();
            case SeatEvent.Released e -> e.revision();
            default -> throw new IllegalArgumentException("unsupported seat event");
        };
        var use = Objects.requireNonNull(seats.uses().get(subject), "seat reservation");
        if (revision != use.revision() || tick < use.changedAt()) throw new IllegalArgumentException("stale seat interaction");
        var policy = ActorExecutionComposition.CAPABILITIES.require(use.execution().activityKind());
        if (event instanceof SeatEvent.Released) {
            boolean dead = state.actorLocations().get(subject).condition().status() != ActorLifeStatus.ALIVE;
            if (use.pendingBody().isPresent() || !dead && (use.phase() != SeatUse.Phase.RESERVED
                    || !policy.permitsSeatRelease(state, use.execution(), use)))
                throw new IllegalArgumentException("seat still occupied, unsettled or owned by another purpose");
            return update(state, new SeatState(ImmutableLookupMap.withoutEntry(seats.uses(), subject)));
        }
        if (event instanceof SeatEvent.Prepared prepared) {
            ActorBodyAuthority.requireActuation(state, prepared.body());
            if (!prepared.body().execution().equals(use.execution()) || state.actorMovements().containsKey(subject)
                    || ActorInventoryInteractionFences.pending(state, subject)
                    || prepared.mode() == SeatUse.ExecutionMode.COMPOSED_ACTIVITY_EFFECT
                        && (prepared.action() != SeatUse.Action.EXIT || !policy.permitsComposedSeatExit(state, use.execution(), use, tick))
                    || prepared.action() != SeatUse.Action.EXIT && (!policy.permitsSeat(state, use.execution(), use)
                        || !present(state, use.place()) || !approachClear(state, use))
                    || prepared.action() == SeatUse.Action.ENTER
                        && !state.actorLocations().get(subject).supportingSurface().equals(use.place().approach()))
                throw new IllegalArgumentException("seat needs an exact current nearby body");
            return update(state, seats.with(use.prepared(prepared.body(), prepared.action(), prepared.mode())));
        }
        if (event instanceof SeatEvent.Checkpointed checkpoint) {
            ActorBodyAuthority.requireObservation(state, checkpoint.body().body());
            if (!Optional.of(checkpoint.body()).equals(use.pendingBody())
                    || !Optional.of(checkpoint.action()).equals(use.pendingAction()) || !Optional.of(checkpoint.mode()).equals(use.pendingMode())
                    || !state.actorLocations().get(subject).body().equals(checkpoint.observed())
                    || (checkpoint.action() == SeatUse.Action.EXIT ? checkpoint.observed().equals(use.place().contact().standingBody())
                        : !checkpoint.observed().equals(use.place().contact().standingBody())))
                throw new IllegalArgumentException("seat checkpoint lacks its exact prepared interaction and common observed body");
            return update(state, seats.with(use.checkpointed(checkpoint.observed())));
        }
        if (event instanceof SeatEvent.ApplicationCheckpointed checkpoint) {
            ActorBodyAuthority.requireObservation(state, checkpoint.body().body());
            if (!Optional.of(checkpoint.body()).equals(use.pendingBody())
                    || !Optional.of(checkpoint.action()).equals(use.pendingAction())
                    || !Optional.of(checkpoint.mode()).equals(use.pendingMode())
                    || (checkpoint.action() == SeatUse.Action.EXIT
                        ? checkpoint.observed().equals(use.place().contact().standingBody())
                        : !checkpoint.observed().equals(use.place().contact().standingBody())))
                throw new IllegalArgumentException("native seat application observation lost its exact prepared contact");
            return update(state, seats.with(use.checkpointed(checkpoint.observed())));
        }
        var change = (SeatEvent.Changed) event;
        var changes = FrontierWorldStateUpdate.begin();
        var expected = change.action() == SeatUse.Action.EXIT ? coldExit(state, use).map(SurfaceAnchor::standingBody).orElse(null) : use.place().contact().standingBody();
        if (change.body().isPresent()) {
            if (!change.body().equals(use.pendingBody()) || !Optional.of(change.action()).equals(use.pendingAction())
                    || !Optional.of(change.mode()).equals(use.pendingMode()) || !change.applied() && use.pendingObservation().isPresent())
                throw new IllegalArgumentException("seat receipt lacks its exact prepared body/action");
            if (change.applied() && state.actorLocations().get(subject).condition().status() == ActorLifeStatus.ALIVE
                    && !use.pendingObservation().equals(Optional.of(change.observed())))
                throw new IllegalArgumentException("seat receipt needs common physical body observation");
        } else {
            state.actorExecutions().requireCurrent(use.execution());
            if (use.pendingBody().isPresent() || change.mode() != SeatUse.ExecutionMode.LOCAL || !change.applied() || change.action() == SeatUse.Action.RESTORE
                    || change.action() == SeatUse.Action.ENTER && (use.phase() != SeatUse.Phase.RESERVED
                        || !policy.permitsSeat(state, use.execution(), use) || !present(state, use.place()))
                    || change.action() == SeatUse.Action.EXIT && use.phase() != SeatUse.Phase.SEATED
                    || change.action() == SeatUse.Action.ENTER && !approachClear(state, use) || !Objects.equals(expected, change.observed()))
                throw new IllegalArgumentException("seat COLD transition lacks exact reservation/geometry");
            var from = change.action() == SeatUse.Action.ENTER ? use.place().approach().standingBody()
                    : state.actorLocations().get(subject).body();
            // A retired native passenger may be captured in the chair's feet
            // cell rather than its logical standing contact. The exact seated
            // use owns this nearby stand-up; common custody/fence/distance checks
            // still apply. A previously vacated supported body stays where it is.
            changes.actorLocations(ActorLocalContact.change(state, use.execution(), from, expected));
        }
        var next = state.actorLocations().get(subject).condition().status() != ActorLifeStatus.ALIVE
                ? new SeatState(ImmutableLookupMap.withoutEntry(seats.uses(), subject))
                : seats.with(use.changed(change.action(), change.applied(), tick));
        return state.withChanges(changes.humanPopulation(state.humanPopulation().withSeats(next)));
    }
    private static FrontierWorldState update(FrontierWorldState state, SeatState seats) {
        return state.withChanges(FrontierWorldStateUpdate.begin().humanPopulation(state.humanPopulation().withSeats(seats)));
    }
    private SeatContacts() { }
}
