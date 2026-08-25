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

    @Test
    void publicV2SnapshotMatchesPinnedPythonReadModelAtEveryCheckpoint() {
        ReferenceWorld world = new ReferenceWorld(ReferenceWorldConfig.sourceV2());
        Map<Integer, String> expectedDigests = Map.of(
                0, "e83c55604acc10b1de850be6021554b26be4ce4f1c755d7dae98ad06838a55f6",
                1, "6d0161e3fb9a0fe46c3216b431073089fe7e13c17baba16789e76c2a10ebc038",
                5, "3d48cce7b68e4044d1ce9aca088d79887daac88f6f6c7fdb79f3f747fdf3ef8d",
                10, "a4e35cbca132be953e922dee08128333a401caac34b80040f380e612e6b43131",
                15, "266b97428ccd0c26b0b4da1ce2e1c509e95b6950bed6dddb264ade1b9e89e8ad",
                20, "dfdf81df56dd8ac788b061ab3094aad956e609c074ad3e953ced6c87a3cdb5c8",
                25, "b57f5ff548126159d3b2c3f0be30a39951b2a18a1cead7106feea8358f50addf",
                30, "bda76a4944b03c5e8467bc6f96888669750b81b1cf96d630cac4104da1def61d");
        Map<String, String> expectedDayZeroComponents = Map.ofEntries(
                Map.entry("companies", "92f3bd9b096653c25bfa9684b34d1fa4da1e7f32d27dc3110685507e5779b0ce"),
                Map.entry("economy", "0dbc3405a12cdb9ca1cbcdd2b7f8fbe8f9127e3a07e119e65ec3a45eaa57f19c"),
                Map.entry("field", "e4df041bb6b634542507d545b74c3a2213eed1de1e923538c15f69725cbcf604"),
                Map.entry("hive", "99addcaa2f0eee293ff5cbbecbe1b8589ecc028c5c800ab3bdfeaebd7e1490e8"),
                Map.entry("infected_fraction", "eb796f33128a3d0a8e3ead887f0bf1354be07bf934bad9173c69983524bd69a0"),
                Map.entry("nests", "479991fde170e3f8054aa49f890af515d3c832eb8fa65b1cdcc7090997382ab2"),
                Map.entry("resource_sites", "fd292934ca1760cf45fd56c1d06e04f5c1e46f3b52239e6c1266364d0fa19cc1"),
                Map.entry("settlements", "b39503a52fd816e4b797ddb539c5a4dfb566c9efb2eb722fd7a7e6d91d8756d4"));
        Map<String, Object> dayZero = ReferenceV2PublicSnapshot.capture(world);
        for (Map.Entry<String, String> expected : expectedDayZeroComponents.entrySet()) {
            assertEquals(expected.getValue(), ReferenceV2PublicSnapshot.sha256(dayZero.get(expected.getKey())),
                    "public snapshot day 0 component " + expected.getKey());
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> v2 = (Map<String, Object>) dayZero.get("v2");
        Map<String, String> expectedDayZeroV2Components = Map.of(
                "civics", "6861c0892b4c83e29b5031b2745717968d1d84a95f65ae0b9046748ff4446aca",
                "doctrines", "bcef672161b8125cf17c2a1c4efb2af7f8d44b8225bff35f34b52c399d3c4b24",
                "sector_control", "a9641169147f17782b461e45cd9f577544cb3d8dd8b4239604377efc52828d87",
                "sectors", "4c31e651d941c5a25855a516633177131745d2ffd52f5a7ee82fe51dc65a9b75",
                "summary", "4ed04a9cb4eb56400c86e6a3668e422c0bcb8a7c6e9b81697eaeaa9e7b86b644");
        for (Map.Entry<String, String> expected : expectedDayZeroV2Components.entrySet()) {
            assertEquals(expected.getValue(), ReferenceV2PublicSnapshot.sha256(v2.get(expected.getKey())),
                    "public snapshot day 0 V2 component " + expected.getKey());
        }
        assertEquals("c8ab38b80c91373e50c334dfa3c98c244bc255317d07923fa3fef8280372929d",
                ReferenceV2PublicSnapshot.sha256(v2), "public snapshot day 0 component v2");

        for (int day = 0; day <= 30; day++) {
            String expected = expectedDigests.get(day);
            if (expected != null) assertEquals(expected, ReferenceV2PublicSnapshot.sha256(world), "public snapshot day " + day);
            if (day < 30) world.tick();
        }
    }

    @Test
    void publicV2SnapshotRejectsAnUnportedDiscreteResidentProjection() {
        ReferenceWorld world = new ReferenceWorld(ReferenceWorldConfig.graybox1To40(42L));

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> ReferenceV2PublicSnapshot.capture(world));

        assertEquals("graybox resident snapshot is not ported", error.getMessage());
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
