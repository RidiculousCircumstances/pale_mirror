package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import io.farfrontier.palemirror.frontier.v3.process.*;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BakerySourceRecoveryTest {
    @Test void isolatedUnladenWorkerDoesNotReserveAFutureTurnAndCanReleaseWithoutMovingStock() {
        var state = ProductionProcessTest.productionTask(FrontierWorldState.initial(
                FrontierBootstrapper.create(new WorldId("frontier:bakery-source-recovery"), 91L)), StrategicTaskStatus.PENDING);
        var task = state.strategicPlans().tasks().values().iterator().next();
        var started = ProductionProcess.planStart(state, ProductionProcess.start(task, 200L)).stream()
                .map(ProposedEvent::payload).filter(ProductionStarted.class::isInstance)
                .map(ProductionStarted.class::cast).findFirst().orElseThrow();
        state = StrategicObjectiveProcess.reduceTaskTransition(state, task.ownerId(),
                new StrategicTaskTransition(task.id(), StrategicTaskStatus.ACTIVE));
        state = ProductionProcess.reduceStarted(state, task.ownerId(), started);
        var job = started.job(); var actor = state.actorLocations().get(job.workerId());
        var point = BakeryStorage.source(job);
        var leaseId = new SceneLeaseId("lease:bakery-source-recovery");
        var lease = SceneLease.forCause(leaseId, state.bootstrap().worldId(), new ProductionWorkSceneCause(job.id()),
                actor.supportingSurface().support(), new SimInstant(300L), 1L, SceneLeaseStatus.PREPARED,
                List.of(new SceneMember(job.workerId(), SceneLease.deterministicEntityId(state.bootstrap().worldId(), job.workerId()))),
                Set.of(), Optional.empty());
        state = FrontierTestActorBodies.present(state.prepareSceneLease(lease), lease).transitionSceneLease(leaseId, SceneLeaseStatus.HOT);
        assertTrue(ServiceAccessCapabilities.current(state, point).stream().anyMatch(d -> d.identity().actorId().equals(job.workerId())));
        var conflicted = state.transitionSceneLease(leaseId, SceneLeaseStatus.CONFLICT);
        assertTrue(ServiceAccessCapabilities.current(conflicted, point).stream().noneMatch(d -> d.identity().actorId().equals(job.workerId())),
                "a conflicted distant worker cannot starve all deliveries to the warehouse");
        assertTrue(ServiceAccessCoordinator.wakePoints(state, conflicted).contains(point));
        var boundary = ServiceAccessCoordinator.boundary(conflicted, point);
        var occupied = conflicted.withActorBody(job.workerId(), boundary.occupiedSurfaces().iterator().next().standingBody());
        assertTrue(ServiceAccessCapabilities.current(occupied, point).stream().anyMatch(d ->
                d.identity().actorId().equals(job.workerId()) && d.presence() == ServiceAccessDemand.Presence.OCCUPIED),
                "conflict does not make an actual occupant disappear");
        var fence = ActorBodyAuthority.require(conflicted, ActorBodyAuthority.current(conflicted, job.workerId()));
        var receipt = new BakeryStationSceneReconciled(job.id(), leaseId, lease.revision(), fence.authorityEpoch(),
                lease.members().getFirst().entityId(), actor.body(), BakeryWorkState.Phase.DEPOT_PICKUP);
        var codecs = FrontierWorldRuntimeDefinition.payloadCodecs();
        assertEquals(receipt, codecs.decode(receipt.type(), codecs.encode(receipt)));
        var draining = BakerySceneReconciliation.reduce(conflicted, job.settlementId(), receipt);
        assertEquals(SceneLeaseStatus.DRAINING, draining.sceneLeases().get(leaseId).status());
        assertEquals(conflicted.inventory(), draining.inventory());
        assertEquals(conflicted.productionJobs(), draining.productionJobs());
        var closed = draining.releaseSceneLease(leaseId, List.of(new SceneMemberPosition(job.workerId(), actor.body(), actor.condition().health())));
        assertTrue(FrontierSceneAdmission.available(closed, List.of(job.workerId())));
        var codec = new FrontierWorldStateCodec();
        assertEquals(closed, codec.decode(codec.encode(closed)));
        assertThrows(IllegalArgumentException.class, () -> BakerySceneReconciliation.reduce(draining, job.settlementId(), receipt));
        assertThrows(IllegalArgumentException.class, () -> BakerySceneReconciliation.reduce(conflicted, job.settlementId(),
                new BakeryStationSceneReconciled(job.id(), leaseId, lease.revision(), fence.authorityEpoch() + 1,
                        receipt.entityId(), actor.body(), receipt.phase())));
        var atStorage = state.withActorBody(job.workerId(), BakeryWorkGoal.current(state, job).station().standingBody());
        var pending = ProductionProcess.reduceBakeryHotEffectPrepared(atStorage, job.settlementId(),
                new BakeryHotEffectPrepared(job.id(), leaseId, BakeryWorkState.Phase.DEPOT_PICKUP, -1));
        var pendingConflict = pending.transitionSceneLease(leaseId, SceneLeaseStatus.CONFLICT);
        var pendingReceipt = new BakeryStationSceneReconciled(job.id(), leaseId, lease.revision(), fence.authorityEpoch(),
                receipt.entityId(), atStorage.actorLocations().get(job.workerId()).body(), receipt.phase());
        assertThrows(IllegalArgumentException.class, () -> BakerySceneReconciliation.reduce(pendingConflict, job.settlementId(), pendingReceipt),
                "an unresolved pickup cannot be abandoned as an empty-hand checkpoint");
    }
}
