package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Map;
import java.util.Objects;

/** One canonical resource location. Optional personal placement survives inventory changes;
 * it is neither resource identity nor proof that a physical stack has been materialized. */
public record CustodyAccount(SubjectId id, ResourceCustody custody, Map<SubjectId, Integer> lotQuantities,
                             Map<SubjectId, Integer> claimQuantities, java.util.Optional<ActorItemSlot> actorPresentation) {
    public CustodyAccount(SubjectId id, ResourceCustody custody, Map<SubjectId, Integer> lots, Map<SubjectId, Integer> claims) {
        this(id, custody, lots, claims, java.util.Optional.empty());
    }
    public CustodyAccount {
        Objects.requireNonNull(id, "custody account id"); Objects.requireNonNull(custody, "custody account location");
        lotQuantities = checked(lotQuantities, "lot", false); claimQuantities = checked(claimQuantities, "claim", true);
        actorPresentation = Objects.requireNonNull(actorPresentation, "declared actor presentation");
        if (actorPresentation.isPresent() && !(custody instanceof ResourceCustody.Actor))
            throw new IllegalArgumentException("personal presentation requires actor custody");
        if (actorPresentation.orElse(null) instanceof ActorItemSlot.AttachedStorage)
            throw new IllegalArgumentException("attached storage must retain container custody, not personal presentation");
    }

    public CustodyAccount withQuantities(Map<SubjectId, Integer> lots, Map<SubjectId, Integer> claims) {
        return new CustodyAccount(id, custody, lots, claims, actorPresentation);
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
