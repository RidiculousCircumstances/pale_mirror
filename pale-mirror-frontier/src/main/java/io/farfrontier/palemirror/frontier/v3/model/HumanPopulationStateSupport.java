package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.LinkedHashMap;
import java.util.Objects;

/** Pure atomic transitions that couple the exact-person register to the actor index. */
final class HumanPopulationStateSupport {
    private HumanPopulationStateSupport() { }

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

    static FrontierWorldState startBirth(FrontierWorldState state, ResidentBirthJob job) {
        Objects.requireNonNull(job, "resident birth job"); FrontierWorldStateSupport.requirePosition(state.bootstrap().bounds(), job.position());
        if (state.actorLocations().containsKey(job.resident().id()) || state.humanPopulation().resident(job.resident().id()) != null) {
            throw new IllegalArgumentException("resident birth collides with existing actor identity");
        }
        Household household = state.humanPopulation().households().get(job.householdId());
        if (household == null || !household.settlementId().equals(job.settlementId())) throw new IllegalArgumentException("resident birth needs a settlement household");
        if (SettlementFacilityCapability.livingResidents(state, job.settlementId()) >= SettlementFacilityCapability.housingCapacity(state, job.settlementId())) {
            throw new IllegalArgumentException("resident birth requires available operational housing");
        }
        return copy(state, state.actorLocations(), state.humanPopulation().startBirth(job));
    }

    static FrontierWorldState completeBirth(FrontierWorldState state, ResidentBirthJob job) {
        ResidentBirthJob current = state.humanPopulation().birthJobs().get(Objects.requireNonNull(job, "resident birth job").id());
        if (!job.equals(current)) throw new IllegalArgumentException("resident birth completion differs from its active permit");
        if (SettlementFacilityCapability.livingResidents(state, job.settlementId()) >= SettlementFacilityCapability.housingCapacity(state, job.settlementId())) {
            throw new IllegalArgumentException("resident birth lost required housing before completion");
        }
        var actors = new LinkedHashMap<>(state.actorLocations()); actors.put(job.resident().id(), new ActorLocation(job.position()));
        return copy(state, actors, state.humanPopulation().completeBirth(job.id()));
    }

    static FrontierWorldState cancelBirth(FrontierWorldState state, SubjectId jobId) {
        return copy(state, state.actorLocations(), state.humanPopulation().cancelBirth(jobId));
    }

    private static FrontierWorldState copy(FrontierWorldState state, java.util.Map<SubjectId, ActorLocation> actors, HumanPopulation population) {
        return new FrontierWorldState(state.bootstrap(), actors, state.structureConditions(), state.infection(), state.inventory(), state.productionJobs(),
                state.contracts(), state.operations(), state.physicalIntents(), state.physicalObservations(), state.sceneLeases(), state.hiveColony(),
                state.structureDamage(), state.physicalDeltas(), state.ambientLeases(), state.routeConstructions(), state.routeTopology(), state.strategicPlans(), population);
    }
}
