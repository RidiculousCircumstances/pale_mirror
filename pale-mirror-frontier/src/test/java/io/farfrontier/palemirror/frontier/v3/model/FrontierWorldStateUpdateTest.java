package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FrontierWorldStateUpdateTest {
    @Test
    void namedChangeReplacesOnlyItsDeclaredComponent() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:update"), 91L));
        FrontierWorldState changed = state.withChanges(FrontierWorldStateUpdate.begin()
                .actorLocations(new LinkedHashMap<>(state.actorLocations())));

        assertNotSame(state.actorLocations(), changed.actorLocations());
        assertSame(state.bootstrap(), changed.bootstrap());
        assertSame(state.structureConditions(), changed.structureConditions());
        assertSame(state.infection(), changed.infection());
        assertSame(state.inventory(), changed.inventory());
        assertSame(state.productionJobs(), changed.productionJobs());
        assertSame(state.contracts(), changed.contracts());
        assertSame(state.operations(), changed.operations());
        assertSame(state.logisticsHistory(), changed.logisticsHistory());
        assertSame(state.physicalIntents(), changed.physicalIntents());
        assertSame(state.physicalObservations(), changed.physicalObservations());
        assertSame(state.sceneLeases(), changed.sceneLeases());
        assertSame(state.hiveColony(), changed.hiveColony());
        assertSame(state.structureDamage(), changed.structureDamage());
        assertSame(state.physicalDeltas(), changed.physicalDeltas());
        assertSame(state.ambientLeases(), changed.ambientLeases());
        assertSame(state.routeConstructions(), changed.routeConstructions());
        assertSame(state.routeTopology(), changed.routeTopology());
        assertSame(state.strategicPlans(), changed.strategicPlans());
        assertSame(state.humanPopulation(), changed.humanPopulation());
        assertSame(state.companies(), changed.companies());
        assertSame(state.resourceSites(), changed.resourceSites());
    }

    @Test
    void namedChangeRejectsDuplicateDeclarationsAndRetainsDeclaredNoOpIdentity() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:update-negative"), 91L));
        FrontierWorldStateUpdate duplicate = FrontierWorldStateUpdate.begin().inventory(state.inventory());
        assertThrows(IllegalStateException.class, () -> duplicate.inventory(state.inventory()));
        assertSame(state.inventory(), state.withChanges(FrontierWorldStateUpdate.begin().inventory(state.inventory())).inventory());
    }
}
