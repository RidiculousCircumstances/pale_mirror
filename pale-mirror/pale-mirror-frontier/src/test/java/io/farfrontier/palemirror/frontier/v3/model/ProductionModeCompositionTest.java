package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.kernel.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.process.ProductionProcess;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import io.farfrontier.palemirror.frontier.v3.model.PhysicalReplicaCustodyPayloads.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Registered model observations, not Minecraft/player evidence. No post-start state injection. */
class ProductionModeCompositionTest {
    @Test void resourceWorkRetainsLaborAcrossTwoHotVisits() { exercise(false); }
    @Test void exactItemWorkRetainsLaborAcrossTwoHotVisits() { exercise(true); }

    private static void exercise(boolean exact) {
        var initial = BoundProductionAdmissionTest.coldFixture();
        if (exact) initial = ProductionProcessTest.withLegacyExactWheat(initial);
        var mixed = new Driver(initial);
        var cold = new Driver(initial);
        mixed.next(); cold.next();
        var original = mixed.job();
        for (int turn = 0; !mixed.job().workProgress().equals(ProductionWorkProgress.processing(17)); turn++) {
            assertTrue(turn < 200, "retained route and input station must lead to actual processing");
            mixed.next(); cold.next();
            compare(mixed, cold, original);
        }
        for (int visit = 1; visit <= 2; visit++) {
            var lease = mixed.enterHot(visit, exact);
            compare(mixed, cold, original);
            for (int unit = 0; unit < 5; unit++) {
                var job = mixed.job();
                var next = ProductionWorkProgress.processing(job.workProgress().completedTicks() + 1);
                var body = job.workTraversal().linearCorridorSurfaces().get(job.traversalCursor()).standingBody();
                mixed.next(); // held schedule advances time, never COLD labor while HOT
                assertEquals(job.workProgress(), mixed.job().workProgress());
                mixed.submit(new ProductionWorkProgressed(job.id(), lease.id(), body, next), true);
                cold.next(); compare(mixed, cold, original);
            }
            mixed.leaveHot(lease, exact);
            mixed.recover();
            compare(mixed, cold, original);
            for (int unit = 0; unit < 3; unit++) { mixed.next(); cold.next(); compare(mixed, cold, original); }
        }
        for (int turn = 0; !mixed.state().productionJobs().isEmpty(); turn++) {
            assertTrue(turn < 100, "remaining earned labor must complete without another visit");
            mixed.next(); cold.next();
            if (!mixed.state().productionJobs().isEmpty()) compare(mixed, cold, original);
        }
        assertTrue(cold.state().productionJobs().isEmpty());
        var output = original.outputItemId();
        if (exact) assertEquals(cold.state().inventory().items().get(output), mixed.state().inventory().items().get(output));
        else assertEquals(cold.state().inventory().fungibleResources().lots().get(output), mixed.state().inventory().fungibleResources().lots().get(output));
        assertEquals(FixedScalar.ONE, mixed.state().inventory().economics().require(original.workerId()).balance());
        assertEquals(cold.engine.checkpoint().instant(), mixed.engine.checkpoint().instant());
    }

    private static void compare(Driver mixed, Driver cold, ProductionJob original) {
        assertEquals(cold.job().workProgress(), mixed.job().workProgress());
        assertEquals(cold.job().traversalCursor(), mixed.job().traversalCursor());
        assertEquals(original.workTraversal(), mixed.job().workTraversal());
        assertEquals(original.workerId(), mixed.job().workerId());
        assertEquals(cold.state().actorLocations().get(original.workerId()).body(), mixed.state().actorLocations().get(original.workerId()).body());
        assertEquals(cold.engine.checkpoint().schedules().getFirst().dueAt(), mixed.engine.checkpoint().schedules().getFirst().dueAt());
        assertFalse(mixed.state().inventory().items().containsKey(original.outputItemId()));
        assertFalse(mixed.state().inventory().fungibleResources().lots().containsKey(original.outputItemId()));
    }

