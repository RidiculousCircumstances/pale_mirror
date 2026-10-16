package io.farfrontier.palemirror.frontier.v3.model.market;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import java.util.*;

/** Read-only spending envelopes. The financial ledger alone owns actual holds and payments. */
public final class ProcurementBudget {
    public record Need(String item, long quantity, FixedScalar unitLimit, long deadline, long restartQuantity) {
        public Need(String item, long quantity, FixedScalar unitLimit, long deadline) {
            this(item, quantity, unitLimit, deadline, 0);
        }
        public Need {
            Objects.requireNonNull(item); Objects.requireNonNull(unitLimit);
            if (item.isBlank() || quantity < 0 || unitLimit.raw() < 0 || deadline < 0
                    || restartQuantity < 0 || restartQuantity > quantity)
                throw new IllegalArgumentException("invalid procurement funding need");
        }
    }
    private ProcurementBudget() { }

    /** Earlier deadlines take precedence. At the same deadline, keep an affordable
     * complete production restart funded before spending on fallback or upper reserves. */
    public static FixedScalar forPurchase(FixedScalar available, List<Need> needs, String item) {
        var target = needs.stream().filter(n -> n.item().equals(item)).findFirst();
        if (target.isEmpty()) return available;
        var selected = target.orElseThrow();
        var left = remaining(available, needs.stream().filter(n -> n.deadline() < selected.deadline()).toList());
        var restart = restartNeeds(needs, selected.deadline());
        if (!affordable(left, restart)) return left;
        return remaining(left, restart.stream().filter(n -> !n.item().equals(item)).toList());
    }

    public static boolean fundsRestart(FixedScalar available, List<Need> needs, String item) {
        var target = needs.stream().filter(n -> n.item().equals(item) && n.restartQuantity() > 0).findFirst();
        if (target.isEmpty()) return false;
        long deadline = target.orElseThrow().deadline();
        var left = remaining(available, needs.stream().filter(n -> n.deadline() < deadline).toList());
        return affordable(left, restartNeeds(needs, deadline));
    }

    private static List<Need> restartNeeds(List<Need> needs, long deadline) {
        return needs.stream().filter(n -> n.deadline() == deadline && n.restartQuantity() > 0)
                .map(n -> new Need(n.item(), n.restartQuantity(), n.unitLimit(), n.deadline())).toList();
    }

    private static boolean affordable(FixedScalar available, List<Need> needs) {
        long left = available.raw();
        for (var need : needs) {
            if (need.unitLimit().raw() > 0 && need.quantity() > left / need.unitLimit().raw()) return false;
            left -= need.quantity() * need.unitLimit().raw();
        }
        return true;
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
