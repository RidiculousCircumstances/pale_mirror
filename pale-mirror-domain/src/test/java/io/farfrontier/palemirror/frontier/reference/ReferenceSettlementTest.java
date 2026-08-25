package io.farfrontier.palemirror.frontier.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReferenceSettlementTest {
    @Test
    void matchesSourceSettlementMicroTraceForStockHealthLabourAndSpecialization() {
        ReferenceSettlement settlement = new ReferenceSettlement(
                1,
                "A",
                2,
                3,
                100.0d,
                10.0d,
                new ReferenceNaturalPotential(),
                new ReferenceFacilities(),
                ReferenceSimulationProfile.SOURCE_V2);

        assertEquals(1.0d, settlement.laborFactor());
        assertEquals("industrial", settlement.specialization());
        settlement.illnessBurden(0.4d);
        assertEquals(0.9d, settlement.laborFactor());
        settlement.illnessBurden(0.0d);
        settlement.medicineFulfillment(0.5d);
        settlement.threat(0.51d);
        settlement.updateHealth();
        assertEquals(0.5d, settlement.infectionExposure());
        assertEquals(0.026500000000000003d, settlement.illnessBurden());
        settlement.primaryCapacity("mine", 2.0d);
        assertEquals("mining", settlement.specialization());

        settlement.add(ReferenceResource.FOOD, 7.0d);
        assertEquals(2.0d, settlement.remove(ReferenceResource.FOOD, 2.0d));
        assertEquals(5.0d, settlement.amount(ReferenceResource.FOOD));
        assertEquals(0.0d, settlement.remove(ReferenceResource.FOOD, -3.0d));
    }

    @Test
    void grayboxSettlementUsesTheSameResidentsForOperationDeathAndGrowth() {
        ReferenceSettlement settlement = new ReferenceSettlement(
                3,
                "G",
                0,
                0,
                80.0d,
                0.0d,
                new ReferenceNaturalPotential(),
                new ReferenceFacilities(),
                ReferenceSimulationProfile.GRAYBOX_1_40);

        assertEquals(List.of("resident:3:1", "resident:3:2"), settlement.residents().livingIds());
        assertEquals(Map.of("line", List.of("resident:3:1")), settlement.deployPeople(7, Map.of("line", 1)));
        assertEquals(1.0d, settlement.mobilizedPersonnel());
        assertEquals(1.0d, settlement.removeExposedPeople(List.of("resident:3:1"), 1.0d, "test"));
        assertEquals(List.of("resident:3:2"), settlement.residents().livingIds());
        assertEquals(0.0d, settlement.mobilizedPersonnel());
        assertEquals(1.0d, settlement.addPeople(0.9d, "growth"));
        assertEquals(List.of("resident:3:2", "resident:3:3"), settlement.residents().livingIds());
        assertNull(settlement.residents().resident("resident:3:1"));
    }

    @Test
    void matchesSourceCombatDemographyAndExactDiscreteCasualtyOrder() {
        ReferenceSettlement defended = new ReferenceSettlement(
                1,
                "s",
                0,
                0,
                1_000.0d,
                0.0d,
                new ReferenceNaturalPotential(),
                new ReferenceFacilities(),
                ReferenceSimulationProfile.SOURCE_V2);
        defended.add(ReferenceResource.WEAPONS, 100.0d);
        defended.add(ReferenceResource.AMMO, 100.0d);
        assertEquals(134.0d, defended.defenceStrength());
        assertEquals(203.0d, defended.combatDefence(1_000.0d));

        ReferenceSwarmAttackResolution attack = defended.resolveSwarmAttack(1_000.0d, 0.0d, 0.0d, 0.0d, 0.0d);
        assertEquals(new ReferenceSwarmAttackResolution(true, 223.16000000000003d, 203.0d, 0.0d, 140.0d), attack);
        assertEquals(860.0d, defended.population());
        assertEquals(0.0d, defended.integrity());
        assertEquals(91.1924d, defended.amount(ReferenceResource.WEAPONS));
        assertEquals(0.0d, defended.amount(ReferenceResource.AMMO));
        assertFalse(defended.alive());

        ReferenceSettlement demography = new ReferenceSettlement(
                2,
                "d",
                0,
                0,
                100.0d,
                0.0d,
                new ReferenceNaturalPotential(),
                new ReferenceFacilities(0.5d, 0.25d, 2.0d, 0.5d),
                ReferenceSimulationProfile.SOURCE_V2);
        assertEquals(5.0d, demography.woundPeople(5.0d, "test"));
        demography.endOfDayDemography();
        assertEquals(99.99939766d, demography.population());
        assertEquals(3.8844d, demography.woundedPersonnel());
        assertTrue(demography.alive());

        ReferenceSettlement graybox = new ReferenceSettlement(
                3,
                "g",
                0,
                0,
                200.0d,
                0.0d,
                new ReferenceNaturalPotential(),
                new ReferenceFacilities(),
                ReferenceSimulationProfile.GRAYBOX_1_40);
        ReferenceCasualtyResult casualties = graybox.applyExposedCasualties(
                graybox.residents().livingIds(), 1.5d, 1.5d, "test");
        assertEquals(List.of("resident:3:3"), casualties.killedIds());
        assertEquals(List.of("resident:3:5"), casualties.woundedIds());
        assertEquals(4.0d, graybox.population());
        assertEquals(1.0d, graybox.woundedPersonnel());
        assertEquals(List.of("resident:3:5"), graybox.recoverExposedWoundedPeople(
                List.of("resident:3:5"), 5.0d, "treatment"));
        assertEquals(0.0d, graybox.woundedPersonnel());
    }
}
