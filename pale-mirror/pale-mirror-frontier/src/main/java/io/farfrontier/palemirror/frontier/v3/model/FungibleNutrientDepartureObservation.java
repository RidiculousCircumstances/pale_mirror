package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.List;
import java.util.Objects;

/** Observed removal of one HOT biomass portion before it becomes unbound nutrient cargo. */
public record FungibleNutrientDepartureObservation(PhysicalObservationId id, PhysicalIntentId intentId,
                                                   SubjectId transferId, SubjectId cargoId, SubjectId sourceAccountId,
                                                   SubjectId lotId, int quantity, long authorityEpoch,
                                                   List<FungiblePhysicalObservation.Stack> remainingStacks)
        implements PhysicalEffectObservation {
    public FungibleNutrientDepartureObservation {
        Objects.requireNonNull(id, "fungible nutrient departure observation id");
        Objects.requireNonNull(intentId, "fungible nutrient departure intent");
        Objects.requireNonNull(transferId, "fungible nutrient departure transfer");
        Objects.requireNonNull(cargoId, "fungible nutrient departure cargo");
        Objects.requireNonNull(sourceAccountId, "fungible nutrient departure source account");
        Objects.requireNonNull(lotId, "fungible nutrient departure lot");
        if (quantity < 1 || quantity > ResourceLot.MAX_QUANTITY || authorityEpoch < 1) {
            throw new IllegalArgumentException("fungible nutrient departure has invalid quantity or authority");
        }
        remainingStacks = List.copyOf(Objects.requireNonNull(remainingStacks, "fungible nutrient departure remaining stacks"));
        if (remainingStacks.size() > 54 || remainingStacks.stream().map(FungiblePhysicalObservation.Stack::address).distinct().count() != remainingStacks.size()) {
            throw new IllegalArgumentException("fungible nutrient departure has duplicate or unbounded remaining layout");
        }
    }
}
