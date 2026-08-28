package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.ProjectionQuery;
import io.farfrontier.palemirror.frontier.v3.api.Revision;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;

/** Pure bounded audit projection compiler for the frontier runtime. */
final class FrontierWorldProjectionCompiler {
    private FrontierWorldProjectionCompiler() { }

    static FrontierWorldProjection compile(FrontierWorldState state, WorldId worldId, Revision revision,
                                           SimInstant instant, ProjectionQuery query) {
        FrontierBootstrap bootstrap = state.bootstrap();
        int residents = (int) state.humanPopulation().residentIds().stream()
                .filter(id -> state.actorLocations().get(id).condition().status() == ActorLifeStatus.ALIVE).count();
        int bioforms = (int) java.util.stream.Stream.concat(bootstrap.hive().bioforms().stream().map(Bioform::id), state.hiveColony().spawnedBioforms().keySet().stream())
                .filter(id -> state.actorLocations().get(id).condition().status() == ActorLifeStatus.ALIVE).count();
        return new FrontierWorldProjection(worldId, revision, instant, bootstrap.canonicalSha256(), bootstrap.settlements().size(),
                residents, bioforms, state.infection().size(), state.inventory().items().size(), state.productionJobs().size(),
                state.operations().size(),
                (int) state.physicalIntents().values().stream().filter(intent -> intent.status() == PhysicalIntentStatus.PREPARED).count(),
                (int) state.physicalIntents().values().stream().filter(intent -> intent.status() == PhysicalIntentStatus.UNKNOWN_AFTER_RESTART).count(),
                (int) state.sceneLeases().values().stream().filter(lease -> lease.status() != SceneLeaseStatus.CLOSED).count(),
                (int) state.sceneLeases().values().stream().filter(lease -> lease.status() == SceneLeaseStatus.UNKNOWN_AFTER_RESTART).count(),
                (int) state.ambientLeases().values().stream().filter(lease -> lease.status() != AmbientLeaseStatus.CLOSED).count(),
                (int) state.ambientLeases().values().stream().filter(lease -> lease.status() == AmbientLeaseStatus.UNKNOWN_AFTER_RESTART).count(),
                state.inventory().conflicts().size());
    }
}
