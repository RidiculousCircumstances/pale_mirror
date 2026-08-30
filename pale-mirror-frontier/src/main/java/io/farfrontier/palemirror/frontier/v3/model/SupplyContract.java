package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;
import java.util.regex.Pattern;

/** A bounded exact-resource obligation from one settlement to the shared hive polity. */
public record SupplyContract(SubjectId id, SubjectId settlementId, SubjectId recipientId, SubjectId cargoId,
                             String itemKind, int itemCount, ContractStatus status) {
    private static final Pattern ITEM_KIND = Pattern.compile("[a-z][a-z0-9_-]{0,31}:[a-z0-9][a-z0-9_./-]{0,127}");
    public SupplyContract {
        Objects.requireNonNull(id); Objects.requireNonNull(settlementId); Objects.requireNonNull(recipientId); Objects.requireNonNull(cargoId);
        if (itemKind == null || !ITEM_KIND.matcher(itemKind).matches()) throw new IllegalArgumentException("contract item kind must be namespace:path");
        if (itemCount <= 0 || itemCount > 64) throw new IllegalArgumentException("contract item count must be 1..64");
        Objects.requireNonNull(status);
    }

    public SupplyContract withStatus(ContractStatus nextStatus) {
        return new SupplyContract(id, settlementId, recipientId, cargoId, itemKind, itemCount, nextStatus);
    }
}
