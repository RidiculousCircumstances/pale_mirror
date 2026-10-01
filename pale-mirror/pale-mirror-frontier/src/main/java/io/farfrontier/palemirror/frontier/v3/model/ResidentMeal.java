package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.navigation.TimedKnownRoute;
import io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder;

import java.util.Objects;
import java.util.Optional;

/** One retained self-care activity for one person; a job assignment remains a separate owner. */
public record ResidentMeal(SubjectId residentId, SubjectId settlementId, SubjectId depotId,
                           SurfaceAnchor clearingSurface,
                           SubjectId sourceAccountId, SubjectId actorAccountId,
                           FoodPortion portion, SubjectId claimId,
                           Optional<SubjectId> retainedWorkOwner, Phase phase,
                           long startedAtTick, Optional<ResidentActivityChoice.Wait> waitReason,
                           Optional<ResidentMealPhysicalStep> pendingPhysicalStep,
                           Optional<TimedKnownRoute> coldTravel) {
    public static final String BREAD_KIND = "minecraft:bread";
    /** CLEAR_ACCESS carries the retained portion outside the shared service passage before eating. */
    public enum Phase { MOVE, TAKE, CONSUME, RETURN, CLEAR_ACCESS }

    public boolean carriesFood() { return phase == Phase.CLEAR_ACCESS || phase == Phase.CONSUME; }

    public boolean movesToClearance() { return phase == Phase.CLEAR_ACCESS || phase == Phase.RETURN; }

    public ResidentMeal {
        Objects.requireNonNull(residentId, "meal resident");
        Objects.requireNonNull(settlementId, "meal settlement");
        Objects.requireNonNull(depotId, "meal depot");
        Objects.requireNonNull(clearingSurface, "meal service clearing surface");
        Objects.requireNonNull(sourceAccountId, "meal source account");
        Objects.requireNonNull(actorAccountId, "meal actor account");
        Objects.requireNonNull(portion, "meal food portion");
        Objects.requireNonNull(claimId, "meal bread claim");
        retainedWorkOwner = Objects.requireNonNull(retainedWorkOwner, "meal retained assignment");
        Objects.requireNonNull(phase, "meal phase");
        waitReason = Objects.requireNonNull(waitReason, "meal wait reason");
        pendingPhysicalStep = Objects.requireNonNull(pendingPhysicalStep, "meal physical step");
        coldTravel = Objects.requireNonNull(coldTravel, "meal COLD travel");
        if (pendingPhysicalStep.isPresent() && pendingPhysicalStep.orElseThrow().phase() != phase)
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
        if (!residentId.value().startsWith("resident:") || !settlementId.value().startsWith("settlement:")
                || !FrontierWorldState.depotId(settlementId).equals(depotId)
                || !sourceAccountId.equals(ReferenceContainerCustody.scopeId(depotId))
                || !actorAccountId.value().startsWith("custody:resident-meal-")
                || sourceAccountId.equals(actorAccountId) || startedAtTick < 0) {
            throw new IllegalArgumentException("meal must retain one exact resident, depot and custody pair");
        }
    }

    public ResidentMeal(SubjectId residentId, SubjectId settlementId, SubjectId depotId,
                        SurfaceAnchor clearingSurface,
                        SubjectId sourceAccountId, SubjectId actorAccountId, FoodPortion portion,
                        SubjectId claimId, Optional<SubjectId> retainedWorkOwner, Phase phase,
                        long startedAtTick, Optional<ResidentActivityChoice.Wait> waitReason) {
        this(residentId, settlementId, depotId, clearingSurface, sourceAccountId, actorAccountId, portion,
                claimId, retainedWorkOwner, phase, startedAtTick, waitReason, Optional.empty(), Optional.empty());
    }

    public ResidentMeal(SubjectId residentId, SubjectId settlementId, SubjectId depotId,
                        SurfaceAnchor clearingSurface, SubjectId sourceAccountId, SubjectId actorAccountId,
                        FoodPortion portion, SubjectId claimId, Optional<SubjectId> retainedWorkOwner,
                        Phase phase, long startedAtTick, Optional<ResidentActivityChoice.Wait> waitReason,
                        Optional<ResidentMealPhysicalStep> pendingPhysicalStep) {
        this(residentId, settlementId, depotId, clearingSurface, sourceAccountId, actorAccountId,
                portion, claimId, retainedWorkOwner, phase, startedAtTick, waitReason,
                pendingPhysicalStep, Optional.empty());
    }

    public ResidentMeal prepare(ResidentMealPhysicalStep step) {
        if (pendingPhysicalStep.isPresent() || step.phase() != phase)
            throw new IllegalArgumentException("meal cannot prepare a second or foreign physical effect");
        return new ResidentMeal(residentId, settlementId, depotId, clearingSurface, sourceAccountId, actorAccountId,
                portion, claimId, retainedWorkOwner, phase, startedAtTick, waitReason, Optional.of(step), coldTravel);
    }

    public ResidentMeal advance(Phase next) {
        boolean legal = switch (phase) {
            case MOVE -> next == Phase.TAKE;
            case TAKE -> next == Phase.CLEAR_ACCESS;
            case CLEAR_ACCESS -> next == Phase.CONSUME;
            case CONSUME -> false; // only confirmed consumption retires the meal
            case RETURN -> false;
        };
        if (!legal) throw new IllegalArgumentException("meal phase cannot skip a physical custody receipt");
        return new ResidentMeal(residentId, settlementId, depotId, clearingSurface, sourceAccountId, actorAccountId,
                portion, claimId, retainedWorkOwner, next, startedAtTick, Optional.empty(), Optional.empty(), Optional.empty());
    }

    private static boolean movesToClearancePhase(Phase phase) {
        return phase == Phase.CLEAR_ACCESS || phase == Phase.RETURN;
    }

    public ResidentMeal waitFor(ResidentActivityChoice.Wait reason) {
        return new ResidentMeal(residentId, settlementId, depotId, clearingSurface, sourceAccountId, actorAccountId,
                portion, claimId, retainedWorkOwner, phase, startedAtTick, Optional.of(reason), pendingPhysicalStep, coldTravel);
    }

    /** Admission beside an occupied station retains the same claim but requires a new approach. */
    public ResidentMeal reapproach() {
        if (phase != Phase.TAKE || pendingPhysicalStep.isPresent() || coldTravel.isPresent())
            throw new IllegalArgumentException("only an unbegun take can resume its station approach");
        return new ResidentMeal(residentId, settlementId, depotId, clearingSurface, sourceAccountId, actorAccountId,
                portion, claimId, retainedWorkOwner, Phase.MOVE, startedAtTick, Optional.empty(), Optional.empty(), Optional.empty());
    }

    public ResidentMeal clearWait() {
        return waitReason.isEmpty() ? this : new ResidentMeal(residentId, settlementId, depotId, clearingSurface,
                sourceAccountId, actorAccountId, portion, claimId, retainedWorkOwner, phase,
                startedAtTick, Optional.empty(), pendingPhysicalStep, coldTravel);
    }

    public ResidentMeal withColdTravel(TimedKnownRoute travel) {
        if (coldTravel.isPresent()) throw new IllegalArgumentException("meal already has COLD travel");
        return new ResidentMeal(residentId, settlementId, depotId, clearingSurface, sourceAccountId, actorAccountId,
                portion, claimId, retainedWorkOwner, phase, startedAtTick, waitReason, pendingPhysicalStep,
                Optional.of(travel));
    }

    public ResidentMeal withoutColdTravel() {
        if (coldTravel.isEmpty()) return this;
        return new ResidentMeal(residentId, settlementId, depotId, clearingSurface, sourceAccountId, actorAccountId,
                portion, claimId, retainedWorkOwner, phase, startedAtTick, waitReason, pendingPhysicalStep,
                Optional.empty());
    }
}
