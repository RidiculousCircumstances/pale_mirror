package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import java.util.*;

/** Finite genesis facts only. No orders, reservations, mission, effect, arrival or delivery is pre-created. */
public final class ConsolidatedTradeFixture {
    private ConsolidatedTradeFixture() { }
    public static FrontierWorldState initial(WorldId world, long seed, boolean requireAnimal) {
        return initial(world, seed, requireAnimal, FrontierRulesets.supplyQuarryGraybox());
    }
    public static FrontierWorldState initial(WorldId world, long seed, boolean requireAnimal, FrontierRuleset rules) {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(world, seed, rules));
        var seller = state.companies().goodsTrade().participants().participants().get(new SubjectId("settlement:1"));
        var buyer = state.companies().goodsTrade().participants().participants().get(seller.known().getFirst().party().id());
        var participants = new HashMap<>(state.companies().goodsTrade().participants().participants());
        participants.put(seller.party().id(), knowsOnly(seller, buyer)); participants.put(buyer.party().id(), knowsOnly(buyer, seller));
        state = state.withCompanies(state.companies().withGoodsTrade(state.companies().goodsTrade()
                .withParticipants(new GoodsParticipantState(participants))));
        var ledger = state.inventory().fungibleResources();
        var target = FungibleResourceCustodySupport.accountAtContainer(state, FrontierWorldState.depotId(buyer.settlementId())).orElseThrow();
        var bread = new TreeMap<SubjectId, Integer>();
        for (var entry : target.lotQuantities().entrySet())
            if (ledger.lots().get(entry.getKey()).itemKind().equals("minecraft:bread")) bread.put(entry.getKey(), entry.getValue());
        state = state.withInventory(state.inventory().withFungibleResources(ledger.destroy(target.id(), bread, Map.of())));
        state = add(state, seller, "minecraft:bread", 512);
        state = add(state, seller, "minecraft:cobblestone", 384);
        state = add(state, seller, "minecraft:coal", 64);
        if (requireAnimal) {
            ledger = state.inventory().fungibleResources();
            for (var resident : state.humanPopulation().residents().values()) {
                if (!resident.settlementId().equals(seller.party().id())) continue;
                // Occupied personal slots are actual goods, not an artificial transport-permission bypass.
                for (int slot = 0; slot < 9; slot++) {
                    var suffix = resident.id().value().replace(':', '-') + "/" + slot;
                    var lot = new SubjectId("lot:consolidated-personal/" + suffix);
                    ledger = ledger.issue(new ResourceLot(lot, resident.settlementId(), "minecraft:stone", 64,
                            "fixture:finite-personal-equipment", List.of()), new CustodyAccount(new SubjectId("custody:consolidated-personal/" + suffix),
                            new ResourceCustody.Actor(resident.id()), Map.of(lot, 64), Map.of(), Optional.of(UnitInventoryPresentation.slots().get(slot))));
                }
            }
            state = state.withInventory(state.inventory().withFungibleResources(ledger));
        }
        return state;
    }
    private static GoodsParticipant knowsOnly(GoodsParticipant actor, GoodsParticipant peer) {
        return new GoodsParticipant(actor.party(), actor.policy(), actor.settlementId(),
                List.of(new GoodsParticipant.Counterparty(peer.party(), peer.settlementId())), 0, "fixture:one-known-counterparty", 0);
    }
    private static FrontierWorldState add(FrontierWorldState state, GoodsParticipant seller, String kind, int quantity) {
        var ledger = state.inventory().fungibleResources();
        var account = FungibleResourceCustodySupport.accountAtContainer(state, FrontierWorldState.depotId(seller.settlementId())).orElseThrow();
        var lot = new SubjectId("lot:trip-test/" + kind.replace(':', '-'));
        var staging = new SubjectId("custody:trip-test/" + kind.replace(':', '-'));
        ledger = ledger.issue(new ResourceLot(lot, seller.party().id(), kind, quantity, "fixture:initial-stock", List.of()),
                new CustodyAccount(staging, new ResourceCustody.Container(FrontierWorldState.depotId(seller.settlementId())), Map.of(lot, quantity), Map.of()));
        return state.withInventory(state.inventory().withFungibleResources(ledger.transfer(staging, account.id(), Map.of(lot, quantity), Map.of())));
    }
}
