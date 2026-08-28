package io.farfrontier.palemirror.frontier.v3.kernel;

import io.farfrontier.palemirror.frontier.v3.api.FrontierProjection;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;

import java.util.List;
import java.util.Objects;

/** Complete pure policy bundle for one canonical Frontier v3 world engine. */
public record FrontierEngineConfiguration<S, P extends FrontierProjection>(
        WorldId worldId, S initialState, SimInstant initialInstant,
        CommandPlanner<S> commandPlanner, ScheduledActionPlanner<S> scheduledPlanner,
        EventReducer<S> reducer, StateCodec<S> stateCodec, ProjectionMapper<S, P> projectionMapper,
        EngineLimits limits, List<ScheduledAction> initialSchedules, TransactionCommitter transactionCommitter
) {
    public FrontierEngineConfiguration {
        Objects.requireNonNull(worldId, "world id");
        Objects.requireNonNull(initialState, "initial state");
        Objects.requireNonNull(initialInstant, "initial instant");
        Objects.requireNonNull(commandPlanner, "command planner");
        Objects.requireNonNull(scheduledPlanner, "scheduled planner");
        Objects.requireNonNull(reducer, "reducer");
        Objects.requireNonNull(stateCodec, "state codec");
        Objects.requireNonNull(projectionMapper, "projection mapper");
        Objects.requireNonNull(limits, "limits");
        initialSchedules = List.copyOf(initialSchedules);
        Objects.requireNonNull(transactionCommitter, "transaction committer");
    }

    /** Rebinds the pure aggregate to the owning server host's mandatory write-ahead boundary. */
    public FrontierEngineConfiguration<S, P> withTransactionCommitter(TransactionCommitter replacement) {
        return new FrontierEngineConfiguration<>(worldId, initialState, initialInstant, commandPlanner, scheduledPlanner,
                reducer, stateCodec, projectionMapper, limits, initialSchedules, replacement);
    }
}
