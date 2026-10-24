package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Read-only production demand from existing inventory/title and owner-declared commitments. */
public final class ProductionStockDemand {
    private record Resource(SubjectId owner, String itemKind) { }
    private static final ImmutableInputView<Map<Resource, Long>> STOCK = new ImmutableInputView<>();
    private ProductionStockDemand() { }

    public static StockTargetDemand read(FrontierWorldState state, SubjectId settlement, String itemKind, long atTick) {
        return read(state, settlement, itemKind, Optional.empty(), atTick);
    }
    public static StockTargetDemand read(FrontierWorldState state, SubjectId settlement, String itemKind,
                                        Optional<SubjectId> completingSubject, long atTick) {
        var supply = readSupply(state, settlement, itemKind, completingSubject, atTick);
        // Unified planning already expands obligations and selects own work vs
        // imports. Adding a protected input target here would override that choice.
        if (state.bootstrap().ruleset().goodsTrade().supplyPlanning().economics().isPresent()) return supply;
        long inputs = StockSupplyComposition.inputDemand(state, settlement, itemKind, atTick);
        return new StockTargetDemand(SettlementReservePlanning.enabled(state) ? Math.max(supply.target(), inputs) : Math.addExact(supply.target(), inputs),
                supply.held(), supply.incoming(), supply.outgoing());
    }
    static StockTargetDemand readSupply(FrontierWorldState state, SubjectId settlement, String itemKind,
                                       Optional<SubjectId> completingSubject, long atTick) {
        var participant = Objects.requireNonNull(state.companies().goodsTrade().participants().participants().get(settlement),
                "production demand requires its declared public participant");
        if (participant.policy() != GoodsPolicyKind.PUBLIC_SETTLEMENT
                || !participant.party().equals(GoodsParticipantDeclarations.publicParty(settlement)))
            throw new IllegalArgumentException("production demand has a foreign public authority");
        var commodity = state.bootstrap().ruleset().goodsTrade().policies().get(participant.policy()).stream()
                .filter(value -> value.itemKind().equals(itemKind)).findFirst().orElseThrow(
                        () -> new IllegalArgumentException("production output has no declared stock target: " + itemKind));
        long incoming = 0, outgoing = 0;
        for (var commitment : StockSupplyComposition.pending(state)) {
            if (!commitment.economicOwner().equals(settlement) || !commitment.itemKind().equals(itemKind)
                    || completingSubject.filter(commitment.subject()::equals).isPresent()) continue;
            incoming = Math.addExact(incoming, commitment.incoming());
            outgoing = Math.addExact(outgoing, commitment.outgoing());
        }
        long stock = held(state.inventory()).getOrDefault(new Resource(settlement, itemKind), 0L);
        var planning = state.bootstrap().ruleset().goodsTrade().supplyPlanning();
        // The economic plan owns the incremental production decision. Its caller
        // does not need a second legacy reserve calculation whose result is discarded.
        if (planning.economics().isPresent()) {
            var economic = state.companies().goodsTrade().participants().economicState(settlement);
            var accepted = economic.accepted();
            if (completingSubject.isEmpty() && economic.awaitingResources(atTick,
                    state.bootstrap().ruleset().goodsTrade().supplyPlanning().commodities().keySet()).contains(itemKind))
                return new StockTargetDemand(0, stock, incoming, outgoing);
            long target = accepted.map(plan -> Objects.requireNonNull(plan.productionCeilings().get(itemKind),
                    "undeclared accepted production item")).orElse(0L);
            return new StockTargetDemand(target, stock, incoming, outgoing);
        }
        long target = SettlementReservePlanning.enabled(state) ? SettlementReservePlanning.read(state, settlement, atTick).get(itemKind).quantity()
                : StockTargetDemand.target(commodity, LivingResidentStockView.counts(state).getOrDefault(settlement, 0),
                    PublicStockProtection.minimum(state, settlement, itemKind, atTick));
        if (planning.enabled()) {
            var published = GoodsParticipantAssessment.view(state, participant, atTick).supplyPlan();
            if (published.isEmpty()) return new StockTargetDemand(0, stock, incoming, outgoing);
            var demand = published.orElseThrow().require(itemKind);
            // Existing export demand remains in outgoing. Replenishment advice alone
            // must not restart production for every one-item drop above the lower mark.
            if (demand.production() == 0 && demand.purchase() == 0) target = Math.min(target, Math.addExact(stock, incoming));
        }
        return new StockTargetDemand(target, stock, incoming, outgoing);
    }
    public static long coverage(FrontierWorldState state, SubjectId owner, String item) {
        long incoming = 0, outgoing = 0;
        for (var commitment : StockSupplyComposition.pending(state)) {
            if (!commitment.economicOwner().equals(owner) || !commitment.itemKind().equals(item)) continue;
            incoming = Math.addExact(incoming, commitment.incoming()); outgoing = Math.addExact(outgoing, commitment.outgoing());
        }
        return new StockTargetDemand(0, held(state.inventory()).getOrDefault(new Resource(owner, item), 0L), incoming, outgoing).covered();
    }
    private static Map<Resource, Long> held(ExactInventory inventory) {
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
        });
    }
}
