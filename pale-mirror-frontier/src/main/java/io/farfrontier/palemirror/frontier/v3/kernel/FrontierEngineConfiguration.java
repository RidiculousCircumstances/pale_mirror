package io.farfrontier.palemirror.frontier.v3.kernel;

import io.farfrontier.palemirror.frontier.v3.api.FrontierProjection;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Complete pure policy bundle for one canonical Frontier v3 world engine. */
public record FrontierEngineConfiguration<S, P extends FrontierProjection>(
        WorldId worldId, S initialState, SimInstant initialInstant,
        CommandPlanner<S> commandPlanner, ScheduledActionPlanner<S> scheduledPlanner,
        EventReducer<S> reducer, StateCodec<S> stateCodec, ProjectionMapper<S, P> projectionMapper,
        EngineLimits limits, List<ScheduledAction> initialSchedules, TransactionCommitter transactionCommitter,
        StateValidator<S> stateValidator, FrontierExecutionMetrics executionMetrics
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
        Objects.requireNonNull(stateValidator, "state validator");
        executionMetrics = Objects.requireNonNull(executionMetrics, "execution metrics");
    }

    /** Compatibility constructor for small kernel fixtures that have no aggregate-specific audit. */
    public FrontierEngineConfiguration(
            WorldId worldId, S initialState, SimInstant initialInstant,
            CommandPlanner<S> commandPlanner, ScheduledActionPlanner<S> scheduledPlanner,
            EventReducer<S> reducer, StateCodec<S> stateCodec, ProjectionMapper<S, P> projectionMapper,
            EngineLimits limits, List<ScheduledAction> initialSchedules, TransactionCommitter transactionCommitter
    ) {
        this(worldId, initialState, initialInstant, commandPlanner, scheduledPlanner, reducer, stateCodec,
                projectionMapper, limits, initialSchedules, transactionCommitter, StateValidator.none(), FrontierExecutionMetrics.noOp());
    }

    /** Compatibility constructor with an explicit transition validator but no metrics adapter. */
    public FrontierEngineConfiguration(
            WorldId worldId, S initialState, SimInstant initialInstant,
            CommandPlanner<S> commandPlanner, ScheduledActionPlanner<S> scheduledPlanner,
            EventReducer<S> reducer, StateCodec<S> stateCodec, ProjectionMapper<S, P> projectionMapper,
            EngineLimits limits, List<ScheduledAction> initialSchedules, TransactionCommitter transactionCommitter,
            StateValidator<S> stateValidator
    ) {
        this(worldId, initialState, initialInstant, commandPlanner, scheduledPlanner, reducer, stateCodec,
                projectionMapper, limits, initialSchedules, transactionCommitter, stateValidator, FrontierExecutionMetrics.noOp());
    }

    /** Rebinds the pure aggregate to the owning server host's mandatory write-ahead boundary. */
    public FrontierEngineConfiguration<S, P> withTransactionCommitter(TransactionCommitter replacement) {
        return new FrontierEngineConfiguration<>(worldId, initialState, initialInstant, commandPlanner, scheduledPlanner,
                reducer, stateCodec, projectionMapper, limits, initialSchedules, replacement, stateValidator, executionMetrics);
    }

    /** Installs the aggregate's complete invariant audit at every commit/recovery boundary. */
    public FrontierEngineConfiguration<S, P> withStateValidator(Consumer<S> replacement) {
        return new FrontierEngineConfiguration<>(worldId, initialState, initialInstant, commandPlanner, scheduledPlanner,
                reducer, stateCodec, projectionMapper, limits, initialSchedules, transactionCommitter, StateValidator.complete(replacement), executionMetrics);
    }

    /** Installs an aggregate-specific validator that can prove safe incremental transitions. */
    public FrontierEngineConfiguration<S, P> withTransitionValidator(StateValidator<S> replacement) {
        return new FrontierEngineConfiguration<>(worldId, initialState, initialInstant, commandPlanner, scheduledPlanner,
                reducer, stateCodec, projectionMapper, limits, initialSchedules, transactionCommitter, replacement, executionMetrics);
    }

    /** Adds an observational timing adapter; it is never part of canonical state or persistence. */
    public FrontierEngineConfiguration<S, P> withExecutionMetrics(FrontierExecutionMetrics replacement) {
        return new FrontierEngineConfiguration<>(worldId, initialState, initialInstant, commandPlanner, scheduledPlanner,
                reducer, stateCodec, projectionMapper, limits, initialSchedules, transactionCommitter, stateValidator, replacement);
    }
}
