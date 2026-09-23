package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3SceneReleaseReadinessTest {
    private static final FrontierWorldState STATE = FrontierWorldState.initial(
            FrontierBootstrapper.create(new WorldId("frontier:release-readiness"), 93L));
    private static final SubjectId ACTOR = STATE.bootstrap().settlements().getFirst().residents().getFirst().id();
    private static final BodyPosition CURRENT = STATE.actorLocations().get(ACTOR).body();
    private static final BodyPosition EARLIER = new BodyPosition(CURRENT.x() + 32, CURRENT.y(), CURRENT.z());
    private static final SceneLease LEASE = SceneLease.forCause(new SceneLeaseId("lease:release-readiness"), STATE.bootstrap().worldId(),
            new ProductionWorkSceneCause(new SubjectId("job:production-release-readiness")), EARLIER.supportingSurface().support(),
            new SimInstant(0), 1, SceneLeaseStatus.DRAINING,
            List.of(new SceneMember(ACTOR, SceneLease.deterministicEntityId(STATE.bootstrap().worldId(), ACTOR))),
            Map.of(ACTOR, EARLIER), Set.of(), Optional.empty());

    @Test
    void presentMovingBodyDoesNotWaitForTheOldHandoffChunk() {
        assertFalse(FrontierV3SceneReleaseReadiness.awaitingEntityStorage(STATE, LEASE, id -> true,
                body -> { throw new AssertionError("a present body must be inspected directly"); }));
    }

    @Test
    void absentBodyWaitsForBothCurrentAndRetainedEntityColumns() {
        assertTrue(FrontierV3SceneReleaseReadiness.awaitingEntityStorage(STATE, LEASE, id -> false, body -> false));
        assertTrue(FrontierV3SceneReleaseReadiness.awaitingEntityStorage(STATE, LEASE, id -> false, CURRENT::equals));
        assertTrue(FrontierV3SceneReleaseReadiness.awaitingEntityStorage(STATE, LEASE, id -> false, EARLIER::equals));
        assertFalse(FrontierV3SceneReleaseReadiness.awaitingEntityStorage(STATE, LEASE, id -> false, body -> true),
                "readiness permits further inspection; it is not a death observation");
    }

    @Test
    void restartCannotClassifyMissingActorsBeforeBothEntityColumnsAreReady() {
        var recovering = LEASE.withStatus(SceneLeaseStatus.UNKNOWN_AFTER_RESTART);
        assertTrue(FrontierV3SceneReleaseReadiness.awaitingEntityStorage(STATE, recovering, id -> false, CURRENT::equals));
        assertTrue(FrontierV3SceneReleaseReadiness.awaitingEntityStorage(STATE, recovering, id -> false, EARLIER::equals));
        assertFalse(FrontierV3SceneReleaseReadiness.awaitingEntityStorage(STATE, recovering, id -> false, body -> true));
        assertEquals(ActorLifeStatus.ALIVE, STATE.actorLocations().get(ACTOR).condition().status(),
                "completed inspection readiness cannot itself classify an absent actor as dead");
    }

    @Test
    void availableBlocksAloneNeverProveEntityStorageReady() {
        assertFalse(FrontierV3SceneExecutor.entityStorageReady(true, false));
        assertFalse(FrontierV3SceneExecutor.entityStorageReady(false, true));
        assertFalse(FrontierV3SceneExecutor.entityStorageReady(false, false));
        assertTrue(FrontierV3SceneExecutor.entityStorageReady(true, true));
    }

    @Test
    void cargoLookupDoesNotDecodeAnUnrelatedHotWorkerSceneAsLogistics() {
        var hot = LEASE.withStatus(SceneLeaseStatus.HOT);
        assertTrue(FrontierV3CargoCarrierExecutor.activeLease(List.of(hot), lease -> {
            throw new AssertionError("cargo inspection must never receive a worker scene");
        }).isEmpty(),
                "a cargo observer must not ask a production scene for a logistics cause");
    }

    @Test
    void absentCargoWaitsForRetainedAndCurrentStorageButEvidenceIsAlwaysInspected() {
        var state = FrontierV3FixtureCatalog.routeSceneReturnConfiguration(new WorldId("frontier:cargo-readiness"), 41L).initialState();
        var operation = state.operations().values().iterator().next();
        var travel = operation.activeTravel().orElseThrow();
        var current = travel.cargoAnchor().surface().support();
        var retained = new BlockPosition(current.x() + 32, current.y(), current.z());
        var lease = SceneLease.atExactPositions(new SceneLeaseId("lease:cargo-readiness"), state.bootstrap().worldId(),
                operation.id(), operation.cargoId(), operation.currentPosition(), retained, new SimInstant(0), 1,
                SceneLeaseStatus.DRAINING, Optional.empty(), operation.participantIds().stream()
                        .map(actor -> new SceneMember(actor, SceneLease.deterministicEntityId(state.bootstrap().worldId(), actor))).toList(),
                travel.formation());
        assertTrue(FrontierV3SceneReleaseReadiness.awaitingCargoStorage(state, lease, id -> false, current::equals));
        assertTrue(FrontierV3SceneReleaseReadiness.awaitingCargoStorage(state, lease, id -> false, retained::equals));
        assertFalse(FrontierV3SceneReleaseReadiness.awaitingCargoStorage(state, lease, id -> false, support -> true));
        assertFalse(FrontierV3SceneReleaseReadiness.awaitingCargoStorage(state, lease, id -> true,
                support -> { throw new AssertionError("available evidence belongs to validation, not a storage wait"); }));
        assertFalse(FrontierV3SceneReleaseReadiness.awaitingCargoStorage(STATE, LEASE,
                id -> { throw new AssertionError("a worker scene has no cargo"); }, support -> false));
    }
}
