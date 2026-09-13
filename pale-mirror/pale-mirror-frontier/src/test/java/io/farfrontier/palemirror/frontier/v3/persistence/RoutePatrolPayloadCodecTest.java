package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.RoutePatrolBlockReason;
import io.farfrontier.palemirror.frontier.v3.model.RoutePatrolBlocked;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoutePatrolPayloadCodecTest {
    @Test
    void blockedPayloadRoundTripsTaskBeforeReason() {
        RoutePatrolBlocked blocked = new RoutePatrolBlocked(new SubjectId("task:development-route-patrol"),
                RoutePatrolBlockReason.OCCUPIED_NEXT_BODY);

        var codecs = FrontierWorldRuntimeDefinition.payloadCodecs();
        assertEquals(blocked, codecs.decode(blocked.type(), codecs.encode(blocked)));
    }
}
