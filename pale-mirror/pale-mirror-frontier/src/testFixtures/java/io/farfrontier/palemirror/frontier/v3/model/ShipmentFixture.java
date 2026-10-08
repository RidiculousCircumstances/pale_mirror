package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind;
import java.util.*;

/** Initial consent/allocation fixture only. Runtime uses the actual transport and physical owners. */
final class ShipmentFixture {
    static final SubjectId ID = new SubjectId("shipment:development-goods");
    static FrontierWorldState initial(WorldId world, long seed) {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(world, seed));
        var seller = state.bootstrap().settlements().getFirst(); var buyer = state.bootstrap().settlements().get(1);
        var source = FrontierWorldState.depotId(seller.id()); var target = FrontierWorldState.depotId(buyer.id());
        var sourceAccount = ReferenceContainerCustody.scopeId(source);
        var lot = new SubjectId("lot:development-shipment-bread"); var staging = new SubjectId("custody:development-goods-setup");
        int stock = 32 + SettlementFoodPolicy.reserveRequirement(state, seller.id());
        var resources = state.inventory().fungibleResources().issue(new ResourceLot(lot, seller.id(), "minecraft:bread", stock,
                "fixture:shipment-initial-stock", List.of()), new CustodyAccount(staging, new ResourceCustody.Container(source), Map.of(lot, stock), Map.of()));
        resources = resources.transfer(staging, sourceAccount, Map.of(lot, stock), Map.of());
        state = state.withInventory(state.inventory().withFungibleResources(resources));
        var sellParty = new GoodsTradeParty(seller.id(), EconomicOwnerKind.SETTLEMENT_TREASURY);
        var buyParty = new GoodsTradeParty(buyer.id(), EconomicOwnerKind.SETTLEMENT_TREASURY);
        var sell = new SubjectId("order:development-goods-sell"); var buy = new SubjectId("order:development-goods-buy");
        var contract = new SubjectId("contract:development-goods"); var claim = new SubjectId("claim:development-goods");
        state = GoodsTradeStateSupport.place(state, seller.id(), new GoodsTradeOrder(sell, sellParty, buyParty,
                GoodsTradeOrder.Side.SELL, source, "minecraft:bread", 32, 0, FixedScalar.whole(1), 100_000), 0);
        state = GoodsTradeStateSupport.place(state, buyer.id(), new GoodsTradeOrder(buy, buyParty, sellParty,
                GoodsTradeOrder.Side.BUY, target, "minecraft:bread", 32, 0, FixedScalar.whole(1), 100_000), 0);
        state = GoodsTradeStateSupport.reserve(state, seller.id(), new GoodsTradeContract(contract, sell, buy, sellParty, buyParty,
                source, target, "minecraft:bread", 32, FixedScalar.whole(1), new SubjectId("reservation:development-goods"),
                0, Map.of(claim, 32), Map.of()), List.of(new GoodsTradeStockAllocation(sourceAccount,
                new ClaimAllocation(claim, contract, seller.id(), "minecraft:bread", 32, Map.of(lot, 32), ClaimPurpose.GOODS_TRADE),
                GoodsTradeStockAllocation.Authority.COLD, 0)), 0);
        var actor = seller.residents().getFirst().id();
        var shipment = new Shipment(ID, new ResourceClaimDelegation(ResourceClaimDelegation.Kind.GOODS_CONTRACT_SHIPMENT,
                claim, contract, ID, 0), state.actorExecutions().next(actor, ActorActivityKind.COURIER, ID),
                endpoint(seller), endpoint(buyer), sourceAccount, new SubjectId("custody:development-goods-courier"),
                ReferenceContainerCustody.scopeId(target), "minecraft:bread", Map.of(lot, 32), Shipment.Status.AWAITING_LOAD, 1);
        state = ShipmentStateSupport.dispatch(state, seller.id(), shipment);
        FrontierDomainRelationships.validate(state);
        return state;
    }
    private static ShipmentEndpoint endpoint(Settlement settlement) {
        var depot = settlement.structures().stream().filter(s -> s.kind() == StructureKind.DEPOT).findFirst().orElseThrow();
        return new ShipmentEndpoint(ShipmentEndpoint.Kind.SETTLEMENT_DEPOT, settlement.id(), depot.id(),
                FrontierWorldState.depotId(settlement.id()), SettlementDepotServicePort.forDepot(depot).serviceSurface());
    }
    private ShipmentFixture() { }
}
