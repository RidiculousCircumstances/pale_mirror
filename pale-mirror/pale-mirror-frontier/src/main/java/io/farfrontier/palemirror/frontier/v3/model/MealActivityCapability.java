package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.execution.*;
import java.util.Optional;

/** A portion remains owned until consumption or explicit resource reconciliation. */
final class MealActivityCapability implements ActorActivityCapability {
    @Override public ActorActivityKind kind() { return ActorActivityKind.MEAL; }
    @Override public void validateAmbientRelease(FrontierWorldState state, ActorExecutionId execution) {
        if (state.humanPopulation().meals().get(execution.actorId()).pendingPhysicalStep().isPresent())
            throw new IllegalArgumentException("ambient meal has an unresolved physical effect");
    }
    @Override public ActorActivityBodyCheckpoint bodyCheckpoint() { return ActorActivityBodyCheckpoint.usesActorLocation(); }
    @Override public Interruption interruption() { return Interruption.TERMINAL_ONLY; }
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
        return new ActorActivityCheckpoint(state, execution, Optional.of(new ActorActivityCheckpoint.Wait(
                meal.pendingPhysicalStep().isPresent() ? ActorActivityCheckpoint.Reason.PHYSICAL_OPERATION
                        : ActorActivityCheckpoint.Reason.OWNER_TERMINAL_BOUNDARY, execution.activityOwnerId())));
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
    @Override public FrontierWorldState pause(FrontierWorldState state, ActorExecutionId execution, long atTick) {
        throw new IllegalArgumentException("meal must settle its portion before replacement");
    }
    @Override public FrontierWorldState resume(FrontierWorldState state, ActorExecutionId execution, long atTick) {
        throw new IllegalArgumentException("meal has no suspended continuation");
    }
}
