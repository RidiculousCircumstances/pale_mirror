package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import java.util.Optional;

/** A portion remains owned until consumption or explicit resource reconciliation. */
final class MealActivityCapability implements ActorActivityCapability {
    public void validateSeatReference(FrontierWorldState state, ActorExecutionId execution,
            io.farfrontier.palemirror.frontier.v3.model.seating.SeatUse use) {
        var meal = state.humanPopulation().meals().get(execution.actorId());
        if (meal == null && state.actorLocations().get(execution.actorId()).condition().status() != ActorLifeStatus.ALIVE) {
            if (!use.execution().equals(execution) || execution.activityKind() != ActorActivityKind.MEAL
                    || !state.humanPopulation().resident(execution.actorId()).settlementId().equals(use.place().home()))
                throw new IllegalArgumentException("dead seat has a foreign declared source");
            var pending = use.pendingBody().orElseThrow(() -> new IllegalArgumentException("settled dead seat must be retired"));
            ActorBodyAuthority.requireRetiredDeath(state, pending.body());
            if (use.pendingMode().equals(Optional.of(io.farfrontier.palemirror.frontier.v3.model.seating.SeatUse.ExecutionMode.COMPOSED_ACTIVITY_EFFECT))) {
                var retired = state.humanPopulation().mealResourceObligations().get(execution.actorId());
                if (retired == null || !retired.executionId().equals(execution) || !retired.body().equals(pending.body())
                        || retired.custodyState() != ResidentMealResourceObligation.CustodyState.ACTOR_CONSUMPTION_PENDING)
                    throw new IllegalArgumentException("delegated dead seat lost its exact food effect");
            }
            return;
        }
        validateReference(state, execution);
        if (!use.execution().equals(execution) || !use.place().home().equals(meal.settlementId())
                || !(meal.destination() instanceof MealDestination.Seat seat) || !seat.id().equals(use.place().id()))
            throw new IllegalArgumentException("seat reservation belongs to another meal");
    }
    public java.util.List<io.farfrontier.palemirror.frontier.v3.model.seating.SeatPlace> seatAlternatives(
            FrontierWorldState state, ActorExecutionId execution, io.farfrontier.palemirror.frontier.v3.model.seating.SeatUse use) {
        validateSeatReference(state, execution, use); return ResidentMealSeating.alternatives(state, use);
    }
    public Optional<io.farfrontier.palemirror.frontier.v3.api.FrontierPayload> seatReselection(FrontierWorldState state,
            ActorActuationId actor, io.farfrontier.palemirror.frontier.v3.model.seating.SeatUse use,
            io.farfrontier.palemirror.frontier.v3.model.seating.SeatPlace destination) {
        validateSeatReference(state, actor.execution(), use);
        return Optional.of(new ResidentMealSeatReselected(actor, use.revision(), destination.id()));
    }
    public boolean permitsSeat(FrontierWorldState state, ActorExecutionId execution,
            io.farfrontier.palemirror.frontier.v3.model.seating.SeatUse use) {
        validateSeatReference(state, execution, use);
        return !state.combat().evasions().containsKey(execution.actorId());
    }
    public boolean permitsSeatRelease(FrontierWorldState state, ActorExecutionId execution,
            io.farfrontier.palemirror.frontier.v3.model.seating.SeatUse use) {
        validateSeatReference(state, execution, use);
        return use.phase() == io.farfrontier.palemirror.frontier.v3.model.seating.SeatUse.Phase.RESERVED;
    }
    public boolean permitsComposedSeatExit(FrontierWorldState state, ActorExecutionId execution,
            io.farfrontier.palemirror.frontier.v3.model.seating.SeatUse use, long tick) {
        validateSeatReference(state, execution, use);
        var meal = state.humanPopulation().meals().get(execution.actorId());
        return meal.phase() == ResidentMeal.Phase.CONSUME && use.phase() == io.farfrontier.palemirror.frontier.v3.model.seating.SeatUse.Phase.SEATED
                && ResidentMealSeating.ready(state, meal, tick);
    }
    public Optional<io.farfrontier.palemirror.frontier.v3.model.seating.SeatUse.Action> seatAction(FrontierWorldState state,
            ActorExecutionId execution, io.farfrontier.palemirror.frontier.v3.model.seating.SeatUse use, long tick) {
        validateSeatReference(state, execution, use);
        var meal = state.humanPopulation().meals().get(execution.actorId());
        if (use.phase() == io.farfrontier.palemirror.frontier.v3.model.seating.SeatUse.Phase.SEATED) {
            if (state.combat().evasions().containsKey(execution.actorId())
                    || !state.actorLocations().get(execution.actorId()).supportingSurface().equals(use.place().contact()))
                return Optional.of(io.farfrontier.palemirror.frontier.v3.model.seating.SeatUse.Action.EXIT);
            return Optional.of(io.farfrontier.palemirror.frontier.v3.model.seating.SeatUse.Action.RESTORE);
        }
        return meal.phase() == ResidentMeal.Phase.CONSUME && permitsSeat(state, execution, use)
                ? Optional.of(io.farfrontier.palemirror.frontier.v3.model.seating.SeatUse.Action.ENTER) : Optional.empty();
    }
    public java.util.Optional<io.farfrontier.palemirror.frontier.v3.model.navigation.TravelPace> navigationPace(
            FrontierWorldState state, ActorExecutionId execution) {
        validateReference(state, execution); return java.util.Optional.of(ResidentWalkingPace.read(state,execution.actorId(),io.farfrontier.palemirror.frontier.v3.model.navigation.LocomotionMode.WALK));
    }

