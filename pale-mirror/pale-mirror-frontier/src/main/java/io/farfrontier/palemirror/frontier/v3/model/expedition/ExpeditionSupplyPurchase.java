package io.farfrontier.palemirror.frontier.v3.model.expedition;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;

/** Priced future supplier stop. Money is secured separately; stock and seller consent are not promises. */
public record ExpeditionSupplyPurchase(ShipmentEndpoint supplier, String foodKind,
        Map<SubjectId, Integer> personalQuantities, FixedScalar unitPrice) {
    public ExpeditionSupplyPurchase {
        Objects.requireNonNull(supplier); Objects.requireNonNull(foodKind); Objects.requireNonNull(unitPrice);
        personalQuantities = Map.copyOf(personalQuantities);
        if (foodKind.isBlank() || personalQuantities.isEmpty() || personalQuantities.size() > 32
                || personalQuantities.values().stream().anyMatch(q -> q == null || q < 1 || q > 640)
                || unitPrice.raw() <= 0) throw new IllegalArgumentException("invalid planned expedition purchase");
    }
    public int quantity() { return personalQuantities.values().stream().reduce(0, Math::addExact); }
    public FixedScalar amount() { return unitPrice.multiply(quantity()); }
}
