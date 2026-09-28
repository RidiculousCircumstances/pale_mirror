package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;
import java.util.Optional;

/** One retained self-care activity for one person; a job assignment remains a separate owner. */
public record ResidentMeal(SubjectId residentId, SubjectId settlementId, SubjectId depotId,
                           SubjectId sourceAccountId, SubjectId actorAccountId,
                           SubjectId lotId, SubjectId claimId,
                           Optional<SubjectId> retainedWorkOwner, Phase phase,
                           long startedAtTick, Optional<ResidentActivityChoice.Wait> waitReason) {
    public static final String BREAD_KIND = "minecraft:bread";
    public enum Phase { MOVE, TAKE, CONSUME, RETURN }

    public ResidentMeal {
        Objects.requireNonNull(residentId, "meal resident");
        Objects.requireNonNull(settlementId, "meal settlement");
        Objects.requireNonNull(depotId, "meal depot");
        Objects.requireNonNull(sourceAccountId, "meal source account");
        Objects.requireNonNull(actorAccountId, "meal actor account");
        Objects.requireNonNull(lotId, "meal bread lot");
        Objects.requireNonNull(claimId, "meal bread claim");
        retainedWorkOwner = Objects.requireNonNull(retainedWorkOwner, "meal retained assignment");
        Objects.requireNonNull(phase, "meal phase");
        waitReason = Objects.requireNonNull(waitReason, "meal wait reason");
        if (!residentId.value().startsWith("resident:") || !settlementId.value().startsWith("settlement:")
                || !FrontierWorldState.depotId(settlementId).equals(depotId)
                || !sourceAccountId.equals(ReferenceContainerCustody.scopeId(depotId))
                || !actorAccountId.value().startsWith("custody:resident-meal-")
                || sourceAccountId.equals(actorAccountId) || startedAtTick < 0) {
            throw new IllegalArgumentException("meal must retain one exact resident, depot and custody pair");
        }
    }

    public ResidentMeal advance(Phase next) {
        boolean legal = switch (phase) {
            case MOVE -> next == Phase.TAKE;
            case TAKE -> next == Phase.CONSUME;
            case CONSUME -> next == Phase.RETURN;
            case RETURN -> false;
        };
        if (!legal) throw new IllegalArgumentException("meal phase cannot skip a physical custody receipt");
        return new ResidentMeal(residentId, settlementId, depotId, sourceAccountId, actorAccountId,
                lotId, claimId, retainedWorkOwner, next, startedAtTick, Optional.empty());
    }

    public ResidentMeal waitFor(ResidentActivityChoice.Wait reason) {
        return new ResidentMeal(residentId, settlementId, depotId, sourceAccountId, actorAccountId,
                lotId, claimId, retainedWorkOwner, phase, startedAtTick, Optional.of(reason));
    }

    public ResidentMeal clearWait() {
        return waitReason.isEmpty() ? this : new ResidentMeal(residentId, settlementId, depotId,
                sourceAccountId, actorAccountId, lotId, claimId, retainedWorkOwner, phase,
                startedAtTick, Optional.empty());
    }
}
