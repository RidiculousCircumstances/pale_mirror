package io.farfrontier.palemirror.frontier.v3.kernel;

import io.farfrontier.palemirror.frontier.v3.api.AdvanceResult;
import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CheckpointImage;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.CommandRejection;
import io.farfrontier.palemirror.frontier.v3.api.CommandReceipt;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.EngineStatus;
import io.farfrontier.palemirror.frontier.v3.api.EventId;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCanonicalState;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCanonicalStateAccess;
import io.farfrontier.palemirror.frontier.v3.api.FrontierEngine;
import io.farfrontier.palemirror.frontier.v3.api.FrontierEvent;
import io.farfrontier.palemirror.frontier.v3.api.FrontierProjection;
import io.farfrontier.palemirror.frontier.v3.api.ProjectionQuery;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.RejectionCode;
import io.farfrontier.palemirror.frontier.v3.api.Revision;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.TransactionId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.persistence.Durability;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Single-threaded bounded kernel implementation for deterministic tests. It intentionally has
 * no filesystem behaviour; Wave 2 supplies the durable store behind the same transaction facts.
 */
final class InMemoryFrontierEngine<S, P extends FrontierProjection> implements FrontierCanonicalStateAccess<S, P> {
    private final Thread ownerThread = Thread.currentThread();
    private final WorldId worldId;
    private final CommandPlanner<S> commandPlanner;
    private final ScheduledActionPlanner<S> scheduledPlanner;
    private final EventReducer<S> reducer;
    private final StateCodec<S> stateCodec;
    private final ProjectionMapper<S, P> projectionMapper;
    private final EngineLimits limits;
    private final TransactionCommitter transactionCommitter;
    private final StateValidator<S> stateValidator;
    private final FrontierExecutionMetrics executionMetrics;
    private ScheduledActionQueue schedules = new ScheduledActionQueue();
    private final Map<CommandId, CommandReceipt> receipts = new LinkedHashMap<>();
    private final List<TransactionRecord> transactions = new ArrayList<>();
    private S state;
    /** Immutable codec output for the current state revision; null means a newer WAL-backed state awaits its next snapshot. */
    private byte[] encodedState;
    private Revision revision = Revision.ZERO;
    private SimInstant instant;
    private EngineStatus status = EngineStatus.active();

    public InMemoryFrontierEngine(
            WorldId worldId,
            S initialState,
            SimInstant initialInstant,
            CommandPlanner<S> commandPlanner,
            ScheduledActionPlanner<S> scheduledPlanner,
            EventReducer<S> reducer,
            StateCodec<S> stateCodec,
            ProjectionMapper<S, P> projectionMapper,
            EngineLimits limits,
            List<ScheduledAction> initialSchedules
    ) {
        this(worldId, initialState, initialInstant, commandPlanner, scheduledPlanner, reducer, stateCodec,
                projectionMapper, limits, initialSchedules, TransactionCommitter.noOp(), StateValidator.none(), FrontierExecutionMetrics.noOp());
    }

    InMemoryFrontierEngine(
            WorldId worldId,
            S initialState,
            SimInstant initialInstant,
            CommandPlanner<S> commandPlanner,
            ScheduledActionPlanner<S> scheduledPlanner,
            EventReducer<S> reducer,
            StateCodec<S> stateCodec,
            ProjectionMapper<S, P> projectionMapper,
            EngineLimits limits,
            List<ScheduledAction> initialSchedules,
            TransactionCommitter transactionCommitter
    ) {
        this(worldId, initialState, initialInstant, commandPlanner, scheduledPlanner, reducer, stateCodec,
                projectionMapper, limits, initialSchedules, transactionCommitter, StateValidator.none(), FrontierExecutionMetrics.noOp());
    }

