package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;

/** Narrow canonical queries shared by recipe and hive owners; it never infers stock from Vanilla. */
public final class FungibleResourceCustodySupport {
    private FungibleResourceCustodySupport() { }

    public static Optional<LotAtContainer> firstAtContainer(FrontierWorldState state, SubjectId containerId, String itemKind, int minimumQuantity) {
        Objects.requireNonNull(state, "resource state"); Objects.requireNonNull(containerId, "resource container");
        if (itemKind == null || itemKind.isBlank() || minimumQuantity < 1) throw new IllegalArgumentException("resource query is invalid");
        FungibleResourceLedger resources = state.inventory().fungibleResources();
        return resources.accounts().values().stream().filter(account -> account.custody() instanceof ResourceCustody.Container container
                        && container.containerId().equals(containerId)).flatMap(account -> account.lotQuantities().entrySet().stream()
                        .filter(entry -> entry.getValue() >= minimumQuantity).map(entry -> new LotAtContainer(account.id(), resources.lots().get(entry.getKey()), entry.getValue())))
                .filter(value -> value.lot().itemKind().equals(itemKind)).sorted(Comparator.comparing(value -> value.lot().id())).findFirst();
    }

    public static Optional<CustodyAccount> accountAtContainer(FrontierWorldState state, SubjectId containerId) {
        Objects.requireNonNull(state, "resource state"); Objects.requireNonNull(containerId, "resource container");
        return state.inventory().fungibleResources().accounts().values().stream().filter(account -> account.custody() instanceof ResourceCustody.Container container
                && container.containerId().equals(containerId)).findFirst();
    }

    public record LotAtContainer(SubjectId accountId, ResourceLot lot, int quantity) {
        public LotAtContainer {
            Objects.requireNonNull(accountId, "resource account"); Objects.requireNonNull(lot, "resource lot");
            if (quantity < 1 || quantity > lot.quantity()) throw new IllegalArgumentException("resource custody quantity is invalid");
        }
    }
}
