package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.LinkedHashMap;

/** Test-only source fixture; production births must cross {@link PopulationBirthProcess}. */
final class HumanPopulationTestFixtures {
    private HumanPopulationTestFixtures() { }

    static FrontierWorldState withResident(FrontierWorldState state, ResidentProfile resident, BlockPosition position) {
        var actors = new LinkedHashMap<SubjectId, ActorLocation>(state.actorLocations());
        actors.put(resident.id(), new ActorLocation(FrontierTestPositions.bodyAboveSupport(position), ActorCondition.HEALTHY, ActorKind.RESIDENT));
        return state.withChanges(FrontierWorldStateUpdate.begin().actorLocations(actors)
                .humanPopulation(state.humanPopulation().add(resident)));
    }
}
