package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId;
import java.util.Objects;
import java.util.Optional;

/**
 * Food-owner resource reconciliation after fatality, not a runnable meal.
 * Retains the original operation fence and ledger references, never a route,
 * assignment, nutrition reward or permission to actuate the former body.
 */
public record ResidentMealResourceObligation(ActorExecutionId executionId, ActorBodyId body,
        SubjectId settlementId, ResidentFoodSource source, SubjectId actorAccountId,
        FoodPortion portion, CustodyState custodyState, Optional<ResidentMealPhysicalStep> pendingPhysicalStep,
        long retiredAtTick) {
    /** Canonical pre-effect custody. A prepared effect may already have happened physically. */
    public enum CustodyState { SOURCE_TAKE_PENDING, ACTOR_PORTION, ACTOR_CONSUMPTION_PENDING }

    public ResidentMealResourceObligation {
        Objects.requireNonNull(executionId, "retired meal execution");
        Objects.requireNonNull(body, "retired meal body");
        Objects.requireNonNull(settlementId, "meal resource owner");
        Objects.requireNonNull(source, "retired meal source declaration");
        Objects.requireNonNull(actorAccountId, "meal portion account");
        Objects.requireNonNull(portion, "retained food portion");
        Objects.requireNonNull(custodyState, "declared meal resource custody state");
        pendingPhysicalStep = Objects.requireNonNull(pendingPhysicalStep, "retained meal physical fence");
        if (executionId.activityKind() != ActorActivityKind.MEAL || !executionId.actorId().equals(body.actorId())
                || retiredAtTick < 0)
            throw new IllegalArgumentException("meal resource obligation has a foreign identity or custody pair");
        if (source instanceof ResidentFoodSource.Depot depot) {
            if (!depot.settlementId().equals(settlementId) || !FrontierWorldState.depotId(settlementId).equals(depot.containerId())
                    || !ReferenceContainerCustody.scopeId(depot.containerId()).equals(depot.accountId())
                    || depot.accountId().equals(actorAccountId)) throw new IllegalArgumentException("retired meal has a foreign depot pair");
        } else if (source instanceof ResidentFoodSource.Personal personal) {
            if (!personal.actorId().equals(body.actorId()) || !personal.accountId().equals(actorAccountId)
                    || custodyState == CustodyState.SOURCE_TAKE_PENDING)
                throw new IllegalArgumentException("retired portable meal has a foreign personal source");
        }
        switch (custodyState) {
            case SOURCE_TAKE_PENDING -> requireStep(pendingPhysicalStep, ResidentMeal.Phase.TAKE, executionId);
            case ACTOR_CONSUMPTION_PENDING -> {
                requireStep(pendingPhysicalStep, ResidentMeal.Phase.CONSUME, executionId);
                if (pendingPhysicalStep.orElseThrow().consumptionQuantity() != portion.quantity())
                    throw new IllegalArgumentException("retired consumption fence differs from its exact portion");
            }
            case ACTOR_PORTION -> {
                if (pendingPhysicalStep.isPresent())
                    throw new IllegalArgumentException("unprepared portion cannot hide a prepared physical effect");
            }
        }
    }

    private static void requireStep(Optional<ResidentMealPhysicalStep> pending, ResidentMeal.Phase phase,
                                    ActorExecutionId execution) {
        if (pending.isEmpty() || pending.orElseThrow().phase() != phase
                || !pending.orElseThrow().executionId().equals(execution))
            throw new IllegalArgumentException("retired meal operation lacks its exact declared fence");
    }

    public SubjectId residentId() { return executionId.actorId(); }
    public SubjectId claimId() { return executionId.activityOwnerId(); }
    public SubjectId sourceAccountId() { return source.accountId(); }
    public boolean portable() { return source instanceof ResidentFoodSource.Personal; }
    public SubjectId depotId() {
        if (!(source instanceof ResidentFoodSource.Depot depot)) throw new IllegalStateException("portable obligation has no depot");
        return depot.containerId();
    }
    public ActorItemSlot inventorySlot() {
        return source instanceof ResidentFoodSource.Personal personal ? personal.slot()
                : ((ResidentFoodSource.Depot) source).portionSlot();
    }
    public ResidentMealResourceObligation(ActorExecutionId executionId, ActorBodyId body,
            SubjectId settlementId, SubjectId depotId, SubjectId sourceAccountId, SubjectId actorAccountId,
            FoodPortion portion, CustodyState custodyState, Optional<ResidentMealPhysicalStep> pending, long retiredAtTick) {
        this(executionId, body, settlementId, new ResidentFoodSource.Depot(settlementId, depotId, sourceAccountId),
                actorAccountId, portion, custodyState, pending, retiredAtTick);
    }

    /** Only the food owner translates its own phase; generic death code never reads it. */
    static ResidentMealResourceObligation retain(ResidentMeal meal, ActorBodyId body, long atTick) {
        if (atTick < meal.startedAtTick()) throw new IllegalArgumentException("meal retirement precedes admission");
        CustodyState state = switch (meal.phase()) {
            case TAKE -> CustodyState.SOURCE_TAKE_PENDING;
            case CLEAR_ACCESS, CONSUME -> meal.pendingPhysicalStep().isPresent()
                    ? CustodyState.ACTOR_CONSUMPTION_PENDING : CustodyState.ACTOR_PORTION;
            case MOVE -> throw new IllegalArgumentException("unbegun meal has no resource obligation");
        };
        return new ResidentMealResourceObligation(meal.executionId(), body, meal.settlementId(), meal.source(),
                meal.actorAccountId(), meal.portion(), state, meal.pendingPhysicalStep(), atTick);
    }
}
