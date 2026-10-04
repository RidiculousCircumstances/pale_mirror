package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FrontierSceneLeaseIdentityTest {
    @Test
    void sceneReferencesCanonicalBodiesWithoutRetainingOrSerializingAnotherPosition() {
        var world = new WorldId("frontier:sole-participant-position");
        var actor = new SubjectId("resident:sole-participant-position");
        var lease = SceneLease.forCause(new SceneLeaseId("lease:sole-participant-position"), world,
                new ResourceSiteHarvestSceneCause(new SubjectId("site:position"), new SubjectId("job:site-harvest-position")),
                new BlockPosition(0, 64, 0), io.farfrontier.palemirror.frontier.v3.api.SimInstant.ZERO, 1L,
                SceneLeaseStatus.PREPARED, java.util.List.of(new SceneMember(actor, SceneLease.deterministicEntityId(world, actor))),
                java.util.Set.of(), java.util.Optional.empty());
        var initial = new ActorLocation(new BodyPosition(0, 65, 0), ActorCondition.HEALTHY, ActorKind.RESIDENT);
        var observed = initial.withBody(new BodyPosition(4, 65, 3));
        assertEquals(initial.body(), lease.memberBody(java.util.Map.of(actor, initial), actor));
        assertEquals(observed.body(), lease.memberBody(java.util.Map.of(actor, observed), actor));
        assertThrows(IllegalArgumentException.class, () -> lease.memberBody(java.util.Map.of(), actor));
        assertThrows(IllegalArgumentException.class, () -> lease.memberBody(java.util.Map.of(actor, initial), new SubjectId("resident:foreign")));
        assertFalse(java.util.Arrays.stream(SceneLease.class.getRecordComponents()).anyMatch(component ->
                component.getName().equals("memberPositions") || component.getType().equals(java.util.Map.class)),
                "durable scene state must have no participant-position store");
        var codecs = io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition.payloadCodecs();
        var prepared = new ResourceSiteHarvestSceneLeasePrepared(lease);
        assertEquals(prepared, codecs.decode(prepared.type(), codecs.encode(prepared)));
    }

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
