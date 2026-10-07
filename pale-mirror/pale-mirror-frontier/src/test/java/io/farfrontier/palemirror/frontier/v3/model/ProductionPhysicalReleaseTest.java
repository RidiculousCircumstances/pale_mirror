package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.persistence.*;
import io.farfrontier.palemirror.frontier.v3.process.ProductionProcess;
import io.farfrontier.palemirror.frontier.v3.process.CompanyFoundationProcess;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class ProductionPhysicalReleaseTest {
    @Test void exactPreparedReleaseRecoversOneColdContinuation() { completeAfterRelease(false); }
    @Test void exactPreparedMutationClosureRecoversOneColdContinuation() { completeAfterRelease(true); }

    private static void completeAfterRelease(boolean mutation) {
        var f = ProductionProcessTest.activePhysicalProduction();
        var depot = FrontierWorldState.depotId(f.job().settlementId());
        var state = ReferenceContainerCustodyFixtures.observedAndHeld(f.state(), depot);
        var config = configuration(state);
        var engine = FrontierEngines.create(config);
        var scope = ReferenceContainerCustody.scopeId(depot);
        var lease = state.replicaCustody().custodyByScope().get(scope);
        if (!mutation) assertAccepted(submit(engine, "checkpoint", new PhysicalReplicaCustodyPayloads.CustodyCheckpointed(scope,
                lease.authorityEpoch(), lease.expectedCanonicalRevision(), lease.expectedReplicaRevision())));
        var current = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        FrontierPayload release = mutation ? ReferenceContainerCustody.confirmedMutationTransition(current, depot, 2L)
                : new PhysicalReplicaCustodyPayloads.CustodyReleased(scope, lease.authorityEpoch(), lease.expectedCanonicalRevision(), lease.expectedReplicaRevision());
        assertAccepted(submit(engine, "release", release));
        var released = new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState());
        assertEquals(f.job(), released.productionJobs().get(f.job().id()));
        assertFalse(released.physicalIntents().containsKey(f.intent().id()));
        assertFalse(released.fencedRecovery().current().containsKey(FencedRecoveryPhysicalIntentSupport.bindingId(f.intent())));
        assertEquals(state.inventory(), released.inventory(), "release must not edit stock or its replica fingerprint");
        assertEquals(List.of(ProductionProcess.complete(f.job(), 1L)), engine.checkpoint().schedules());
        var recovered = FrontierEngines.recover(config, new RecoveryImage(state.bootstrap().worldId(),
                Optional.of(new SnapshotRecord(engine.checkpoint(), 1L)), List.of()));
        assertInstanceOf(CommandResult.Rejected.class, submit(recovered, "late-start",
                new PhysicalIntentTransition(f.intent().id(), PhysicalIntentStatus.RUNNING, Optional.empty())));
        recovered.advanceTo(new SimInstant(1L), new WorkBudget(100, 100));
        var completed = new FrontierWorldStateCodec().decode(recovered.checkpoint().canonicalState());
        assertFalse(completed.productionJobs().containsKey(f.job().id()));
        assertFalse(completed.inventory().items().containsKey(f.job().consumedItemId()));
        assertEquals(64, completed.inventory().items().get(f.job().outputItemId()).count());
        assertEquals(f.state().inventory().economics().require(CompanyFoundationProcess.companyId(f.job().settlementId())).balance()
                        .plus(f.order().acceptedTotalPrice()),
                completed.inventory().economics().require(CompanyFoundationProcess.companyId(f.job().settlementId())).balance());
    }

    @Test void startedExactEffectCannotLoseItsCustodyThroughEitherReleaseEntryPoint() {
        var f = ProductionProcessTest.activePhysicalProduction();
        var depot = FrontierWorldState.depotId(f.job().settlementId());
        var state = ReferenceContainerCustodyFixtures.observedAndHeld(f.state(), depot);
        var engine = FrontierEngines.create(configuration(state));
        assertAccepted(submit(engine, "running", new PhysicalIntentTransition(f.intent().id(), PhysicalIntentStatus.RUNNING, Optional.empty())));
        var scope = ReferenceContainerCustody.scopeId(depot);
        var lease = state.replicaCustody().custodyByScope().get(scope);
        assertAccepted(submit(engine, "checkpoint", new PhysicalReplicaCustodyPayloads.CustodyCheckpointed(scope, 1L,
                lease.expectedCanonicalRevision(), lease.expectedReplicaRevision())));
        var before = engine.checkpoint();
        assertInstanceOf(CommandResult.Rejected.class, submit(engine, "release", new PhysicalReplicaCustodyPayloads.CustodyReleased(scope,
                1L, lease.expectedCanonicalRevision(), lease.expectedReplicaRevision())));
        assertInstanceOf(CommandResult.Rejected.class, submit(engine, "close", ReferenceContainerCustody.confirmedMutationTransition(
                new FrontierWorldStateCodec().decode(before.canonicalState()), depot, 2L)));
        assertArrayEquals(before.canonicalState(), engine.checkpoint().canonicalState());
    }

    private static FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> configuration(FrontierWorldState state) {
        var base = FrontierWorldRuntimeDefinition.configuration(state.bootstrap().worldId(), 91L);
        return new FrontierEngineConfiguration<>(state.bootstrap().worldId(), state, SimInstant.ZERO, base.commandPlanner(),
                base.scheduledPlanner(), base.reducer(), new FrontierWorldStateCodec(), base.projectionMapper(), base.limits(), List.of(), base.transactionCommitter());
    }
    private static CommandResult submit(FrontierEngine<FrontierWorldProjection> engine, String phase, FrontierPayload payload) {
        var cp = engine.checkpoint(); var id = new CommandId("command:exact-release-" + phase);
        return engine.submit(new FrontierCommand(1, id, cp.worldId(), cp.revision(), cp.instant(), FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(id), payload));
    }
    private static void assertAccepted(CommandResult result) { assertInstanceOf(CommandResult.Accepted.class, result, result.toString()); }
}
