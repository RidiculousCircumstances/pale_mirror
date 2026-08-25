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

    private static void assertClose(double expected, double actual) {
        assertEquals(expected, actual, 1.0e-9d);
    }
}
