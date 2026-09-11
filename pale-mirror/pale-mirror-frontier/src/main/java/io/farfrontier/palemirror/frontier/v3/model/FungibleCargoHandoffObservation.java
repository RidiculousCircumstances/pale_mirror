package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.List;
import java.util.Objects;

/** Exact HOT receipt for one ordinary cargo lot materialized into the destination store. */
public record FungibleCargoHandoffObservation(PhysicalObservationId id, PhysicalIntentId intentId, SubjectId cargoId,
                                              long authorityEpoch, List<FungiblePhysicalObservation.Stack> stacks)
        implements PhysicalEffectObservation {
    public FungibleCargoHandoffObservation {
        Objects.requireNonNull(id, "fungible cargo observation id"); Objects.requireNonNull(intentId, "fungible cargo intent");
        Objects.requireNonNull(cargoId, "fungible cargo id");
        if (authorityEpoch < 1) throw new IllegalArgumentException("fungible cargo authority epoch must be positive");
        stacks = List.copyOf(Objects.requireNonNull(stacks, "fungible cargo stacks"));
        if (stacks.isEmpty() || stacks.size() > 27 || stacks.stream().map(FungiblePhysicalObservation.Stack::address).distinct().count() != stacks.size()) {
            throw new IllegalArgumentException("fungible cargo receipt requires one bounded distinct stack layout");
        }
    }
}
