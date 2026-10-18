package io.farfrontier.palemirror.frontier.v3.model.expedition;

import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.TransportMission;
import java.util.Optional;

/** Funding owner for admitted transport itineraries, independent of the transported goods. */
public final class ExpeditionFundingAuthority {
    private ExpeditionFundingAuthority() { }

    public static FixedScalar allocation(FrontierWorldState state, TransportMission mission) {
        if (state.bootstrap().ruleset().schemaVersion() >= 17 && mission.supplies().isEmpty())
            throw new IllegalArgumentException("expedition funding requires its full round-trip supply declaration");
        // Current itineraries provision the entire round trip before departure. They do not
        // plan purchases en route. A future purchase itinerary must declare its required cost,
        // not repurpose the optional replenishment ceiling as that requirement.
        return FixedScalar.ZERO;
    }

    public static TransportMission declare(FrontierWorldState state, TransportMission mission) {
        var amount = allocation(state, mission);
        return mission.withFinancialBudget(amount.raw() == 0 ? Optional.empty()
                : Optional.of(new SubjectId("budget:" + mission.id().value().replace(':', '-'))));
    }

    /** A treasury quote is not a hold: competing missions are serialized at purchase admission. */
    public static FixedScalar available(FrontierWorldState state, TransportMission mission) {
        var ledger = state.inventory().economics();
        return ledger.availableToReserve(mission.organization().sponsor().id()).plus(mission.financialBudgetId()
                .map(id -> ledger.budgets().get(id).remaining()).orElse(FixedScalar.ZERO));
    }

    public static io.farfrontier.palemirror.frontier.v3.model.FinancialBudget purchaseBudget(
            FrontierWorldState state, TransportMission mission, FixedScalar cost) {
        if (cost.raw() <= 0 || cost.compareTo(available(state, mission)) > 0)
            throw new IllegalArgumentException("expedition purchase exceeds actual available treasury");
        var id = mission.financialBudgetId().orElseGet(() -> new SubjectId("budget:" + mission.id().value().replace(':', '-')));
        return new io.farfrontier.palemirror.frontier.v3.model.FinancialBudget(id, mission.organization().sponsor().id(),
                io.farfrontier.palemirror.frontier.v3.model.FinancialBudget.OwnerKind.TRANSPORT_MISSION, mission.id(), cost);
    }

    public static io.farfrontier.palemirror.frontier.v3.model.ExactInventory cancelPurchase(
            io.farfrontier.palemirror.frontier.v3.model.ExactInventory inventory,
            io.farfrontier.palemirror.frontier.v3.model.GoodsSpotPurchase purchase) {
        var released = io.farfrontier.palemirror.frontier.v3.model.GoodsSpotPurchaseAuthority.cancel(inventory, purchase);
        var ledger = released.economics();
        var budget = ledger.budgets().get(purchase.payment().budgetId().orElseThrow());
        // Refund becomes treasury liquidity immediately, not another indefinite mission contingency.
        return released.withEconomics(ledger.resizeBudget(budget, FixedScalar.ZERO));
    }
}
