package io.farfrontier.palemirror.internal.frontier.v3;

import com.google.gson.JsonParser;
import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.runtime.FrontierWorldRuntimeDefinition;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FrontierV3PatrolSpatialDiagnosticTest {
    @Test void exposesActualPoseSeparatelyFromRetainedSemanticCheckpoint(@TempDir Path directory) {
        var configuration = FrontierV3FixtureCatalog.routePatrolConfiguration(new WorldId("frontier:patrol-diagnostic"), 713L);
        var runtime = FrontierV3ServerRuntime.start(configuration, new FrontierFileStore(directory,
                FrontierWorldRuntimeDefinition.payloadCodecs()), 10_000);
        try {
            var state = runtime.decodedState().orElseThrow();
            var patrol = state.strategicPlans().routePatrols().values().iterator().next();
            var candidate = FrontierRoutePatrolSceneSupport.candidates(state).getFirst();
            var lease = SceneLease.forCause(new SceneLeaseId("lease:patrol-diagnostic"), state.bootstrap().worldId(),
                    new RoutePatrolSceneCause(patrol.taskId()), candidate.handoffPosition(), SimInstant.ZERO, 1L, SceneLeaseStatus.PREPARED,
                    patrol.memberIds().stream().map(actor -> new SceneMember(actor,
                            SceneLease.deterministicEntityId(state.bootstrap().worldId(), actor))).toList(), Set.of(), Optional.empty());
            var scoped = state.prepareSceneLease(lease);
            var original = new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec(scoped.bootstrap()).encode(scoped);
            var json = JsonParser.parseString(FrontierV3DiagnosticJson.render("scene", patrol.taskId().value(),
                    runtime.checkpointImage().orElseThrow(), scoped, Optional.empty())
                    .substring(FrontierV3DiagnosticJson.PREFIX.length())).getAsJsonObject();
            var members = json.getAsJsonArray("patrolSpatialMembers");
            assertEquals(patrol.memberIds().size(), members.size());
            for (var entry : members) {
                var value = entry.getAsJsonObject();
                var actor = new SubjectId(value.get("actor").getAsString());
                assertEquals(1L, value.get("routeRevision").getAsLong());
                assertEquals(-1, value.get("approachCursor").getAsInt());
                assertEquals(JsonParser.parseString(FrontierV3DiagnosticJson.position(scoped.actorLocations().get(actor).body())), value.get("actualBody"));
            }
            org.junit.jupiter.api.Assertions.assertArrayEquals(original,
                    new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec(scoped.bootstrap()).encode(scoped));
        } finally { runtime.shutdown(); }
    }
}
