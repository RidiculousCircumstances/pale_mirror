package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class SceneLeaseRecoveryTransitionTest {
    private static final SceneLeaseId ID = new SceneLeaseId("lease:recovery-transition");
    private static SceneLease lease(SceneLeaseStatus status) {
        WorldId world = new WorldId("world:recovery-transition");
        SubjectId actor = new SubjectId("actor:recovery-transition");
        BlockPosition support = new BlockPosition(0, 64, 0);
        return SceneLease.forCause(ID, world, new ProductionWorkSceneCause(new SubjectId("job:production-recovery-transition")),
                support, SimInstant.ZERO, 7, status,
                List.of(new SceneMember(actor, SceneLease.deterministicEntityId(world, actor))), Set.of(), Optional.empty());
    }

    @Test
    void preparedTransitionIsARecoveryEdgeNotCreationOrRewindOfAnActiveScene() {
        var transition = new SceneLeaseTransition(ID, SceneLeaseStatus.PREPARED);
        assertTrue(transition.appliesTo(lease(SceneLeaseStatus.CONFLICT)));
        assertTrue(transition.appliesTo(lease(SceneLeaseStatus.UNKNOWN_AFTER_RESTART)));
        for (var status : List.of(SceneLeaseStatus.PREPARED, SceneLeaseStatus.HOT, SceneLeaseStatus.DRAINING, SceneLeaseStatus.CLOSED)) {
            assertFalse(transition.appliesTo(lease(status)));
        }
        assertFalse(transition.appliesTo(null));
        assertFalse(new SceneLeaseTransition(new SceneLeaseId("lease:foreign"), SceneLeaseStatus.PREPARED)
                .appliesTo(lease(SceneLeaseStatus.CONFLICT)));
    }

    @Test
    void recoveryTargetRoundTripsButDirectClosureStillRequiresItsOwnProtocol() {
        var transition = new SceneLeaseTransition(ID, SceneLeaseStatus.PREPARED);
        var codecs = FrontierWorldPayloadCodecs.sceneLifecycleCodecs();
        assertEquals(transition, codecs.decode(transition.type(), codecs.encode(transition)));
        assertThrows(IllegalArgumentException.class, () -> new SceneLeaseTransition(ID, SceneLeaseStatus.CLOSED));
    }
}
