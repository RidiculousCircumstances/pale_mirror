package io.farfrontier.palemirror.frontier.reference;

import java.util.Comparator;
import java.util.EnumMap;

/** Exact canonical custody transfer for a physical settlement warehouse. */
final class ReferenceWarehouseCustody {
    private ReferenceWarehouseCustody() { }

    static double apply(ReferenceMarketEconomy economy, ReferenceMarketWorld world, int settlementId,
                        ReferenceResource resource, double quantity) {
        if (!Double.isFinite(quantity) || quantity == 0.0d) throw new IllegalArgumentException("warehouse delta is invalid");
        if (!world.settlements().containsKey(settlementId)) throw new IllegalArgumentException("warehouse settlement is unknown");
        if (quantity > 0.0d) {
            economy.warehousePublicInventory(settlementId).merge(resource, quantity, Double::sum);
            economy.synchronizeWarehouse(world);
            return quantity;
        }
        double requested = -quantity;
        double available = economy.warehousePublicInventory(settlementId).getOrDefault(resource, 0.0d);
        for (ReferenceCompany company : economy.warehouseCompanies()) {
            if (company.homeSettlementId() == settlementId) available += company.amount(resource);
        }
        if (available + 1.0e-9d < requested) return 0.0d;
        double removed = withdraw(economy, settlementId, resource, requested);
        if (Math.abs(removed - requested) > 1.0e-9d) throw new IllegalStateException("warehouse withdrawal lost canonical custody");
        economy.synchronizeWarehouse(world);
        return -removed;
    }

    static double withdraw(ReferenceMarketEconomy economy, int settlementId, ReferenceResource resource, double quantity) {
        double remaining = Math.max(0.0d, quantity);
        EnumMap<ReferenceResource, Double> reserve = economy.warehousePublicInventory(settlementId);
        double taken = Math.min(reserve.get(resource), remaining);
        reserve.put(resource, reserve.get(resource) - taken);
        remaining -= taken;
        for (ReferenceCompany company : economy.warehouseCompanies().stream().filter(value -> value.homeSettlementId() == settlementId)
                .sorted(Comparator.comparingDouble((ReferenceCompany value) -> value.amount(resource)).reversed()
                        .thenComparingInt(ReferenceCompany::id)).toList()) {
            if (remaining <= 1.0e-9d) break;
            remaining -= company.remove(resource, remaining);
        }
        return quantity - remaining;
    }
}
