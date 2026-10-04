package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Pure atomic transitions that couple the exact-person register to the actor index. */
public final class HumanPopulationStateSupport {
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
        if (journey == null || !journey.executionId().equals(migration.executionId()))
            throw new IllegalArgumentException("migration completion has stale or foreign execution identity");
        state.actorExecutions().requireCurrent(migration.executionId());
        if (journey == null || !journey.arriving() || !actor.supportingSurface().support().equals(journey.currentPosition())
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
        var permissions = SettlementWorkPolicy.permissions(state, current.settlementId()).withoutResident(migration.residentId());
        return state.withChanges(FrontierWorldStateUpdate.begin()
                .humanPopulation(state.humanPopulation().completeMigration(migration.residentId(),
                        migration.destinationHouseholdId(), migration.destinationSettlementId()))
                .actorExecutions(state.actorExecutions().finish(journey.executionId()))
                .strategicPlans(state.strategicPlans().withWorkPermissions(current.settlementId(), permissions)));
    }

    public static FrontierWorldState startMigration(FrontierWorldState state, ResidentMigrationJourney journey) {
        Objects.requireNonNull(journey, "migration journey");
        ActorLocation actor = state.actorLocations().get(journey.residentId()); ResidentProfile resident = state.humanPopulation().resident(journey.residentId());
        if (journey.routeRevision() != 1 || journey.spatial().pending()
                || actor == null || actor.condition().status() != ActorLifeStatus.ALIVE || resident == null || !resident.settlementId().equals(journey.originSettlementId())
                || !actor.supportingSurface().support().equals(journey.currentPosition()) || !coldAvailable(state, journey.residentId())) {
            throw new IllegalArgumentException("migration journey must start from one available living COLD resident");
        }
        if (!canReserveHousing(state, journey.destinationSettlementId())) {
            throw new IllegalArgumentException("migration journey requires one reservable destination housing place");
        }
        requireJourneyBounds(state, journey);
        return ActorExecutionComposition.LIFECYCLE.prepareVacant(state, journey.executionId()).commit(state,
                FrontierWorldStateUpdate.begin().humanPopulation(state.humanPopulation().startMigration(journey)));
    }

    public static FrontierWorldState advanceMigration(FrontierWorldState state, ResidentMigrationAdvanced advanced) {
        ResidentMigrationJourney journey = requireJourney(state, advanced.residentId(), advanced.executionId()); ActorLocation actor = state.actorLocations().get(advanced.residentId());
        if (journey.routeRevision() != advanced.routeRevision() || journey.spatial().pending()
                || journey.status() != ResidentMigrationStatus.EN_ROUTE || journey.arriving() || advanced.nextRouteIndex() <= journey.routeIndex()
                || advanced.nextRouteIndex() > journey.routeIndex() + ResidentMigrationJourney.MAX_COLD_ADVANCE_BLOCKS
                || advanced.nextRouteIndex() >= journey.route().size()
                || actor == null || !actor.supportingSurface().support().equals(journey.currentPosition()) || !coldAvailable(state, advanced.residentId())
                || !KnownPedestrianRouteKnowledge.forFrontier(state).traversable(journey.route()
                    .subList(journey.routeIndex(), advanced.nextRouteIndex() + 1).stream().map(SurfaceAnchor::new).toList())) {
            throw new IllegalArgumentException("migration advancement lacks its exact COLD hand-off");
        }
        Map<SubjectId, ActorLocation> actors = new LinkedHashMap<>(state.actorLocations());
        actors.put(advanced.residentId(), actor.withBody(BodyPosition.above(new SurfaceAnchor(journey.route().get(advanced.nextRouteIndex())))));
        return copy(state, actors, state.humanPopulation().advanceMigration(advanced.residentId(), advanced.nextRouteIndex()));
    }

    public static FrontierWorldState advanceMigrationRejoin(FrontierWorldState state, ResidentMigrationRejoinAdvanced advanced) {
        var journey = requireJourney(state, advanced.residentId(), advanced.executionId());
        var actor = state.actorLocations().get(advanced.residentId());
        if (journey.routeRevision() != advanced.routeRevision() || !journey.spatial().pending()
                || journey.status() != ResidentMigrationStatus.EN_ROUTE || !coldAvailable(state, advanced.residentId())
                || actor == null || !actor.supportingSurface().support().equals(journey.currentPosition()))
            throw new IllegalArgumentException("migration rejoin lacks its exact COLD pose, revision or ownership");
        var approach = ResidentMigrationJourneyKnowledge.approach(state, journey).orElseThrow(
                () -> new IllegalArgumentException("migration rejoin still lacks known geometry"));
        if (!KnownPedestrianRouteKnowledge.forFrontier(state).traversable(
                approach.path().subList(approach.cursor(), approach.path().size())))
            throw new IllegalArgumentException("migration rejoin has a known blocked edge");
        var replacement = journey.advanceRejoin(approach, advanced.nextRejoinCursor());
        var actors = new LinkedHashMap<>(state.actorLocations());
        actors.put(advanced.residentId(), actor.withBody(BodyPosition.aboveSupportCell(replacement.currentPosition())));
        return copy(state, actors, state.humanPopulation().replaceMigration(journey, replacement));
    }

