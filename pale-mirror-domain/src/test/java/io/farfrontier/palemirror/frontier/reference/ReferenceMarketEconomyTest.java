package io.farfrontier.palemirror.frontier.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class ReferenceMarketEconomyTest {
    @Test
    void matchesSourceBootstrapOwnershipLicencesAndWarehouseProjectionMicroTrace() {
        ReferenceMarketWorld world = world(ReferenceSimulationProfile.SOURCE_V2);
        ReferenceMarketEconomy market = new ReferenceMarketEconomy(new ReferenceEconomyEngine(ReferenceSimulationProfile.SOURCE_V2));
        market.bootstrap(world);

        assertEquals(8, market.companies().size());
        assertEquals(2, market.licences().size());
        ReferenceCompany mine = market.companies().get(1);
        assertEquals("Beta mine-3 Co.", mine.name());
        assertEquals(ReferenceCompanySector.MINING, mine.sector());
        assertEquals(72.5d, mine.cash());
        assertEquals(69.30000000000001d, mine.assets());
        assertEquals(32.8d, mine.amount(ReferenceResource.ORE));
        assertEquals(2, world.resourceSites().get(3).ownerId());
        assertEquals(1, world.resourceSites().get(3).operatorCompanyId());
        ReferenceCompany farm = market.companies().get(2);
        assertEquals("Alpha farm-7 Co.", farm.name());
        assertEquals(164.0d, farm.amount(ReferenceResource.FOOD));
        assertEquals(1.2d, market.companies().get(3).capacity());
        assertEquals(24.599999999999998d, market.companies().get(3).amount(ReferenceResource.TOOLS));
        assertEquals(8.2d, market.companies().get(4).amount(ReferenceResource.MEDICINE));
        assertEquals(16.4d, market.companies().get(5).amount(ReferenceResource.WEAPONS));

        ReferenceSettlement alpha = world.settlements().get(1);
        ReferenceSettlement beta = world.settlements().get(2);
        assertEquals(200.0d, alpha.cash());
        assertEquals(100.0d, beta.cash());
        assertEquals(200.0d, alpha.amount(ReferenceResource.FOOD));
        assertEquals(100.0d, alpha.amount(ReferenceResource.ORE));
        assertEquals(0.8400000000000001d, alpha.primaryCapacity("farm"));
        assertEquals(0.5940000000000001d, beta.primaryCapacity("mine"));
        assertEquals(1.2d, alpha.facilities().workshop());
        assertEquals(0.6d, alpha.facilities().clinic());
        assertEquals(0.4d, alpha.facilities().armory());
        assertEquals(220.00000000000003d, market.households().get(1).cash());
        assertEquals(55.00000000000001d, market.households().get(1).workers());
        assertEquals(5.0d, market.households().get(1).owners());
        assertEquals(40.0d, market.households().get(1).dependents());
        assertEquals(36.0d, market.publicInventory(1).get(ReferenceResource.FOOD));
        assertEquals(5.400000000000002d, market.publicInventory(1).get(ReferenceResource.TOOLS));
        assertEquals(ReferenceCompanySector.MINING, market.licences().get(1).sector());
        assertEquals(3, market.licences().get(1).siteId());

        ReferenceCompany retained = market.companies().get(1);
        market.bootstrap(world);
        assertEquals(8, market.companies().size());
        assertSame(retained, market.companies().get(1));
        assertEquals(3.0d, market.minimumWorkers());
        assertEquals(0.2d, market.minimumLot());
        assertSeasons(market);
    }

    @Test
    void grayboxBootstrapCountsRealResidentClassesAndScalesGoodsOnly() {
        ReferenceMarketWorld world = world(ReferenceSimulationProfile.GRAYBOX_1_40);
        ReferenceMarketEconomy market = new ReferenceMarketEconomy(new ReferenceEconomyEngine(ReferenceSimulationProfile.GRAYBOX_1_40));
        market.bootstrap(world);

        assertEquals(3.0d, market.households().get(1).workers());
        assertEquals(0.0d, market.households().get(1).owners());
        assertEquals(0.0d, market.households().get(1).dependents());
        assertEquals(2.0d, market.households().get(2).workers());
        assertEquals(5.0d, world.settlements().get(1).amount(ReferenceResource.FOOD));
        assertEquals(4.1d, market.companies().get(2).amount(ReferenceResource.FOOD));
        assertEquals(0.75d, world.settlements().get(1).amount(ReferenceResource.TOOLS));
        assertEquals(0.615d, market.companies().get(3).amount(ReferenceResource.TOOLS));
        assertEquals(1.0d, market.minimumWorkers());
        assertEquals(0.005d, market.minimumLot());
    }

    @Test
    void matchesSourceLabourHaulAndProductionMicroTrace() {
        ReferenceMarketWorld world = world(ReferenceSimulationProfile.SOURCE_V2);
        ReferenceMarketEconomy market = new ReferenceMarketEconomy(new ReferenceEconomyEngine(ReferenceSimulationProfile.SOURCE_V2));
        market.bootstrap(world);
        prepareDailyWorld(world, ReferenceSimulationProfile.SOURCE_V2);
        market.runLabourHaulProduction(world);

        assertEquals(6.0d, market.companies().get(1).employees());
        assertEquals(69.98d, market.companies().get(1).cash());
        assertEquals(38.32d, market.companies().get(1).amount(ReferenceResource.ORE));
        assertEquals(6.0d, market.companies().get(2).employees());
        assertEquals(142.48d, market.companies().get(2).cash());
        assertEquals(167.68d, market.companies().get(2).amount(ReferenceResource.FOOD));
        assertEquals(49.00000000000001d, market.companies().get(3).employees());
        assertEquals(35.1d, market.companies().get(3).amount(ReferenceResource.TOOLS));
        assertEquals(7.92d, market.companies().get(7).amount(ReferenceResource.MEDICINE));
        assertEquals(11.820476190476189d, market.companies().get(8).amount(ReferenceResource.WEAPONS));
        assertEquals(1.8571428571428572d, market.companies().get(8).amount(ReferenceResource.AMMO));
        assertEquals(0.42252d, market.companies().get(1).wageOffer());
        assertEquals(159.24d, world.resourceSites().get(7).amount(ReferenceResource.FOOD));
        assertEquals(23.1764d, world.resourceSites().get(3).amount(ReferenceResource.ORE));
        assertEquals(156.24d, world.settlements().get(1).dailyProduction().get(ReferenceResource.FOOD));
        assertEquals(10.500000000000002d, world.settlements().get(1).dailyProduction().get(ReferenceResource.TOOLS));
        assertEquals(18.1764d, world.settlements().get(2).dailyProduction().get(ReferenceResource.ORE));
        assertEquals(3.0d, world.settlements().get(2).dailyProduction().get(ReferenceResource.MEDICINE));
        assertEquals(18.1764d, world.extractedAtSite(3));
        assertEquals(156.24d, world.extractedAtSite(7));
    }

    @Test
    void grayboxDailyMarketUsesNamedWorkersAndScaledStoresWithoutCohorts() {
        ReferenceMarketWorld world = world(ReferenceSimulationProfile.GRAYBOX_1_40);
        ReferenceMarketEconomy market = new ReferenceMarketEconomy(new ReferenceEconomyEngine(ReferenceSimulationProfile.GRAYBOX_1_40));
        market.bootstrap(world);
        prepareDailyWorld(world, ReferenceSimulationProfile.GRAYBOX_1_40);
        market.runLabourHaulProduction(world);

        assertEquals(2.0d, market.companies().get(1).employees());
        assertEquals(2.0d, market.companies().get(2).employees());
        assertEquals(1.0d, market.companies().get(3).employees());
        assertEquals(3, world.settlements().get(1).residents().workerIds().size());
        assertEquals(2, world.settlements().get(1).residents().resident("resident:1:1").employerCompanyId());
        assertEquals(2, world.settlements().get(1).residents().resident("resident:1:2").employerCompanyId());
        assertEquals(3, world.settlements().get(1).residents().resident("resident:1:3").employerCompanyId());
        assertEquals(1.073d, market.companies().get(1).amount(ReferenceResource.ORE));
        assertEquals(4.260999999999999d, market.companies().get(2).amount(ReferenceResource.FOOD));
        assertEquals(0.8292857142857142d, market.companies().get(3).amount(ReferenceResource.TOOLS));
        assertEquals(11.573333333333345d, world.resourceSites().get(7).amount(ReferenceResource.FOOD));
        assertEquals(5.357142857142856d, world.resourceSites().get(3).amount(ReferenceResource.ORE));
        assertEquals(11.573333333333345d, world.settlements().get(1).dailyProduction().get(ReferenceResource.FOOD));
    }

    @Test
    void matchesSourceContractsSpotClearingCreditConsumptionAndSettlementMicroTrace() {
        ReferenceMarketWorld world = world(ReferenceSimulationProfile.SOURCE_V2);
        ReferenceMarketEconomy market = new ReferenceMarketEconomy(new ReferenceEconomyEngine(ReferenceSimulationProfile.SOURCE_V2));
        market.bootstrap(world);
        prepareDailyWorld(world, ReferenceSimulationProfile.SOURCE_V2);
        market.runLabourHaulProduction(world);
        world.settlements().get(2).remove(ReferenceResource.FOOD, Double.POSITIVE_INFINITY);
        world.settlements().get(2).cash(0.0d);
        world.trade().addRoute(new ReferenceRoute(1, 2, 9.0d, 100.0d));

        var records = market.runDay(world);

        assertEquals(18, records.size());
        assertEquals(4, market.contracts().size());
        assertEquals(17, market.credits().size());
        assertEquals(184.57d, market.history().getLast().credit());
        assertEquals(99.0d, market.history().getLast().employment());
        assertEquals(219.7774753433617d, market.companies().get(2).cash());
        assertEquals(85.69378095238099d, market.companies().get(2).amount(ReferenceResource.FOOD));
        assertEquals(30.121461227190295d, world.settlements().get(2).cash());
        assertEquals(80.46621904761902d, world.settlements().get(2).amount(ReferenceResource.FOOD));
        assertEquals(18, world.trade().history().size());
    }

    @Test
    void grayboxContractsAndCreditKeepIndividualPeopleAndScaledLots() {
        ReferenceMarketWorld world = world(ReferenceSimulationProfile.GRAYBOX_1_40);
        ReferenceMarketEconomy market = new ReferenceMarketEconomy(new ReferenceEconomyEngine(ReferenceSimulationProfile.GRAYBOX_1_40));
        market.bootstrap(world);
        prepareDailyWorld(world, ReferenceSimulationProfile.GRAYBOX_1_40);
        market.runLabourHaulProduction(world);
        world.settlements().get(2).remove(ReferenceResource.FOOD, Double.POSITIVE_INFINITY);
        world.settlements().get(2).cash(0.0d);
        world.trade().addRoute(new ReferenceRoute(1, 2, 9.0d, 100.0d));

        var records = market.runDay(world);

        assertEquals(4, records.size());
        assertEquals(3, market.contracts().size());
        assertEquals(1, market.credits().size());
        assertEquals(12.07d, market.history().getLast().credit());
        assertEquals(5.0d, market.history().getLast().employment());
        assertEquals(2, world.settlements().get(1).residents().resident("resident:1:1").employerCompanyId());
        assertEquals(5.628419999999999d, world.settlements().get(1).amount(ReferenceResource.FOOD));
        assertEquals(2.88758d, world.settlements().get(2).amount(ReferenceResource.FOOD));
    }

    @Test
    void matchesSourceLicensedSurveyProjectsAndCapacityExpansionMicroTrace() {
        ReferenceMarketWorld world = clearingWorld(ReferenceSimulationProfile.SOURCE_V2);
        ReferenceMarketEconomy market = clearingMarket(world, ReferenceSimulationProfile.SOURCE_V2);
        world.siteSurveyor(new CountingSurveyor());
        world.day(91);

        market.runDay(world);

        assertEquals(2, market.reports().size());
        assertEquals(2, market.projects().size());
        assertEquals(29, market.reports().get(1).x());
        assertEquals(-10, market.reports().get(1).y());
        assertEquals(21, market.reports().get(2).x());
        assertEquals(-11, market.reports().get(2).y());
        assertEquals(0.71d, market.reports().get(1).quality());
        assertEquals(10, market.projects().get(1).daysRemaining());
        assertEquals(1.75d, market.companies().get(3).capacity());
        assertEquals(218.0d, market.companies().get(3).assets());
        assertEquals(-0.1925d, market.companies().get(3).cash());
        assertEquals(91, market.companies().get(1).lastInvestmentDay());
    }

    @Test
    void grayboxExpansionScalesLicensedCapacityButNeverCreatesCohorts() {
        ReferenceMarketWorld world = clearingWorld(ReferenceSimulationProfile.GRAYBOX_1_40);
        ReferenceMarketEconomy market = clearingMarket(world, ReferenceSimulationProfile.GRAYBOX_1_40);
        world.siteSurveyor(new CountingSurveyor());
        world.day(91);

        market.runDay(world);

        assertEquals(1, market.reports().size());
        assertEquals(1, market.projects().size());
        assertEquals(20, market.reports().get(1).x());
        assertEquals(-10, market.reports().get(1).y());
        assertEquals(1.2137499999999999d, market.companies().get(3).capacity());
        assertEquals(2, world.settlements().get(1).residents().resident("resident:1:1").employerCompanyId());
    }

    @Test
    void completesSourceLicensedConstructionOnlyAfterItsExactTenDayCountdown() {
        ReferenceMarketWorld world = clearingWorld(ReferenceSimulationProfile.SOURCE_V2);
        ReferenceMarketEconomy market = clearingMarket(world, ReferenceSimulationProfile.SOURCE_V2);
        world.siteSurveyor(new CountingSurveyor());
        world.day(91);
        market.runDay(world);
        for (int day = 92; day < 102; day++) {
            world.day(day);
            market.runDay(world);
        }

        assertEquals("complete", market.projects().get(1).status());
        assertEquals("complete", market.projects().get(2).status());
        assertEquals(0, market.projects().get(1).daysRemaining());
        assertEquals(4, world.resourceSites().size());
        assertEquals(ReferenceSiteKind.MINE, world.resourceSites().get(8).kind());
        assertEquals(0.55d, world.resourceSites().get(8).capacity());
        assertEquals(300.0d, world.resourceSites().get(8).haulCapacity());
        assertEquals(1, world.resourceSites().get(8).operatorCompanyId());
        assertEquals(2, world.resourceSites().get(9).operatorCompanyId());
    }

    @Test
    void unavailableSiteSurveyorDefersPrimaryExpansionWithoutInventingAveragedTerrain() {
        ReferenceMarketWorld world = clearingWorld(ReferenceSimulationProfile.SOURCE_V2);
        ReferenceMarketEconomy market = clearingMarket(world, ReferenceSimulationProfile.SOURCE_V2);
        world.day(91);

        market.runDay(world);

        assertEquals(0, market.reports().size());
        assertEquals(0, market.projects().size());
        assertEquals(-10_000, market.companies().get(1).lastInvestmentDay());
        assertEquals(2, world.resourceSites().size());
    }

    private static ReferenceMarketWorld world(ReferenceSimulationProfile profile) {
        ReferenceEconomyEngine economy = new ReferenceEconomyEngine(profile);
        ReferenceMarketWorld world = new ReferenceMarketWorld(new ReferenceTradeNetwork(economy));
        ReferenceSettlement alpha = new ReferenceSettlement(1, "Alpha", 0, 0, 100.0d, 1_000.0d,
                new ReferenceNaturalPotential(), new ReferenceFacilities(1.2d, 0.4d, 0.6d, 0.8d), profile);
        ReferenceSettlement beta = new ReferenceSettlement(2, "Beta", 9, 0, 80.0d, 500.0d,
                new ReferenceNaturalPotential(), new ReferenceFacilities(0.5d, 0.8d, 0.25d, 0.3d), profile);
        seed(alpha, profile, 200.0d, 50.0d, 100.0d, 80.0d, 30.0d, 10.0d, 20.0d, 40.0d, 8.0d);
        seed(beta, profile, 120.0d, 20.0d, 40.0d, 60.0d, 12.0d, 6.0d, 14.0d, 25.0d, 4.0d);
        world.addSettlement(alpha);
        world.addSettlement(beta);
        ReferenceResourceSite farm = new ReferenceResourceSite(7, ReferenceSiteKind.FARM, 2, 0, 0.8d, 1.5d, 1);
        farm.condition(0.7d);
        farm.haulCapacity(4.0d);
        ReferenceResourceSite mine = new ReferenceResourceSite(3, ReferenceSiteKind.MINE, 7, 0, 0.9d, 1.1d, 2);
        mine.condition(0.6d);
        mine.haulCapacity(6.0d);
        world.addResourceSite(farm);
        world.addResourceSite(mine);
        return world;
    }

    private static void seed(ReferenceSettlement settlement, ReferenceSimulationProfile profile, double food, double timber, double ore,
                             double energy, double tools, double medicine, double weapons, double ammo, double seeds) {
        double scale = profile.discretePeople() ? profile.personScale() : 1.0d;
        settlement.add(ReferenceResource.FOOD, food / scale);
        settlement.add(ReferenceResource.TIMBER, timber / scale);
        settlement.add(ReferenceResource.ORE, ore / scale);
        settlement.add(ReferenceResource.ENERGY, energy / scale);
        settlement.add(ReferenceResource.TOOLS, tools / scale);
        settlement.add(ReferenceResource.MEDICINE, medicine / scale);
        settlement.add(ReferenceResource.WEAPONS, weapons / scale);
        settlement.add(ReferenceResource.AMMO, ammo / scale);
        settlement.add(ReferenceResource.SEEDS, seeds / scale);
    }

    private static void prepareDailyWorld(ReferenceMarketWorld world, ReferenceSimulationProfile profile) {
        double scale = profile.discretePeople() ? profile.personScale() : 1.0d;
        world.resourceSites().get(7).add(ReferenceResource.FOOD, 7.0d / scale);
        world.resourceSites().get(3).add(ReferenceResource.ORE, 11.0d / scale);
        world.siteOutputFactor(7, 0.75d);
        world.siteOutputFactor(3, 0.9d);
        world.siteHaulInfection(7, 0.5d);
        world.siteHaulInfection(3, 0.5d);
        world.day(1);
    }

    private static ReferenceMarketWorld clearingWorld(ReferenceSimulationProfile profile) {
        ReferenceMarketWorld world = world(profile);
        world.trade().addRoute(new ReferenceRoute(1, 2, 9.0d, 100.0d));
        return world;
    }

    private static ReferenceMarketEconomy clearingMarket(ReferenceMarketWorld world, ReferenceSimulationProfile profile) {
        ReferenceMarketEconomy market = new ReferenceMarketEconomy(new ReferenceEconomyEngine(profile));
        market.bootstrap(world);
        prepareDailyWorld(world, profile);
        market.runLabourHaulProduction(world);
        world.settlements().get(2).remove(ReferenceResource.FOOD, Double.POSITIVE_INFINITY);
        world.settlements().get(2).cash(0.0d);
        market.runDay(world);
        return market;
    }

    private static final class CountingSurveyor implements ReferenceSiteSurveyor {
        private int reports;

        @Override
        public ReferenceSitePosition place(ReferenceSettlement host, ReferenceSiteKind kind) {
            int current = reports++;
            return new ReferenceSitePosition(host.x() + 20 + current, host.y() - 10 - current);
        }

        @Override
        public double quality(ReferenceSiteKind kind, int x, int y) {
            return 0.71d;
        }
    }

    private static void assertSeasons(ReferenceMarketEconomy market) {
        assertEquals("spring", market.season(0));
        assertEquals("spring", market.season(30));
        assertEquals("summer", market.season(31));
        assertEquals("autumn", market.season(61));
        assertEquals("winter", market.season(91));
        assertEquals("winter", market.season(120));
    }
}