    InMemoryFrontierEngine(
            WorldId worldId,
            S initialState,
            SimInstant initialInstant,
            CommandPlanner<S> commandPlanner,
            ScheduledActionPlanner<S> scheduledPlanner,
            EventReducer<S> reducer,
            StateCodec<S> stateCodec,
            ProjectionMapper<S, P> projectionMapper,
            EngineLimits limits,
            List<ScheduledAction> initialSchedules,
            TransactionCommitter transactionCommitter,
            StateValidator<S> stateValidator,
            FrontierExecutionMetrics executionMetrics
    ) {
        this.worldId = Objects.requireNonNull(worldId, "world id");
        this.state = Objects.requireNonNull(initialState, "initial state");
        this.instant = Objects.requireNonNull(initialInstant, "initial instant");
        this.commandPlanner = Objects.requireNonNull(commandPlanner, "command planner");
        this.scheduledPlanner = Objects.requireNonNull(scheduledPlanner, "scheduled planner");
        this.reducer = Objects.requireNonNull(reducer, "reducer");
        this.stateCodec = Objects.requireNonNull(stateCodec, "state codec");
        this.projectionMapper = Objects.requireNonNull(projectionMapper, "projection mapper");
        this.limits = Objects.requireNonNull(limits, "limits");
        this.transactionCommitter = Objects.requireNonNull(transactionCommitter, "transaction committer");
        this.stateValidator = Objects.requireNonNull(stateValidator, "state validator");
        this.executionMetrics = Objects.requireNonNull(executionMetrics, "execution metrics");
        this.stateValidator.validateInitial(initialState);
        List<ScheduledAction> initial = List.copyOf(initialSchedules);
        if (initial.size() > limits.maxPendingSchedules()) {
            throw new IllegalArgumentException("initial scheduled-action capacity exceeded: " + initial.size() + ">" + limits.maxPendingSchedules());
        }
        initial.forEach(schedules::schedule);
        encodedState = stateCodec.encode(initialState);
        if (encodedState == null) throw new IllegalStateException("state codec returned null");
    }

    /** Compatibility constructor for existing kernel fixtures with a complete-state consumer. */
    InMemoryFrontierEngine(
            WorldId worldId, S initialState, SimInstant initialInstant,
            CommandPlanner<S> commandPlanner, ScheduledActionPlanner<S> scheduledPlanner,
            EventReducer<S> reducer, StateCodec<S> stateCodec, ProjectionMapper<S, P> projectionMapper,
            EngineLimits limits, List<ScheduledAction> initialSchedules, TransactionCommitter transactionCommitter,
            Consumer<S> stateValidator
    ) {
        this(worldId, initialState, initialInstant, commandPlanner, scheduledPlanner, reducer, stateCodec,
                projectionMapper, limits, initialSchedules, transactionCommitter, StateValidator.complete(stateValidator), FrontierExecutionMetrics.noOp());
    }

    static <S, P extends FrontierProjection> InMemoryFrontierEngine<S, P> recovered(
            FrontierEngineConfiguration<S, P> configuration, TransactionReplayer.ReplayResult<S> replay,
            List<CommandReceipt> receipts, List<TransactionRecord> retainedTransactions
    ) {
        InMemoryFrontierEngine<S, P> engine = new InMemoryFrontierEngine<>(configuration.worldId(), replay.state(), replay.instant(),
                configuration.commandPlanner(), configuration.scheduledPlanner(), configuration.reducer(), configuration.stateCodec(),
                configuration.projectionMapper(), configuration.limits(), replay.schedules(), configuration.transactionCommitter(),
                configuration.stateValidator(), configuration.executionMetrics());
        engine.revision = replay.revision();
        for (CommandReceipt receipt : receipts) {
            if (engine.receipts.put(receipt.commandId(), receipt) != null) throw new IllegalArgumentException("duplicate recovered command receipt");
        }
        engine.transactions.addAll(List.copyOf(retainedTransactions));
        return engine;
    }

