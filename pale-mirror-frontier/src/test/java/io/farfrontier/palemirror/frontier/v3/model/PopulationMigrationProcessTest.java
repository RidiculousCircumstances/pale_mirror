package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngines;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
import io.farfrontier.palemirror.frontier.v3.kernel.WorkBudget;
import io.farfrontier.palemirror.frontier.v3.persistence.RecoveryImage;
import io.farfrontier.palemirror.frontier.v3.persistence.SnapshotRecord;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PopulationMigrationProcessTest {
    @Test
    void displacedResidentMovesOneColdWaypointAtATimeThenChangesHouseholdOnlyAtArrival() {
        FrontierWorldState state = displaced(); Settlement source = state.bootstrap().settlements().getFirst();
        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> review = PopulationMigrationProcess.planReview(state,
                PopulationMigrationProcess.review(1, 100L));
        ResidentMigrationStarted started = payload(review, ResidentMigrationStarted.class);
        state = HumanPopulationStateSupport.startMigration(state, started.journey());
        assertEquals(started.journey(), state.humanPopulation().migration(started.journey().residentId()));
        assertEquals(state, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state)));

        ResidentProfile before = state.humanPopulation().resident(started.journey().residentId());
        ScheduledAction progress = scheduled(review, "frontier.population.migration.progress");
        while (!state.humanPopulation().migration(before.id()).arriving()) {
            List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> events = PopulationMigrationProcess.planProgress(state, progress);
            ResidentMigrationAdvanced advanced = payload(events, ResidentMigrationAdvanced.class);
            ResidentMigrationJourney prior = state.humanPopulation().migration(before.id());
            state = HumanPopulationStateSupport.advanceMigration(state, advanced);
            assertEquals(prior.nextColdPosition(), state.actorLocations().get(before.id()).position());
            assertEquals(before, state.humanPopulation().resident(before.id()), "membership remains with the origin while in transit");
            progress = scheduled(events, "frontier.population.migration.progress");
        }

        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> arrived = PopulationMigrationProcess.planProgress(state, progress);
        ResidentMigrated migration = payload(arrived, ResidentMigrated.class);
        state = state.recordResidentMigration(migration);
        assertEquals(migration.destinationSettlementId(), state.humanPopulation().resident(before.id()).settlementId());
        assertEquals(migration.destinationHouseholdId(), state.humanPopulation().resident(before.id()).householdId());
        assertEquals(migration.destination(), state.actorLocations().get(before.id()).position());
        assertEquals(null, state.humanPopulation().migration(before.id()));
        assertTrue(!state.humanPopulation().residents().values().stream().anyMatch(person -> person.id().equals(before.id()) && person.settlementId().equals(source.id())));
    }

    @Test
    void quarantineBlocksTheSameJourneyWithoutMovingOrReassigningTheResident() {
        FrontierWorldState state = displaced();
        ResidentMigrationStarted started = payload(PopulationMigrationProcess.planReview(state, PopulationMigrationProcess.review(1, 100L)), ResidentMigrationStarted.class);
        state = HumanPopulationStateSupport.startMigration(state, started.journey());
        state = state.withHumanPopulation(state.humanPopulation().transitionQuarantine(started.journey().destinationSettlementId(), SettlementQuarantineStatus.QUARANTINED, 101L));
        ResidentProfile before = state.humanPopulation().resident(started.journey().residentId());
        BlockPosition position = state.actorLocations().get(before.id()).position();
        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> events = PopulationMigrationProcess.planProgress(state,
                scheduled(PopulationMigrationProcess.planReview(displaced(), PopulationMigrationProcess.review(1, 100L)), "frontier.population.migration.progress"));
        ResidentMigrationBlocked blocked = payload(events, ResidentMigrationBlocked.class);
        assertEquals(ResidentMigrationBlockReason.QUARANTINE, blocked.reason());
        state = HumanPopulationStateSupport.blockMigration(state, blocked);
        assertEquals(ResidentMigrationStatus.BLOCKED, state.humanPopulation().migration(before.id()).status());
        assertEquals(position, state.actorLocations().get(before.id()).position());
        assertEquals(before, state.humanPopulation().resident(before.id()));
    }

    @Test
    void restartPreservesAnExactInFlightJourneyAndItsDueProgressAction() {
        FrontierWorldState displaced = displaced();
        WorldId world = displaced.bootstrap().worldId();
        FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> base = FrontierWorldRuntimeDefinition.configuration(world, displaced.bootstrap().seed());
        FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> configuration = new FrontierEngineConfiguration<>(world, displaced,
                SimInstant.ZERO, base.commandPlanner(), base.scheduledPlanner(), base.reducer(), base.stateCodec(), base.projectionMapper(), base.limits(),
                List.of(PopulationMigrationProcess.review(1, 100L)), base.transactionCommitter());
        var uninterrupted = FrontierEngines.create(configuration);

        uninterrupted.advanceTo(new SimInstant(100L), new WorkBudget(8, 64));
        FrontierWorldState beforeRestart = decode(uninterrupted.checkpoint());
        ResidentMigrationJourney journey = beforeRestart.humanPopulation().migrations().values().stream().findFirst().orElseThrow();
        assertTrue(uninterrupted.checkpoint().schedules().stream().anyMatch(action -> action.id().equals(progressId(journey.residentId()))));

        var recovered = FrontierEngines.recover(configuration, new RecoveryImage(world,
                Optional.of(new SnapshotRecord(uninterrupted.checkpoint(), uninterrupted.checkpoint().revision().value())), List.of()));
        assertEquals(beforeRestart, decode(recovered.checkpoint()));
        assertEquals(uninterrupted.checkpoint().schedules(), recovered.checkpoint().schedules());

        recovered.advanceTo(new SimInstant(300L), new WorkBudget(8, 64));
        FrontierWorldState afterProgress = decode(recovered.checkpoint());
        ResidentMigrationJourney advanced = afterProgress.humanPopulation().migration(journey.residentId());
        assertEquals(journey.nextRouteIndex(), advanced.routeIndex());
        assertEquals(journey.nextColdPosition(), afterProgress.actorLocations().get(journey.residentId()).position());
    }

    @Test
    void reviewStartsBoundedParallelJourneysWithDistinctResidentsAndReservedBeds() {
        FrontierWorldState state = displaced();
        List<ResidentMigrationStarted> starts = PopulationMigrationProcess.planReview(state, PopulationMigrationProcess.review(1, 100L)).stream()
                .map(io.farfrontier.palemirror.frontier.v3.api.ProposedEvent::payload).filter(ResidentMigrationStarted.class::isInstance)
                .map(ResidentMigrationStarted.class::cast).toList();

        assertEquals(2, starts.size(), "one displaced settlement may hold only its bounded migration share");
        assertEquals(starts.size(), starts.stream().map(start -> start.journey().residentId()).collect(Collectors.toSet()).size());
        for (ResidentMigrationStarted start : starts) state = HumanPopulationStateSupport.startMigration(state, start.journey());
        for (Settlement settlement : state.bootstrap().settlements()) {
            if (state.humanPopulation().inboundHousingReservations(settlement.id()) == 0) continue;
            long committed = SettlementFacilityCapability.livingResidents(state, settlement.id())
                    + state.humanPopulation().inboundHousingReservations(settlement.id());
            assertTrue(committed <= SettlementFacilityCapability.housingCapacity(state, settlement.id()),
                    "every active journey owns one destination bed without oversubscription");
        }
        for (ResidentMigrationJourney journey : state.humanPopulation().migrations().values()) {
            assertTrue(journey.route().size() > 255, "the exact COLD route is no longer a coarse waypoint teleport");
            assertEquals(new ResidentMigrationStarted(journey), roundTrip(new ResidentMigrationStarted(journey)));
            assertEquals(new ResidentMigrationAdvanced(journey.residentId(), 512), roundTrip(new ResidentMigrationAdvanced(journey.residentId(), 512)));
            for (int index = 1; index < journey.route().size(); index++) {
                BlockPosition previous = journey.route().get(index - 1), current = journey.route().get(index);
                assertEquals(1, Math.abs(previous.x() - current.x()) + Math.abs(previous.z() - current.z()));
            }
        }
    }

    @Test
    void everyFiniteProfileSettlementCanCompileOneBoundedAdjacentTransitRoute() {
        FrontierWorldState initial = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:population-migration-profile"), 91L));
        for (Settlement source : initial.bootstrap().settlements()) {
            SubjectId housing = source.structures().stream().filter(structure -> structure.kind() == StructureKind.HOUSING).findFirst().orElseThrow().id();
            FrontierWorldState displaced = initial.withStructureCondition(housing, StructureCondition.DESTROYED);
            ResidentMigrationStarted started = payload(PopulationMigrationProcess.planReview(displaced, PopulationMigrationProcess.review(1, 100L)), ResidentMigrationStarted.class);
            assertTrue(started.journey().route().size() <= ResidentMigrationJourney.MAX_WAYPOINTS);
            assertEquals(started.journey().route().getFirst(), displaced.actorLocations().get(started.journey().residentId()).position());
        }
    }

    private static FrontierWorldState displaced() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:population-migration"), 91L));
        Settlement source = state.bootstrap().settlements().getFirst();
        SubjectId housing = source.structures().stream().filter(structure -> structure.kind() == StructureKind.HOUSING).findFirst().orElseThrow().id();
        return state.withStructureCondition(housing, StructureCondition.DESTROYED);
    }

    private static <T> T payload(List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> events, Class<T> type) {
        return events.stream().map(io.farfrontier.palemirror.frontier.v3.api.ProposedEvent::payload).filter(type::isInstance).map(type::cast).findFirst().orElseThrow();
    }

    private static ScheduledAction scheduled(List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> events, String kind) {
        return events.stream().map(io.farfrontier.palemirror.frontier.v3.api.ProposedEvent::payload).filter(ScheduleEffect.Created.class::isInstance)
                .map(ScheduleEffect.Created.class::cast).map(ScheduleEffect.Created::action).filter(action -> action.kind().equals(kind)).findFirst().orElseThrow();
    }

    private static io.farfrontier.palemirror.frontier.v3.api.ScheduleId progressId(SubjectId resident) {
        return new io.farfrontier.palemirror.frontier.v3.api.ScheduleId("schedule:resident-migration-progress-" + resident.value().substring("resident:".length()));
    }

    private static FrontierWorldState decode(io.farfrontier.palemirror.frontier.v3.api.CheckpointImage checkpoint) {
        return new FrontierWorldStateCodec().decode(checkpoint.canonicalState());
    }

    private static io.farfrontier.palemirror.frontier.v3.api.FrontierPayload roundTrip(io.farfrontier.palemirror.frontier.v3.api.FrontierPayload payload) {
        var codecs = FrontierWorldRuntimeDefinition.payloadCodecs();
        return codecs.decode(payload.type(), codecs.encode(payload));
    }
}
