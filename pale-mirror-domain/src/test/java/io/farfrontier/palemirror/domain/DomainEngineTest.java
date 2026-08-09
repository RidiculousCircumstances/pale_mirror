package io.farfrontier.palemirror.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DomainEngineTest {
    @Test
    void infectionRecoveryAndNarrativeAreDeterministic() {
        WorldState state = new WorldState();
        WorldObjectId mine = new WorldObjectId("pale_mirror:test_mine");
        state.putFacility(new FacilityState(mine, 80, 10, 10));
        DomainEngine engine = new DomainEngine();

        DomainEvent infection = engine.advanceSimulation(state, 1).getFirst();
        assertEquals(DomainEventType.MINE_INFECTED, infection.type());
        assertEquals(FacilityStatus.INFECTED, state.facility(mine).orElseThrow().status());

        ScenarioInstance scenario = new Narrator().offerFor(state, infection, StoryAudienceId.globalTestAudience()).orElseThrow();
        ScenarioRuntime runtime = new ScenarioRuntime();
        runtime.accept(state, scenario.id());
        runtime.playerEntered(state, StoryAudienceId.globalTestAudience(), mine);
        engine.controllerDestroyed(state, mine, "test:controller");
        runtime.reconcileRecovery(state, mine);
        engine.advanceSimulation(state, 1);

        assertEquals(FacilityStatus.OPERATIONAL, state.facility(mine).orElseThrow().status());
        assertEquals(80, state.facility(mine).orElseThrow().currentProduction());
        assertEquals(ScenarioStatus.RESOLVED, state.scenario(scenario.id()).orElseThrow().status());
        assertTrue(state.history().stream().anyMatch(event -> event.type() == DomainEventType.FACILITY_OPERATIONAL));
    }
}
