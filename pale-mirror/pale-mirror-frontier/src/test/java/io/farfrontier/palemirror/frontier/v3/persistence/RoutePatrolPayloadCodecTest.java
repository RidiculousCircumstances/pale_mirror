package io.farfrontier.palemirror.frontier.v3.persistence;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RoutePatrolPayloadCodecTest {
    @Test
    void patrolPayloadsRetainTheCompleteCrewExecutionAndRejectTruncatedAuthority() {
        var state = FrontierV3FixtureCatalog.routePatrolConfiguration(new WorldId("frontier:patrol-codecs"), 713L).initialState();
        var patrol = state.strategicPlans().routePatrols().values().iterator().next();
        var group = RoutePatrolExecutionAuthority.current(state, patrol);
        var codecs = FrontierWorldRuntimeDefinition.payloadCodecs();
        var payloads = java.util.List.of(
                new RoutePatrolStarted(patrol, group),
                RoutePatrolDiagnosticProducer.OCCUPIED_NEXT_BODY.create(patrol.taskId(), group),
                RoutePatrolFailureDiagnosticProducer.memberLost(patrol.taskId(), group),
                new RoutePatrolFormationAdvanced(patrol.taskId(), group),
                new RoutePatrolObstructionConfirmed(patrol.taskId(), patrol.route().getFirst(), group),
                new RoutePatrolFormationObserved(patrol.taskId(), new SceneLeaseId("lease:patrol-codecs"),
                        FrontierRoutePatrolSceneSupport.bodies(patrol.advanceFormation()), group));
        for (var payload : payloads) {
            byte[] bytes = codecs.encode(payload);
            assertEquals(payload, codecs.decode(payload.type(), bytes));
            assertThrows(IllegalArgumentException.class, () -> codecs.decode(payload.type(),
                    java.util.Arrays.copyOf(bytes, bytes.length - 1)));
        }
    }
}
