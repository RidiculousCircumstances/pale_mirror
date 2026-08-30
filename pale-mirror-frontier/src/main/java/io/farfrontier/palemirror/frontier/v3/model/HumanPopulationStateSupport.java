package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.LinkedHashMap;
import java.util.Map;
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
        ResidentMigrationJourney journey = state.humanPopulation().migration(migration.residentId());
        if (journey == null || !journey.arriving() || !actor.position().equals(journey.currentPosition())
                || !journey.destinationHouseholdId().equals(migration.destinationHouseholdId())
                || !journey.destinationSettlementId().equals(migration.destinationSettlementId()) || !journey.currentPosition().equals(migration.destination())) {
            throw new IllegalArgumentException("resident migration must complete one exact arrived journey");
        }
        if (state.humanPopulation().quarantined(current.settlementId()) || state.humanPopulation().quarantined(migration.destinationSettlementId())) {
            throw new IllegalArgumentException("resident migration cannot cross an active settlement quarantine");
        }
        if (!current.settlementId().equals(migration.destinationSettlementId()) && !hasReservedHousing(state, migration.destinationSettlementId())) {
            throw new IllegalArgumentException("resident migration lost its reserved operational housing");
        }
        var actors = new LinkedHashMap<>(state.actorLocations()); actors.put(migration.residentId(), actor.withPosition(migration.destination()));
        return copy(state, actors, state.humanPopulation().completeMigration(migration.residentId(), migration.destinationHouseholdId(), migration.destinationSettlementId()));
    }

    static FrontierWorldState startMigration(FrontierWorldState state, ResidentMigrationJourney journey) {
        Objects.requireNonNull(journey, "migration journey");
        ActorLocation actor = state.actorLocations().get(journey.residentId()); ResidentProfile resident = state.humanPopulation().resident(journey.residentId());
        if (actor == null || actor.condition().status() != ActorLifeStatus.ALIVE || resident == null || !resident.settlementId().equals(journey.originSettlementId())
                || !actor.position().equals(journey.currentPosition()) || !coldAvailable(state, journey.residentId())) {
            throw new IllegalArgumentException("migration journey must start from one available living COLD resident");
        }
        if (!canReserveHousing(state, journey.destinationSettlementId())) {
            throw new IllegalArgumentException("migration journey requires one reservable destination housing place");
        }
        requireJourneyBounds(state, journey);
        return copy(state, state.actorLocations(), state.humanPopulation().startMigration(journey));
    }

    static FrontierWorldState advanceMigration(FrontierWorldState state, ResidentMigrationAdvanced advanced) {
        ResidentMigrationJourney journey = requireJourney(state, advanced.residentId()); ActorLocation actor = state.actorLocations().get(advanced.residentId());
        if (journey.status() != ResidentMigrationStatus.EN_ROUTE || journey.arriving() || advanced.nextRouteIndex() <= journey.routeIndex()
                || advanced.nextRouteIndex() > journey.routeIndex() + ResidentMigrationJourney.MAX_COLD_ADVANCE_BLOCKS
                || advanced.nextRouteIndex() >= journey.route().size()
                || actor == null || !actor.position().equals(journey.currentPosition()) || !coldAvailable(state, advanced.residentId())) {
            throw new IllegalArgumentException("migration advancement lacks its exact COLD hand-off");
        }
        Map<SubjectId, ActorLocation> actors = new LinkedHashMap<>(state.actorLocations());
        actors.put(advanced.residentId(), actor.withPosition(journey.route().get(advanced.nextRouteIndex())));
        return copy(state, actors, state.humanPopulation().advanceMigration(advanced.residentId(), advanced.nextRouteIndex()));
    }

    static FrontierWorldState advanceMigrationHot(FrontierWorldState state, ResidentTransitAdvanced advanced) {
        ResidentMigrationJourney journey = requireJourney(state, advanced.residentId());
        ActorLocation actor = state.actorLocations().get(advanced.residentId()); AmbientActorLease lease = state.ambientLeases().get(advanced.residentId());
        if (journey.status() != ResidentMigrationStatus.EN_ROUTE || journey.arriving() || advanced.nextRouteIndex() != journey.nextRouteIndex()
                || actor == null || !actor.position().equals(journey.currentPosition()) || lease == null || lease.status() != AmbientLeaseStatus.HOT
                || lease.goal() != AmbientGoalKind.TRANSIT || !lease.goalPosition().equals(journey.nextColdPosition())) {
            throw new IllegalArgumentException("HOT transit observation lacks its exact leased segment");
        }
        Map<SubjectId, ActorLocation> actors = new LinkedHashMap<>(state.actorLocations());
        actors.put(advanced.residentId(), actor.withPosition(journey.nextColdPosition()));
        return copy(state, actors, state.humanPopulation().advanceMigration(advanced.residentId(), advanced.nextRouteIndex()));
    }

    static FrontierWorldState blockMigration(FrontierWorldState state, ResidentMigrationBlocked blocked) {
        ResidentMigrationJourney journey = requireJourney(state, blocked.residentId());
        if (journey.status() != ResidentMigrationStatus.EN_ROUTE || migrationBlockReason(state, journey) != blocked.reason()) {
            throw new IllegalArgumentException("migration block reason is not the current exact precondition failure");
        }
        return copy(state, state.actorLocations(), state.humanPopulation().blockMigration(blocked.residentId(), blocked.reason()));
    }

    static FrontierWorldState resumeMigration(FrontierWorldState state, ResidentMigrationResumed resumed) {
        ResidentMigrationJourney journey = requireJourney(state, resumed.residentId());
        if (journey.status() != ResidentMigrationStatus.BLOCKED || migrationBlockReason(state, journey) != null) {
            throw new IllegalArgumentException("migration may resume only after its current exact blockers clear");
        }
        return copy(state, state.actorLocations(), state.humanPopulation().resumeMigration(resumed.residentId()));
    }

    static FrontierWorldState cancelMigrationForDeath(FrontierWorldState state, SubjectId residentId) {
        return copy(state, state.actorLocations(), state.humanPopulation().cancelMigration(residentId));
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
                state.contracts(), state.operations(), state.logisticsHistory(), state.physicalIntents(), state.physicalObservations(), state.sceneLeases(), state.hiveColony(),
                state.structureDamage(), state.physicalDeltas(), state.ambientLeases(), state.routeConstructions(), state.routeTopology(), state.strategicPlans(), population, state.companies(), state.resourceSites());
    }

    private static ResidentMigrationJourney requireJourney(FrontierWorldState state, SubjectId residentId) {
        ResidentMigrationJourney journey = state.humanPopulation().migration(residentId);
        if (journey == null || state.humanPopulation().resident(residentId) == null) throw new IllegalArgumentException("migration has no exact resident journey");
        requireJourneyBounds(state, journey); return journey;
    }

    private static void requireJourneyBounds(FrontierWorldState state, ResidentMigrationJourney journey) {
        journey.route().forEach(position -> FrontierWorldStateSupport.requirePosition(state.bootstrap().bounds(), position));
    }

    private static boolean coldAvailable(FrontierWorldState state, SubjectId residentId) {
        return FrontierSceneAdmission.available(state, java.util.List.of(residentId))
                && !FrontierWorldStateSupport.activeOperationClaim(state, residentId)
                && !FrontierWorldStateSupport.activePatrolClaim(state, residentId);
    }

    static ResidentMigrationBlockReason migrationBlockReason(FrontierWorldState state, ResidentMigrationJourney journey) {
        ResidentProfile resident = state.humanPopulation().resident(journey.residentId());
        if (state.humanPopulation().quarantined(resident.settlementId()) || state.humanPopulation().quarantined(journey.destinationSettlementId())) {
            return ResidentMigrationBlockReason.QUARANTINE;
        }
        if (!hasReservedHousing(state, journey.destinationSettlementId())) {
            return ResidentMigrationBlockReason.DESTINATION_HOUSING_LOST;
        }
        if (!routePassable(state, journey)) return ResidentMigrationBlockReason.ROUTE_OBSTRUCTED;
        return null;
    }

    private static boolean routePassable(FrontierWorldState state, ResidentMigrationJourney journey) {
        return FrontierRouteNetwork.isPassable(state.bootstrap(), journey.route(), state.physicalDeltas());
    }

    private static boolean hasReservedHousing(FrontierWorldState state, SubjectId destinationSettlementId) {
        long occupants = SettlementFacilityCapability.livingResidents(state, destinationSettlementId);
        long reservations = state.humanPopulation().inboundHousingReservations(destinationSettlementId);
        long capacity = SettlementFacilityCapability.housingCapacity(state, destinationSettlementId);
        return occupants + reservations <= capacity;
    }

    private static boolean canReserveHousing(FrontierWorldState state, SubjectId destinationSettlementId) {
        long occupants = SettlementFacilityCapability.livingResidents(state, destinationSettlementId);
        long reservations = state.humanPopulation().inboundHousingReservations(destinationSettlementId);
        long capacity = SettlementFacilityCapability.housingCapacity(state, destinationSettlementId);
        return occupants + reservations < capacity;
    }
}
