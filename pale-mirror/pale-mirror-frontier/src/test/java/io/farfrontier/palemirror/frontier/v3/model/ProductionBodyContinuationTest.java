package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.process.ProductionProcess;
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
    @Test void observedDepartureSurvivesRecoveryAndRejoinsWithoutAwardingWork() {
        var fixture = working(); var job = fixture.job();
        var observed = ModeledActorBodyFacts.inspected(fixture.state(), job.workerId(), fixture.port().inputStation().standingBody());
        assertThrows(IllegalArgumentException.class, () -> ProductionColdJourney.next(observed, job),
                "inspection alone does not fabricate the owner's departure acknowledgement");
        var scopeOrigin = ProductionJourneyKnowledge.atScopeAdmission(observed, job);
        assertEquals(observed.actorLocations(), scopeOrigin.actorLocations());
        assertEquals(observed.fencedRecovery(), scopeOrigin.fencedRecovery());
        assertEquals(fixture.port().workStation(), scopeOrigin.productionJobs().get(job.id()).spatial().approach().orElseThrow().target());
        var departed = ModeledActorBodyFacts.unloaded(observed, job.workerId());
        var retained = departed.productionJobs().get(job.id());
        assertEquals(job.workTraversal(), retained.workTraversal());
        assertEquals(job.workProgress(), retained.workProgress());
        assertEquals(fixture.port().inputStation(), retained.spatial().approach().orElseThrow().current());
        assertEquals(fixture.port().workStation(), retained.spatial().approach().orElseThrow().target());
        var restored = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(departed));
        assertEquals(retained, restored.productionJobs().get(job.id()));
        assertEquals(fixture.port().inputStation().support(), FrontierProductionWorkSceneSupport.candidate(restored, retained)
                .orElseThrow().handoffPosition(), "HOT re-admission uses actual saved departure, not the old semantic cursor");
        var event = coldEvent(restored, retained, 1000L);
        var codecs = FrontierWorldRuntimeDefinition.payloadCodecs();
        assertEquals(event, codecs.decode(event.type(), codecs.encode(event)));
        var arrived = ProductionProcess.reduceColdWorkAdvanced(restored, job.settlementId(), event);
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
    }
    private static ProductionColdWorkAdvanced coldEvent(FrontierWorldState state, ProductionJob job, long tick) {
        return ProductionProcess.planCompletion(state, ProductionProcess.complete(job, tick)).stream()
                .map(value -> value.payload()).filter(ProductionColdWorkAdvanced.class::isInstance)
                .map(ProductionColdWorkAdvanced.class::cast).findFirst().orElseThrow();
    }
}
