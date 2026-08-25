package io.farfrontier.palemirror.frontier.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReferenceSimulationEngineTest {
    @Test
    void completeFirstV2DayMatchesThePinnedPythonEngineTrace() {
        ReferenceWorld world = new ReferenceWorld(ReferenceWorldConfig.sourceV2());

        assertEquals(0, world.day());
        assertEquals(1, world.history().size());
        world.tick();

        assertEquals(1, world.day());
        assertEquals(2, world.history().size());
        ReferenceDailyWorldHistory history = world.history().getLast();
        assertEquals(1, history.day());
        assertEquals(12, history.alive());
        assertClose(12749.030993270018d, history.population());
        assertClose(12938.678273469115d, history.cash());
        assertClose(20752.653163760682d, history.privateCash());
        assertClose(.013849431818181818d, history.infection());
        assertEquals(2, history.nests());
        assertClose(267.70389759871694d, history.hiveBiomass());
        assertClose(57.74454449486295d, history.harvestedBiomass());
        assertClose(273788.6724327424d, history.ecologyOrganic());
        assertClose(.19000869291321176d, history.ecologyScar());
        assertClose(1245.4244070135412d, history.trade30d());
        assertEquals(12, world.v2().decisionHistory().size());
        assertEquals(0, world.v2().chrysalises().size());
        assertEquals(0, world.v2().frontCampaigns().size());

        ReferenceSettlement first = world.settlements().get(1);
        assertClose(1225.378343494121d, first.population());
        assertClose(1357.6608862672858d, first.cash());
        assertClose(163.84752169145725d, first.amount(ReferenceResource.ORE));
        assertClose(1051.4462152209587d, first.amount(ReferenceResource.FOOD));
        assertClose(72.33947826163097d, first.dailyProduction().get(ReferenceResource.ORE));
        assertClose(79.63764668011585d, first.dailyConsumption().get(ReferenceResource.FOOD));

        ReferenceSettlement twelfth = world.settlements().get(12);
        assertClose(762.5794411769837d, twelfth.population());
        assertClose(1260.2870454334522d, twelfth.cash());
        assertClose(143.77977321782555d, twelfth.amount(ReferenceResource.ORE));
        assertClose(64.87110505034973d, twelfth.dailyProduction().get(ReferenceResource.ORE));
        assertClose(49.560229642057635d, twelfth.dailyConsumption().get(ReferenceResource.FOOD));

        assertClose(1.0d, world.trade().routes().getFirst().checkpointCapacityMultiplier());
        assertClose(0.0d, world.trade().routes().getFirst().infection());
        assertEquals("spring", world.microeconomy().summary().get("season"));
        assertEquals(70, world.microeconomy().summary().get("companies"));
        assertEquals(52, world.microeconomy().summary().get("active_contracts"));
        assertClose(0.0d, (Double) world.microeconomy().summary().get("credit"));
        assertEquals(0, world.microeconomy().summary().get("construction"));
        assertEquals("D1: sector 0:0 is abandoned: outside sustainable human reach", world.events().getFirst());
        assertEquals("D1: Lakestead-12 upgraded mine site 33", world.events().getLast());
        assertEquals(2, world.settlementHistory().get(1).size());
        assertClose(163.84752169145725d, world.settlementHistory().get(1).getLast().resources().get(ReferenceResource.ORE).amount());
    }

    @Test
    void legacyProfileFailsClosedInsteadOfSilentlyDroppingItsUnportedStrategist() {
        ReferenceWorld world = new ReferenceWorld(new ReferenceWorldConfig(
                64, 44, 12, 42L, 2, false, ReferenceSimulationProfile.SOURCE_V2));

        IllegalStateException error = assertThrows(IllegalStateException.class, world::tick);

        assertTrue(error.getMessage().contains("strategy is not ported"));
        assertEquals(0, world.day());
        assertEquals(1, world.history().size());
    }

    @Test
    void grayboxDayKeepsNamedPeopleUnderOneCustodianAndRejectsAnOrphanedDeployment() {
        ReferenceWorld world = new ReferenceWorld(ReferenceWorldConfig.graybox1To40(42L));

        world.tick();

        assertEquals(1, world.day());
        assertEquals(12, world.history().getLast().alive());
        ReferenceSettlement settlement = world.settlements().get(1);
        String residentId = settlement.residents().availableIds().getFirst();
        settlement.residents().deploy(List.of(residentId), 999, Map.of(residentId, "line"));

        IllegalStateException error = assertThrows(IllegalStateException.class, world::assertProfileInvariants);

        assertTrue(error.getMessage().contains("without a custodian"));
    }

    @Test
    void thirtyV2DaysFollowThePinnedPythonTrajectoryAtEveryPhaseBoundary() {
        ReferenceWorld world = new ReferenceWorld(ReferenceWorldConfig.sourceV2());
        Map<Integer, Checkpoint> expected = Map.of(
                5, new Checkpoint(12756.682133157283d, 16399.03438398508d, 34697.88089338411d, .013849431818181818d,
                        273278.40539309196d, .727779777242157d, 426.49364180984907d, 304.4522509701408d,
                        3332.2199906874434d, 0, 0, 2, 52, 0.0d),
                10, new Checkpoint(12766.252515441201d, 17431.0754687519d, 37881.22061107734d, .013849431818181818d,
                        272977.9129644284d, 1.5347058269293907d, 517.2121024927415d, 653.6169218130582d,
                        4732.070243943449d, 1, 0, 2, 52, 0.0d),
                15, new Checkpoint(12775.830077665492d, 16643.973970767573d, 39469.189498807675d, .024502840909090908d,
                        272867.59364104166d, 2.487942831580816d, 609.0898405930036d, 1047.0584660240477d,
                        7421.517582222815d, 2, 0, 2, 98, 0.0d),
                20, new Checkpoint(12785.414825216725d, 16263.505504978919d, 40972.99521926133d, .04580965909090909d,
                        272848.7709439577d, 3.600745598323352d, 611.0964075212636d, 1496.26603380732d,
                        9688.58212380891d, 5, 1, 2, 98, 0.0d),
                25, new Checkpoint(12795.006763485511d, 14678.691428412441d, 42847.167850659054d, .04651988636363636d,
                        272573.9391405604d, 5.3825166966784685d, 508.3886435042293d, 2275.8712794527296d,
                        13316.54997885628d, 5, 1, 6, 154, 10.05d),
                30, new Checkpoint(12804.605897866513d, 14318.594735264869d, 44427.40484190321d, .060369318181818184d,
                        272164.0265906391d, 7.752552336102551d, 824.7901907477968d, 3293.025420823076d,
                        15657.113881511215d, 5, 1, 8, 154, 10.15d));

        for (int day = 1; day <= 30; day++) {
            world.tick();
            Checkpoint checkpoint = expected.get(day);
            if (checkpoint != null) assertCheckpoint(world, checkpoint);
        }

        assertClose(1230.7199482459048d, world.settlements().get(1).population());
        assertClose(1273.9523318338834d, world.settlements().get(1).cash());
        assertClose(765.903637322795d, world.settlements().get(12).population());
        assertClose(1137.7830052222255d, world.settlements().get(12).cash());
        List<String> events = world.events();
        assertEquals(List.of(
                "D29: Oldgate-08 forest-23 Co. is insolvent",
                "D29: Ironreach-01 workshop Co. is stressed",
                "D29: Ashhill-03 workshop Co. is stressed",
                "D29: Highford-10 workshop Co. is stressed",
                "D29: 82 trades, value=345; largest tools Lakestead-07->Ironreach-01 x4.8 @ 5.06",
                "D30: sector 5:7 is contested: neither side can hold the frontier",
                "D30: Ashwatch-02 clinic Co. entered municipal receivership",
                "D30: creditors restructured Ashwatch-02 armory Co.",
                "D30: creditors restructured Blackfield-05 workshop Co.",
                "D30: creditors restructured Stonehaven-11 armory Co.",
                "D30: Lakestead-07 power-19 Co. is insolvent",
                "D30: 80 trades, value=340; largest tools Lakestead-07->Ironreach-01 x4.8 @ 5.06"),
                events.subList(events.size() - 12, events.size()));
    }

    private static void assertCheckpoint(ReferenceWorld world, Checkpoint expected) {
        ReferenceDailyWorldHistory actual = world.history().getLast();
        assertClose(expected.population(), actual.population());
        assertClose(expected.cash(), actual.cash());
        assertClose(expected.privateCash(), actual.privateCash());
        assertClose(expected.infection(), actual.infection());
        assertClose(expected.ecologyOrganic(), actual.ecologyOrganic());
        assertClose(expected.ecologyScar(), actual.ecologyScar());
        assertClose(expected.hiveBiomass(), actual.hiveBiomass());
        assertClose(expected.harvestedBiomass(), actual.harvestedBiomass());
        assertClose(expected.trade30d(), actual.trade30d());
        assertEquals(expected.frontCampaigns(), world.v2().frontCampaigns().size());
        assertEquals(expected.heldSectors(), world.v2().sectorControl().values().stream()
                .filter(control -> control.state() == ReferenceSectorControlState.HUMAN).count());
        assertEquals(expected.hiveSectors(), world.v2().sectorControl().values().stream()
                .filter(control -> control.state() == ReferenceSectorControlState.HIVE).count());
        assertEquals(expected.activeContracts(), world.microeconomy().summary().get("active_contracts"));
        assertClose(expected.credit(), (Double) world.microeconomy().summary().get("credit"));
    }

    private record Checkpoint(
            double population,
            double cash,
            double privateCash,
            double infection,
            double ecologyOrganic,
            double ecologyScar,
            double hiveBiomass,
            double harvestedBiomass,
            double trade30d,
            int frontCampaigns,
            long heldSectors,
            long hiveSectors,
            int activeContracts,
            double credit
    ) { }

    private static void assertClose(double expected, double actual) {
        assertEquals(expected, actual, 1.0e-9d);
    }
}
