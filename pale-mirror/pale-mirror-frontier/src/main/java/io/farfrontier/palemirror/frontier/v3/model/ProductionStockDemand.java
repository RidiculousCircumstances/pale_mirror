package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.*;

/** Read-only production demand from existing inventory/title and owner-declared commitments. */
public final class ProductionStockDemand {
    private record Resource(SubjectId owner, String itemKind) { }
    private static final ImmutableInputView<Map<Resource, Long>> STOCK = new ImmutableInputView<>();
    private ProductionStockDemand() { }

    public static StockTargetDemand read(FrontierWorldState state, SubjectId settlement, String itemKind) {
        return read(state, settlement, itemKind, Optional.empty());
    }
    public static StockTargetDemand read(FrontierWorldState state, SubjectId settlement, String itemKind,
                                        Optional<SubjectId> completingSubject) {
        var supply = readSupply(state, settlement, itemKind, completingSubject);
        return new StockTargetDemand(Math.addExact(supply.target(), StockSupplyComposition.inputDemand(state, settlement, itemKind)),
                supply.held(), supply.incoming(), supply.outgoing());
    }
    static StockTargetDemand readSupply(FrontierWorldState state, SubjectId settlement, String itemKind,
                                       Optional<SubjectId> completingSubject) {
        var participant = Objects.requireNonNull(state.companies().goodsTrade().participants().participants().get(settlement),
                "production demand requires its declared public participant");
        if (participant.policy() != GoodsPolicyKind.PUBLIC_SETTLEMENT
                || !participant.party().equals(GoodsParticipantDeclarations.publicParty(settlement)))
            throw new IllegalArgumentException("production demand has a foreign public authority");
        var commodity = state.bootstrap().ruleset().goodsTrade().policies().get(participant.policy()).stream()
                .filter(value -> value.itemKind().equals(itemKind)).findFirst().orElseThrow(
                        () -> new IllegalArgumentException("production output has no declared stock target: " + itemKind));
        int residents = LivingResidentStockView.counts(state).getOrDefault(settlement, 0);
        int minimum = PublicStockProtection.minimum(state, settlement, itemKind);
        long incoming = 0, outgoing = 0;
        for (var commitment : StockSupplyComposition.pending(state)) {
            if (!commitment.economicOwner().equals(settlement) || !commitment.itemKind().equals(itemKind)
                    || completingSubject.filter(commitment.subject()::equals).isPresent()) continue;
            incoming = Math.addExact(incoming, commitment.incoming());
            outgoing = Math.addExact(outgoing, commitment.outgoing());
        }
        return new StockTargetDemand(StockTargetDemand.target(commodity, residents, minimum),
                held(state.inventory()).getOrDefault(new Resource(settlement, itemKind), 0L), incoming, outgoing);
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
