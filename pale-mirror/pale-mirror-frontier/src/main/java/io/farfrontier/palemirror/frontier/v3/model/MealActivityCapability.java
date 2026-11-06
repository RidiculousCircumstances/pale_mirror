package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import java.util.Optional;

/** A portion remains owned until consumption or explicit resource reconciliation. */
final class MealActivityCapability implements ActorActivityCapability {
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
                || order.goalOrdinal() != FrontierWireTags.tag(meal.phase()) || order.goalRevision() != meal.startedAtTick() + 1L
                || order.capability() != TraversalCapability.PEDESTRIAN) return false;
        if (meal.phase() == ResidentMeal.Phase.MOVE_TO_DINING || meal.phase() == ResidentMeal.Phase.CONSUME) {
            if (!(meal.destination() instanceof MealDestination.DiningArea)
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
            return new ActorActivityBodyCheckpoint.Acknowledgement(request, FrontierWorldStateUpdate.begin());
        };
    }
    @Override public Interruption interruption() { return Interruption.RETAIN_CONTINUATION; }
    @Override public Optional<ActorActivityDeath> deathAcknowledgement() {
        return Optional.of((state, execution, tick) -> {
            validateReference(state, execution);
            var meal = state.humanPopulation().meals().get(execution.actorId());
            var resources = state.inventory().fungibleResources();
            if (meal.pendingPhysicalStep().isPresent() || meal.carriesFood()) {
                var obligation = ResidentMealResourceObligation.retain(meal, ActorBodyAuthority.current(state, execution.actorId()), tick);
                return new ActorActivityDeath.Acknowledgement(state, execution, FrontierWorldStateUpdate.begin()
                        .humanPopulation(state.humanPopulation().retainMealResources(meal, obligation)),
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
            var changes = FrontierWorldStateUpdate.begin()
                    .humanPopulation(state.humanPopulation().abandonMealSource(meal))
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
        return new ActorActivityCheckpoint(state,execution,meal.pendingPhysicalStep().isPresent()
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
        return lease.goal() == AmbientGoalKind.MEAL && lease.goalBody().supportingSurface().equals(
                io.farfrontier.palemirror.frontier.v3.process.ResidentMealProcess.goalSurface(state, meal));
    }
    public ActorActivityResumption resumptionReference(){return request->{
        var state=request.expectedState();validateReference(state,request.suspended());var meal=state.humanPopulation().meals().get(request.suspended().actorId());
        return new ActorActivityResumption.Acknowledgement(request,FrontierWorldStateUpdate.begin().humanPopulation(state.humanPopulation().advanceMeal(meal,meal.resumed(request.successor()))));
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