    private static final class Driver {
        private FrontierEngine<FrontierWorldProjection> engine;
        private final FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> configuration;
        private int sequence;
        Driver(FrontierWorldState initial) {
            var base = FrontierWorldRuntimeDefinition.configuration(initial.bootstrap().worldId(), 91L);
            var task = initial.strategicPlans().tasks().values().iterator().next();
            configuration = new FrontierEngineConfiguration<>(initial.bootstrap().worldId(), initial, SimInstant.ZERO,
                    base.commandPlanner(), base.scheduledPlanner(), base.reducer(), new FrontierWorldStateCodec(), base.projectionMapper(),
                    base.limits(), List.of(ProductionProcess.start(task, 200L)), base.transactionCommitter());
            engine = FrontierEngines.create(configuration);
        }
        void recover() {
            var cp = engine.checkpoint();
            engine = FrontierEngines.recover(configuration, new io.farfrontier.palemirror.frontier.v3.persistence.RecoveryImage(cp.worldId(),
                    Optional.of(new io.farfrontier.palemirror.frontier.v3.persistence.SnapshotRecord(cp, cp.revision().value())), List.of()));
            assertArrayEquals(cp.canonicalState(), engine.checkpoint().canonicalState());
            assertEquals(cp.schedules(), engine.checkpoint().schedules());
        }
        FrontierWorldState state() { return new FrontierWorldStateCodec().decode(engine.checkpoint().canonicalState()); }
        ProductionJob job() { return state().productionJobs().values().iterator().next(); }
        void next() { engine.advanceTo(engine.checkpoint().schedules().getFirst().dueAt(), new WorkBudget(100, 100)); }
        void submit(FrontierPayload payload, boolean bound) {
            var cp = engine.checkpoint(); var id = new CommandId("command:mode-composition-" + ++sequence);
            var result = engine.submit(new FrontierCommand(bound ? 2 : 1, id, cp.worldId(), cp.revision(), cp.instant(),
                    FrontierWorldRuntimeDefinition.PHYSICAL_EXECUTOR, CauseChain.root(id), payload,
                    bound ? Optional.of(new EngineScheduleBinding(cp.revision(), cp.schedules().getFirst())) : Optional.empty()));
            assertInstanceOf(CommandResult.Accepted.class, result, payload.type() + ": " + result);
        }
        SceneLease enterHot(long epoch, boolean exact) {
            var job = job(); var depot = FrontierWorldState.depotId(job.settlementId());
            var prior = state().replicaCustody().replicas().get(depot);
            submit(new ReferenceProjectionPrepared(depot, epoch, prior == null ? 0L : prior.replicaRevision(),
                    prior == null ? "" : prior.fingerprint(), prior == null ? "" : prior.provenance()), false);
            if (state().inventory().surfaces().get(depot).status() == ContainerSurfaceStatus.PREPARED)
                submit(new ContainerSurfaceTransition(depot, ContainerSurfaceStatus.ACTIVE), false);
            var expected = state().replicaCustody().replicas().get(depot);
            submit(new ProjectionCustodyConfirmed(ReferenceContainerCustody.scopeId(depot), epoch, expected.emittedCanonicalRevision(),
                    expected.replicaRevision(), expected.fingerprint(), expected.provenance()), false);
            if (!exact) submit(new FungibleStackLayoutObserved(new SubjectId("custody:container-1-depot"), epoch,
                    List.of(new FungiblePhysicalObservation.Stack(new PhysicalStackAddress.ContainerSlot(new InventoryCustody.ContainerSlot(depot, 0)), "minecraft:wheat", 64))), false);
            var candidate = FrontierProductionWorkSceneSupport.candidate(state(), job()).orElseThrow();
            var cp = engine.checkpoint();
            var lease = SceneLease.forCause(new SceneLeaseId("lease:mode-composition-" + epoch), cp.worldId(), new ProductionWorkSceneCause(job.id()),
                    candidate.handoffPosition(), cp.instant(), cp.revision().value(), SceneLeaseStatus.PREPARED,
                    List.of(new SceneMember(job.workerId(), SceneLease.deterministicEntityId(cp.worldId(), job.workerId()))),
                    SceneLease.bodiesAboveSupportCells(candidate.memberPositions()), Set.of(), Optional.empty());
            submit(new ProductionWorkSceneLeasePrepared(lease), false);
            submit(new SceneLeaseTransition(lease.id(), SceneLeaseStatus.HOT), false);
            return lease;
        }
        void leaveHot(SceneLease lease, boolean exact) {
            submit(new SceneLeaseTransition(lease.id(), SceneLeaseStatus.DRAINING), false);
            var job = job(); var state = state();
            submit(new SceneLeaseReleased(lease.id(), List.of(new SceneMemberPosition(job.workerId(),
                    state.sceneLeases().get(lease.id()).memberPosition(job.workerId()), state.actorLocations().get(job.workerId()).condition().health()))), false);
            var depot = FrontierWorldState.depotId(job.settlementId()); var scope = ReferenceContainerCustody.scopeId(depot);
            var custody = state().replicaCustody().custodyByScope().get(scope);
            if (!exact) submit(new FungibleStackBindingsReleased(new SubjectId("custody:container-1-depot"), custody.authorityEpoch()), false);
            submit(new CustodyCheckpointed(scope, custody.authorityEpoch(), custody.expectedCanonicalRevision(), custody.expectedReplicaRevision()), false);
            submit(new CustodyReleased(scope, custody.authorityEpoch(), custody.expectedCanonicalRevision(), custody.expectedReplicaRevision()), false);
        }
    }
}
