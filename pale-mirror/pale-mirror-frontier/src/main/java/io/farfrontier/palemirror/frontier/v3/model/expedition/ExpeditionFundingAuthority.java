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
        return mission.supplies().flatMap(ExpeditionSupplyLoad::purchase).map(ExpeditionSupplyPurchase::amount).orElse(FixedScalar.ZERO);
    }
    /** Read-only admission/replan funds, including this exact mission's existing unspent hold. */
    public static FixedScalar availableForPlan(FrontierWorldState state, TransportMission mission) {
        var ledger = state.inventory().economics();
        var held = mission.financialBudgetId().map(ledger.budgets()::get);
        held.ifPresent(budget -> {
            if (!budget.payerId().equals(mission.organization().sponsor().id()) || !budget.ownerId().equals(mission.id())
                    || budget.ownerKind() != io.farfrontier.palemirror.frontier.v3.model.FinancialBudget.OwnerKind.TRANSPORT_MISSION)
                throw new IllegalArgumentException("foreign expedition budget");
        });
        return ledger.availableToReserve(mission.organization().sponsor().id()).plus(held.map(b -> b.remaining()).orElse(FixedScalar.ZERO));
    }

    public static TransportMission declare(FrontierWorldState state, TransportMission mission) {
        var amount=allocation(state,mission);
        return mission.withFinancialBudget(amount.raw()==0 ? Optional.empty() : plannedBudgetId(mission,mission.supplies().orElseThrow()));
    }

    public static Optional<SubjectId> plannedBudgetId(TransportMission mission, ExpeditionSupplyLoad load) {
        return load.purchase().isEmpty() ? Optional.empty()
                : Optional.of(new SubjectId("budget:" + mission.id().value().replace(':', '-')));
    }

    /** A treasury quote is not a hold: competing missions are serialized at purchase admission. */
    public static FixedScalar available(FrontierWorldState state, TransportMission mission) {
        if (mission.financialBudgetId().filter(id -> !state.inventory().economics().budgets().containsKey(id)).isPresent())
            throw new IllegalArgumentException("active expedition lost its retained budget");
        return availableForPlan(state, mission);
    }

    public static io.farfrontier.palemirror.frontier.v3.model.FinancialBudget purchaseBudget(
            FrontierWorldState state, TransportMission mission, FixedScalar cost) {
        if (cost.raw() <= 0 || cost.compareTo(available(state, mission)) > 0)
            throw new IllegalArgumentException("expedition purchase exceeds actual available treasury");
        var previous = mission.financialBudgetId().map(state.inventory().economics().budgets()::get);
        if (previous.isPresent() && cost.compareTo(previous.orElseThrow().remaining()) <= 0) return previous.orElseThrow();
        var id = mission.financialBudgetId().orElseGet(() -> new SubjectId("budget:" + mission.id().value().replace(':', '-')));
        return new io.farfrontier.palemirror.frontier.v3.model.FinancialBudget(id, mission.organization().sponsor().id(),
                io.farfrontier.palemirror.frontier.v3.model.FinancialBudget.OwnerKind.TRANSPORT_MISSION, mission.id(), cost);
    }

    public static io.farfrontier.palemirror.frontier.v3.model.ExactInventory cancelPurchase(
            io.farfrontier.palemirror.frontier.v3.model.ExactInventory inventory,
            io.farfrontier.palemirror.frontier.v3.model.GoodsSpotPurchase purchase) {
        return io.farfrontier.palemirror.frontier.v3.model.GoodsSpotPurchaseAuthority.cancel(inventory, purchase);
    }
}
