package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class FrontierSceneLeaseIdentityTest {
    @Test
    void physicalActorIdentityDoesNotChangeBetweenSceneLeasesOrWorlds() {
        SubjectId actor = new SubjectId("resident:identity-stability");
        WorldId world = new WorldId("frontier:identity-stability");

        assertEquals(SceneLease.deterministicEntityId(world, actor),
                SceneLease.deterministicEntityId(world, new SceneLeaseId("lease:first"), actor));
        assertEquals(SceneLease.deterministicEntityId(world, new SceneLeaseId("lease:first"), actor),
                SceneLease.deterministicEntityId(world, new SceneLeaseId("lease:second"), actor));
        assertNotEquals(SceneLease.deterministicEntityId(world, actor),
                SceneLease.deterministicEntityId(new WorldId("frontier:other-world"), actor));
    }
}
