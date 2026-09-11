package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Durable exact evidence for one partial player, hopper, drop, or pickup handoff. */
public record FungibleResourceHandoffObserved(SubjectId sourceAccountId, CustodyAccount destinationAccount,
                                              long sourceEpoch, long destinationEpoch,
                                              Map<SubjectId, Integer> lotQuantities, Map<SubjectId, Integer> claimQuantities,
                                              List<PhysicalStackBinding> remainingSource, List<PhysicalStackBinding> destinationBindings)
        implements FrontierPayload {
    public FungibleResourceHandoffObserved {
        Objects.requireNonNull(sourceAccountId, "handoff source account"); Objects.requireNonNull(destinationAccount, "handoff destination account");
        if (sourceEpoch < 1 || destinationEpoch < 1) throw new IllegalArgumentException("handoff authority epochs must be positive");
        lotQuantities = quantities(lotQuantities, "lot", false); claimQuantities = quantities(claimQuantities, "claim", true);
        remainingSource = remainingSource.stream().sorted(java.util.Comparator.comparing(PhysicalStackBinding::id)).toList();
        destinationBindings = destinationBindings.stream().sorted(java.util.Comparator.comparing(PhysicalStackBinding::id)).toList();
        if (remainingSource.stream().anyMatch(Objects::isNull) || destinationBindings.isEmpty() || destinationBindings.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("handoff requires current non-null destination binding evidence");
        }
    }
    @Override public String type() { return "frontier.fungible_resource_handoff_observed"; }

    private static Map<SubjectId, Integer> quantities(Map<SubjectId, Integer> values, String label, boolean emptyAllowed) {
        if (values == null || (!emptyAllowed && values.isEmpty())) throw new IllegalArgumentException("handoff " + label + " quantities are required");
        Map<SubjectId, Integer> copy = Map.copyOf(values);
        if (copy.values().stream().anyMatch(value -> value == null || value < 1 || value > ResourceLot.MAX_QUANTITY)) {
            throw new IllegalArgumentException("handoff " + label + " quantity is invalid");
        }
        return copy;
    }
}
