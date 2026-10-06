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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PopulationMigrationProcessTest {
    @Test void aForeignGenerationCannotAdvanceTheSameResidentAndCursor() {
        var state = displaced();
        var started = payload(PopulationMigrationProcess.planReview(state,
                PopulationMigrationProcess.review(1, 100L)), ResidentMigrationStarted.class);
        state = HumanPopulationStateSupport.startMigration(state, started.journey());
        var id = started.journey().executionId();
        var foreign = new io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId(
                id.actorId(), id.activityKind(), id.activityOwnerId(), id.generation() + 1L);
        var before = state;
        assertThrows(IllegalArgumentException.class, () -> HumanPopulationStateSupport.advanceMigration(before,
                new ResidentMigrationAdvanced(id.actorId(), started.journey().nextRouteIndex(), started.journey().routeRevision(), foreign)));
        assertEquals(started.journey(), before.humanPopulation().migration(id.actorId()));
        before.actorExecutions().requireCurrent(id);
    }
    @Test
    void displacedResidentMovesOneColdWaypointAtATimeThenChangesHouseholdOnlyAtArrival() {
        FrontierWorldState state = displaced(); Settlement source = state.bootstrap().settlements().getFirst();
        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> review = PopulationMigrationProcess.planReview(state,
                PopulationMigrationProcess.review(1, 100L));
        ResidentMigrationStarted started = payload(review, ResidentMigrationStarted.class);
        state = HumanPopulationStateSupport.startMigration(state, started.journey());
        ResidentProfile selected = state.humanPopulation().resident(started.journey().residentId());
        ResidentCharacteristics changedRate = selected.characteristics().withBaseMetabolism(1_500);
        state = state.withHumanPopulation(state.humanPopulation().changeMetabolism(selected.id(),
                selected.characteristics(), changedRate, 100L, state.bootstrap().ruleset().residentLife()));
        ResidentNutrition needBeforeMigration = state.humanPopulation().nutrition(selected.id());
        assertTrue(needBeforeMigration.fractionalProgress() > 0);
        assertEquals(started.journey(), state.humanPopulation().migration(started.journey().residentId()));
        assertEquals(state, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state)));

        ResidentProfile before = state.humanPopulation().resident(started.journey().residentId());
        ScheduledAction progress = scheduled(review, "frontier.population.migration.progress");
        while (!state.humanPopulation().migration(before.id()).arriving()) {
            List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> events = PopulationMigrationProcess.planProgress(state, progress);
            ResidentMigrationAdvanced advanced = payload(events, ResidentMigrationAdvanced.class);
            ResidentMigrationJourney prior = state.humanPopulation().migration(before.id());
            state = HumanPopulationStateSupport.advanceMigration(state, advanced);
            assertEquals(prior.nextColdPosition(), state.actorLocations().get(before.id()).supportingSurface().support());
            assertEquals(before, state.humanPopulation().resident(before.id()), "membership remains with the origin while in transit");
            progress = scheduled(events, "frontier.population.migration.progress");
        }

        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> arrived = PopulationMigrationProcess.planProgress(state, progress);
        ResidentMigrated migration = payload(arrived, ResidentMigrated.class);
        state = state.recordResidentMigration(migration);
        assertEquals(migration.destinationSettlementId(), state.humanPopulation().resident(before.id()).settlementId());
        assertEquals(migration.destinationHouseholdId(), state.humanPopulation().resident(before.id()).householdId());
        assertEquals(changedRate, state.humanPopulation().resident(before.id()).characteristics(),
                "migration must retain this resident's typed physiological characteristics");
        assertEquals(needBeforeMigration, state.humanPopulation().nutrition(before.id()),
                "migration must retain fractional need progress rather than restarting hunger");
        assertEquals(migration.destination(), state.actorLocations().get(before.id()).supportingSurface().support());
        assertEquals(null, state.humanPopulation().migration(before.id()));
        assertTrue(state.actorExecutions().actors().get(before.id()).current().isEmpty());
        assertTrue(!state.humanPopulation().residents().values().stream().anyMatch(person -> person.id().equals(before.id()) && person.settlementId().equals(source.id())));
    }

    @Test
    void closingPresentationMidTransitRetainsObservedBodyAndDoesNotGrantColdOrArrival() {
        var initial = displaced();
        var started = payload(PopulationMigrationProcess.planReview(initial, PopulationMigrationProcess.review(1, 100L)), ResidentMigrationStarted.class);
        var state = HumanPopulationStateSupport.startMigration(initial, started.journey());
        var journey = started.journey();
        var actor = journey.residentId();
        var lease = AmbientActorProcess.nextLease(state, actor, new SimInstant(100L));
        state = AmbientLeaseStateProcess.prepare(state, lease);
        state = ModeledActorBodyFacts.present(state, actor);
        state = AmbientLeaseStateProcess.transition(state, actor, AmbientLeaseStatus.HOT);
        var body = io.farfrontier.palemirror.frontier.v3.model.ActorBodyAuthority.current(state, actor);
        var origin = state.actorLocations().get(actor);
        var observed = BodyPosition.aboveSupportCell(journey.route().get(1));
        state = ActorBodyAuthority.inspected(state,
                new io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyInspected(body,
                        io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyInspected.Source.INDEXED_LIVING,
                        origin.body(), origin.condition().health(), observed, origin.condition().health(), Optional.of(journey.executionId())));
        var draining = AmbientLeaseStateProcess.transition(state, actor, AmbientLeaseStatus.DRAINING);
        var closed = AmbientLeaseStateProcess.release(draining, new AmbientLeaseReleased(actor, observed, origin.condition().health()));
        assertEquals(AmbientLeaseStatus.CLOSED, closed.ambientLeases().get(actor).status());
        assertEquals(observed, closed.actorLocations().get(actor).body());
        assertSame(draining.humanPopulation(), closed.humanPopulation());
        assertSame(draining.actorExecutions(), closed.actorExecutions());
        assertSame(draining.inventory(), closed.inventory());
        assertEquals(journey, closed.humanPopulation().migration(actor));
        assertEquals(body, ActorBodyAuthority.current(closed, actor));
        assertFalse(ActorExecutionCoordinator.coldAvailable(closed, actor));
        var unloaded = ActorBodyAuthority.unloaded(closed,
                new io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyUnloaded(body, observed,
                        origin.condition().health(), observed, origin.condition().health(), Optional.of(journey.executionId())));
        var rebased = unloaded.humanPopulation().migration(actor);
        assertEquals(observed, unloaded.actorLocations().get(actor).body());
        assertEquals(journey.route(), rebased.route(), "the retained strategic corridor is not rewritten");
        assertEquals(journey.routeIndex(), rebased.routeIndex(), "departure does not award semantic arrival");
        assertEquals(journey.nextColdPosition(), rebased.rejoin().orElseThrow().target().support());
        assertEquals(journey.routeRevision() + 1, rebased.routeRevision());
        assertSame(closed.actorExecutions(), unloaded.actorExecutions());
        assertSame(closed.inventory(), unloaded.inventory());
        assertTrue(ActorExecutionCoordinator.coldAvailable(unloaded, actor));
        assertThrows(IllegalArgumentException.class, () -> HumanPopulationStateSupport.advanceMigration(unloaded,
                new ResidentMigrationAdvanced(actor, journey.nextRouteIndex(), journey.routeRevision(), journey.executionId())),
                "old COLD callbacks cannot consume the changed physical continuation");
        var codec = new FrontierWorldStateCodec();
        assertEquals(closed, codec.decode(codec.encode(closed)));
        assertEquals(unloaded, codec.decode(codec.encode(unloaded)));
        var review = PopulationMigrationProcess.planReview(initial, PopulationMigrationProcess.review(1, 100L));
        var due = scheduled(review, "frontier.population.migration.progress");
        var coldEvents = PopulationMigrationProcess.planProgress(unloaded, due);
        var rejoin = payload(coldEvents, ResidentMigrationRejoinAdvanced.class);
        assertEquals(rejoin, roundTrip(rejoin));
        var continued = HumanPopulationStateSupport.advanceMigrationRejoin(unloaded, rejoin);
        assertEquals(journey.nextRouteIndex(), continued.humanPopulation().migration(actor).routeIndex());
        assertTrue(continued.humanPopulation().migration(actor).rejoin().isEmpty());
        assertEquals(journey.nextColdPosition(), continued.actorLocations().get(actor).supportingSurface().support());
        assertThrows(IllegalArgumentException.class, () -> HumanPopulationStateSupport.advanceMigrationRejoin(continued, rejoin));
        assertEquals(continued, codec.decode(codec.encode(continued)));
        var next = payload(PopulationMigrationProcess.planProgress(continued,
                scheduled(coldEvents, "frontier.population.migration.progress")), ResidentMigrationAdvanced.class);
        var progressed = HumanPopulationStateSupport.advanceMigration(continued, next);
        assertTrue(progressed.humanPopulation().migration(actor).routeIndex() > journey.nextRouteIndex());

        // Same real common-departure boundary, now with the unfinished target physically lost.
        var damaged = closed.recordPhysicalDelta(new PhysicalDelta(journey.nextColdPosition().offset(0, 1, 0),
                PhysicalDeltaKind.UNKNOWN_SCAR, Optional.empty(), Optional.empty(), "test:migration-blocked-checkpoint"));
        var held = ModeledActorBodyFacts.unloaded(damaged, actor);
        var heldJourney = held.humanPopulation().migration(actor);
        assertEquals(observed.supportingSurface(), heldJourney.spatial().waitingOrigin().orElseThrow());
        assertEquals(journey.routeIndex(), heldJourney.routeIndex());
        assertEquals(journey.route(), heldJourney.route());
        assertEquals(observed, held.actorLocations().get(actor).body());
        assertEquals(held, codec.decode(codec.encode(held)));
        assertTrue(ResidentMigrationJourneyKnowledge.approach(held, heldJourney).isEmpty());
        assertEquals(ResidentMigrationBlockReason.ROUTE_OBSTRUCTED,
                payload(PopulationMigrationProcess.planProgress(held, due), ResidentMigrationBlocked.class).reason());
        var repaired = held.withChanges(FrontierWorldStateUpdate.begin().physicalDeltas(java.util.Map.of()));
        var refreshed = ResidentMigrationJourneyKnowledge.approach(repaired, heldJourney).orElseThrow();
        assertEquals(observed.supportingSurface(), refreshed.current());
        assertEquals(new SurfaceAnchor(journey.nextColdPosition()), refreshed.target());
        assertSame(held.humanPopulation(), repaired.humanPopulation(), "reading a HOT hint cannot replay/adopt canonical work");
        var resumed = payload(PopulationMigrationProcess.planProgress(repaired, due), ResidentMigrationRejoinAdvanced.class);
        assertEquals(resumed, roundTrip(resumed));
        var reached = HumanPopulationStateSupport.advanceMigrationRejoin(repaired, resumed);
        assertEquals(journey.nextRouteIndex(), reached.humanPopulation().migration(actor).routeIndex());
        assertFalse(reached.humanPopulation().migration(actor).spatial().pending());
        assertEquals(reached, codec.decode(codec.encode(reached)));
    }

    @Test
    void exactTransitOwnerSettlesDeathWithoutSceneOrGenericMigrationDiscovery() {
        var initial = displaced();
        var started = payload(PopulationMigrationProcess.planReview(initial, PopulationMigrationProcess.review(1, 100L)), ResidentMigrationStarted.class);
        var state = HumanPopulationStateSupport.startMigration(initial, started.journey());
        var actor = started.journey().residentId();
        state = ActorBodyAuthority.demand(state, actor);
        state = ModeledActorBodyFacts.present(state, actor);
        var body = ActorBodyAuthority.current(state, actor);
        var basis = state;
        var dead = ModeledActorBodyFacts.died(state, actor, state.actorLocations().get(actor).body(), "migration-test-fatality", 101L);
        assertEquals(ActorLifeStatus.DEAD, dead.actorLocations().get(actor).condition().status());
        assertEquals(null, dead.humanPopulation().migration(actor));
        assertTrue(dead.actorExecutions().actors().get(actor).current().isEmpty());
        assertEquals(basis.actorExecutions().generation(actor), dead.actorExecutions().generation(actor));
        assertSame(basis.inventory(), dead.inventory());
        assertEquals(basis.humanPopulation().resident(actor), dead.humanPopulation().resident(actor));
        assertFalse(ActorBodyAuthority.retainsPhysicalCustody(dead, actor));
        assertTrue(dead.fencedRecovery().tombstones().containsKey(
                io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId.recoveryBindingId(body.actorId())));
        assertThrows(IllegalArgumentException.class, () -> HumanPopulationStateSupport.advanceMigration(dead,
                new ResidentMigrationAdvanced(actor, started.journey().nextRouteIndex(), started.journey().routeRevision(), started.journey().executionId())));
        var codec = new FrontierWorldStateCodec();
        assertEquals(dead, codec.decode(codec.encode(dead)));
    }

    @Test
    void quarantineBlocksTheSameJourneyWithoutMovingOrReassigningTheResident() {
        FrontierWorldState state = displaced();
        ResidentMigrationStarted started = payload(PopulationMigrationProcess.planReview(state, PopulationMigrationProcess.review(1, 100L)), ResidentMigrationStarted.class);
        state = HumanPopulationStateSupport.startMigration(state, started.journey());
        state = state.withHumanPopulation(state.humanPopulation().transitionQuarantine(started.journey().destinationSettlementId(), SettlementQuarantineStatus.QUARANTINED, 101L));
        ResidentProfile before = state.humanPopulation().resident(started.journey().residentId());
        BlockPosition position = state.actorLocations().get(before.id()).supportingSurface().support();
        List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> events = PopulationMigrationProcess.planProgress(state,
                scheduled(PopulationMigrationProcess.planReview(displaced(), PopulationMigrationProcess.review(1, 100L)), "frontier.population.migration.progress"));
        ResidentMigrationBlocked blocked = payload(events, ResidentMigrationBlocked.class);
        assertEquals(ResidentMigrationBlockReason.QUARANTINE, blocked.reason());
        state = HumanPopulationStateSupport.blockMigration(state, blocked);
        assertEquals(ResidentMigrationStatus.BLOCKED, state.humanPopulation().migration(before.id()).status());
        assertEquals(position, state.actorLocations().get(before.id()).supportingSurface().support());
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
        assertTrue(uninterrupted.checkpoint().schedules().stream().anyMatch(action -> action.id().equals(progressId(journey))));

        var recovered = FrontierEngines.recover(configuration, new RecoveryImage(world,
                Optional.of(new SnapshotRecord(uninterrupted.checkpoint(), uninterrupted.checkpoint().revision().value())), List.of()));
        assertEquals(beforeRestart, decode(recovered.checkpoint()));
        assertEquals(uninterrupted.checkpoint().schedules(), recovered.checkpoint().schedules());

        recovered.advanceTo(new SimInstant(300L), new WorkBudget(8, 64));
        FrontierWorldState afterProgress = decode(recovered.checkpoint());
        ResidentMigrationJourney advanced = afterProgress.humanPopulation().migration(journey.residentId());
        assertEquals(journey.nextRouteIndex(), advanced.routeIndex());
        assertEquals(journey.nextColdPosition(), afterProgress.actorLocations().get(journey.residentId()).supportingSurface().support());
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
            var advanced = new ResidentMigrationAdvanced(journey.residentId(), 512, journey.routeRevision(), journey.executionId());
            assertEquals(advanced, roundTrip(advanced));
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
        state = state.openEmployment(new EmploymentContract(new SubjectId("contract:employment-migration-reservation"), WorkEmployer.company(company), reserved,
                FixedScalar.whole(2L), EmploymentContractStatus.ACTIVE, 1L, 0L));

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
            assertEquals(started.journey().route().getFirst(), displaced.actorLocations().get(started.journey().residentId()).supportingSurface().support());
            var graybox = FrontierGrayboxPlan.compile(displaced).cells();
            assertTrue(started.journey().route().stream().anyMatch(position -> FrontierRouteNetwork.isSurfaceCell(displaced.bootstrap(), displaced.routeTopology(), position)),
                    "the migration corridor must use the visible route network instead of a hidden direct line");
            for (BlockPosition position : started.journey().route()) {
                assertTrue(clearOfStructuralGeometry(graybox, position),
                        () -> "migration corridor enters planned geometry at " + position);
                assertTrue(displaced.actorLocations().entrySet().stream()
                                .filter(entry -> !entry.getKey().equals(started.journey().residentId()))
                                .filter(entry -> entry.getValue().condition().status() == ActorLifeStatus.ALIVE)
                                .noneMatch(entry -> entry.getValue().supportingSurface().support().equals(position)),
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
        assertEquals(started.journey().nextColdPosition(), prepared.goalBody().supportingSurface().support());
        state = AmbientLeaseStateProcess.prepare(state, prepared);
        state = ModeledActorBodyFacts.present(state, resident);
        state = AmbientLeaseStateProcess.transition(state, resident, AmbientLeaseStatus.HOT);
        FrontierWorldState hot = state;
        assertThrows(IllegalArgumentException.class, () -> HumanPopulationStateSupport.advanceMigration(hot,
                new ResidentMigrationAdvanced(resident, started.journey().nextRouteIndex(), started.journey().routeRevision(), started.journey().executionId())), "COLD may not race a HOT lease");

        ResidentMigrationJourney before = state.humanPopulation().migration(resident);
        ResidentTransitAdvanced first = transitReceipt(state, before);
        assertEquals(first, roundTrip(first));
        var beforeObservation = state;
        assertThrows(IllegalArgumentException.class, () -> PopulationMigrationProcess.reduceHotAdvance(beforeObservation, first, 101L),
                "family arrival cannot fabricate a physical position");
        state = observeTransitArrival(state, before);
        var observedState = state;
        var wrongBody = new io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId(resident, first.bodyId().physicalEpoch() + 1);
        assertThrows(IllegalArgumentException.class, () -> PopulationMigrationProcess.reduceHotAdvance(observedState,
                new ResidentTransitAdvanced(resident, first.nextRouteIndex(), first.routeRevision(), first.leaseRevision(), first.executionId(), wrongBody), 101L));
        assertThrows(IllegalArgumentException.class, () -> PopulationMigrationProcess.reduceHotAdvance(observedState,
                new ResidentTransitAdvanced(resident, first.nextRouteIndex(), first.routeRevision() + 1, first.leaseRevision(), first.executionId(), first.bodyId()), 101L));
        assertThrows(IllegalArgumentException.class, () -> PopulationMigrationProcess.reduceHotAdvance(observedState,
                new ResidentTransitAdvanced(resident, first.nextRouteIndex(), first.routeRevision(), first.leaseRevision() + 1, first.executionId(), first.bodyId()), 101L));
        state = PopulationMigrationProcess.reduceHotAdvance(state, first, 101L);
        ResidentMigrationJourney after = state.humanPopulation().migration(resident);
        assertEquals(before.nextRouteIndex(), after.routeIndex());
        assertEquals(after.currentPosition(), state.actorLocations().get(resident).supportingSurface().support());
        assertEquals(AmbientLeaseStatus.HOT, state.ambientLeases().get(resident).status());
        assertEquals(AmbientGoalKind.TRANSIT, state.ambientLeases().get(resident).goal());
        assertEquals(after.nextColdPosition(), state.ambientLeases().get(resident).goalBody().supportingSurface().support());

        while (state.humanPopulation().migration(resident) != null) {
            ResidentMigrationJourney journey = state.humanPopulation().migration(resident);
            var receipt = transitReceipt(state, journey);
            state = observeTransitArrival(state, journey);
            state = PopulationMigrationProcess.reduceHotAdvance(state, receipt, 101L);
        }
        assertEquals(started.journey().destinationSettlementId(), state.humanPopulation().resident(resident).settlementId());
        assertEquals(started.journey().route().getLast(), state.actorLocations().get(resident).supportingSurface().support());
        assertNotEquals(AmbientGoalKind.TRANSIT, state.ambientLeases().get(resident).goal());
    }

    private static ResidentTransitAdvanced transitReceipt(FrontierWorldState state, ResidentMigrationJourney journey) {
        return new ResidentTransitAdvanced(journey.residentId(), journey.nextRouteIndex(), journey.routeRevision(),
                state.ambientLeases().get(journey.residentId()).revision(), journey.executionId(),
                ActorBodyAuthority.current(state, journey.residentId()));
    }
    private static FrontierWorldState observeTransitArrival(FrontierWorldState state, ResidentMigrationJourney journey) {
        var location = state.actorLocations().get(journey.residentId());
        return ActorBodyAuthority.inspected(state, new io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyInspected(
                ActorBodyAuthority.current(state, journey.residentId()),
                io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyInspected.Source.INDEXED_LIVING,
                location.body(), location.condition().health(), BodyPosition.aboveSupportCell(journey.nextColdPosition()),
                location.condition().health(), Optional.of(journey.executionId())));
    }

    @Test
    void deathOfAHOTTransitResidentCancelsTheSameReservationInsteadOfLeavingAnOrphanedJourney() {
        FrontierWorldState state = displaced();
        ResidentMigrationStarted started = payload(PopulationMigrationProcess.planReview(state, PopulationMigrationProcess.review(1, 100L)), ResidentMigrationStarted.class);
        state = HumanPopulationStateSupport.startMigration(state, started.journey());
        SubjectId resident = started.journey().residentId();
        state = AmbientLeaseStateProcess.prepare(state, AmbientActorProcess.nextLease(state, resident, new SimInstant(101L)));
        state = ModeledActorBodyFacts.present(state, resident);
        state = AmbientLeaseStateProcess.transition(state, resident, AmbientLeaseStatus.HOT);
        state = ModeledActorBodyFacts.died(state, resident, state.actorLocations().get(resident).body(), "test:transit-death", 0L);

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

    private static io.farfrontier.palemirror.frontier.v3.api.ScheduleId progressId(ResidentMigrationJourney journey) {
        return new io.farfrontier.palemirror.frontier.v3.api.ScheduleId("schedule:resident-migration-progress-"
                + journey.residentId().value().substring("resident:".length()) + "-execution-" + journey.executionId().generation());
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
                .map(cells::get).filter(java.util.Objects::nonNull).noneMatch(cell -> cell.semanticPart() != GrayboxSemanticPart.ROUTE_SURFACE
                        && cell.semanticPart() != GrayboxSemanticPart.PUBLIC_ACCESS_SURFACE);
    }
}