    @Override
    public CommandResult submit(FrontierCommand command) {
        Objects.requireNonNull(command, "command");
        if (!onOwnerThread()) {
            return rejected(command, RejectionCode.WRONG_THREAD, "FrontierEngine may run only on its owning server thread");
        }
        if (!worldId.equals(command.worldId())) {
            return rejected(command, RejectionCode.WRONG_WORLD, "command world does not match this engine");
        }
        if (status.kind() == EngineStatus.Kind.QUARANTINED) {
            return rejected(command, RejectionCode.QUARANTINED, status.failureDetail().orElse("engine quarantined"));
        }
        pruneReceipts();
        if (!command.submittedAt().equals(instant)) {
            return rejected(command, RejectionCode.COMMAND_EXPIRED, "command must enter at the current simulation instant");
        }
        if (receipts.containsKey(command.id())) {
            return rejected(command, RejectionCode.DUPLICATE_COMMAND, "command id is already retained");
        }
        if (!revision.equals(command.expectedRevision())) {
            return rejected(command, RejectionCode.STALE_REVISION, "command expected revision does not match canonical revision");
        }
        if (command.scheduleBinding().isPresent() && (!revision.equals(command.scheduleBinding().orElseThrow().checkpointRevision())
                || !schedules.containsExact(command.scheduleBinding().orElseThrow().action()))) {
            return rejected(command, RejectionCode.STALE_SCHEDULE_BINDING,
                    "command schedule binding no longer matches the engine-owned action");
        }
        if (receipts.size() == limits.maxReceipts()) {
            return rejected(command, RejectionCode.RECEIPT_CAPACITY_EXHAUSTED, "receipt retention capacity is full");
        }
        if (transactions.size() == limits.maxTransactions()) {
            return rejected(command, RejectionCode.TRANSACTION_CAPACITY_EXHAUSTED, "transaction retention capacity is full");
        }
        try {
            CommandPlan plan;
            try (FrontierExecutionMetrics.Span ignored = measure(FrontierExecutionMetrics.Stage.COMMAND_PLAN, command.payload().type(), command.actor().value())) {
                plan = Objects.requireNonNull(commandPlanner.plan(state, command), "command plan");
            }
            if (plan instanceof CommandPlan.Rejected rejected) {
                return new CommandResult.Rejected(command.id(), revision, rejected.rejection());
            }
            CommandPlan.Accepted accepted = (CommandPlan.Accepted) plan;
            TransactionId transactionId = new TransactionId("transaction:revision-" + revision.next().value());
            CommandReceipt receipt = new CommandReceipt(command.id(), command.submittedAt(), transactionId, revision.next());
            commit(command.causes(), command.submittedAt(), accepted.events(), Optional.of(receipt));
            receipts.put(command.id(), receipt);
            return new CommandResult.Accepted(command.id(), transactionId, revision);
        } catch (RuntimeException error) {
            return quarantine(command, error);
        }
    }

    @Override
    public AdvanceResult advanceTo(SimInstant target, WorkBudget budget) {
        requireOwnerThread();
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(budget, "budget");
        if (target.compareTo(instant) < 0) {
            throw new IllegalArgumentException("simulation time cannot move backwards");
        }
        if (status.kind() == EngineStatus.Kind.QUARANTINED) {
            return advanceResult(List.of(), Optional.empty());
        }
        ScheduledWork work;
        try (FrontierExecutionMetrics.Span ignored = measure(FrontierExecutionMetrics.Stage.SCHEDULE_ALLOCATION, "due-actions", worldId.value())) {
            work = schedules.selectDue(target, budget);
        }
        observeQueue(target, schedules.size(), work.blockedActionOptional());
        List<TransactionId> completed = new ArrayList<>();
        for (ScheduledAction action : work.admitted()) {
            if (!schedules.isHead(action)) {
                continue;
            }
            if (transactions.size() == limits.maxTransactions()) {
                status = new EngineStatus(EngineStatus.Kind.QUARANTINED, "transaction retention capacity exhausted during due work");
                return advanceResult(completed, Optional.of(action));
            }
            try {
                List<ProposedEvent> planned;
                try (FrontierExecutionMetrics.Span ignored = measure(FrontierExecutionMetrics.Stage.SCHEDULE_PLAN, action.kind(), action.subject().value())) {
                    planned = List.copyOf(scheduledPlanner.plan(state, action));
                }
                if (planned.isEmpty()) {
                    throw new IllegalStateException("due action emitted no completion event: " + action.id().value());
                }
                List<ProposedEvent> events = new ArrayList<>(planned.size() + 1);
                if (!plannerCompletes(action, planned)) {
                    events.add(new ProposedEvent(action.subject(), new ScheduleEffect.Consumed(action.id())));
                }
                events.addAll(planned);
                CommandId cause = new CommandId("scheduler:" + action.id().value().replace(':', '/'));
                // A held physical continuation can deliberately retain an already-due action
                // until its owner observes or releases the physical work.  Its dueAt remains
                // the sole deadline/ordering fact, but an eventual durable disposition occurs
                // at the current canonical instant; writing the historical dueAt here would
                // make the WAL move backwards after an intervening physical command.
                completed.add(commit(CauseChain.root(cause), laterOf(instant, action.dueAt()), events, Optional.empty()));
            } catch (RuntimeException error) {
                status = new EngineStatus(EngineStatus.Kind.QUARANTINED, boundedFailure(
                        new IllegalStateException("scheduled action " + action.kind() + "/" + action.id().value() + " failed", error)));
                return advanceResult(completed, Optional.of(action));
            }
        }
        instant = target;
        pruneReceipts();
        observeQueue(target, schedules.size(), work.blockedActionOptional());
        return advanceResult(completed, work.blockedActionOptional());
    }

