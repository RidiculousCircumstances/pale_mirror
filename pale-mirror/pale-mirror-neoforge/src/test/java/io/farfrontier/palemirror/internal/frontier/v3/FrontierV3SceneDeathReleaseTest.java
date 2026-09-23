package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FrontierV3SceneDeathReleaseTest {
    @Test
    void onlyRecordedDeathOfTheExactCurrentMemberCanBypassALivingCarrier() {
        var config = FrontierV3FixtureCatalog.routeSceneReturnConfiguration(new WorldId("frontier:death-release"), 41L);
        var initial = config.initialState();
        assertEquals(1, initial.operations().size(), "fixture must provide one unambiguous initial shipment");
        var operation = initial.operations().values().iterator().next();
        var travel = operation.activeTravel().orElseThrow();
        var lease = SceneLease.atExactPositions(new SceneLeaseId("lease:death-release"), initial.bootstrap().worldId(),
                operation.id(), operation.cargoId(), operation.currentPosition(), travel.cargoAnchor().surface().support(),
                config.initialInstant(), 1L, SceneLeaseStatus.PREPARED, Optional.empty(),
                operation.participantIds().stream().map(actor -> new SceneMember(actor,
                        SceneLease.deterministicEntityId(initial.bootstrap().worldId(), actor))).toList(), travel.formation());
        var hot = initial.prepareSceneLease(lease).transitionSceneLease(lease.id(), SceneLeaseStatus.HOT);
        var member = lease.members().getFirst();
        var living = hot.transitionSceneLease(lease.id(), SceneLeaseStatus.DRAINING);
        var draining = living.sceneLeases().get(lease.id());
        assertFalse(FrontierV3AmbientActorExecutor.recordedSceneDeath(living, draining, member));
        var dead = living.recordActorDeath(new ActorDied(lease.id(), member.actorId(),
                living.actorLocations().get(member.actorId()).body(), "observed-test-death"), config.initialInstant().ticks());
        var codec = new FrontierWorldStateCodec();
        dead = codec.decode(codec.encode(dead));
        assertTrue(FrontierV3AmbientActorExecutor.recordedSceneDeath(dead, draining, member));
        assertFalse(FrontierV3AmbientActorExecutor.recordedSceneDeath(dead, lease, member), "stale scene cannot borrow death authority");
        assertFalse(FrontierV3AmbientActorExecutor.recordedSceneDeath(dead, draining,
                new SceneMember(member.actorId(), UUID.randomUUID())), "foreign physical identity cannot borrow death authority");
        assertFalse(FrontierV3AmbientActorExecutor.recordedSceneDeath(dead, draining, lease.members().get(1)),
                "another living member still requires a physical carrier");
        // Null world/entity are intentional: accepted death needs neither a world lookup nor
        // an invented inactive carrier. Calling either would fail this regression.
        assertEquals(FrontierV3AmbientActorExecutor.SceneCarrierFenceResult.RETIRED_BY_DEATH,
                FrontierV3AmbientActorExecutor.fenceDrainingSceneBody(null, dead, draining, member, null));
        for (var result : FrontierV3AmbientActorExecutor.SceneCarrierFenceResult.values()) {
            assertEquals(result == FrontierV3AmbientActorExecutor.SceneCarrierFenceResult.FENCED
                            || result == FrontierV3AmbientActorExecutor.SceneCarrierFenceResult.RETIRED_BY_DEATH,
                    result.permitsRelease());
        }
    }
}
