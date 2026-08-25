package io.farfrontier.palemirror.frontier.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ReferenceEconomyEngineTest {
    @Test
    void matchesSourceProductionSiteHaulConsumptionAndScarcityMicroTrace() {
        ReferenceSettlement settlement = new ReferenceSettlement(
                1, "a", 0, 0, 100.0d, 0.0d,
                new ReferenceNaturalPotential(), new ReferenceFacilities(), ReferenceSimulationProfile.SOURCE_V2);
        ReferenceEconomyEngine economy = new ReferenceEconomyEngine(ReferenceSimulationProfile.SOURCE_V2);
        for (ReferenceResource resource : ReferenceResource.values()) settlement.add(resource, 100.0d);

        economy.resetDailyFlows(settlement);
        economy.productionPhase(settlement);
        assertEquals(98.8d, settlement.amount(ReferenceResource.FOOD));
        assertEquals(92.565d, settlement.amount(ReferenceResource.ORE));
        assertEquals(95.42d, settlement.amount(ReferenceResource.ENERGY));
        assertEquals(104.39d, settlement.amount(ReferenceResource.TOOLS));
        assertEquals(4.5d, settlement.dailyProduction().get(ReferenceResource.TOOLS));
        assertEquals(7.435d, settlement.dailyConsumption().get(ReferenceResource.ORE));

        ReferenceResourceSite site = new ReferenceResourceSite(2, ReferenceSiteKind.MINE, 0, 0, 0.8d, 1.2d, 1);
        site.haulCapacity(10.0d);
        site.condition(0.9d);
        site.contamination(0.2d);
        assertEquals(17.6020992d, economy.siteProductionPhase(settlement, site, 0.7d));
        assertEquals(17.6020992d, site.amount(ReferenceResource.ORE));
        assertEquals(9.200000000000001d, economy.haulSiteOutput(settlement, site, 0.5d));
        assertEquals(101.765d, settlement.amount(ReferenceResource.ORE));

        economy.consumptionPhase(settlement);
        assertEquals(1.0d, settlement.foodFulfillment());
        assertEquals(1.0d, settlement.medicineFulfillment());
        assertEquals(92.3d, settlement.amount(ReferenceResource.FOOD));
        assertEquals(102.93d, settlement.amount(ReferenceResource.MEDICINE));
        assertEquals(89.891412224d, settlement.amount(ReferenceResource.ENERGY));
        assertEquals(7.7d, economy.expectedDailyDemand(settlement, ReferenceResource.FOOD));
        assertEquals(107.8d, economy.targetStock(settlement, ReferenceResource.FOOD));
        assertEquals(1.1410495416310578d, economy.localValue(settlement, ReferenceResource.FOOD));
        assertEquals(15.5d, economy.desiredImport(settlement, ReferenceResource.FOOD));
        assertEquals(47.89141222400001d, economy.sellableQuantity(settlement, ReferenceResource.ENERGY));
    }

    @Test
    void grayboxScalesOnlyExtensiveHumanAmounts() {
        ReferenceSettlement settlement = new ReferenceSettlement(
                1, "g", 0, 0, 100.0d, 0.0d,
                new ReferenceNaturalPotential(), new ReferenceFacilities(), ReferenceSimulationProfile.GRAYBOX_1_40);
        ReferenceEconomyEngine economy = new ReferenceEconomyEngine(ReferenceSimulationProfile.GRAYBOX_1_40);

        assertEquals(2.0d, economy.resourceRule(ReferenceResource.FOOD).minimumTarget());
        assertEquals(1.3950000000000002d, economy.expectedDailyDemand(settlement, ReferenceResource.FOOD));
        assertEquals(19.530000000000005d, economy.targetStock(settlement, ReferenceResource.FOOD));

        for (ReferenceResource resource : ReferenceResource.values()) settlement.add(resource, 2.5d);
        ReferenceResourceSite site = projectSite(9);
        assertTrue(economy.siteProject(settlement, site, "upgrade"));
        assertEquals(2.05d, settlement.amount(ReferenceResource.TIMBER));
        assertEquals(2.15d, settlement.amount(ReferenceResource.ORE));
        assertEquals(2.15d, settlement.amount(ReferenceResource.TOOLS));
        assertEquals(1.20625d, site.capacity());

        ReferenceSettlement investment = settlement(ReferenceSimulationProfile.GRAYBOX_1_40);
        for (ReferenceResource resource : ReferenceResource.values()) investment.add(resource, 2.5d);
        investment.threat(0.6d);
        investment.integrity(85.0d);
        assertEquals("armory", economy.invest(investment, 100, values(), neutralDoctrine()));
        assertEquals(2.0d, investment.amount(ReferenceResource.TIMBER));
        assertEquals(1.3d, investment.amount(ReferenceResource.ORE));
        assertEquals(1.85d, investment.amount(ReferenceResource.TOOLS));
        assertEquals(0.25625d, investment.facilities().armory());
        assertEquals(100, investment.lastInvestmentDay());
    }

    @Test
    void matchesSourceSiteProjectsAndClaimMicroTrace() {
        ReferenceEconomyEngine economy = new ReferenceEconomyEngine(ReferenceSimulationProfile.SOURCE_V2);
        assertProject(economy, "upgrade", 100.0d, 100.0d, 82.0d, 86.0d, 86.0d, 1.45d, 0.5d, 0.8d, 70.0d);
        assertProject(economy, "repair", 100.0d, 100.0d, 92.0d, 96.0d, 92.0d, 1.2d, 0.8200000000000001d, 0.8d, 70.0d);
        assertProject(economy, "cleanse", 92.0d, 98.0d, 100.0d, 100.0d, 96.0d, 1.2d, 0.5d, 0.52d, 50.4d);
        assertProject(economy, "scorch", 100.0d, 100.0d, 100.0d, 100.0d, 100.0d, 1.2d, 0.21999999999999997d,
                0.18000000000000005d, 26.6d);
        assertProject(economy, "restore", 90.0d, 100.0d, 92.0d, 100.0d, 94.0d, 1.2d, 0.5d, 0.8d, 70.0d);

        ReferenceSettlement rejected = settlement(ReferenceSimulationProfile.SOURCE_V2);
        ReferenceResourceSite rejectedSite = new ReferenceResourceSite(5, ReferenceSiteKind.MINE, 0, 0, 0.8d, 1.2d, 1);
        assertFalse(economy.siteProject(rejected, rejectedSite, "unknown"));
        assertEquals(0.0d, rejected.amount(ReferenceResource.TOOLS));
        assertEquals(1.2d, rejectedSite.capacity());

        ReferenceSettlement claim = settlement(ReferenceSimulationProfile.SOURCE_V2);
        claim.add(ReferenceResource.TIMBER, 12.0d);
        claim.add(ReferenceResource.ORE, 8.0d);
        claim.add(ReferenceResource.TOOLS, 8.0d);
        assertTrue(economy.canStartClaim(claim));
        economy.payClaimMaterials(claim);
        assertFalse(economy.canStartClaim(claim));
        assertEquals(0.0d, claim.amount(ReferenceResource.TIMBER));
        assertEquals(0.0d, claim.amount(ReferenceResource.ORE));
        assertEquals(0.0d, claim.amount(ReferenceResource.TOOLS));
    }

    @Test
    void matchesSourceInvestmentChoiceMaterialsAndCooldownMicroTrace() {
        ReferenceEconomyEngine economy = new ReferenceEconomyEngine(ReferenceSimulationProfile.SOURCE_V2);
        ReferenceSettlement investment = settlement(ReferenceSimulationProfile.SOURCE_V2);
        for (ReferenceResource resource : ReferenceResource.values()) investment.add(resource, 100.0d);
        investment.threat(0.6d);
        investment.integrity(85.0d);

        assertEquals("armory", economy.invest(investment, 100, values(), neutralDoctrine()));
        assertEquals(80.0d, investment.amount(ReferenceResource.TIMBER));
        assertEquals(52.0d, investment.amount(ReferenceResource.ORE));
        assertEquals(74.0d, investment.amount(ReferenceResource.TOOLS));
        assertEquals(0.5d, investment.facilities().workshop());
        assertEquals(0.5d, investment.facilities().armory());
        assertEquals(0.25d, investment.facilities().clinic());
        assertEquals(100, investment.lastInvestmentDay());
        assertNull(economy.invest(investment, 123, values(), neutralDoctrine()));
        assertEquals(80.0d, investment.amount(ReferenceResource.TIMBER));

        ReferenceSettlement doctrinal = settlement(ReferenceSimulationProfile.SOURCE_V2);
        for (ReferenceResource resource : ReferenceResource.values()) doctrinal.add(resource, 100.0d);
        assertEquals("clinic", economy.invest(doctrinal, 100, values(), Map.of("site", 0.1d, "defence", 0.1d, "cleanse", 3.0d)));
        assertEquals(82.0d, doctrinal.amount(ReferenceResource.TIMBER));
        assertEquals(88.0d, doctrinal.amount(ReferenceResource.ORE));
        assertEquals(82.0d, doctrinal.amount(ReferenceResource.TOOLS));
        assertEquals(0.5d, doctrinal.facilities().clinic());
    }

    private static ReferenceSettlement settlement(ReferenceSimulationProfile profile) {
        return new ReferenceSettlement(1, "a", 0, 0, 100.0d, 0.0d,
                new ReferenceNaturalPotential(), new ReferenceFacilities(), profile);
    }

    private static ReferenceResourceSite projectSite(int id) {
        ReferenceResourceSite site = new ReferenceResourceSite(id, ReferenceSiteKind.MINE, 0, 0, 0.8d, 1.2d, 1);
        site.condition(0.5d);
        site.contamination(0.8d);
        site.substrate(70.0d);
        return site;
    }

    private static Map<ReferenceResource, Double> values() {
        return Map.of(ReferenceResource.TOOLS, 4.5d, ReferenceResource.WEAPONS, 12.0d, ReferenceResource.MEDICINE, 7.0d);
    }

    private static Map<String, Double> neutralDoctrine() {
        return Map.of("site", 1.0d, "defence", 1.0d, "cleanse", 1.0d);
    }

    private static void assertProject(
            ReferenceEconomyEngine economy,
            String action,
            double food,
            double medicine,
            double timber,
            double ore,
            double tools,
            double capacity,
            double condition,
            double contamination,
            double substrate
    ) {
        ReferenceSettlement settlement = settlement(ReferenceSimulationProfile.SOURCE_V2);
        for (ReferenceResource resource : ReferenceResource.values()) settlement.add(resource, 100.0d);
        ReferenceResourceSite site = projectSite(4);
        assertTrue(economy.siteProject(settlement, site, action));
        assertEquals(food, settlement.amount(ReferenceResource.FOOD));
        assertEquals(medicine, settlement.amount(ReferenceResource.MEDICINE));
        assertEquals(timber, settlement.amount(ReferenceResource.TIMBER));
        assertEquals(ore, settlement.amount(ReferenceResource.ORE));
        assertEquals(tools, settlement.amount(ReferenceResource.TOOLS));
        assertEquals(capacity, site.capacity());
        assertEquals(condition, site.condition());
        assertEquals(contamination, site.contamination());
        assertEquals(substrate, site.substrate());
    }
}
