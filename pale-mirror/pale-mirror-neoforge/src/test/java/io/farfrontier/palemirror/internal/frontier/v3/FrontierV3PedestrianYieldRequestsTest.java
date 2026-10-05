package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class FrontierV3PedestrianYieldRequestsTest {
    @Test void courtesyRequestCannotAddressAnotherIncarnationOrAnAlreadyDepartedBody() {
        UUID entity = UUID.randomUUID();
        var identity = new ActorBodyId(new SubjectId("resident:7-6"), 1L);
        var bounds = new AABB(128.2, 64, 12.2, 128.8, 65.9, 12.8);
        var blocker = new FrontierV3PedestrianYieldRequests.Blocker(identity, entity, bounds);
        assertTrue(FrontierV3PedestrianYieldRequests.matches(blocker, entity, identity, bounds));
        assertFalse(FrontierV3PedestrianYieldRequests.matches(blocker, UUID.randomUUID(), identity, bounds));
        assertFalse(FrontierV3PedestrianYieldRequests.matches(blocker, entity,
                new ActorBodyId(identity.actorId(), 2L), bounds));
        assertFalse(FrontierV3PedestrianYieldRequests.matches(blocker, entity, identity, bounds.move(3, 0, 0)));
    }
}
