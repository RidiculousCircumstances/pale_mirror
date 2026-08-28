package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.LinkedHashMap;
import java.util.Objects;

/** Pure atomic transitions that couple the exact-person register to the actor index. */
final class HumanPopulationStateSupport {
    private HumanPopulationStateSupport() { }

    static FrontierWorldState recordBirth(FrontierWorldState state, ResidentBorn birth) {
        Objects.requireNonNull(birth, "resident birth"); FrontierWorldStateSupport.requirePosition(state.bootstrap().bounds(), birth.position());
        if (state.actorLocations().containsKey(birth.resident().id())) throw new IllegalArgumentException("resident birth collides with existing actor identity");
        if (state.bootstrap().settlements().stream().noneMatch(settlement -> settlement.id().equals(birth.resident().settlementId()))) {
            throw new IllegalArgumentException("resident birth settlement is unknown");
        }
        int occupants = SettlementFacilityCapability.livingResidents(state, birth.resident().settlementId());
        int beds = SettlementFacilityCapability.housingCapacity(state, birth.resident().settlementId());
        if (occupants >= beds) throw new IllegalArgumentException("resident birth requires available operational housing");
        var actors = new LinkedHashMap<>(state.actorLocations()); actors.put(birth.resident().id(), new ActorLocation(birth.position()));
        return copy(state, actors, state.humanPopulation().add(birth.resident()));
    }

    static FrontierWorldState recordMigration(FrontierWorldState state, ResidentMigrated migration) {
        Objects.requireNonNull(migration, "resident migration"); FrontierWorldStateSupport.requirePosition(state.bootstrap().bounds(), migration.destination());
        ActorLocation actor = state.actorLocations().get(migration.residentId());
        if (actor == null || actor.condition().status() != ActorLifeStatus.ALIVE) throw new IllegalArgumentException("only a living resident may migrate");
        if (state.humanPopulation().resident(migration.residentId()) == null) throw new IllegalArgumentException("migration subject is not a resident");
        if (state.operations().values().stream().anyMatch(operation -> FrontierWorldStateSupport.retainsParticipantClaim(state, operation)
                && operation.participantIds().contains(migration.residentId()))) throw new IllegalArgumentException("resident assigned to an active operation cannot migrate");
        ResidentProfile current = state.humanPopulation().resident(migration.residentId());
        if (!current.settlementId().equals(migration.destinationSettlementId())) {
            int occupants = SettlementFacilityCapability.livingResidents(state, migration.destinationSettlementId());
            int beds = SettlementFacilityCapability.housingCapacity(state, migration.destinationSettlementId());
            if (occupants >= beds) throw new IllegalArgumentException("resident migration requires available operational housing");
        }
        var actors = new LinkedHashMap<>(state.actorLocations()); actors.put(migration.residentId(), actor.withPosition(migration.destination()));
        return copy(state, actors, state.humanPopulation().migrate(migration.residentId(), migration.destinationHouseholdId(), migration.destinationSettlementId()));
    }

    private static FrontierWorldState copy(FrontierWorldState state, java.util.Map<SubjectId, ActorLocation> actors, HumanPopulation population) {
        return new FrontierWorldState(state.bootstrap(), actors, state.structureConditions(), state.infection(), state.inventory(), state.productionJobs(),
                state.contracts(), state.operations(), state.physicalIntents(), state.physicalObservations(), state.sceneLeases(), state.hiveColony(),
                state.structureDamage(), state.physicalDeltas(), state.ambientLeases(), state.routeConstructions(), state.routeTopology(), state.strategicPlans(), population);
    }
}
