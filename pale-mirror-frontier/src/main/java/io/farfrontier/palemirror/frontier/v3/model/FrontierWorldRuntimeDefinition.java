package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierProjection;
import io.farfrontier.palemirror.frontier.v3.api.FixedRatio;
import io.farfrontier.palemirror.frontier.v3.api.ProjectionQuery;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.kernel.CommandPlan;
import io.farfrontier.palemirror.frontier.v3.kernel.EngineLimits;
import io.farfrontier.palemirror.frontier.v3.kernel.FrontierEngineConfiguration;
import io.farfrontier.palemirror.frontier.v3.kernel.PayloadCodecs;
import io.farfrontier.palemirror.frontier.v3.kernel.ScheduledAction;
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
                FrontierWorldRuntimeDefinition::planScheduled,
                (state, event) -> event.payload() instanceof InfectionChanged changed
                        ? state.withInfection(changed.cell(), changed.intensity())
                        : fail(event.payload().type()),
                new FrontierWorldStateCodec(), FrontierWorldRuntimeDefinition::projection,
                new EngineLimits(4_096, 1_200L, 4_096), List.of(pulse(1, 100)), TransactionCommitter.noOp());
    }

    public static PayloadCodecs payloadCodecs() { return FrontierWorldPayloadCodecs.create(); }

    private static List<io.farfrontier.palemirror.frontier.v3.api.ProposedEvent> planScheduled(FrontierWorldState state, ScheduledAction action) {
        if (!"frontier.infection.pulse".equals(action.kind())) throw new IllegalStateException("unknown v3 scheduled action: " + action.kind());
        List<InfectionCell> cells = state.infection().keySet().stream().sorted(java.util.Comparator.comparingInt(InfectionCell::x).thenComparingInt(InfectionCell::z)).toList();
        InfectionCell source = cells.get(Math.floorMod(action.weight() - 1, cells.size()));
        InfectionCell target = switch (Math.floorMod(action.weight() - 1, 4)) {
            case 0 -> new InfectionCell(source.x() + 1, source.z()); case 1 -> new InfectionCell(source.x(), source.z() + 1);
            case 2 -> new InfectionCell(source.x() - 1, source.z()); default -> new InfectionCell(source.x(), source.z() - 1);
        };
        if (!state.bootstrap().bounds().contains(target.originAtY(64))) target = source;
        FixedRatio prior = state.infection().getOrDefault(target, new FixedRatio(io.farfrontier.palemirror.frontier.v3.api.FixedScalar.ZERO));
        long raw = Math.min(io.farfrontier.palemirror.frontier.v3.api.FixedScalar.SCALE, Math.addExact(prior.value().raw(), 125_000L));
        return List.of(new ProposedEvent(action.subject(), new InfectionChanged(target, new FixedRatio(new io.farfrontier.palemirror.frontier.v3.api.FixedScalar(raw)))),
                new ProposedEvent(action.subject(), new io.farfrontier.palemirror.frontier.v3.kernel.ScheduleEffect.Created(pulse(action.weight() + 1, action.dueAt().ticks() + 100L))));
    }
    private static ScheduledAction pulse(int ordinal, long due) { return new ScheduledAction(new io.farfrontier.palemirror.frontier.v3.api.ScheduleId("schedule:infection-pulse-" + ordinal), new SimInstant(due), 0, new io.farfrontier.palemirror.frontier.v3.api.SubjectId("hive:frontier"), "frontier.infection.pulse", ordinal); }
    private static FrontierWorldState fail(String type) { throw new IllegalStateException("unregistered v3 world event: " + type); }

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
