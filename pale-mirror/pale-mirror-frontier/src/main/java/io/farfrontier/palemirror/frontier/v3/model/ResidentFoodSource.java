package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;

/** Producer-declared food access. A personal inventory is not a fictitious service container. */
public sealed interface ResidentFoodSource permits ResidentFoodSource.Depot, ResidentFoodSource.Personal {
    SubjectId accountId();
    record Depot(SubjectId settlementId, SubjectId containerId, SubjectId accountId, ActorItemSlot portionSlot) implements ResidentFoodSource {
        public Depot(SubjectId settlementId, SubjectId containerId, SubjectId accountId) {
            this(settlementId, containerId, accountId, ResidentMeal.CARRIED_PORTION_SLOT);
        }
        public Depot { Objects.requireNonNull(settlementId); Objects.requireNonNull(containerId); Objects.requireNonNull(accountId);
            Objects.requireNonNull(portionSlot);
            if (portionSlot instanceof ActorItemSlot.AttachedStorage) throw new IllegalArgumentException("meal portion belongs to the eater, not attached storage"); }
    }
    record Personal(SubjectId actorId, SubjectId accountId, ActorItemSlot slot) implements ResidentFoodSource {
        public Personal { Objects.requireNonNull(actorId); Objects.requireNonNull(accountId); Objects.requireNonNull(slot);
            if (slot instanceof ActorItemSlot.AttachedStorage) throw new IllegalArgumentException("personal food has no mobile container custody"); }
    }
}
