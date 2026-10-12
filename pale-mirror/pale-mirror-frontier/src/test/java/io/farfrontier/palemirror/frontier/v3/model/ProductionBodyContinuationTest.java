package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.process.ProductionProcess;
import io.farfrontier.palemirror.frontier.v3.process.FrontierWorldProcessCatalog;
import io.farfrontier.palemirror.frontier.v3.process.PedestrianPlanningContinuations;
import io.farfrontier.palemirror.frontier.v3.process.PedestrianPlanningWakeIndex;
import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRoutePlanning;
import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult;
import io.farfrontier.palemirror.frontier.v3.api.FrontierExecutionView;
import io.farfrontier.palemirror.frontier.v3.api.Revision;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** Connected modeled departure/recovery/continuation, not native movement acceptance. */
class ProductionBodyContinuationTest {
    private record Fixture(FrontierWorldState state, ProductionJob job, SettlementWorkshopServicePort port) { }
    private static Fixture working() {
        var fixture = ProductionProcessTest.activeMaterializedProduction();
        var job = fixture.job(); var state = fixture.state();
        var workshop = state.bootstrap().settlements().stream().filter(value -> value.id().equals(job.settlementId()))
                .findFirst().orElseThrow().structures().stream().filter(value -> value.id().equals(job.facilityId()))
                .findFirst().orElseThrow();
        var port = SettlementWorkshopServicePort.forWorkshop(workshop);
        var topology = ProductionWorkTraversal.compile(state.bootstrap(), workshop, state.actorLocations().get(job.workerId()), job.id());
        var working = job.withWorkTraversal(topology, topology.linearCorridorSurfaces().size() - 1)
                .withWorkProgress(ProductionWorkProgress.processing(3));
        state = state.withChanges(FrontierWorldStateUpdate.begin().productionJobs(Map.of(working.id(), working)))
                .withActorBody(working.workerId(), port.workStation().standingBody());
        return new Fixture(ModeledActorBodyFacts.present(state, working.workerId()), working, port);
    }
    @Test void observedDepartureSurvivesRecoveryAndRejoinsWithoutAwardingWork() throws Exception {
        var fixture = working(); var job = fixture.job();
        var observed = ModeledActorBodyFacts.inspected(fixture.state(), job.workerId(), fixture.port().inputStation().standingBody());
        assertThrows(IllegalArgumentException.class, () -> ProductionColdJourney.next(observed, job),
                "inspection alone does not fabricate the owner's departure acknowledgement");
        FrontierWorldState scopeOrigin, departed;
        try (var binding = io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRoutePlanning.bind((geometry, from, to) -> {
            throw new AssertionError("physical checkpoint cannot calculate its new approach");
        })) {
            scopeOrigin = ProductionJourneyKnowledge.atScopeAdmission(observed, job);
            departed = ModeledActorBodyFacts.unloaded(observed, job.workerId());
        }
        assertEquals(observed.actorLocations(), scopeOrigin.actorLocations());
        assertEquals(observed.fencedRecovery(), scopeOrigin.fencedRecovery());
        assertEquals(fixture.port().inputStation(), scopeOrigin.productionJobs().get(job.id()).spatial().waitingOrigin().orElseThrow());
        var retained = departed.productionJobs().get(job.id());
        assertEquals(job.workTraversal(), retained.workTraversal());
        assertEquals(job.workProgress(), retained.workProgress());
        assertEquals(fixture.port().inputStation(), retained.spatial().waitingOrigin().orElseThrow());
        assertTrue(retained.spatial().approach().isEmpty(), "observation does not manufacture a calculated route");
        var restored = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(departed));
        assertEquals(retained, restored.productionJobs().get(job.id()));
        assertEquals(fixture.port().inputStation().support(), FrontierProductionWorkSceneSupport.candidate(restored, retained)
                .orElseThrow().handoffPosition(), "HOT re-admission uses actual saved departure, not the old semantic cursor");
        var event = coldEvent(restored, retained, 1000L);
        var codecs = FrontierWorldRuntimeDefinition.payloadCodecs();
        assertEquals(event, codecs.decode(event.type(), codecs.encode(event)));
        FrontierWorldState arrived;
        try (var binding = io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRoutePlanning.bind((geometry, from, to) -> {
            throw new AssertionError("production receipt replay cannot calculate an approach");
        })) {
            arrived = ProductionProcess.reduceColdWorkAdvanced(restored, job.settlementId(), event);
            var missing = new ProductionColdWorkAdvanced(event.jobId(), event.nextCursor(), event.next(), event.execution(),
                    event.expectedBody(), event.expectedCursor(), event.expectedProgress(), event.spatialRevision(), java.util.Optional.empty());
            assertThrows(IllegalArgumentException.class, () -> ProductionProcess.reduceColdWorkAdvanced(restored, job.settlementId(), missing));
            var selected = event.approach().orElseThrow();
            var reversed = new io.farfrontier.palemirror.frontier.v3.model.navigation.TraversalRejoin(selected.path().reversed(), 0);
            var foreign = new ProductionColdWorkAdvanced(event.jobId(), event.nextCursor(), event.next(), event.execution(),
                    event.expectedBody(), event.expectedCursor(), event.expectedProgress(), event.spatialRevision(), java.util.Optional.of(reversed));
            assertThrows(IllegalArgumentException.class, () -> ProductionProcess.reduceColdWorkAdvanced(restored, job.settlementId(), foreign));
        }
        var bytes = codecs.encode(event);
        assertThrows(IllegalArgumentException.class, () -> codecs.decode(event.type(), java.util.Arrays.copyOf(bytes, bytes.length - 1)));
        assertEquals(job.workProgress(), arrived.productionJobs().get(job.id()).workProgress(), "return travel is not processing");
        assertEquals(fixture.port().workStation().standingBody(), arrived.actorLocations().get(job.workerId()).body());
        assertFalse(arrived.productionJobs().get(job.id()).spatial().pending());
        assertThrows(IllegalArgumentException.class, () -> ProductionProcess.reduceColdWorkAdvanced(arrived, job.settlementId(), event));
        var processing = coldEvent(arrived, arrived.productionJobs().get(job.id()), 1020L);
        var next = ProductionProcess.reduceColdWorkAdvanced(arrived, job.settlementId(), processing);
        assertEquals(ProductionWorkProgress.processing(4), next.productionJobs().get(job.id()).workProgress());
    }
    @Test void damagedStationRetainsDepartureWithoutLosingTheJobOrCreditingProcessing() {
        var fixture = working(); var job = fixture.job();
        var blocked = fixture.state().recordPhysicalDelta(new PhysicalDelta(fixture.port().workStation().support().offset(0, 1, 0),
                PhysicalDeltaKind.UNKNOWN_SCAR, java.util.Optional.empty(), java.util.Optional.empty(), "test:blocked-work-station"));
        var observed = ModeledActorBodyFacts.inspected(blocked, job.workerId(), fixture.port().inputStation().standingBody());
        var departed = ModeledActorBodyFacts.unloaded(observed, job.workerId());
        var retained = departed.productionJobs().get(job.id());
        assertTrue(retained.spatial().waitingOrigin().isPresent());
        assertTrue(ProductionColdJourney.next(departed, retained).isEmpty());
        assertEquals(job.workProgress(), retained.workProgress());
        assertEquals(job.consumedItemId(), retained.consumedItemId());
        assertTrue(ProductionJourneyKnowledge.approach(departed, retained).isEmpty(), "missing support cannot manufacture a HOT hint");
        var repaired = departed.withChanges(FrontierWorldStateUpdate.begin().physicalDeltas(java.util.Map.of()));
        var hint = ProductionJourneyKnowledge.approach(repaired, retained).orElseThrow();
        assertEquals(fixture.port().inputStation(), hint.current());
        assertEquals(fixture.port().workStation(), hint.target());
        assertEquals(retained, repaired.productionJobs().get(job.id()), "read-only re-wake cannot replace the saved job or approach");
        assertEquals(departed.actorLocations(), repaired.actorLocations());
        assertEquals(departed.inventory(), repaired.inventory());
    }
    @Test void unfinishedApproachDefersTheActualProducerAndRestoresItsCalculationWait() throws Exception {
        var fixture = working();
        var observed = ModeledActorBodyFacts.inspected(fixture.state(), fixture.job().workerId(), fixture.port().inputStation().standingBody());
        var departed = ModeledActorBodyFacts.unloaded(observed, fixture.job().workerId());
        var job = departed.productionJobs().get(fixture.job().id());
        var action = ProductionProcess.complete(job, 1000L);
        for (var status : java.util.List.of(PedestrianRouteResult.Status.PLANNING,
                PedestrianRouteResult.Status.UNKNOWN_GEOMETRY, PedestrianRouteResult.Status.SAFETY_LIMIT)) {
            var queries = new java.util.concurrent.atomic.AtomicInteger();
            var waits = new PedestrianPlanningWakeIndex();
            try (var binding = PedestrianRoutePlanning.bind((geometry, from, to) -> {
                queries.incrementAndGet();
                return new PedestrianRouteResult(status, java.util.List.of(), 1, 1, "pending approach");
            }); var continuation = PedestrianPlanningContinuations.bind(waits)) {
                var events = FrontierWorldRuntimeDefinition.planScheduled(departed, action);
                assertEquals(1, events.size(), status + " cannot award movement or processing");
                var retry = assertInstanceOf(ScheduleEffect.Rescheduled.class, events.getFirst().payload());
                assertEquals(action.id(), retry.scheduleId());
                assertEquals(1, queries.get());
                assertEquals(job, departed.productionJobs().get(job.id()));
                if (status == PedestrianRouteResult.Status.PLANNING) {
                    assertEquals(1, waits.waitingCount());
                    waits.remove(action.id());
                    var codec = new FrontierWorldStateCodec();
                    var recovered = codec.decode(codec.encode(departed));
                    FrontierWorldProcessCatalog.restorePlanningWaits(FrontierWorldRuntimeDefinition.processRegistry(), recovered,
                            new FrontierExecutionView(departed.bootstrap().worldId(), new Revision(0), action.dueAt(),
                                    java.util.List.of(retry.replacement())));
                    assertEquals(1, waits.waitingCount(), "restart restores the addressed search, not a work outcome");
                }
            }
        }
    }
    private static ProductionColdWorkAdvanced coldEvent(FrontierWorldState state, ProductionJob job, long tick) {
        return ProductionProcess.planCompletion(state, ProductionProcess.complete(job, tick)).stream()
                .map(value -> value.payload()).filter(ProductionColdWorkAdvanced.class::isInstance)
                .map(ProductionColdWorkAdvanced.class::cast).findFirst().orElseThrow();
    }
}
