package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3AmbientPendingAdmissionsTest {
    @Test void sceneHandoffEndsAmbientJoinBridgeEvenAfterCloseButNotForStaleOwnership() {
        var config = FrontierV3FixtureCatalog.routeSceneReturnConfiguration(new WorldId("frontier:join-bridge"), 41L);
        var initial = config.initialState();
        var operation = initial.operations().values().iterator().next();
        var travel = operation.activeTravel().orElseThrow();
        var actor = operation.participantIds().getFirst();
        var uuid = FrontierV3AmbientActorExecutor.entityId(initial, actor);
        var sceneId = new SceneLeaseId("lease:join-bridge-regression");
        var lease = SceneLease.atExactPositions(sceneId, initial.bootstrap().worldId(),
                operation.id(), operation.cargoId(), operation.currentPosition(), travel.cargoAnchor().surface().support(), config.initialInstant(),
                17L, SceneLeaseStatus.CLOSED, Optional.empty(), operation.participantIds().stream()
                        .map(id -> new SceneMember(id, SceneLease.deterministicEntityId(initial.bootstrap().worldId(), id))).toList(), travel.formation());
        var state = initial.withChanges(FrontierWorldStateUpdate.begin().sceneLeases(Map.of(sceneId, lease)));
        var declaration = FrontierV3AmbientActorExecutor.carrierDeclaration(state, actor,
                FrontierV3ActorCarrierComposition.Owner.SCENE_LEASE, uuid,
                FrontierV3ActorCarrierComposition.Representation.LIVE_BODY, 17L, 2L);
        var binding = FrontierV3ActorOwnerBinding.scene(declaration, sceneId);
        var ledger = FrontierV3AmbientCarrierLedger.emptyForTest();
        var closed = state;
        assertTrue(FrontierV3AmbientPendingAdmissions.recordedSceneOwns(closed, binding, ledger),
                "retained closed scene custody must not be stuck behind an ambient-only join bridge");
        assertFalse(FrontierV3AmbientPendingAdmissions.recordedSceneOwns(initial, binding, ledger));
        assertFalse(FrontierV3AmbientPendingAdmissions.recordedSceneOwns(closed,
                FrontierV3ActorOwnerBinding.scene(declaration.liveBody(declaration.owner(), 16L, 2L), sceneId), ledger));
        assertFalse(FrontierV3AmbientPendingAdmissions.recordedSceneOwns(closed,
                FrontierV3ActorOwnerBinding.scene(declaration, new SceneLeaseId("lease:foreign")), ledger));
        var ambient = declaration.liveBody(FrontierV3ActorCarrierComposition.Owner.AMBIENT_LEASE, 3L, 2L);
        assertTrue(ledger.prepareHandoff(binding, FrontierV3ActorOwnerBinding.ambient(ambient)));
        assertFalse(FrontierV3AmbientPendingAdmissions.recordedSceneOwns(closed, binding, ledger),
                "a stale scene declaration cannot override the recorded successor");
        var restored = FrontierV3AmbientCarrierLedger.load(ledger.save(new net.minecraft.nbt.CompoundTag(), null), null);
        assertFalse(FrontierV3AmbientPendingAdmissions.recordedSceneOwns(closed, binding, restored));
        assertTrue(restored.pendingHandoff(actor).isPresent(), "bridge cleanup cannot acknowledge an unsaved handoff");
    }
}
