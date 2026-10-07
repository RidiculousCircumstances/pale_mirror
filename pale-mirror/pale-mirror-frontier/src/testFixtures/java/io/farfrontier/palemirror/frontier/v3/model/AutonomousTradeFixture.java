package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import java.util.*;

/** Finite initial stock only. The registered participants must create orders, contracts and dispatch themselves. */
final class AutonomousTradeFixture {
    static FrontierWorldState initial(WorldId world, long seed) {
        return initial(world, seed, FrontierRulesets.production());
    }
    static FrontierWorldState initial(WorldId world, long seed, FrontierRuleset rules) {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(world, seed, rules));
        var seller = state.companies().goodsTrade().participants().participants().get(new SubjectId("settlement:1"));
        var view = GoodsParticipantView.read(state, seller);
        var commodity = state.bootstrap().ruleset().goodsTrade().policies().get(seller.policy()).stream()
                .filter(value -> value.itemKind().equals("minecraft:bread")).findFirst().orElseThrow();
        int stock = Math.max(commodity.target(view.residents()), view.stocks().get("minecraft:bread").protectedMinimum()) + 16;
        var lot = new SubjectId("lot:autonomous-trade-initial-bread"); var staging = new SubjectId("custody:autonomous-trade-setup");
        var resources = state.inventory().fungibleResources().issue(new ResourceLot(lot, seller.party().id(), "minecraft:bread", stock,
                "fixture:finite-initial-bread", List.of()), new CustodyAccount(staging, new ResourceCustody.Container(seller.endpoint().containerId()), Map.of(lot, stock), Map.of()));
        resources = resources.transfer(staging, ReferenceContainerCustody.scopeId(seller.endpoint().containerId()), Map.of(lot, stock), Map.of());
        return state.withInventory(state.inventory().withFungibleResources(resources));
    }
    private AutonomousTradeFixture() { }
}