    public static FrontierWorldState advanceMigrationHot(FrontierWorldState state, ResidentTransitAdvanced advanced) {
        ResidentMigrationJourney journey = requireJourney(state, advanced.residentId(), advanced.executionId());
        ActorLocation actor = state.actorLocations().get(advanced.residentId()); AmbientActorLease lease = state.ambientLeases().get(advanced.residentId());
        var body = ActorBodyAuthority.require(state, advanced.bodyId());
        if (journey.routeRevision() != advanced.routeRevision() || body.phase() != FencedRecoveryPhase.RUNNING
                || journey.status() != ResidentMigrationStatus.EN_ROUTE || journey.arriving() || advanced.nextRouteIndex() != journey.nextRouteIndex()
                || actor == null || !actor.supportingSurface().support().equals(journey.nextColdPosition())
                || lease == null || lease.revision() != advanced.leaseRevision() || lease.status() != AmbientLeaseStatus.HOT
                || lease.goal() != AmbientGoalKind.TRANSIT || !lease.goalBody().supportingSurface().support().equals(journey.nextColdPosition())) {
            throw new IllegalArgumentException("HOT transit observation lacks its exact leased segment");
        }
        return state.withChanges(FrontierWorldStateUpdate.begin().humanPopulation(
                state.humanPopulation().replaceMigration(journey, journey.arrivedHot(advanced.nextRouteIndex()))));
    }

    public static FrontierWorldState blockMigration(FrontierWorldState state, ResidentMigrationBlocked blocked) {
        ResidentMigrationJourney journey = requireJourney(state, blocked.residentId(), blocked.executionId());
        if (journey.status() != ResidentMigrationStatus.EN_ROUTE || migrationBlockReason(state, journey) != blocked.reason()) {
            throw new IllegalArgumentException("migration block reason is not the current exact precondition failure");
        }
        return copy(state, state.actorLocations(), state.humanPopulation().blockMigration(blocked.residentId(), blocked.reason()));
    }

    public static FrontierWorldState resumeMigration(FrontierWorldState state, ResidentMigrationResumed resumed) {
        ResidentMigrationJourney journey = requireJourney(state, resumed.residentId(), resumed.executionId());
        if (journey.status() != ResidentMigrationStatus.BLOCKED || migrationBlockReason(state, journey) != null) {
            throw new IllegalArgumentException("migration may resume only after its current exact blockers clear");
        }
        return copy(state, state.actorLocations(), state.humanPopulation().resumeMigration(resumed.residentId()));
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
        var actors = new LinkedHashMap<>(state.actorLocations()); actors.put(job.resident().id(), ActorLocation.standingOn(new SurfaceAnchor(job.position()), ActorKind.RESIDENT));
        return copy(state, actors, state.humanPopulation().completeBirth(job.id(), state.bootstrap().ruleset().residentLife()));
    }

    static FrontierWorldState cancelBirth(FrontierWorldState state, SubjectId jobId) {
        return copy(state, state.actorLocations(), state.humanPopulation().cancelBirth(jobId));
    }

    private static FrontierWorldState copy(FrontierWorldState state, java.util.Map<SubjectId, ActorLocation> actors, HumanPopulation population) {
        return state.withChanges(FrontierWorldStateUpdate.begin().actorLocations(actors).humanPopulation(population));
    }

    private static ResidentMigrationJourney requireJourney(FrontierWorldState state, SubjectId residentId,
            io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId execution) {
        ResidentMigrationJourney journey = state.humanPopulation().migration(residentId);
        if (journey == null || state.humanPopulation().resident(residentId) == null) throw new IllegalArgumentException("migration has no exact resident journey");
        if (!journey.executionId().equals(execution)) throw new IllegalArgumentException("migration input has stale execution identity");
        state.actorExecutions().requireCurrent(execution);
        requireJourneyBounds(state, journey); return journey;
    }

    private static void requireJourneyBounds(FrontierWorldState state, ResidentMigrationJourney journey) {
        journey.route().forEach(position -> FrontierWorldStateSupport.requirePosition(state.bootstrap().bounds(), position));
        journey.rejoin().ifPresent(approach -> approach.path().forEach(surface ->
                FrontierWorldStateSupport.requirePosition(state.bootstrap().bounds(), surface.support())));
        journey.spatial().waitingOrigin().ifPresent(surface ->
                FrontierWorldStateSupport.requirePosition(state.bootstrap().bounds(), surface.support()));
    }

    private static boolean coldAvailable(FrontierWorldState state, SubjectId residentId) {
        return FrontierSceneAdmission.available(state, java.util.List.of(residentId))
                && !FrontierWorldStateSupport.activeOperationClaim(state, residentId)
                && !FrontierWorldStateSupport.activePatrolClaim(state, residentId);
    }

    public static ResidentMigrationBlockReason migrationBlockReason(FrontierWorldState state, ResidentMigrationJourney journey) {
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
        int remaining = journey.spatial().pending() ? journey.nextRouteIndex() : journey.routeIndex();
        if (!FrontierRouteNetwork.isPassable(state.bootstrap(), journey.route().subList(remaining, journey.route().size()), state.physicalDeltas())) return false;
        if (!KnownPedestrianRouteKnowledge.forFrontier(state).traversable(journey.route()
                .subList(remaining, journey.route().size()).stream().map(SurfaceAnchor::new).toList())) return false;
        return journey.rejoin().map(approach -> KnownPedestrianRouteKnowledge.forFrontier(state)
                .traversable(approach.path().subList(approach.cursor(), approach.path().size()))).orElse(true);
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
