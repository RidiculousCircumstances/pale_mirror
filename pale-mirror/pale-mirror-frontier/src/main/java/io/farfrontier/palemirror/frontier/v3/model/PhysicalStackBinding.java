package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Map;
import java.util.Objects;

/** Durable HOT observation of one current physical stack, fenced by a custody authority epoch. */
public record PhysicalStackBinding(SubjectId id, SubjectId accountId, PhysicalStackAddress address,
                                   long authorityEpoch, String itemKind, Map<SubjectId, Integer> lotQuantities,
                                   Map<SubjectId, Integer> claimQuantities) {
    public PhysicalStackBinding {
        Objects.requireNonNull(id, "physical stack binding id"); Objects.requireNonNull(accountId, "binding account"); Objects.requireNonNull(address, "binding address");
        if (authorityEpoch < 1) throw new IllegalArgumentException("binding authority epoch must be positive");
        if (itemKind == null || !itemKind.matches("[a-z][a-z0-9_-]{0,31}:[a-z0-9][a-z0-9_./-]{0,127}")) {
            throw new IllegalArgumentException("physical stack binding kind must be namespace:path");
        }
        lotQuantities = quantities(lotQuantities, "lot", false); claimQuantities = quantities(claimQuantities, "claim", true);
    }

    public int quantity() { return lotQuantities.values().stream().mapToInt(Integer::intValue).sum(); }

    private static Map<SubjectId, Integer> quantities(Map<SubjectId, Integer> values, String label, boolean emptyAllowed) {
        if (values == null || (!emptyAllowed && values.isEmpty())) throw new IllegalArgumentException("physical stack binding must retain " + label + " quantities");
        Map<SubjectId, Integer> copy = Map.copyOf(values);
        if (copy.values().stream().anyMatch(value -> value == null || value < 1 || value > 64)) {
            throw new IllegalArgumentException("physical stack binding " + label + " quantity is invalid");
        }
        return copy;
    }
}
