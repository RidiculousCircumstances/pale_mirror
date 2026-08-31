package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.process.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.FixedScalar;
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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void activeEmploymentReservesTheExactResidentFromMigrationUntilItsOwnLifecycleEnds() {
        FrontierWorldState state = displaced(); Settlement source = state.bootstrap().settlements().getFirst();
        ResidentProfile founder = state.humanPopulation().residents().values().stream().filter(person -> person.settlementId().equals(source.id())
                && person.role() == ResidentRole.CRAFTER).sorted(java.util.Comparator.comparing(ResidentProfile::id)).findFirst().orElseThrow();
        Company company = new Company(CompanyFoundationProcess.companyId(source.id()), source.id(), founder.id(), CompanyPurpose.WORKS, CompanyStatus.ACTIVE, 1L);
        state = state.registerCompany(company);
        SubjectId reserved = state.humanPopulation().residents().values().stream().filter(person -> person.settlementId().equals(source.id()))
                .sorted(java.util.Comparator.comparing(ResidentProfile::id)).findFirst().orElseThrow().id();
        state = state.openEmployment(new EmploymentContract(new SubjectId("contract:employment-migration-reservation"), company.id(), reserved,
                FixedScalar.whole(2L), FixedScalar.ONE, EmploymentContractStatus.ACTIVE, 1L, 0L, FixedScalar.ZERO));

        List<ResidentMigrationStarted> starts = PopulationMigrationProcess.planReview(state, PopulationMigrationProcess.review(1, 100L)).stream()
                .map(io.farfrontier.palemirror.frontier.v3.api.ProposedEvent::payload).filter(ResidentMigrationStarted.class::isInstance)
                .map(ResidentMigrationStarted.class::cast).toList();
        assertTrue(starts.stream().noneMatch(start -> start.journey().residentId().equals(reserved)));
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
            var graybox = FrontierGrayboxPlan.compile(displaced).cells();
            assertTrue(started.journey().route().stream().anyMatch(position -> FrontierRouteNetwork.isSurfaceCell(displaced.bootstrap(), displaced.routeTopology(), position)),
                    "the migration corridor must use the visible route network instead of a hidden direct line");
            for (BlockPosition position : started.journey().route()) {
                assertTrue(clearOfStructuralGeometry(graybox, position),
                        () -> "migration corridor enters planned geometry at " + position);
                assertTrue(displaced.actorLocations().entrySet().stream()
                                .filter(entry -> !entry.getKey().equals(started.journey().residentId()))
                                .filter(entry -> entry.getValue().condition().status() == ActorLifeStatus.ALIVE)
                                .noneMatch(entry -> entry.getValue().position().equals(position)),
                        () -> "migration corridor enters another actor hand-off cell at " + position);
            }
        }
    }

    @Test
    void hotTransitAdvancesTheSameJourneyThenArrivesWithoutATeleportOrSecondLease() {
        FrontierWorldState state = displaced();
        ResidentMigrationStarted started = payload(PopulationMigrationProcess.planReview(state, PopulationMigrationProcess.review(1, 100L)), ResidentMigrationStarted.class);
        state = HumanPopulationStateSupport.startMigration(state, started.journey());
        SubjectId resident = started.journey().residentId();
        AmbientActorLease prepared = AmbientActorProcess.nextLease(state, resident, new SimInstant(101L));
        assertEquals(AmbientGoalKind.TRANSIT, prepared.goal());
        assertEquals(started.journey().nextColdPosition(), prepared.goalPosition());
        state = AmbientLeaseStateProcess.prepare(state, prepared);
        state = AmbientLeaseStateProcess.transition(state, resident, AmbientLeaseStatus.HOT);
        FrontierWorldState hot = state;
        assertThrows(IllegalArgumentException.class, () -> HumanPopulationStateSupport.advanceMigration(hot,
                new ResidentMigrationAdvanced(resident, started.journey().nextRouteIndex())), "COLD may not race a HOT lease");

        ResidentMigrationJourney before = state.humanPopulation().migration(resident);
        ResidentTransitAdvanced first = new ResidentTransitAdvanced(resident, before.nextRouteIndex());
        assertEquals(first, roundTrip(first));
        state = PopulationMigrationProcess.reduceHotAdvance(state, first);
        ResidentMigrationJourney after = state.humanPopulation().migration(resident);
        assertEquals(before.nextRouteIndex(), after.routeIndex());
        assertEquals(after.currentPosition(), state.actorLocations().get(resident).position());
        assertEquals(AmbientLeaseStatus.HOT, state.ambientLeases().get(resident).status());
        assertEquals(AmbientGoalKind.TRANSIT, state.ambientLeases().get(resident).goal());
        assertEquals(after.nextColdPosition(), state.ambientLeases().get(resident).goalPosition());

        while (state.humanPopulation().migration(resident) != null) {
            ResidentMigrationJourney journey = state.humanPopulation().migration(resident);
            state = PopulationMigrationProcess.reduceHotAdvance(state, new ResidentTransitAdvanced(resident, journey.nextRouteIndex()));
        }
        assertEquals(started.journey().destinationSettlementId(), state.humanPopulation().resident(resident).settlementId());
        assertEquals(started.journey().route().getLast(), state.actorLocations().get(resident).position());
        assertNotEquals(AmbientGoalKind.TRANSIT, state.ambientLeases().get(resident).goal());
    }

    @Test
    void deathOfAHOTTransitResidentCancelsTheSameReservationInsteadOfLeavingAnOrphanedJourney() {
        FrontierWorldState state = displaced();
        ResidentMigrationStarted started = payload(PopulationMigrationProcess.planReview(state, PopulationMigrationProcess.review(1, 100L)), ResidentMigrationStarted.class);
        state = HumanPopulationStateSupport.startMigration(state, started.journey());
        SubjectId resident = started.journey().residentId();
        state = AmbientLeaseStateProcess.prepare(state, AmbientActorProcess.nextLease(state, resident, new SimInstant(101L)));
        state = AmbientLeaseStateProcess.transition(state, resident, AmbientLeaseStatus.HOT);
        state = AmbientLeaseStateProcess.recordDeath(state, new AmbientActorDied(resident, state.actorLocations().get(resident).position(), "test:transit-death"));

        assertEquals(null, state.humanPopulation().migration(resident));
        assertEquals(0L, state.humanPopulation().inboundHousingReservations(started.journey().destinationSettlementId()));
        assertEquals(ActorLifeStatus.DEAD, state.actorLocations().get(resident).condition().status());
        assertEquals(AmbientLeaseStatus.CLOSED, state.ambientLeases().get(resident).status());
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

    private static boolean clearOfStructuralGeometry(java.util.Map<BlockPosition, GrayboxCell> cells, BlockPosition position) {
        return java.util.stream.Stream.of(position, position.offset(0, 1, 0), position.offset(0, 2, 0))
                .map(cells::get).filter(java.util.Objects::nonNull).noneMatch(cell -> cell.semanticPart() != GrayboxSemanticPart.ROUTE_SURFACE);
    }
}
