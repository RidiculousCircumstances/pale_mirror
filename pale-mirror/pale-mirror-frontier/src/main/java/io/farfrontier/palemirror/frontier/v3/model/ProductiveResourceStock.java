package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Actual owner-held productive stock only. No incoming promises, policy or recursive economic planning. */
public final class ProductiveResourceStock {
    private record Resource(SubjectId owner, String kind) { }
    private static final ImmutableGroupView<SubjectId, ResourceLot, Resource> LOTS = new ImmutableGroupView<>();
    private static final ImmutableGroupView<SubjectId, CustodyAccount, SubjectId> HOLDERS = new ImmutableGroupView<>();
    private ProductiveResourceStock() { }
    public static long held(ExactInventory inventory, SubjectId owner, String kind) {
        var resource = new Resource(Objects.requireNonNull(owner), Objects.requireNonNull(kind));
        long quantity = 0;
        for (var item : ExactItemReadIndex.owned(inventory, owner, kind)) {
            if (item.custody() instanceof InventoryCustody.Player || item.custody() instanceof InventoryCustody.WorldCarrier) continue;
            quantity = Math.addExact(quantity, item.count());
        }
        var ledger = inventory.fungibleResources();
        // Read title from current lot declarations, and quantity from current
        // holder rows. A title transfer may leave the account itself unchanged.
        for (var lot : LOTS.unorderedRows(ProductiveResourceStock.class, ledger.lots(), value ->
                Set.of(new Resource(value.economicOwnerId(), value.itemKind())), resource)) {
            for (var account : HOLDERS.unorderedRows(ProductiveResourceStock.class, ledger.accounts(), value ->
                    value.custody() instanceof ResourceCustody.Player || value.custody() instanceof ResourceCustody.WorldCarrier
                            || value.custody() instanceof ResourceCustody.Ground ? Set.of() : value.lotQuantities().keySet(), lot.id()))
                quantity = Math.addExact(quantity, account.lotQuantities().get(lot.id()));
        }
        return quantity;
    }
}
