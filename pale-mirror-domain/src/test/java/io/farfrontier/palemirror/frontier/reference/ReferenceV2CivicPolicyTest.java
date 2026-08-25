package io.farfrontier.palemirror.frontier.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReferenceV2CivicPolicyTest {
    @Test
    void sourceCivicRegimesApplyRationsQuarantineInsuranceAndRecovery() {
        ReferenceWorld world = sourceCivicWorld(41L);
        ReferenceV2State v2 = world.v2();
        ReferenceSettlement settlement = world.settlements().get(1);
        ReferenceCompany company = world.microeconomy().companies().get(1);

        v2.updateCivics(world);
        ReferenceCivicLedger normal = v2.civics().get(1);
        assertEquals(ReferenceCivicState.NORMAL, normal.state());
        assertEquals(20.50745815348357d, normal.foodReserveDays());
        assertEquals(0.704d, normal.legitimacy());
        assertEquals(148.4408021113925d, normal.warBudget());
        assertEquals(1.0d, normal.rationFraction());
        assertEquals(0.42d, company.wageOffer());
        assertTrue(v2.emergencyRegimes().isEmpty());

        settlement.threat(0.40d);
        settlement.illnessBurden(0.30d);
        world.day(5);
        v2.updateCivics(world);
        ReferenceCivicLedger emergency = v2.civics().get(1);
        assertEquals(ReferenceCivicState.EMERGENCY, emergency.state());
        assertEquals(5, emergency.enteredDay());
        assertEquals(0.92d, emergency.rationFraction());
        assertEquals(13, emergency.quarantineUntil());
        assertEquals(320.5058424171821d, emergency.warBudget());
        assertEquals(0.46704d, company.wageOffer());
        assertEquals(92.0d, v2.applyRations(world, settlement.id(), 100.0d));
        assertEquals(ReferenceCivicState.EMERGENCY, v2.emergencyRegimes().get(1).state());
        assertEquals(4, v2.routeInsurance().size());
        ReferenceRouteInsurance insurance = v2.routeInsurance().get(ReferenceRouteKey.between(1, 4));
        assertEquals(0.047446553768475105d, insurance.premium());
        assertEquals(0.8814613926811895d, insurance.coverage());
        assertEquals(17, insurance.expiresDay());
        assertEquals("D5: Ashfield-01 entered emergency: infection or medical emergency", world.events().getLast());

        settlement.threat(0.70d);
        world.day(6);
        v2.updateCivics(world);
        ReferenceCivicLedger siege = v2.civics().get(1);
        assertEquals(ReferenceCivicState.SIEGE, siege.state());
        assertEquals(0.82d, siege.rationFraction());
        assertEquals(14, siege.quarantineUntil());
        assertEquals(0.6795d, siege.legitimacy());
        assertEquals(0.525d, company.wageOffer());
        assertEquals(ReferenceCivicState.SIEGE, v2.emergencyRegimes().get(1).state());
        assertEquals(18, v2.routeInsurance().get(ReferenceRouteKey.between(1, 4)).expiresDay());

        settlement.threat(0.05d);
        settlement.illnessBurden(0.0d);
        world.day(7);
        v2.updateCivics(world);
        ReferenceCivicLedger recovery = v2.civics().get(1);
        assertEquals(ReferenceCivicState.RECOVERY, recovery.state());
        assertEquals(1.0d, recovery.rationFraction());
        assertEquals(148.4408021113925d, recovery.warBudget());
        assertEquals(0.525d, company.wageOffer());
        assertFalse(v2.emergencyRegimes().containsKey(1));
        assertEquals("D7: Ashfield-01 entered recovery: threat receding", world.events().getLast());
    }

    @Test
    void normalCivicObservationDoesNotRewriteMarketWages() {
        ReferenceWorld world = sourceCivicWorld(72L);
        Map<Integer, Double> before = wages(world);

        world.v2().updateCivics(world);

        assertEquals(before, wages(world));
    }

    private static ReferenceWorld sourceCivicWorld(long seed) {
        return new ReferenceWorld(new ReferenceWorldConfig(
                64, 44, 12, seed, 0, true, ReferenceSimulationProfile.SOURCE_V2));
    }

    private static Map<Integer, Double> wages(ReferenceWorld world) {
        Map<Integer, Double> result = new LinkedHashMap<>();
        for (Map.Entry<Integer, ReferenceCompany> entry : world.microeconomy().companies().entrySet()) {
            result.put(entry.getKey(), entry.getValue().wageOffer());
        }
        return result;
    }
}
