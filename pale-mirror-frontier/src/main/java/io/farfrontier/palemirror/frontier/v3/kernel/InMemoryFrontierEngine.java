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

/**
 * Single-threaded bounded kernel implementation for deterministic tests. It intentionally has
 * no filesystem behaviour; Wave 2 supplies the durable store behind the same transaction facts.
 */
final class InMemoryFrontierEngine<S, P extends FrontierProjection> implements FrontierEngine<P> {
    private final Thread ownerThread = Thread.currentThread();
    private final WorldId worldId;
    private final CommandPlanner<S> commandPlanner;
    private final ScheduledActionPlanner<S> scheduledPlanner;
    private final EventReducer<S> reducer;
    private final StateCodec<S> stateCodec;
    private final ProjectionMapper<S, P> projectionMapper;
    private final EngineLimits limits;
    private final TransactionCommitter transactionCommitter;
    private ScheduledActionQueue schedules = new ScheduledActionQueue();
    private final Map<CommandId, CommandReceipt> receipts = new LinkedHashMap<>();
    private final List<TransactionRecord> transactions = new ArrayList<>();
    private S state;
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
                projectionMapper, limits, initialSchedules, TransactionCommitter.noOp());
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
        List.copyOf(initialSchedules).forEach(schedules::schedule);
        stateCodec.encode(initialState);
    }

    static <S, P extends FrontierProjection> InMemoryFrontierEngine<S, P> recovered(
            FrontierEngineConfiguration<S, P> configuration, TransactionReplayer.ReplayResult<S> replay,
            List<CommandReceipt> receipts, List<TransactionRecord> retainedTransactions
    ) {
        InMemoryFrontierEngine<S, P> engine = new InMemoryFrontierEngine<>(configuration.worldId(), replay.state(), replay.instant(),
                configuration.commandPlanner(), configuration.scheduledPlanner(), configuration.reducer(), configuration.stateCodec(),
                configuration.projectionMapper(), configuration.limits(), replay.schedules(), configuration.transactionCommitter());
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
        if (receipts.size() == limits.maxReceipts()) {
            return rejected(command, RejectionCode.RECEIPT_CAPACITY_EXHAUSTED, "receipt retention capacity is full");
        }
        if (transactions.size() == limits.maxTransactions()) {
            return rejected(command, RejectionCode.TRANSACTION_CAPACITY_EXHAUSTED, "transaction retention capacity is full");
        }
        try {
            CommandPlan plan = Objects.requireNonNull(commandPlanner.plan(state, command), "command plan");
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
        ScheduledWork work = schedules.selectDue(target, budget);
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
                List<ProposedEvent> planned = List.copyOf(scheduledPlanner.plan(state, action));
                if (planned.isEmpty()) {
                    throw new IllegalStateException("due action emitted no completion event: " + action.id().value());
                }
                List<ProposedEvent> events = new ArrayList<>(planned.size() + 1);
                events.add(new ProposedEvent(action.subject(), new ScheduleEffect.Consumed(action.id())));
                events.addAll(planned);
                CommandId cause = new CommandId("scheduler:" + action.id().value().replace(':', '/'));
                completed.add(commit(CauseChain.root(cause), action.dueAt(), events, Optional.empty()));
            } catch (RuntimeException error) {
                status = new EngineStatus(EngineStatus.Kind.QUARANTINED, boundedFailure(error));
                return advanceResult(completed, Optional.of(action));
            }
        }
        instant = target;
        pruneReceipts();
        return advanceResult(completed, work.blockedActionOptional());
    }

    @Override
    public P projection(ProjectionQuery query) {
        requireOwnerThread();
        return projectionMapper.project(state, worldId, revision, instant, Objects.requireNonNull(query, "query"));
    }

    @Override
    public CheckpointImage checkpoint() {
        requireOwnerThread();
        return new CheckpointImage(worldId, revision, instant, stateCodec.encode(state), schedules.snapshot(), List.copyOf(receipts.values()));
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

    private TransactionId commit(CauseChain causes, SimInstant eventInstant, List<ProposedEvent> proposed,
                                 Optional<CommandReceipt> acceptedCommandReceipt) {
        Revision nextRevision = revision.next();
        TransactionId transactionId = new TransactionId("transaction:revision-" + nextRevision.value());
        List<FrontierEvent> events = new ArrayList<>(proposed.size());
        S nextState = state;
        ScheduledActionQueue nextSchedules = schedules.copy();
        for (int index = 0; index < proposed.size(); index++) {
            ProposedEvent next = proposed.get(index);
            FrontierEvent event = new FrontierEvent(
                    FrontierEvent.SCHEMA_VERSION,
                    new EventId("event:revision-" + nextRevision.value() + "-" + index),
                    transactionId, worldId, nextRevision, eventInstant, next.subject(), causes, next.payload());
            if (event.payload() instanceof ScheduleEffect effect) {
                ScheduleEffectApplier.apply(nextSchedules, effect);
            } else {
                nextState = Objects.requireNonNull(reducer.apply(nextState, event), "reducer state");
            }
            events.add(event);
        }
        byte[] encoded = stateCodec.encode(nextState);
        if (encoded == null) {
            throw new IllegalStateException("state codec returned null");
        }
        TransactionRecord transaction = new TransactionRecord(transactionId, worldId, nextRevision, eventInstant, events,
                acceptedCommandReceipt);
        Durability durability = events.stream().anyMatch(event -> event.payload().requiresDurableBeforeEffect())
                ? Durability.DURABLE_BEFORE_EFFECT : Durability.BATCHABLE;
        transactionCommitter.commit(transaction, durability);
        state = nextState;
        revision = nextRevision;
        schedules = nextSchedules;
        transactions.add(transaction);
        return transactionId;
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
        return detail.length() <= 240 ? detail : detail.substring(0, 240);
    }
}
