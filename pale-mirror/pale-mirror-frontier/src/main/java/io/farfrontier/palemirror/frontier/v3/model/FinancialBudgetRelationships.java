package io.farfrontier.palemirror.frontier.v3.model;

import java.util.List;
import java.util.Optional;
import static io.farfrontier.palemirror.frontier.v3.model.FrontierDomainRelationships.*;

/** Explicit registered budget-owner references. No spending, selection or inferred owner kind. */
final class FinancialBudgetRelationships {
    private FinancialBudgetRelationships() { }
    static void collect(FrontierWorldState state, List<Edge> edges) {
        for (var budget : state.inventory().economics().budgets().values()) {
            var source = new SubjectEndpoint(EntityKind.FINANCIAL_BUDGET, budget.id());
            var mission = state.shipments().missions().get(budget.ownerId());
            if (budget.ownerKind() != FinancialBudget.OwnerKind.TRANSPORT_MISSION || mission == null
                    || !mission.financialBudgetId().equals(Optional.of(budget.id()))
                    || !mission.sender().settlementId().equals(budget.payerId())
                    || mission.stage() == TransportMission.Stage.COMPLETE && budget.remaining().raw() != 0)
                throw new IllegalArgumentException("financial budget lost its exact active or closed mission owner");
            var life = mission.stage() == TransportMission.Stage.COMPLETE ? Lifecycle.TERMINAL_RETAINED : Lifecycle.ACTIVE;
            edges.add(declaredEdge(Kind.BUDGET_PAYER, source, source,
                    new SubjectEndpoint(EntityKind.ECONOMIC_ACCOUNT, budget.payerId()), life, "budget:" + budget.id().value()));
            edges.add(declaredEdge(Kind.BUDGET_OWNER, source, source,
                    new SubjectEndpoint(EntityKind.TRANSPORT_MISSION, budget.ownerId()), life, "budget:" + budget.id().value()));
        }
        for (var mission : state.shipments().missions().values()) mission.financialBudgetId().ifPresent(id -> {
            var budget = state.inventory().economics().budgets().get(id);
            if (budget == null || !budget.ownerId().equals(mission.id()))
                throw new IllegalArgumentException("transport mission lost its exact treasury budget");
        });
        for (var purchase : state.inventory().economics().reservations().values()) purchase.budgetId().ifPresent(id -> {
            var source = new SubjectEndpoint(EntityKind.FINANCIAL_RESERVATION, purchase.id());
            edges.add(declaredEdge(Kind.RESERVATION_BUDGET, source, source,
                    new SubjectEndpoint(EntityKind.FINANCIAL_BUDGET, id), Lifecycle.ACTIVE, "purchase:" + purchase.id().value()));
        });
    }
}
