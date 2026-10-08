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
            List.of(new SceneMember(ACTOR, SceneLease.deterministicEntityId(STATE.bootstrap().worldId(), ACTOR))), Set.of(), Optional.empty());

    @Test
    void presentMovingBodyDoesNotWaitForTheOldHandoffChunk() {
        assertFalse(FrontierV3SceneReleaseReadiness.awaitingEntityStorage(STATE, LEASE, id -> true,
                body -> { throw new AssertionError("a present body must be inspected directly"); }));
    }

    @Test
    void absentBodyWaitsForTheSingleAuthoritativePositionNotAnOldSceneAnchor() {
        assertTrue(FrontierV3SceneReleaseReadiness.awaitingEntityStorage(STATE, LEASE, id -> false, body -> false));
        assertFalse(FrontierV3SceneReleaseReadiness.awaitingEntityStorage(STATE, LEASE, id -> false, CURRENT::equals),
                "a scene no longer retains an independent old body position to wait for");
        assertTrue(FrontierV3SceneReleaseReadiness.awaitingEntityStorage(STATE, LEASE, id -> false, EARLIER::equals));
        assertFalse(FrontierV3SceneReleaseReadiness.awaitingEntityStorage(STATE, LEASE, id -> false, body -> true),
                "readiness permits further inspection; it is not a death observation");
    }

    @Test
    void restartCannotClassifyMissingActorsBeforeTheCanonicalEntityColumnIsReady() {
        var recovering = LEASE.withStatus(SceneLeaseStatus.UNKNOWN_AFTER_RESTART);
        assertFalse(FrontierV3SceneReleaseReadiness.awaitingEntityStorage(STATE, recovering, id -> false, CURRENT::equals));
        assertTrue(FrontierV3SceneReleaseReadiness.awaitingEntityStorage(STATE, recovering, id -> false, EARLIER::equals));
        assertFalse(FrontierV3SceneReleaseReadiness.awaitingEntityStorage(STATE, recovering, id -> false, body -> true));
        assertEquals(ActorLifeStatus.ALIVE, STATE.actorLocations().get(ACTOR).condition().status(),
                "completed inspection readiness cannot itself classify an absent actor as dead");
    }

    @Test
    void nativeQueuedBodyWaitsForItsReceiptButMissingOrContradictoryEvidenceDoesNot() {
        assertTrue(FrontierV3SceneReleaseReadiness.awaitingEntityStorage(STATE, LEASE,
                id -> false, body -> true, id -> id.equals(LEASE.members().getFirst().entityId())),
                "loaded storage can still contain the exact hidden body awaiting the store pass");
        assertFalse(FrontierV3SceneReleaseReadiness.awaitingEntityStorage(STATE, LEASE,
                id -> false, body -> true, id -> false), "an empty unload queue cannot hide unexplained absence");
        assertFalse(FrontierV3SceneReleaseReadiness.awaitingEntityStorage(STATE, LEASE,
                id -> true, body -> true, id -> true), "an existing departure receipt must be validated, not waited away");
    }

    @Test
    void availableBlocksAloneNeverProveEntityStorageReady() {
        assertFalse(FrontierV3SceneExecutor.entityStorageReady(true, false));
        assertFalse(FrontierV3SceneExecutor.entityStorageReady(false, true));
        assertFalse(FrontierV3SceneExecutor.entityStorageReady(false, false));
        assertTrue(FrontierV3SceneExecutor.entityStorageReady(true, true));
    }


}
