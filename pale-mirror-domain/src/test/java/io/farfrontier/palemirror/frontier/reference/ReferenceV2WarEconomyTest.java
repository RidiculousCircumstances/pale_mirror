package io.farfrontier.palemirror.frontier.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.EnumMap;
import org.junit.jupiter.api.Test;

class ReferenceV2WarEconomyTest {
    @Test
    void sourceCompanyStateThresholdsPreserveClosedState() {
        ReferenceWorld world = sourceV2World();
        ReferenceCompany company = localFoodCompany(world);
        company.wageBill(2.0d);

        company.cash(-20.01d);
        company.ownerKind("private");
        company.status("operating");
        company.v2State("operating");
        world.v2().updateCompanyStates(world);
        assertEquals("insolvent", company.v2State());
        assertEquals("D0: Ashfield-01 farm-3 Co. is insolvent", world.events().getLast());

        company.cash(7.99d);
        company.v2State("insolvent");
        world.v2().updateCompanyStates(world);
        assertEquals("stressed", company.v2State());
        assertEquals("D0: Ashfield-01 farm-3 Co. is stressed", world.events().getLast());

        company.cash(100.0d);
        company.ownerKind("municipal_receiver");
        company.v2State("stressed");
        world.v2().updateCompanyStates(world);
        assertEquals("receivership", company.v2State());
        assertEquals("D0: Ashfield-01 farm-3 Co. is receivership", world.events().getLast());

        int eventCount = world.events().size();
        company.ownerKind("private");
        company.status("closed");
        company.v2State("closed");
        world.v2().updateCompanyStates(world);
        assertEquals("closed", company.v2State());
        assertEquals(eventCount, world.events().size());
    }

    @Test
    void sourceEmergencyProcurementBorrowsThenTransfersCriticalStock() {
        ReferenceWorld world = sourceV2World();
        ReferenceSettlement settlement = world.settlements().get(1);
        ReferenceCompany food = localFoodCompany(world);
        world.v2().civics().get(settlement.id()).state(ReferenceCivicState.EMERGENCY);
        world.day(5);
        emptySettlementWarehouse(world, settlement);
        setInventory(food, ReferenceResource.FOOD, 20.0d);
        food.cash(100.0d);
        settlement.treasury(0.0d);

        world.v2().issueProcurement(world);

        assertEquals(3, world.v2().procurements().size());
        ReferenceProcurementOrder foodOrder = world.v2().procurements().get(1);
        assertEquals(ReferenceResource.FOOD, foodOrder.resource());
        assertEquals(6.0d, foodOrder.quantity());
        assertEquals(28.751532992815534d, foodOrder.maxPrice());
        assertEquals(6.0d, foodOrder.fulfilled());
        assertEquals("fulfilled", foodOrder.status());
        ReferenceProcurementOrder medicine = world.v2().procurements().get(2);
        assertEquals(ReferenceResource.MEDICINE, medicine.resource());
        assertEquals(2.636313357133869d, medicine.quantity());
        ReferenceProcurementOrder weapons = world.v2().procurements().get(3);
        assertEquals(ReferenceResource.WEAPONS, weapons.resource());
        assertEquals(21.723065901997124d, weapons.quantity());
        assertEquals(14.0d, food.amount(ReferenceResource.FOOD));
        assertEquals(272.5091979568932d, food.cash());
        assertEquals(6.0d, world.microeconomy().publicInventory(settlement.id()).get(ReferenceResource.FOOD));
        assertEquals(2.636313357133869d, world.microeconomy().publicInventory(settlement.id()).get(ReferenceResource.MEDICINE));
        assertEquals(21.723065901997124d, world.microeconomy().publicInventory(settlement.id()).get(ReferenceResource.WEAPONS));
        assertEquals(0.0d, settlement.treasury());
        assertEquals(3, world.microeconomy().credits().size());
        assertEquals(172.50919795689322d, world.microeconomy().credits().get(1).principal());
        assertEquals(9012.937738964803d, world.microeconomy().credits().get(3).principal());
        assertEquals("D5: Ashfield-01 procured 21.7 weapons from Ashfield-01 armory Co.", world.events().getLast());
    }

