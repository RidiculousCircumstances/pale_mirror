package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.navigation.TimedKnownRoute;
import io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind;

import java.util.Objects;
import java.util.Optional;

/** One retained self-care activity for one person; a job assignment remains a separate owner. */
public record ResidentMeal(SubjectId residentId, SubjectId settlementId, ResidentFoodSource source,
                           SurfaceAnchor clearingSurface,
                           SubjectId actorAccountId,
                           FoodPortion portion, SubjectId claimId,
                           Optional<SubjectId> retainedWorkOwner, Phase phase,
                           long startedAtTick, Optional<ResidentActivityChoice.Wait> waitReason,
                           Optional<ResidentMealPhysicalStep> pendingPhysicalStep,
                           Optional<TimedKnownRoute> coldTravel, ActorExecutionId executionId) {
    public static final String BREAD_KIND = "minecraft:bread";
    public static final ActorItemSlot.Pocket CARRIED_PORTION_SLOT = new ActorItemSlot.Pocket(0);
    /** CLEAR_ACCESS carries the retained portion outside the shared service passage before eating. */
    public enum Phase { MOVE, TAKE, CONSUME, CLEAR_ACCESS }

    public boolean carriesFood() { return phase == Phase.CLEAR_ACCESS || phase == Phase.CONSUME; }

    public boolean movesToClearance() { return phase == Phase.CLEAR_ACCESS; }
    public boolean portable() { return source instanceof ResidentFoodSource.Personal; }
    public SubjectId sourceAccountId() { return source.accountId(); }
    public SubjectId depotId() {
        if (!(source instanceof ResidentFoodSource.Depot depot))
            throw new IllegalStateException("portable meal has no service depot");
        return depot.containerId();
    }
    public ActorItemSlot inventorySlot() {
        return source instanceof ResidentFoodSource.Personal personal ? personal.slot()
                : ((ResidentFoodSource.Depot) source).portionSlot();
    }

    public ResidentMeal {
        Objects.requireNonNull(residentId, "meal resident");
        Objects.requireNonNull(settlementId, "meal settlement");
        Objects.requireNonNull(source, "declared meal food source");
        Objects.requireNonNull(clearingSurface, "meal service clearing surface");
        Objects.requireNonNull(actorAccountId, "meal actor account");
        Objects.requireNonNull(portion, "meal food portion");
        Objects.requireNonNull(claimId, "meal bread claim");
        Objects.requireNonNull(executionId, "meal execution authority");
        if (!executionId.actorId().equals(residentId) || executionId.activityKind() != ActorActivityKind.MEAL
                || !executionId.activityOwnerId().equals(claimId))
            throw new IllegalArgumentException("meal execution differs from its declared resident and exact claim owner");
        retainedWorkOwner = Objects.requireNonNull(retainedWorkOwner, "meal retained assignment");
        Objects.requireNonNull(phase, "meal phase");
        waitReason = Objects.requireNonNull(waitReason, "meal wait reason");
        pendingPhysicalStep = Objects.requireNonNull(pendingPhysicalStep, "meal physical step");
        coldTravel = Objects.requireNonNull(coldTravel, "meal COLD travel");
        if (pendingPhysicalStep.isPresent() && (pendingPhysicalStep.orElseThrow().phase() != phase
                || !pendingPhysicalStep.orElseThrow().executionId().equals(executionId)))
            throw new IllegalArgumentException("meal physical fence differs from current phase");
        if (pendingPhysicalStep.isPresent() && phase == Phase.CONSUME
                && pendingPhysicalStep.orElseThrow().consumptionQuantity() != portion.quantity())
            throw new IllegalArgumentException("consumption fence differs from retained portion");
        if (coldTravel.isPresent() && (phase != Phase.MOVE && !movesToClearancePhase(phase)
                || !coldTravel.orElseThrow().order().actorId().equals(residentId)
                || !coldTravel.orElseThrow().order().ownerId().equals(residentId)
                || coldTravel.orElseThrow().order().goalOrdinal() != FrontierWireTags.tag(phase)
                || coldTravel.orElseThrow().order().goalRevision() != 1L
                || coldTravel.orElseThrow().order().capability() != TraversalCapability.PEDESTRIAN
                || coldTravel.orElseThrow().order().arrivalPolicy() != MovementOrder.ArrivalPolicy.EXACT_STATION
                || pendingPhysicalStep.isPresent()))
            throw new IllegalArgumentException("meal travel must retain the exact moving resident and phase");
        if (startedAtTick < 0) throw new IllegalArgumentException("negative meal start");
        if (source instanceof ResidentFoodSource.Depot depot) {
            if (!depot.settlementId().equals(settlementId) || !FrontierWorldState.depotId(settlementId).equals(depot.containerId())
                    || !depot.accountId().equals(ReferenceContainerCustody.scopeId(depot.containerId()))
                    || depot.accountId().equals(actorAccountId))
                throw new IllegalArgumentException("depot meal needs its exact household source and distinct portion account");
        } else if (source instanceof ResidentFoodSource.Personal personal) {
            if (!personal.actorId().equals(residentId) || !personal.accountId().equals(actorAccountId)
                    || phase != Phase.CONSUME || coldTravel.isPresent())
                throw new IllegalArgumentException("portable meal consumes its current personal account without travel or take");
        }
    }

    public ResidentMeal(SubjectId residentId, SubjectId settlementId, SubjectId depotId,
                        SurfaceAnchor clearingSurface, SubjectId sourceAccountId, SubjectId actorAccountId,
                        FoodPortion portion, SubjectId claimId, Optional<SubjectId> retainedWorkOwner,
                        Phase phase, long startedAtTick, Optional<ResidentActivityChoice.Wait> waitReason,
                        Optional<ResidentMealPhysicalStep> pendingPhysicalStep, Optional<TimedKnownRoute> coldTravel,
                        ActorExecutionId executionId) {
        this(residentId, settlementId, new ResidentFoodSource.Depot(settlementId, depotId, sourceAccountId),
                clearingSurface, actorAccountId, portion, claimId, retainedWorkOwner, phase, startedAtTick,
                waitReason, pendingPhysicalStep, coldTravel, executionId);
    }

    public ResidentMeal(SubjectId residentId, SubjectId settlementId, SubjectId depotId,
                        SurfaceAnchor clearingSurface,
                        SubjectId sourceAccountId, SubjectId actorAccountId, FoodPortion portion,
                        SubjectId claimId, Optional<SubjectId> retainedWorkOwner, Phase phase,
                        long startedAtTick, Optional<ResidentActivityChoice.Wait> waitReason, ActorExecutionId executionId) {
        this(residentId, settlementId, depotId, clearingSurface, sourceAccountId, actorAccountId, portion,
                claimId, retainedWorkOwner, phase, startedAtTick, waitReason, Optional.empty(), Optional.empty(), executionId);
    }

    public ResidentMeal(SubjectId residentId, SubjectId settlementId, SubjectId depotId,
                        SurfaceAnchor clearingSurface, SubjectId sourceAccountId, SubjectId actorAccountId,
                        FoodPortion portion, SubjectId claimId, Optional<SubjectId> retainedWorkOwner,
                        Phase phase, long startedAtTick, Optional<ResidentActivityChoice.Wait> waitReason,
                        Optional<ResidentMealPhysicalStep> pendingPhysicalStep, ActorExecutionId executionId) {
        this(residentId, settlementId, depotId, clearingSurface, sourceAccountId, actorAccountId,
                portion, claimId, retainedWorkOwner, phase, startedAtTick, waitReason,
                pendingPhysicalStep, Optional.empty(), executionId);
    }

    public ResidentMeal prepare(ResidentMealPhysicalStep step) {
        if (pendingPhysicalStep.isPresent() || step.phase() != phase)
            throw new IllegalArgumentException("meal cannot prepare a second or foreign physical effect");
        return new ResidentMeal(residentId, settlementId, source, clearingSurface, actorAccountId,
                portion, claimId, retainedWorkOwner, phase, startedAtTick, waitReason, Optional.of(step), coldTravel, executionId);
    }

    public ResidentMeal advance(Phase next) {
        boolean legal = switch (phase) {
            case MOVE -> next == Phase.TAKE;
            case TAKE -> next == Phase.CLEAR_ACCESS;
            case CLEAR_ACCESS -> next == Phase.CONSUME;
            case CONSUME -> false; // only confirmed consumption retires the meal
        };
        if (!legal) throw new IllegalArgumentException("meal phase cannot skip a physical custody receipt");
        return new ResidentMeal(residentId, settlementId, source, clearingSurface, actorAccountId,
                portion, claimId, retainedWorkOwner, next, startedAtTick, Optional.empty(), Optional.empty(), Optional.empty(), executionId);
    }

    private static boolean movesToClearancePhase(Phase phase) {
        return phase == Phase.CLEAR_ACCESS;
    }

    public ResidentMeal waitFor(ResidentActivityChoice.Wait reason) {
        return new ResidentMeal(residentId, settlementId, source, clearingSurface, actorAccountId,
                portion, claimId, retainedWorkOwner, phase, startedAtTick, Optional.of(reason), pendingPhysicalStep, coldTravel, executionId);
    }

    /** Admission beside an occupied station retains the same claim but requires a new approach. */
    public ResidentMeal reapproach() {
        if (phase != Phase.TAKE || pendingPhysicalStep.isPresent() || coldTravel.isPresent())
            throw new IllegalArgumentException("only an unbegun take can resume its station approach");
        return new ResidentMeal(residentId, settlementId, source, clearingSurface, actorAccountId,
                portion, claimId, retainedWorkOwner, Phase.MOVE, startedAtTick, Optional.empty(), Optional.empty(), Optional.empty(), executionId);
    }

    public ResidentMeal clearWait() {
        return waitReason.isEmpty() ? this : new ResidentMeal(residentId, settlementId, source, clearingSurface,
                actorAccountId, portion, claimId, retainedWorkOwner, phase,
                startedAtTick, Optional.empty(), pendingPhysicalStep, coldTravel, executionId);
    }

    public ResidentMeal withColdTravel(TimedKnownRoute travel) {
        if (coldTravel.isPresent()) throw new IllegalArgumentException("meal already has COLD travel");
        return new ResidentMeal(residentId, settlementId, source, clearingSurface, actorAccountId,
                portion, claimId, retainedWorkOwner, phase, startedAtTick, waitReason, pendingPhysicalStep,
                Optional.of(travel), executionId);
    }

    public ResidentMeal withoutColdTravel() {
        if (coldTravel.isEmpty()) return this;
        return new ResidentMeal(residentId, settlementId, source, clearingSurface, actorAccountId,
                portion, claimId, retainedWorkOwner, phase, startedAtTick, waitReason, pendingPhysicalStep,
                Optional.empty(), executionId);
    }
}
