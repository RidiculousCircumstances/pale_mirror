package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.process.ProductionProcess;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalReplicaCustodyPayloads.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ReferenceProjectionStateSupportTest {
    @Test
    void exactColdInputAndWriteFenceTransferAtomicallyWithoutLosingPartialWork() {
        var fixture = ProductionProcessTest.coldMarketJob();
        ProductionJob job = fixture.job().withWorkProgress(ProductionWorkProgress.processing(17));
        FrontierWorldState initial = fixture.state().withChanges(FrontierWorldStateUpdate.begin().productionJobs(Map.of(job.id(), job)));
        SubjectId depot = FrontierWorldState.depotId(job.settlementId());
        var base = FrontierWorldRuntimeDefinition.configuration(initial.bootstrap().worldId(), 91L);
        var due = ProductionProcess.complete(job, 120L);
        var engine = FrontierEngines.create(new FrontierEngineConfiguration<>(initial.bootstrap().worldId(), initial, SimInstant.ZERO,
                base.commandPlanner(), base.scheduledPlanner(), base.reducer(), new FrontierWorldStateCodec(), base.projectionMapper(),
                base.limits(), List.of(due), base.transactionCommitter()));
        var request = new ReferenceProjectionPrepared(depot, 1L, 0L, "", "");
        var codecs = FrontierWorldRuntimeDefinition.payloadCodecs();
        assertEquals(request, codecs.decode(request.type(), codecs.encode(request)));
        assertInstanceOf(CommandResult.Accepted.class, submit(engine, "prepare", request));
        FrontierWorldState prepared = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        ProductionJob transferred = prepared.productionJobs().get(job.id());
        assertEquals(job.withInputHold(new ProductionInputHold.Materialized(job.consumedItemId())), transferred);
        assertEquals(fixture.input(), prepared.inventory().items().get(job.consumedItemId()));
        assertEquals(initial.inventory().economics(), prepared.inventory().economics());
        assertEquals(initial.actorLocations(), prepared.actorLocations());
        assertEquals(List.of(due), engine.checkpoint().schedules());
        assertEquals(ContainerSurfaceStatus.PREPARED, prepared.inventory().surfaces().get(depot).status());
        assertTrue(ReferenceContainerCustody.hasLiveCustody(prepared, depot));
        assertFalse(ReferenceContainerCustody.hasOperationalCustody(prepared, depot));
        assertTrue(FrontierProductionWorkSceneSupport.candidate(prepared, transferred).isEmpty());
        assertFalse(prepared.inventory().items().containsKey(job.outputItemId()));
        ProductionJob finished = transferred.withWorkProgress(ProductionWorkProgress.outputReady());
        FrontierWorldState ready = prepared.withChanges(FrontierWorldStateUpdate.begin().productionJobs(Map.of(job.id(), finished)));
        var completion = ProductionProcess.planCompletion(ready, ProductionProcess.complete(finished, 120L));
        assertEquals(1, completion.size());
        assertInstanceOf(ScheduleEffect.Rescheduled.class, completion.getFirst().payload(), "unobserved input may not be transformed");
        assertThrows(IllegalArgumentException.class, () -> ready.completeProductionJob(job.id(), new ExactItemStack(job.outputItemId(),
                job.settlementId(), job.outputItemKind(), job.outputCount(), fixture.input().custody())));
        var beforeRepeat = engine.checkpoint();
        assertInstanceOf(CommandResult.Rejected.class, submit(engine, "duplicate", request));
        assertArrayEquals(beforeRepeat.canonicalState(), engine.checkpoint().canonicalState());

        // Model evidence only: the adapter must obtain these facts from the actual written chest.
        assertInstanceOf(CommandResult.Accepted.class, submit(engine, "surface-active", new ContainerSurfaceTransition(depot, ContainerSurfaceStatus.ACTIVE)));
        var expected = prepared.replicaCustody().replicas().get(depot);
        SubjectId scope = ReferenceContainerCustody.scopeId(depot);
        assertInstanceOf(CommandResult.Accepted.class, submit(engine, "confirm", new ProjectionCustodyConfirmed(scope, 1L,
                expected.emittedCanonicalRevision(), expected.replicaRevision(), expected.fingerprint(), expected.provenance())));
        FrontierWorldState confirmed = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertTrue(ReferenceContainerCustody.hasOperationalCustody(confirmed, depot));
        assertEquals(transferred, confirmed.productionJobs().get(job.id()));
        var lease = confirmed.replicaCustody().custodyByScope().get(scope);
        assertInstanceOf(CommandResult.Accepted.class, submit(engine, "checkpoint", new CustodyCheckpointed(scope, 1L,
                lease.expectedCanonicalRevision(), lease.expectedReplicaRevision())));
        assertInstanceOf(CommandResult.Accepted.class, submit(engine, "release", new CustodyReleased(scope, 1L,
                lease.expectedCanonicalRevision(), lease.expectedReplicaRevision())));
        var prior = confirmed.replicaCustody().replicas().get(depot);
        assertInstanceOf(CommandResult.Rejected.class, submit(engine, "foreign-catchup", new ReferenceProjectionPrepared(depot, 2L,
                prior.replicaRevision(), "foreign", prior.provenance())));
        assertInstanceOf(CommandResult.Accepted.class, submit(engine, "catchup", new ReferenceProjectionPrepared(depot, 2L,
                prior.replicaRevision(), prior.fingerprint(), prior.provenance())));
        FrontierWorldState repeated = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertEquals(fixture.input(), repeated.inventory().items().get(job.consumedItemId()));
        assertEquals(transferred, repeated.productionJobs().get(job.id()));
        assertEquals(2L, repeated.replicaCustody().custodyByScope().get(scope).authorityEpoch());
    }

    private static CommandResult submit(FrontierEngine<FrontierWorldProjection> engine, String suffix, FrontierPayload payload) {
        var checkpoint = engine.checkpoint();
        var id = new CommandId("command:reference-projection-" + suffix);
        return engine.submit(new FrontierCommand(1, id, checkpoint.worldId(), checkpoint.revision(), checkpoint.instant(),
                FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(id), payload));
    }
}
