package io.farfrontier.palemirror.frontier.v3.model.market;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import java.util.*;

/** Read-only spending envelopes. The financial ledger alone owns actual holds and payments. */
public final class ProcurementBudget {
    public record Need(String item, long quantity, FixedScalar unitLimit, long deadline) {
        public Need {
            Objects.requireNonNull(item); Objects.requireNonNull(unitLimit);
            if (item.isBlank() || quantity < 0 || unitLimit.raw() < 0 || deadline < 0)
                throw new IllegalArgumentException("invalid procurement funding need");
        }
    }
    private ProcurementBudget() { }

    /** Earlier supply deadlines take precedence. Equal-priority quotes remain alternatives. */
    public static FixedScalar forPurchase(FixedScalar available, List<Need> needs, String item) {
        var target = needs.stream().filter(n -> n.item().equals(item)).findFirst();
        if (target.isEmpty()) return available;
        return remaining(available, needs.stream().filter(n -> n.deadline() < target.orElseThrow().deadline()).toList());
    }

    public static FixedScalar forOptionalSpending(FixedScalar available, List<Need> needs) {
        return remaining(available, needs);
    }

    private static FixedScalar remaining(FixedScalar available, List<Need> protectedNeeds) {
        if (available.raw() < 0) throw new IllegalArgumentException("negative procurement funds");
        long left = available.raw();
        for (var need : protectedNeeds) {
            // Saturate at available money, without overflowing on a large desired reserve.
            if (need.unitLimit().raw() > 0 && need.quantity() > left / need.unitLimit().raw()) return FixedScalar.ZERO;
            left -= need.quantity() * need.unitLimit().raw();
        }
        return new FixedScalar(left);
    }
}
