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
        // This narrow transport fixture stages sale goods and provisions at an
        // outdoor loading port. Deep indoor kitchen access is not an animal capability.
        for (var home : state.bootstrap().settlements()) {
            var depot = FrontierWorldState.depotId(home.id());
            var policy = state.inventory().containers().get(depot).storage().orElseThrow();
            state = new ResourceStoragePolicyChanged(depot, policy, new ResourceStoragePolicy(
                    policy.endpoint(), ResourceStorageFilter.all(), policy.priority(), policy.role())).apply(state, home.id());
            var pantry = CommunalDiningPlan.initialFoodStorage(state.bootstrap(), home.id());
            var dining = state.inventory().containers().get(pantry).storage().orElseThrow();
            state = new ResourceStoragePolicyChanged(pantry, dining, new ResourceStoragePolicy(
                    dining.endpoint(), dining.filter(), dining.priority(), ResourceStorageRole.LOCAL_BUFFER)).apply(state, home.id());
        }
        var seller = state.companies().goodsTrade().participants().participants().get(new SubjectId("settlement:1"));
        var view = GoodsParticipantAssessment.view(state, seller, 0);
        var commodity = state.bootstrap().ruleset().goodsTrade().policies().get(seller.policy()).stream()
                .filter(value -> value.itemKind().equals("minecraft:bread")).findFirst().orElseThrow();
        int stock = Math.max(commodity.target(view.residents()), view.stocks().get("minecraft:bread").protectedMinimum()) + 16;
        var lot = new SubjectId("lot:autonomous-trade-initial-bread"); var staging = new SubjectId("custody:autonomous-trade-setup");
        var resources = state.inventory().fungibleResources().issue(new ResourceLot(lot, seller.party().id(), "minecraft:bread", stock,
                "fixture:finite-initial-bread", List.of()), new CustodyAccount(staging, new ResourceCustody.Container(FrontierWorldState.depotId(seller.settlementId())), Map.of(lot, stock), Map.of()));
        resources = resources.transfer(staging, ReferenceContainerCustody.scopeId(FrontierWorldState.depotId(seller.settlementId())), Map.of(lot, stock), Map.of());
        return state.withInventory(state.inventory().withFungibleResources(resources));
    }
    private AutonomousTradeFixture() { }
}
