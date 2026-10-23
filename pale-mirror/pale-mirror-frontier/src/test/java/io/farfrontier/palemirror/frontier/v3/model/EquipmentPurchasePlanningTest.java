package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EquipmentPurchasePlanningTest {
    @Test void unavailableForgeUsesAFundedDeliveredToolOfferAndCountsItsExactPromiseOnce() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:equipment-purchase"), 126,
                FrontierRulesets.toolGraybox()));
        var buyer = state.companies().goodsTrade().participants().participants().get(new SubjectId("settlement:1"));
        var seller = state.companies().goodsTrade().participants().participants().get(buyer.known().getFirst().party().id());
        var inventory = state.inventory();
        for (var item : List.copyOf(inventory.items().values()))
            if (item.economicOwnerId().equals(buyer.party().id()) && item.itemKind().endsWith("_hoe"))
                inventory = inventory.moveObservedItem(item.id(), item.custody(), new InventoryCustody.Player(new UUID(0, 228)));
        for (var record : inventory.containers().values())
            if (record.ownerId().equals(buyer.party().id()) && record.productionStation()
                    .filter(station -> station.capability() == ProductionStationSpec.Capability.FABRICATION).isPresent())
                inventory = inventory.withSurfaceStatus(record.id(), ContainerSurfaceStatus.PREPARED)
                        .withSurfaceStatus(record.id(), ContainerSurfaceStatus.ACTIVE).withSurfaceStatus(record.id(), ContainerSurfaceStatus.RETIRED);
        var source = FrontierWorldState.depotId(seller.settlementId());
        var target = FrontierWorldState.depotId(buyer.settlementId());
        for (int index = 0; index < 12; index++) inventory = inventory.store(new ExactItemStack(
                new SubjectId("item:equipment-offer-" + index), seller.party().id(), "minecraft:iron_hoe", 1,
                new InventoryCustody.ContainerSlot(source, inventory.availableSlots(source).getFirst()), new ItemCondition(250, 0)));
        state = state.withInventory(inventory);
        var sell = new GoodsTradeOrder(new SubjectId("order:equipment-offer"), seller.party(), buyer.party(),
                GoodsTradeOrder.Side.SELL, source, "minecraft:iron_hoe", 12, 0, FixedScalar.ONE, 24000);
        state = GoodsTradeStateSupport.place(state, seller.party().id(), sell, 300);
        long dayTicks = state.humanPopulation().schedule(buyer.party().id()).dayTicks();
        assertTrue(EquipmentRecipeValuation.read(state, buyer.party().id(), 300).offers().stream()
                .anyMatch(offer -> offer.deliveryTicks() > dayTicks));
        var plan = EquipmentProductionPlanning.acquisition(state, buyer.party().id(), 300);
        assertTrue(plan.purchases().stream().anyMatch(p -> p.itemKind().equals("minecraft:iron_hoe")), plan + " deficits=" + EquipmentStockProjection.deficits(state, buyer.party().id())
                + " market=" + EquipmentRecipeValuation.read(state, buyer.party().id(), 300));
        assertTrue(plan.production().isEmpty());
        var buy = new GoodsTradeOrder(new SubjectId("order:equipment-buy"), buyer.party(), seller.party(),
                GoodsTradeOrder.Side.BUY, target, "minecraft:iron_hoe", 1, 0, FixedScalar.ONE, 24000);
        state = GoodsTradeStateSupport.place(state, buyer.party().id(), buy, 300);
        var item = GoodsParticipantStorage.individualStock(state, seller, source, "minecraft:iron_hoe").getFirst();
        var contractId = new SubjectId("contract:equipment-purchase"); var claim = new SubjectId("claim:equipment-purchase");
        var allocation = new IndividualResourceAllocation(claim, contractId, seller.party().id(), item.id(), item.itemKind(), item.condition());
        var contract = new GoodsTradeContract(contractId, sell.id(), buy.id(), seller.party(), buyer.party(),
                new GoodsTradeFulfillment(GoodsTradeFulfillment.Mode.SELLER_DELIVERY, seller.party()), source, target,
                item.itemKind(), 1, FixedScalar.ONE, new SubjectId("reservation:equipment-purchase"), 0,
                Map.of(claim, 1), Map.of(), Map.of(), 0, 0, FixedScalar.ZERO, Map.of(claim, allocation));
        var before = state;
        var admitted = GoodsTradeStateSupport.reserve(state, seller.party().id(), contract, List.of(), 300);
        assertEquals(1, EquipmentStockProjection.units(admitted, buyer.party().id()).stream().filter(u -> u.id().equals(item.id())).count());
        assertTrue(EquipmentStockProjection.units(admitted, seller.party().id()).stream().noneMatch(u -> u.id().equals(item.id())));
        assertThrows(IllegalArgumentException.class, () -> GoodsTradeStateSupport.reserve(admitted, seller.party().id(), contract, List.of(), 300));
        var damaged = before.withInventory(before.inventory().changeCondition(item, new ItemCondition(250, 1)));
        assertThrows(IllegalArgumentException.class, () -> GoodsTradeStateSupport.reserve(damaged, seller.party().id(), contract, List.of(), 300));
        var codec = new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec();
        assertEquals(admitted, codec.decode(codec.encode(admitted)));
    }
}
