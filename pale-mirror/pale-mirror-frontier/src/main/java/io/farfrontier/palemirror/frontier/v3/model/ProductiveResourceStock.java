package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Actual owner-held productive stock only. No incoming promises, policy or recursive economic planning. */
public final class ProductiveResourceStock {
    private record Resource(SubjectId owner, String kind) { }
    private static final ImmutableInputView<Map<Resource, Long>> STOCK = new ImmutableInputView<>();
    private ProductiveResourceStock() { }
    public static long held(ExactInventory inventory, SubjectId owner, String kind) {
        return STOCK.get(List.of(inventory), () -> {
            var result = new HashMap<Resource, Long>();
            for (var item : inventory.items().values()) {
                if (item.custody() instanceof InventoryCustody.Player || item.custody() instanceof InventoryCustody.WorldCarrier) continue;
                result.merge(new Resource(item.economicOwnerId(), item.itemKind()), (long) item.count(), Math::addExact);
            }
            var ledger = inventory.fungibleResources();
            for (var account : ledger.accounts().values()) {
                if (account.custody() instanceof ResourceCustody.Player || account.custody() instanceof ResourceCustody.WorldCarrier
                        || account.custody() instanceof ResourceCustody.Ground) continue;
                for (var entry : account.lotQuantities().entrySet()) {
                    var lot = ledger.lots().get(entry.getKey());
                    result.merge(new Resource(lot.economicOwnerId(), lot.itemKind()), (long) entry.getValue(), Math::addExact);
                }
            }
            return Map.copyOf(result);
        }).getOrDefault(new Resource(Objects.requireNonNull(owner), Objects.requireNonNull(kind)), 0L);
    }
}
