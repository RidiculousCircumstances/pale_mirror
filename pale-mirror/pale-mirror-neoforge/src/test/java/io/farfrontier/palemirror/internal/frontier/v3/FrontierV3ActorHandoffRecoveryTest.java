package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static io.farfrontier.palemirror.internal.frontier.v3.FrontierV3ActorCarrierComposition.*;

class FrontierV3ActorHandoffRecoveryTest {
    @Test void recordedSceneTargetRequiresExactLiveCanonicalOwnerNotASimilarCandidate() {
        var config = FrontierV3FixtureCatalog.routeSceneReturnConfiguration(new WorldId("frontier:handoff-scene-policy"), 41L);
        var initial = config.initialState(); var operation = initial.operations().values().iterator().next();
        var travel = operation.activeTravel().orElseThrow();
        var lease = SceneLease.atExactPositions(new SceneLeaseId("lease:handoff-scene-policy"), initial.bootstrap().worldId(),
                operation.id(), operation.cargoId(), operation.currentPosition(), travel.cargoAnchor().surface().support(),
                config.initialInstant(), 17L, SceneLeaseStatus.PREPARED, Optional.empty(),
                operation.participantIds().stream().map(actor -> new SceneMember(actor,
                        SceneLease.deterministicEntityId(initial.bootstrap().worldId(), actor))).toList(), travel.formation());
        var prepared = initial.prepareSceneLease(lease); var member = lease.members().getFirst();
        var target = FrontierV3AmbientActorExecutor.carrierDeclaration(prepared, member.actorId(), Owner.SCENE_LEASE,
                member.entityId(), Representation.LIVE_BODY, lease.revision(), 4L);
        var binding = FrontierV3ActorOwnerBinding.scene(target, lease.id());
        assertTrue(FrontierV3ActorHandoffRecovery.currentSceneTarget(prepared, binding));
        assertFalse(FrontierV3ActorHandoffRecovery.currentSceneTarget(initial, binding));
        assertFalse(FrontierV3ActorHandoffRecovery.currentSceneTarget(prepared,
                FrontierV3ActorOwnerBinding.scene(target, new SceneLeaseId("lease:not-the-owner"))));
        assertFalse(FrontierV3ActorHandoffRecovery.currentSceneTarget(prepared,
                FrontierV3ActorOwnerBinding.scene(target.liveBody(Owner.SCENE_LEASE, 18L, 4L), lease.id())));
        assertFalse(FrontierV3ActorHandoffRecovery.currentSceneTarget(prepared,
                FrontierV3ActorOwnerBinding.ambient(target.liveBody(Owner.AMBIENT_LEASE, 17L, 4L))));
        var hot = prepared.transitionSceneLease(lease.id(), SceneLeaseStatus.HOT);
        assertTrue(FrontierV3ActorHandoffRecovery.currentSceneTarget(hot, binding));
        var unknown = hot.transitionSceneLease(lease.id(), SceneLeaseStatus.UNKNOWN_AFTER_RESTART);
        assertTrue(FrontierV3ActorHandoffRecovery.currentSceneTarget(unknown, binding));
        assertEquals(SceneLeaseStatus.UNKNOWN_AFTER_RESTART, unknown.sceneLeases().get(lease.id()).status(),
                "recognition does not grant HOT authority");
        var conflict = prepared.transitionSceneLease(lease.id(), SceneLeaseStatus.CONFLICT);
        assertFalse(FrontierV3ActorHandoffRecovery.currentSceneTarget(conflict, binding));
    }
}
