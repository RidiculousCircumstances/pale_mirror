package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.List;
import java.util.Optional;

/** A derived personal-inventory view. The resource ledger alone owns stock and claims. */
public final class UnitInventory {
    private UnitInventory() { }

    public static List<CustodyAccount> accounts(FungibleResourceLedger resources, SubjectId unit) {
        return ActorCarriedResources.accounts(resources, unit);
    }

    /** Select only free owned stock; a commercial/work claim is never edible spare inventory. */
    public static Optional<FungibleResourceCustodySupport.LotSelection> select(
            FungibleResourceLedger resources, SubjectId unit, SubjectId owner, String kind, int quantity) {
        for (var account : accounts(resources, unit)) {
            var selection = FungibleResourceCustodySupport.selectAtAccount(resources, account.id(), owner, kind, quantity);
            if (selection.isPresent()) return selection;
        }
        return Optional.empty();
    }

    public static int available(FungibleResourceLedger resources, SubjectId unit, SubjectId owner, String kind) {
        return accounts(resources, unit).stream().mapToInt(account ->
                resources.unclaimedQuantity(account.id(), owner, kind)).reduce(0, Math::addExact);
    }

    public static void requireCapacity(FungibleResourceLedger resources, SubjectId unit, int stackSlots) {
        if (stackSlots < 1 || stackSlots > ActorCarriedResources.MAX_STACK_ACCOUNTS)
            throw new IllegalArgumentException("invalid personal inventory capacity");
        var accounts = accounts(resources, unit);
        if (accounts.size() > stackSlots)
            throw new IllegalArgumentException("personal inventory exceeds declared stack capacity");
        for (var account : accounts) {
            if (account.lotQuantities().values().stream().mapToInt(Integer::intValue).sum() > 64
                    || account.lotQuantities().keySet().stream().map(resources.lots()::get)
                        .map(ResourceLot::itemKind).distinct().count() != 1)
                throw new IllegalArgumentException("personal account must represent one homogeneous stack");
        }
    }
}