    @Test
    void sourceSiegeRequisitionStaysPendingWhenUnfundedThenPays() {
        ReferenceWorld world = sourceV2World();
        ReferenceSettlement settlement = world.settlements().get(1);
        ReferenceCompany food = localFoodCompany(world);
        ReferenceCivicLedger civic = world.v2().civics().get(settlement.id());
        civic.state(ReferenceCivicState.SIEGE);
        world.day(6);
        setInventory(food, ReferenceResource.FOOD, 20.0d);
        world.microeconomy().mutablePublicInventory(settlement.id()).put(ReferenceResource.FOOD, 6.0d);
        double legitimacy = world.v2().doctrines().get(settlement.id()).legitimacy();

        world.v2().requisition(world, settlement, food, ReferenceResource.FOOD, 5.0d, 2.0d);

        ReferenceCompensationClaim claim = world.v2().compensation().get(1);
        assertEquals(15.0d, food.amount(ReferenceResource.FOOD));
        assertEquals(11.0d, world.microeconomy().publicInventory(settlement.id()).get(ReferenceResource.FOOD));
        assertEquals(10.0d, claim.amount());
        assertEquals(26, claim.dueDay());
        assertEquals("siege requisition of 5.0 food", claim.reason());
        assertEquals("pending", claim.status());
        assertEquals(legitimacy - 0.035d, world.v2().doctrines().get(settlement.id()).legitimacy());
        assertEquals("D6: Ashfield-01 requisitioned 5.0 food; compensation claim 1 issued", world.events().getLast());

        civic.state(ReferenceCivicState.EMERGENCY);
        int claimCount = world.v2().compensation().size();
        world.v2().requisition(world, settlement, food, ReferenceResource.FOOD, 5.0d, 2.0d);
        assertEquals(claimCount, world.v2().compensation().size());
        assertEquals(15.0d, food.amount(ReferenceResource.FOOD));

        world.day(claim.dueDay());
        food.cash(100.0d);
        settlement.treasury(9.99d);
        world.v2().settleCompensation(world);
        assertEquals("pending", claim.status());
        assertEquals(9.99d, settlement.treasury());
        assertEquals(100.0d, food.cash());

        settlement.treasury(10.0d);
        world.v2().settleCompensation(world);
        assertEquals("paid", claim.status());
        assertEquals(0.0d, settlement.treasury());
        assertEquals(110.0d, food.cash());
        assertEquals("D26: Ashfield-01 paid compensation claim 1", world.events().getLast());
    }

    @Test
    void sourceEventQuantitiesRoundTheBinaryValueHalfEven() {
        ReferenceWorld world = sourceV2World();
        ReferenceSettlement settlement = world.settlements().get(1);
        world.v2().civics().get(settlement.id()).state(ReferenceCivicState.SIEGE);

        world.v2().requisition(world, settlement, localFoodCompany(world), ReferenceResource.FOOD, 2.25d, 1.0d);

        assertEquals("D0: Ashfield-01 requisitioned 2.2 food; compensation claim 1 issued", world.events().getLast());
        assertEquals("siege requisition of 2.2 food", world.v2().compensation().get(1).reason());
    }

    @Test
    void sourceRequisitionDoesNotSilentlyClampItsAlreadyValidatedInput() {
        ReferenceWorld world = sourceV2World();
        ReferenceSettlement settlement = world.settlements().get(1);
        ReferenceCompany food = localFoodCompany(world);
        world.v2().civics().get(settlement.id()).state(ReferenceCivicState.SIEGE);
        setInventory(food, ReferenceResource.FOOD, 1.0d);
        double publicFood = world.microeconomy().publicInventory(settlement.id()).get(ReferenceResource.FOOD);

        world.v2().requisition(world, settlement, food, ReferenceResource.FOOD, 2.0d, 1.0d);

        assertEquals(-1.0d, food.amount(ReferenceResource.FOOD));
        assertEquals(publicFood + 2.0d, world.microeconomy().publicInventory(settlement.id()).get(ReferenceResource.FOOD));
        assertEquals(2.0d, world.v2().compensation().get(1).amount());
    }

    private static ReferenceWorld sourceV2World() {
        return new ReferenceWorld(new ReferenceWorldConfig(
                64, 44, 12, 41L, 0, true, ReferenceSimulationProfile.SOURCE_V2));
    }

    private static ReferenceCompany localFoodCompany(ReferenceWorld world) {
        return world.microeconomy().companies().values().stream()
                .filter(item -> item.homeSettlementId() == 1 && item.output() == ReferenceResource.FOOD)
                .findFirst().orElseThrow();
    }

    private static void emptySettlementWarehouse(ReferenceWorld world, ReferenceSettlement settlement) {
        EnumMap<ReferenceResource, Double> stock = new EnumMap<>(ReferenceResource.class);
        for (ReferenceResource resource : ReferenceResource.values()) {
            stock.put(resource, 0.0d);
            world.microeconomy().mutablePublicInventory(settlement.id()).put(resource, 0.0d);
        }
        settlement.replaceStock(stock);
    }

    private static void setInventory(ReferenceCompany company, ReferenceResource resource, double amount) {
        company.remove(resource, Double.MAX_VALUE);
        company.add(resource, amount);
    }
}