    @Override
    public P projection(ProjectionQuery query) {
        requireOwnerThread();
        return projectionMapper.project(state, worldId, revision, instant, Objects.requireNonNull(query, "query"));
    }

    @Override
    public FrontierCanonicalState<S> canonicalState() {
        requireOwnerThread();
        return new FrontierCanonicalState<>(worldId, revision, instant, state);
    }

    @Override
    public CheckpointImage checkpoint() {
        requireOwnerThread();
        return new CheckpointImage(worldId, revision, instant, currentEncodedState(), schedules.snapshot(), List.copyOf(receipts.values()));
    }

    @Override
    public Optional<SimInstant> nextScheduledInstantAfter(SimInstant after) {
        requireOwnerThread();
        return schedules.nextDueAfter(after);
    }

    @Override
    public void compact(Revision coveredRevision) {
        requireOwnerThread(); Objects.requireNonNull(coveredRevision, "covered revision");
        if (coveredRevision.compareTo(revision) > 0) throw new IllegalArgumentException("cannot compact beyond the current canonical revision");
        transactions.removeIf(record -> record.revision().compareTo(coveredRevision) <= 0);
    }

    @Override
    public EngineStatus status() {
        return status;
    }

    public List<TransactionRecord> transactions() {
        requireOwnerThread();
        return List.copyOf(transactions);
    }

    List<ScheduledAction> scheduledActions() {
        requireOwnerThread();
        return schedules.snapshot();
    }

    /**
     * Normal due work is acknowledged by the kernel. A planner may instead durably cancel,
     * consume, or reschedule its own due action when a prior physical observation has made its
     * original domain transition obsolete. This is deliberately narrow: a planner cannot
     * silently discard work, and a completion for any other action does not suppress the normal
     * head acknowledgement.
     */
    private static boolean plannerCompletes(ScheduledAction action, List<ProposedEvent> planned) {
        long completions = planned.stream().filter(event -> switch (event.payload()) {
            case ScheduleEffect.Cancelled cancelled -> cancelled.scheduleId().equals(action.id());
            case ScheduleEffect.Consumed consumed -> consumed.scheduleId().equals(action.id());
            case ScheduleEffect.Rescheduled rescheduled -> rescheduled.scheduleId().equals(action.id());
            default -> false;
        }).count();
        if (completions > 1L) {
            throw new IllegalStateException("due action emits multiple completion effects: " + action.id().value());
        }
        return completions == 1L;
    }

    private TransactionId commit(CauseChain causes, SimInstant eventInstant, List<ProposedEvent> proposed,
                                 Optional<CommandReceipt> acceptedCommandReceipt) {
        ProposedEvent attribution = proposed.isEmpty() ? null : proposed.getFirst();
        String kind = attribution == null ? "empty" : attribution.payload().type();
        String owner = attribution == null ? worldId.value() : attribution.subject().value();
        try (FrontierExecutionMetrics.Span ignored = measure(FrontierExecutionMetrics.Stage.TRANSACTION, kind, owner)) {
            return commitMeasured(causes, eventInstant, proposed, acceptedCommandReceipt);
        }
    }

    private static SimInstant laterOf(SimInstant first, SimInstant second) {
        return first.compareTo(second) >= 0 ? first : second;
    }