    @Override public ActorActivityKind kind() { return ActorActivityKind.MEAL; }
    @Override public Optional<KnownPedestrianRouteKnowledge> navigationKnowledge(FrontierWorldState state,
            ActorExecutionId execution, io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder order) {
        if (!permitsNavigationOrder(state, execution, order))
            throw new IllegalArgumentException("meal route knowledge requested for a stale order");
        return Optional.of(ResidentMealKnownNavigation.departureKnowledge(state,
                state.humanPopulation().meals().get(execution.actorId()),
                state.actorLocations().get(execution.actorId()).supportingSurface()));
    }
    @Override public boolean permitsNavigationOrder(FrontierWorldState state, ActorExecutionId execution,
            io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder order) {
        validateReference(state, execution);
        var meal = state.humanPopulation().meals().get(execution.actorId());
        if (meal.pendingPhysicalStep().isPresent() && meal.phase() != ResidentMeal.Phase.CONSUME
                || !order.actorId().equals(meal.residentId()) || !order.ownerId().equals(meal.claimId())
                || order.goalOrdinal() != FrontierWireTags.tag(meal.phase()) || order.goalRevision() != ResidentMealSeating.goalRevision(state, meal)
                || order.capability() != TraversalCapability.PEDESTRIAN) return false;
        if (meal.phase() == ResidentMeal.Phase.MOVE_TO_DINING || meal.phase() == ResidentMeal.Phase.CONSUME) {
            if (meal.destination() instanceof MealDestination.Here
                    || order.arrivalPolicy() != io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder.ArrivalPolicy.ANY_DECLARED_STATION)
                return false;
            return order.legalStations().stream().allMatch(station ->
                    ResidentDiningPlaces.contains(state, meal.settlementId(), meal.destination(), station)
                    && (meal.portable() || ResourceStorageAccess.at(state, meal.depotId())
                            .accessBoundary().cleared(station.standingBody())));
        }
        if (meal.portable() || meal.phase() != ResidentMeal.Phase.MOVE
                || order.arrivalPolicy() != io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder.ArrivalPolicy.EXACT_STATION)
            return false;
        var storage = state.inventory().containers().get(meal.depotId());
        if (storage == null || storage.storage().isEmpty()) return false;
        var station = order.legalStations().getFirst();
        var service = ResourceStorageAccess.at(state, meal.depotId()).interactionStation(state);
        return station.equals(service)
                ? ResidentMealServiceAccess.available(state, meal.depotId(), meal.residentId())
                : ResidentMealKnownNavigation.waitingStationAvailable(state, meal, station);
    }
    @Override public void validateAmbientRelease(FrontierWorldState state, ActorExecutionId execution) {
        if (state.humanPopulation().meals().get(execution.actorId()).pendingPhysicalStep().isPresent())
            throw new IllegalArgumentException("ambient meal has an unresolved physical effect");
    }
    @Override public ActorActivityBodyCheckpoint bodyCheckpoint() {
        return request -> {
            validateAmbientRelease(request.expectedState(), request.execution());
            return new ActorMovementBodyCheckpoint().acknowledge(request);
        };
    }
    @Override public Interruption interruption() { return Interruption.RETAIN_CONTINUATION; }
    @Override public Optional<ActorActivityDeath> deathAcknowledgement() {
        return Optional.of((state, execution, tick) -> {
            validateReference(state, execution);
            var meal = state.humanPopulation().meals().get(execution.actorId());
            var resources = state.inventory().fungibleResources();
            var changes = FrontierWorldStateUpdate.begin();
            var movement = state.actorMovements().get(execution.actorId());
            if (movement != null && movement.executionId().equals(execution))
                changes.actorMovements(ImmutableLookupMap.withoutEntry(state.actorMovements(), execution.actorId()));
            if (meal.pendingPhysicalStep().isPresent() || meal.carriesFood()) {
                var obligation = ResidentMealResourceObligation.retain(meal, ActorBodyAuthority.current(state, execution.actorId()), tick);
                return new ActorActivityDeath.Acknowledgement(state, execution, changes
                        .humanPopulation(state.humanPopulation().retainMealResources(meal, obligation).withSeats(
                                io.farfrontier.palemirror.frontier.v3.model.seating.SeatContacts.atFatality(state.humanPopulation().seats(), execution))),
                        ActorActivityDeath.Disposition.RETIRE_EXACT_EXECUTION);
            }
            var claim = resources.claims().get(meal.claimId());
            var source = resources.accounts().get(meal.sourceAccountId());
            if (claim == null || claim.purpose() != ClaimPurpose.RESIDENT_MEAL
                    || !claim.claimantId().equals(meal.residentId()) || !claim.economicOwnerId().equals(meal.settlementId())
                    || !claim.lotQuantities().equals(meal.portion().lotQuantities()) || source == null
                    || !source.custody().equals(new ResourceCustody.Container(meal.depotId()))
                    || source.claimQuantities().getOrDefault(meal.claimId(), 0) != meal.portion().quantity()
                    || resources.accounts().containsKey(meal.actorAccountId()))
                throw new IllegalArgumentException("meal death lacks the exact unbegun source allocation");
            changes.humanPopulation(state.humanPopulation().abandonMealSource(meal).withSeats(
                            io.farfrontier.palemirror.frontier.v3.model.seating.SeatContacts.atFatality(state.humanPopulation().seats(), execution)))
                    .inventory(state.inventory().withFungibleResources(resources.releaseClaims(java.util.Set.of(meal.claimId()))));
            return new ActorActivityDeath.Acknowledgement(state, execution, changes,
                    ActorActivityDeath.Disposition.RETIRE_EXACT_EXECUTION);
        });
    }
    @Override public FrontierWorldState release(FrontierWorldState state, ActorExecutionId execution) {
        throw new IllegalArgumentException("meal must settle its physical portion before exact terminal retirement");
    }
    @Override public void validateReference(FrontierWorldState state, ActorExecutionId execution) {
        var meal = state.humanPopulation().meals().get(execution.actorId());
        if (execution.activityKind() != kind() || meal == null || !meal.executionId().equals(execution))
            throw new IllegalArgumentException("meal capability has no exact retained portion execution");
    }
    @Override public ActorActivityCheckpoint checkpoint(FrontierWorldState state, ActorExecutionId execution) {
        validateReference(state, execution);
        var meal = state.humanPopulation().meals().get(execution.actorId());
        var seat = state.humanPopulation().seats().uses().get(execution.actorId());
        return new ActorActivityCheckpoint(state,execution,meal.pendingPhysicalStep().isPresent()
                || seat != null && seat.execution().equals(execution) && (seat.pendingBody().isPresent()
                    || seat.phase() == io.farfrontier.palemirror.frontier.v3.model.seating.SeatUse.Phase.SEATED)
                ?Optional.of(new ActorActivityCheckpoint.Wait(ActorActivityCheckpoint.Reason.PHYSICAL_OPERATION,execution.activityOwnerId())):Optional.empty());
    }
    @Override public ActorActivityCheckpoint spatialYieldCheckpoint(FrontierWorldState state, ActorExecutionId execution) {
        validateReference(state, execution);
        var meal = state.humanPopulation().meals().get(execution.actorId());
        // Moving aside cannot consume a portion or acknowledge leaving the service boundary.
        return meal.phase() == ResidentMeal.Phase.MOVE && meal.pendingPhysicalStep().isEmpty()
                ? new ActorActivityCheckpoint(state, execution, Optional.empty()) : checkpoint(state, execution);
    }
    @Override public boolean permitsAmbientMotion(FrontierWorldState state, ActorExecutionId execution, AmbientActorLease lease) {
        var meal = state.humanPopulation().meals().get(execution.actorId());
        var movement = state.actorMovements().get(execution.actorId());
        if (movement != null && movement.executionId().equals(execution) && lease.goal() == AmbientGoalKind.ACTOR_MOVEMENT)
            return movement.order().legalStations().contains(lease.goalBody().supportingSurface());
        return lease.goal() == AmbientGoalKind.MEAL && lease.goalBody().supportingSurface().equals(
                io.farfrontier.palemirror.frontier.v3.process.ResidentMealProcess.goalSurface(state, meal));
    }
    public ActorActivityResumption resumptionReference(){return request->{
        var state=request.expectedState();validateReference(state,request.suspended());var meal=state.humanPopulation().meals().get(request.suspended().actorId());
        var seats=state.humanPopulation().seats();var seat=seats.uses().get(meal.residentId());
        if(seat!=null)seats=seats.with(seat.resumed(request.successor()));
        return new ActorActivityResumption.Acknowledgement(request,FrontierWorldStateUpdate.begin().humanPopulation(state.humanPopulation().advanceMeal(meal,meal.resumed(request.successor())).withSeats(seats)));
    };}
    public java.util.List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> continuationAfterResume(FrontierWorldState state,ActorExecutionId execution,long tick){
        validateReference(state,execution);var meal=state.humanPopulation().meals().get(execution.actorId());
        return java.util.List.of(io.farfrontier.palemirror.frontier.v3.process.ResidentMealProcess.wake(meal, tick));
    }
    @Override public FrontierWorldState pause(FrontierWorldState state, ActorExecutionId execution, long atTick) {
        validateReference(state,execution);var meal=state.humanPopulation().meals().get(execution.actorId());
        if(!checkpoint(state,execution).ready())throw new IllegalArgumentException("meal retains a physical effect");
        var body=io.farfrontier.palemirror.frontier.v3.process.ResidentMealProcess.bodyAt(state,execution.actorId(),atTick);
        return state.withChanges(FrontierWorldStateUpdate.begin().humanPopulation(state.humanPopulation().advanceMeal(meal,meal.withoutColdTravel()))
                .actorLocations(ImmutableLookupMap.withEntry(state.actorLocations(),execution.actorId(),state.actorLocations().get(execution.actorId()).withBody(body))));
    }
    @Override public FrontierWorldState resume(FrontierWorldState state, ActorExecutionId execution, long atTick) {
        validateReference(state,execution);return state;
    }
}
