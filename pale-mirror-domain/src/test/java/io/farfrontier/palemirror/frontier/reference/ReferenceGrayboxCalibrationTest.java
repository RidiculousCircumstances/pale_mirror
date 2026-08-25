package io.farfrontier.palemirror.frontier.reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Behavioural acceptance gate corresponding to frontier-reference-graybox-calibration.json. */
class ReferenceGrayboxCalibrationTest {
    private static final int DAYS = 365;
    private static final List<Long> SEEDS = List.of(7L, 17L, 41L, 73L);

    @Test
    void grayboxStaysInsideTheSourceDerivedYearlyBehaviourEnvelope() {
        List<ReferenceGrayboxCalibration.Run> runs = SEEDS.stream()
                .map(seed -> ReferenceGrayboxCalibration.run(seed, DAYS)).toList();
        ReferenceGrayboxCalibration.Summary summary = ReferenceGrayboxCalibration.summarize(runs);
        String evidence = "runs=" + runs + ", summary=" + summary;

        assertTrue(runs.stream().allMatch(run -> run.daysSimulated() == DAYS), "all source seeds complete the yearly horizon: " + evidence);
        assertEquals(4, summary.runs());
        assertTrue(summary.minimumAliveSettlementsPerRun() >= 10, "each world retains a legible settlement network: " + evidence);
        assertInclusive(summary.tradeActiveRuns(), 3, 4, "trade-active seed count; " + evidence);
        assertInclusive(summary.medianPopulationRatio(), .65d, .90d, "median population survival; " + evidence);
        assertInclusive(summary.medianPeakActiveNests(), 10.0d, 25.0d, "median peak hive organs; " + evidence);
        assertInclusive(summary.medianPeakActiveBioforms(), 2.0d, 15.0d, "median peak whole bioforms; " + evidence);
        assertInclusive(summary.medianDestroyedOrgans(), 25.0d, 75.0d, "median destroyed organs; " + evidence);
        assertInclusive(summary.medianFinalEcologicalScar(), 1000.0d, 2600.0d, "median final ecological scar; " + evidence);
        assertInclusive(summary.medianFrontCampaignsStarted(), 10.0d, 40.0d, "median frontier campaigns begun; " + evidence);
        assertInclusive(summary.medianFrontCampaignsCompleted(), 8.0d, 30.0d, "median frontier campaigns completed; " + evidence);
        assertInclusive(summary.frontCampaignFailedRuns(), 0, 4, "frontier-campaign failure seed count; " + evidence);
    }

    @Test
    void repeatedJavaRunIsIdenticalBeforeItIsComparedToThePythonEnvelope() {
        assertEquals(ReferenceGrayboxCalibration.run(41L, 90), ReferenceGrayboxCalibration.run(41L, 90));
    }

    @Test
    void civicEmergencyRationsReachTheMarketConsumptionOwner() {
        ReferenceWorld world = new ReferenceWorld(ReferenceWorldConfig.graybox1To40(7L));
        assertNotNull(world.marketWorld().rationAuthority(), "a V2 world must expose civic rations to household consumption");

        for (int day = 0; day < 57; day++) world.tick();

        ReferenceSettlement settlement = world.settlements().get(9);
        assertEquals(ReferenceCivicState.EMERGENCY, world.v2().civics().get(9).state());
        assertEquals(.92d, world.v2().civics().get(9).rationFraction());
        assertEquals(.92d, settlement.foodFulfillment(), 1.0e-12d,
                "the market must consume the current civic-ration issue, not an implicit full meal");
    }

    private static void assertInclusive(double actual, double minimum, double maximum, String subject) {
        assertTrue(actual >= minimum && actual <= maximum,
                () -> subject + " expected within [" + minimum + ", " + maximum + "], got " + actual);
    }
}
