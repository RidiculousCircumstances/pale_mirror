package io.farfrontier.palemirror.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SimulationEngineTest {
    @Test
    void infectionRecoveryAndNarrativeAreDeterministic() {
        WorldState state = new WorldState();
        WorldObjectId mine = new WorldObjectId("pale_mirror:test_mine");
        state.putFacility(new FacilityState(mine, 80, 10, 10));
        DomainServices services = new DomainServices();

        DomainEvent infection = services.simulation().advance(state, 1).getFirst();
        assertEquals(DomainEventType.MINE_INFECTED, infection.type());
        assertEquals(FacilityStatus.INFECTED, state.facility(mine).orElseThrow().status());

        ScenarioInstance scenario = services.narrator()
                .offerFor(state, infection, StoryAudienceId.globalTestAudience()).orElseThrow();
        services.scenarios().accept(state, scenario.id());
        services.scenarios().playerEntered(state, StoryAudienceId.globalTestAudience(), mine);
        services.threats().controllerDestroyed(state, mine, "test:controller");
        services.scenarios().reconcileRecovery(state, mine);
        services.simulation().advance(state, 1);

        assertEquals(FacilityStatus.OPERATIONAL, state.facility(mine).orElseThrow().status());
        assertEquals(80, state.facility(mine).orElseThrow().currentProduction());
        assertEquals(ScenarioStatus.RESOLVED, state.scenario(scenario.id()).orElseThrow().status());
        assertTrue(state.history().stream().anyMatch(event -> event.type() == DomainEventType.FACILITY_OPERATIONAL));
    }

    @Test
    void negativeSimulationStepFailsVisiblyWithoutMutatingState() {
        WorldState state = new WorldState();

        assertThrows(IllegalArgumentException.class, () -> new DomainServices().simulation().advance(state, -1));
        assertEquals(0, state.simulationStep());
    }
}
