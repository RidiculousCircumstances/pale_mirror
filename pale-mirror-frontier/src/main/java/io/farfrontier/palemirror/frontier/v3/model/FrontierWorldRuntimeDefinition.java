package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierProjection;
import io.farfrontier.palemirror.frontier.v3.api.ProjectionQuery;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan;
import io.farfrontier.palemirror.frontier.v3.kernel.EngineLimits;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodecs;
import io.farfrontier.palemirror.frontier.v3.kernel.TransactionCommitter;

import java.util.List;

/** Pure composition root for the fresh 1024x1024 Frontier v3 profile. */
public final class FrontierWorldRuntimeDefinition {
    private FrontierWorldRuntimeDefinition() { }

    public static FrontierEngineConfiguration<FrontierWorldState, FrontierWorldProjection> configuration(WorldId worldId, long seed) {
        FrontierBootstrap bootstrap = FrontierBootstrapper.create(worldId, seed);
        FrontierWorldState initial = FrontierWorldState.initial(bootstrap);
        return new FrontierEngineConfiguration<>(worldId, initial, SimInstant.ZERO,
                (state, command) -> new CommandPlan.Rejected(new io.farfrontier.palemirror.frontier.v3.api.CommandRejection(
                        io.farfrontier.palemirror.frontier.v3.api.RejectionCode.REJECTED_BY_POLICY, "no v3 world command handler is installed")),
                (state, action) -> List.of(),
                (state, event) -> { throw new IllegalStateException("unregistered v3 world event: " + event.payload().type()); },
                new FrontierWorldStateCodec(), FrontierWorldRuntimeDefinition::projection,
                new EngineLimits(4_096, 1_200L, 4_096), List.of(), TransactionCommitter.noOp());
    }

    public static PayloadCodecs payloadCodecs() { return new PayloadCodecs(List.of()); }

    private static FrontierWorldProjection projection(
            FrontierWorldState state, WorldId worldId, io.farfrontier.palemirror.frontier.v3.api.Revision revision,
            SimInstant instant, ProjectionQuery query
    ) {
        FrontierBootstrap bootstrap = state.bootstrap();
        int residents = bootstrap.settlements().stream().mapToInt(settlement -> settlement.residents().size()).sum();
        return new FrontierWorldProjection(worldId, revision, instant, bootstrap.canonicalSha256(), bootstrap.settlements().size(),
                residents, bootstrap.hive().bioforms().size(), state.infection().size(), state.inventory().items().size());
    }
}
