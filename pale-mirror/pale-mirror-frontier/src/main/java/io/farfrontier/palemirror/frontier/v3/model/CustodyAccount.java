package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Map;
import java.util.Objects;

/** One canonical resource location; slots are transient physical bindings rather than stock owners. */
public record CustodyAccount(SubjectId id, InventoryCustody custody, Map<SubjectId, Integer> lotQuantities,
                             Map<SubjectId, Integer> claimQuantities) {
    public CustodyAccount {
        Objects.requireNonNull(id, "custody account id"); Objects.requireNonNull(custody, "custody account location");
        lotQuantities = checked(lotQuantities, "lot", false); claimQuantities = checked(claimQuantities, "claim", true);
    }

    private static Map<SubjectId, Integer> checked(Map<SubjectId, Integer> values, String label, boolean emptyAllowed) {
        if (values == null || (!emptyAllowed && values.isEmpty())) throw new IllegalArgumentException("custody account must retain at least one " + label + " quantity");
        Map<SubjectId, Integer> copy = Map.copyOf(values);
        if (copy.values().stream().anyMatch(value -> value == null || value < 1 || value > ResourceLot.MAX_QUANTITY)) {
            throw new IllegalArgumentException("custody account " + label + " quantity is invalid");
        }
        return copy;
    }
}
