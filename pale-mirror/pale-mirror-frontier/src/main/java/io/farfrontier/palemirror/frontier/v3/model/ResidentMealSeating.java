package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.seating.*;
import java.util.*;

/** Meal policy over the shared seat owner. Food, navigation and physical body authority remain separate. */
public final class ResidentMealSeating {
    public static final int EATING_TICKS = 160;
    public static Optional<SeatUse> use(FrontierWorldState state, ResidentMeal meal) {
        if (!(meal.destination() instanceof MealDestination.Seat seat)) return Optional.empty();
        var use = state.humanPopulation().seats().uses().get(meal.residentId());
        if (use == null || !use.execution().equals(meal.executionId()) || !use.place().id().equals(seat.id()))
            throw new IllegalArgumentException("seated meal lost its exact shared reservation");
        return Optional.of(use);
    }
    public static HumanPopulation reserve(FrontierWorldState state, HumanPopulation population, ResidentMeal meal) {
        if (!(meal.destination() instanceof MealDestination.Seat seat)) return population;
        var place = SeatContacts.place(state, meal.settlementId(), seat.id());
        return population.withSeats(population.seats().reserve(SeatUse.reserve(meal.executionId(), place, meal.startedAtTick())));
    }
    public static boolean seatedAt(FrontierWorldState state, ResidentMeal meal, BodyPosition body) {
        return use(state, meal).map(use -> use.phase() == SeatUse.Phase.SEATED && body.equals(use.place().contact().standingBody())).orElse(true);
    }
    public static boolean ready(FrontierWorldState state, ResidentMeal meal, long tick) {
        return use(state, meal).map(use -> use.phase() == SeatUse.Phase.SEATED
                && tick >= Math.addExact(use.changedAt(), state.bootstrap().ruleset().residentLife().routines().eatingTicks())).orElse(true);
    }
    /** Mirror the owner's native stand-up policy before a displaced COLD meal navigates again. */
    public static Optional<SeatEvent.Changed> coldDeparture(FrontierWorldState state, ResidentMeal meal, long tick) {
        return use(state, meal).filter(use -> use.phase() == SeatUse.Phase.SEATED && use.pendingBody().isEmpty())
                .filter(use -> ActorExecutionComposition.CAPABILITIES.require(meal.executionId().activityKind())
                        .seatAction(state, meal.executionId(), use, tick).equals(Optional.of(SeatUse.Action.EXIT)))
                .flatMap(use -> SeatContacts.coldExit(state, use).map(exit -> new SeatEvent.Changed(meal.residentId(),
                        use.revision(), Optional.empty(), SeatUse.Action.EXIT, SeatUse.ExecutionMode.LOCAL, true, exit.standingBody())));
    }
    public static Optional<SeatEvent.Changed> coldEntrance(FrontierWorldState state, ResidentMeal meal) {
        var use = use(state, meal);
        return meal.phase() == ResidentMeal.Phase.CONSUME && use.filter(u -> u.phase() == SeatUse.Phase.RESERVED
                && u.pendingBody().isEmpty() && state.actorLocations().get(meal.residentId()).supportingSurface().equals(u.place().approach()))
                .isPresent() ? Optional.of(new SeatEvent.Changed(meal.residentId(), use.orElseThrow().revision(), Optional.empty(),
                        SeatUse.Action.ENTER, SeatUse.ExecutionMode.LOCAL, true, use.orElseThrow().place().contact().standingBody())) : Optional.empty();
    }
    public static FrontierWorldState finishCold(FrontierWorldState state, ResidentMeal meal, long tick) {
        var selected = use(state, meal);
        if (selected.isEmpty()) return state;
        var use = selected.orElseThrow();
        return FrontierWorldState.duringUnpublishedTransition(() -> {
            var exited = SeatContacts.apply(state, meal.residentId(), new SeatEvent.Changed(meal.residentId(), use.revision(), Optional.empty(),
                    SeatUse.Action.EXIT, SeatUse.ExecutionMode.LOCAL, true, SeatContacts.coldExit(state, use).orElseThrow().standingBody()), tick);
            return SeatContacts.apply(exited, meal.residentId(), new SeatEvent.Released(meal.residentId(), use.revision() + 1), tick);
        });
    }
    public static boolean confirmedExit(FrontierWorldState state, ResidentMeal meal, BodyPosition observed, long tick) {
        return use(state, meal).map(use -> use.phase() == SeatUse.Phase.SEATED && ready(state, meal, tick)
                && use.pendingAction().equals(Optional.of(SeatUse.Action.EXIT)) && !use.place().contact().standingBody().equals(observed)
                && use.pendingObservation().equals(Optional.of(observed))).orElse(false);
    }
    public static FrontierWorldState finishHot(FrontierWorldState state, ResidentMeal meal, BodyPosition observed, long tick) {
        var selected = use(state, meal);
        if (selected.isEmpty()) return state;
        var use = selected.orElseThrow();
        if (!confirmedExit(state, meal, observed, tick)) throw new IllegalArgumentException("meal did not exit its prepared seat");
        return FrontierWorldState.duringUnpublishedTransition(() -> {
            var exited = SeatContacts.apply(state, meal.residentId(), new SeatEvent.Changed(meal.residentId(), use.revision(), use.pendingBody(),
                    SeatUse.Action.EXIT, use.pendingMode().orElseThrow(), true, observed), tick);
            return SeatContacts.apply(exited, meal.residentId(), new SeatEvent.Released(meal.residentId(), use.revision() + 1), tick);
        });
    }
    public static FrontierWorldState cancelHot(FrontierWorldState state, ResidentMeal meal, long tick) {
        var selected = use(state, meal);
        if (selected.isEmpty() || meal.phase() != ResidentMeal.Phase.CONSUME) return state;
        var use = selected.orElseThrow();
        if (!use.pendingMode().equals(Optional.of(SeatUse.ExecutionMode.COMPOSED_ACTIVITY_EFFECT)))
            throw new IllegalArgumentException("food cancellation lost its delegated seat fence");
        return SeatContacts.apply(state, meal.residentId(), new SeatEvent.Changed(meal.residentId(), use.revision(), use.pendingBody(),
                SeatUse.Action.EXIT, use.pendingMode().orElseThrow(), false, state.actorLocations().get(meal.residentId()).body()), tick);
    }
    /** Changes the retained navigation identity when an unused reservation changes. */
    public static long goalRevision(FrontierWorldState state, ResidentMeal meal) {
        return Math.addExact(meal.startedAtTick(), use(state, meal).map(SeatUse::revision).orElse(1L));
    }
    public static List<SeatPlace> alternatives(FrontierWorldState state, SeatUse use) {
        var meal = state.humanPopulation().meals().get(use.execution().actorId());
        if (meal == null || !meal.executionId().equals(use.execution()) || meal.phase() != ResidentMeal.Phase.CONSUME
                || meal.pendingPhysicalStep().isPresent() || use.phase() != SeatUse.Phase.RESERVED || use.pendingBody().isPresent()) return List.of();
        var building = ResidentDiningPlaces.facility(state, meal.settlementId(), ResidentDiningPlaces.area(state, meal.settlementId(), meal.destination()));
        var zone = building.definition().seats().get(use.place().id().localId()).zoneId();
        return building.definition().seats().values().stream().filter(s -> s.zoneId().equals(zone))
                .sorted(Comparator.comparing(io.farfrontier.palemirror.frontier.v3.model.buildings.BuildingSeat::id))
                .map(s -> SeatPlace.authored(meal.settlementId(), building, s))
                .filter(s -> !s.id().equals(use.place().id()) && state.humanPopulation().seats().free(s.id()))
                .filter(s -> SeatContacts.approachClear(state, SeatUse.reserve(use.execution(), s, use.changedAt())))
                .limit(6).toList();
    }
    public static FrontierWorldState reselect(FrontierWorldState state, ResidentMealSeatReselected change, long tick) {
        ActorBodyAuthority.requireActuation(state, change.actor());
        var actor = change.actor().body().actorId(); var use = state.humanPopulation().seats().uses().get(actor);
        var meal = state.humanPopulation().meals().get(actor);
        if (use == null || meal == null || !use.execution().equals(change.actor().execution()) || use.revision() != change.revision()
                || state.actorMovements().containsKey(actor) || ActorInventoryInteractionFences.pending(state, actor)
                || !state.actorLocations().get(actor).supportingSurface().equals(use.place().approach()))
            throw new IllegalArgumentException("seat reselection lost its exact standing meal/body/reservation");
        var replacement = alternatives(state, use).stream().filter(s -> s.id().equals(change.destination())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("replacement seat is not available to this meal"));
        var people = state.humanPopulation().advanceMeal(meal, meal.reselectSeat(new MealDestination.Seat(replacement.id())))
                .withSeats(state.humanPopulation().seats().with(use.reselected(replacement, tick)));
        return state.withHumanPopulation(people);
    }
    private ResidentMealSeating() { }
}
