package io.farfrontier.palemirror.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;

class JourneySimulationTest {
    @Test
    void offScreenRiskIsDeterministicAndIndependentOfPhysicalProjection() {
        WorldState first = state();
        WorldState second = state();
        DomainServices firstServices = new DomainServices();
        DomainServices secondServices = new DomainServices();

        for (long step = 1; step <= 3; step++) {
            first.setSimulationStep(step);
            second.setSimulationStep(step);
            if (step == 2) second.journey("pale_mirror:journey").orElseThrow().observeCheckpoint(1);
            firstServices.journeys().reconcile(first);
            secondServices.journeys().reconcile(second);
        }

        assertEquals(first.populationGroup("pale_mirror:group").orElseThrow().cohorts(),
                second.populationGroup("pale_mirror:group").orElseThrow().cohorts());
        assertEquals(first.journey("pale_mirror:journey").orElseThrow().confirmedLosses(),
                second.journey("pale_mirror:journey").orElseThrow().confirmedLosses());
        assertEquals(JourneyState.ARRIVED, first.journey("pale_mirror:journey").orElseThrow().state());
        assertEquals(JourneyState.ARRIVED, second.journey("pale_mirror:journey").orElseThrow().state());
    }

    private static WorldState state() {
        WorldObjectId community = new WorldObjectId("pale_mirror:community");
        WorldObjectId origin = new WorldObjectId("pale_mirror:origin");
        WorldObjectId destination = new WorldObjectId("pale_mirror:destination");
        WorldState state = new WorldState();
        state.putPopulationGroup(new PopulationGroup("pale_mirror:group", community,
                Map.of(SettlementCohort.CIVILIANS, 10), origin, PopulationDisposition.IN_TRANSIT,
                null, null, "pale_mirror:journey", -1, 0));
        state.putJourney(new WorldJourney("pale_mirror:journey", "pale_mirror:group", "pale_mirror:path",
                origin, destination, JourneyMode.LAND_FOOT,
                new JourneyRiskPolicy("pale_mirror:test", "1", 10_000, 2, 0),
                42L, 0, 3, JourneyState.IN_TRANSIT, 0, 0, 0, "", 0));
        return state;
    }
}
