package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Actual post-effect container layout for a claimed resource-lot recipe, never exact-item IDs. */
public record FungibleProductionObservation(PhysicalObservationId id, PhysicalIntentId intentId,
        SubjectId accountId, SubjectId containerId, SubjectId inputLotId, Map<SubjectId, Integer> inputLots,
        SubjectId claimId, SubjectId outputLotId,
        int quantity, long authorityEpoch, List<FungiblePhysicalObservation.Stack> observedStacks)
        implements PhysicalEffectObservation {
    public FungibleProductionObservation {
        Objects.requireNonNull(id, "production observation"); Objects.requireNonNull(intentId, "production intent");
        Objects.requireNonNull(accountId, "production account"); Objects.requireNonNull(containerId, "production container");
        Objects.requireNonNull(inputLotId, "production input lot"); Objects.requireNonNull(claimId, "production claim");
        Objects.requireNonNull(outputLotId, "production output lot");
        inputLots = Map.copyOf(Objects.requireNonNull(inputLots, "production input lot portions"));
        if (inputLotId.equals(outputLotId) || quantity < 1 || quantity > 64 || authorityEpoch < 1) {
            throw new IllegalArgumentException("production lot receipt has invalid identity, quantity or epoch");
        }
        if (inputLots.isEmpty() || inputLots.size() > 64 || !inputLots.containsKey(inputLotId)
                || inputLots.values().stream().anyMatch(value -> value < 1 || value > 64)
                || inputLots.values().stream().mapToInt(Integer::intValue).sum() != quantity
                || !inputLotId.equals(inputLots.keySet().stream().min(SubjectId::compareTo).orElseThrow())) {
            throw new IllegalArgumentException("production receipt must retain its complete bounded lot input");
        }
        observedStacks = List.copyOf(Objects.requireNonNull(observedStacks, "production observed layout"));
        if (observedStacks.isEmpty() || observedStacks.size() > 54
                || observedStacks.stream().map(FungiblePhysicalObservation.Stack::address).distinct().count() != observedStacks.size()
                || observedStacks.stream().anyMatch(stack -> !(stack.address() instanceof PhysicalStackAddress.ContainerSlot slot)
                        || !slot.slot().containerId().equals(containerId) || slot.slot().slot() >= 54)) {
            throw new IllegalArgumentException("production lot receipt requires one bounded exact container layout");
        }
    }

    public FungibleProductionObservation(PhysicalObservationId id, PhysicalIntentId intentId, SubjectId accountId,
                                         SubjectId containerId, SubjectId inputLotId, SubjectId claimId, SubjectId outputLotId,
                                         int quantity, long authorityEpoch, List<FungiblePhysicalObservation.Stack> observedStacks) {
        this(id, intentId, accountId, containerId, inputLotId, Map.of(inputLotId, quantity), claimId, outputLotId,
                quantity, authorityEpoch, observedStacks);
    }
}
