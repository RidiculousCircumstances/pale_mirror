package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.LinkedHashMap;

/** Test-only source fixture; production births must cross {@link PopulationBirthProcess}. */
final class HumanPopulationTestFixtures {
    private HumanPopulationTestFixtures() { }

    static FrontierWorldState withResident(FrontierWorldState state, ResidentProfile resident, BlockPosition position) {
        var actors = new LinkedHashMap<SubjectId, ActorLocation>(state.actorLocations());
        actors.put(resident.id(), new ActorLocation(position));
        return new FrontierWorldState(state.bootstrap(), actors, state.structureConditions(), state.infection(), state.inventory(), state.productionJobs(),
                state.contracts(), state.operations(), state.logisticsHistory(), state.physicalIntents(), state.physicalObservations(), state.sceneLeases(), state.hiveColony(),
                state.structureDamage(), state.physicalDeltas(), state.ambientLeases(), state.routeConstructions(), state.routeTopology(), state.strategicPlans(),
                state.humanPopulation().add(resident), state.resourceSites());
    }
}