    private TransactionId commitMeasured(CauseChain causes, SimInstant eventInstant, List<ProposedEvent> proposed,
                                         Optional<CommandReceipt> acceptedCommandReceipt) {
        Revision nextRevision = revision.next();
        TransactionId transactionId = new TransactionId("transaction:revision-" + nextRevision.value());
        List<FrontierEvent> events = new ArrayList<>(proposed.size());
        S nextState = state;
        ScheduledActionQueue.Mutation nextSchedules = schedules.beginMutation();
        for (int index = 0; index < proposed.size(); index++) {
            ProposedEvent next = proposed.get(index);
            FrontierEvent event = new FrontierEvent(
                    FrontierEvent.SCHEMA_VERSION,
                    new EventId("event:revision-" + nextRevision.value() + "-" + index),
                    transactionId, worldId, nextRevision, eventInstant, next.subject(), causes, next.payload());
            if (event.payload() instanceof ScheduleEffect effect) {
                ScheduleEffectApplier.apply(nextSchedules, effect);
            } else {
                try {
                    try (FrontierExecutionMetrics.Span ignored = measure(FrontierExecutionMetrics.Stage.REDUCTION, event.payload().type(), event.subject().value())) {
                        nextState = Objects.requireNonNull(reducer.apply(nextState, event), "reducer state");
                    }
                } catch (RuntimeException error) {
                    throw new IllegalStateException("reducer failed for " + event.payload().type() + " at " + event.id().value(), error);
                }
            }
            events.add(event);
        }
        nextSchedules.requireCapacity(limits.maxPendingSchedules());
        // Reducers may construct several transient immutable aggregate snapshots for one
        // transaction. Validate their final state exactly once, before WAL durability and
        // before it becomes canonical; this keeps the failure boundary strict without making
        // every one-field state transition scan the entire 12-settlement world.
        if (nextState != state) {
            try (FrontierExecutionMetrics.Span ignored = measure(FrontierExecutionMetrics.Stage.VALIDATION, "canonical-state", worldId.value())) {
                stateValidator.validateTransition(state, nextState);
            }
        }
        // WAL commits are already durable before the state becomes authoritative. A complete
        // snapshot is needed only at the explicit checkpoint boundary; eagerly encoding every
        // immutable state transition turns ordinary COLD background work into repeated full
        // serialization. Schedule-only transactions retain the existing bytes, while a changed
        // world state marks the snapshot cache dirty until checkpoint() requests it.
        byte[] encoded = nextState == state ? encodedState : null;
        TransactionRecord transaction = new TransactionRecord(transactionId, worldId, nextRevision, eventInstant, events,
                acceptedCommandReceipt);
        Durability durability = events.stream().anyMatch(event -> event.payload().requiresDurableBeforeEffect())
                ? Durability.DURABLE_BEFORE_EFFECT : Durability.BATCHABLE;
        transactionCommitter.commit(transaction, durability);
        state = nextState;
        encodedState = encoded;
        revision = nextRevision;
        nextSchedules.commit();
        transactions.add(transaction);
        return transactionId;
    }

    private FrontierExecutionMetrics.Span measure(FrontierExecutionMetrics.Stage stage, String kind, String owner) {
        return FrontierExecutionMetrics.safelyBegin(executionMetrics, stage, kind, owner);
    }

    private void observeQueue(SimInstant observedAt, int queueDepth, Optional<ScheduledAction> deferred) {
        FrontierExecutionMetrics.safelyObserveQueue(executionMetrics, observedAt, queueDepth, deferred);
    }

    private byte[] currentEncodedState() {
        if (encodedState == null) {
            encodedState = stateCodec.encode(state);
            if (encodedState == null) throw new IllegalStateException("state codec returned null");
        }
        return encodedState;
    }

    private CommandResult.Rejected rejected(FrontierCommand command, RejectionCode code, String detail) {
        return new CommandResult.Rejected(command.id(), revision, new CommandRejection(code, detail));
    }

    private CommandResult.Rejected quarantine(FrontierCommand command, RuntimeException error) {
        status = new EngineStatus(EngineStatus.Kind.QUARANTINED, boundedFailure(error));
        return rejected(command, RejectionCode.INVARIANT_FAILURE, status.failureDetail().orElseThrow());
    }

    private AdvanceResult advanceResult(List<TransactionId> completed, Optional<ScheduledAction> deferred) {
        long lag = deferred.map(action -> Math.max(0L, instant.ticks() - action.dueAt().ticks())).orElse(0L);
        return new AdvanceResult(revision, instant, completed, deferred, lag, status);
    }

    private void pruneReceipts() {
        long oldest = safeOldestReceiptInstant();
        receipts.entrySet().removeIf(entry -> entry.getValue().submittedAt().ticks() < oldest);
    }

    private long safeOldestReceiptInstant() {
        long window = limits.receiptWindowTicks();
        return instant.ticks() < window ? 0L : instant.ticks() - window;
    }

    private boolean onOwnerThread() {
        return Thread.currentThread() == ownerThread;
    }

    private void requireOwnerThread() {
        if (!onOwnerThread()) {
            throw new IllegalStateException("FrontierEngine may run only on its owning server thread");
        }
    }

    private static String boundedFailure(RuntimeException error) {
        String detail = error.getClass().getSimpleName() + ": " + Objects.toString(error.getMessage(), "no detail");
        Throwable cause = error.getCause();
        if (cause != null && cause != error) {
            detail += " <- " + cause.getClass().getSimpleName() + ": " + Objects.toString(cause.getMessage(), "no detail");
        }
        return detail.length() <= 240 ? detail : detail.substring(0, 240);
    }
}
