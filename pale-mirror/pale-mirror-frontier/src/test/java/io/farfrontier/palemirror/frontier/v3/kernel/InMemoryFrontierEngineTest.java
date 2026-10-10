package io.farfrontier.palemirror.frontier.v3.kernel;

import io.farfrontier.palemirror.frontier.v3.api.AdvanceResult;
import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
import io.farfrontier.palemirror.frontier.v3.api.EngineScheduleBinding;
import io.farfrontier.palemirror.frontier.v3.api.FrontierCommand;
import io.farfrontier.palemirror.frontier.v3.api.FrontierEvent;
import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.FrontierProjection;
import io.farfrontier.palemirror.frontier.v3.api.ProjectionQuery;
import io.farfrontier.palemirror.frontier.v3.api.ProposedEvent;
import io.farfrontier.palemirror.frontier.v3.api.RejectionCode;
import io.farfrontier.palemirror.frontier.v3.api.Revision;
import io.farfrontier.palemirror.frontier.v3.api.ScheduleId;
import io.farfrontier.palemirror.frontier.v3.api.SimInstant;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.persistence.RecoveryImage;
import io.farfrontier.palemirror.frontier.v3.persistence.SnapshotRecord;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryFrontierEngineTest {
    private static final WorldId WORLD = new WorldId("frontier:test-world");
    private static final SubjectId SUBJECT = new SubjectId("settlement:test");

    @Test void reviewHintsRetainTheirCausalWalFactsAndRecoverTheCoalescedQueue() {
        var engine = engine(List.of(), false);
        var codecs = KernelPayloadCodecs.scheduleEffects();
        for (int i = 0; i < 5; i++) {
            var requested = new ScheduleEffect.ReconsiderationRequested(scheduled("schedule:review-" + i, SUBJECT.value(), 10 + i, 1));
            assertEquals(requested, codecs.decode(requested.type(), codecs.encode(requested)));
            assertInstanceOf(CommandResult.Accepted.class, engine.submit(command("command:review-" + i, new Revision(i), requested)));
        }
        assertEquals(1, engine.scheduledActions().size());
        assertEquals(5, engine.transactions().size(), "coalescing dispatch must not erase accepted causes");
        var replay = TransactionReplayer.replay(WORLD, new Counter(0), SimInstant.ZERO, List.of(), engine.transactions(),
                (state, event) -> reduce(state, event, false), state -> ByteBuffer.allocate(4).putInt(state.value()).array());
        assertEquals(engine.scheduledActions(), replay.schedules());
        engine.advanceTo(new SimInstant(10), new WorkBudget(1, 1));
        assertTrue(engine.scheduledActions().isEmpty());
        assertEquals(1, engine.projection(ProjectionQuery.summary()).value(), "the current state is reconsidered once, not five times");
    }

    @Test void admissionPressureIsReadOnlyAndCountsExpiryAtTheSameBoundaryAsSubmit() {
        var engine = engine(List.of(), false);
        for (int i = 0; i < 8; i++) assertInstanceOf(CommandResult.Accepted.class,
                engine.submit(command("command:pressure-" + i, new Revision(i), 1)));
        var full = engine.checkpoint();
        assertEquals(0, engine.commandAdmissionCapacity().availableCommands());
        assertEquals(0, engine.commandAdmissionCapacity().optionalCommands(8));
        assertEquals(full, engine.checkpoint(), "pressure inspection must not compact receipts or write state");
        engine.compact(engine.canonicalState().revision());
        engine.advanceTo(new SimInstant(100), new WorkBudget(1, 1));
        assertEquals(0, engine.commandAdmissionCapacity().availableReceipts(), "receipt cutoff is inclusive");
        engine.advanceTo(new SimInstant(101), new WorkBudget(1, 1));
        assertEquals(8, engine.commandAdmissionCapacity().availableReceipts());
        assertEquals(6, engine.commandAdmissionCapacity().optionalCommands(8), "optional work preserves the final quarter");
    }

    @Test
    void commandTransactionIsAtomicAndStaleOrDuplicateInputChangesNothing() {
        InMemoryFrontierEngine<Counter, CounterProjection> engine = engine(List.of(), false);
        FrontierCommand first = command("command:first", Revision.ZERO, 2);

        assertInstanceOf(CommandResult.Accepted.class, engine.submit(first));
        assertRejected(engine.submit(command("command:stale", Revision.ZERO, 2)), RejectionCode.STALE_REVISION);
        assertRejected(engine.submit(first), RejectionCode.DUPLICATE_COMMAND);

        CounterProjection projection = engine.projection(ProjectionQuery.summary());
        assertEquals(2, projection.value());
        assertEquals(new Revision(1L), projection.revision());
        assertEquals(1, engine.transactions().size());
    }

    @Test
    void exactContinuationBindingRejectsMissingAndStaleEngineActionsWithoutMutation() {
        ScheduledAction current = scheduled("schedule:bound", "settlement:a", 10L, 1);
        InMemoryFrontierEngine<Counter, CounterProjection> engine = engine(List.of(current), false);
        CommandId missingId = new CommandId("command:bound-missing");
        ScheduledAction missing = scheduled("schedule:bound-missing", "settlement:a", 10L, 1);
        FrontierCommand absent = new FrontierCommand(FrontierCommand.SCHEMA_VERSION, missingId, WORLD, Revision.ZERO,
                SimInstant.ZERO, SUBJECT, CauseChain.root(missingId), new Delta(1), Optional.of(new EngineScheduleBinding(Revision.ZERO, missing)));
        assertRejected(engine.submit(absent), RejectionCode.STALE_SCHEDULE_BINDING);
        assertEquals(Revision.ZERO, engine.projection(ProjectionQuery.summary()).revision());
        assertEquals(List.of(current), engine.scheduledActions());

        CommandId acceptedId = new CommandId("command:bound-accepted");
        FrontierCommand accepted = new FrontierCommand(FrontierCommand.SCHEMA_VERSION, acceptedId, WORLD, Revision.ZERO,
                SimInstant.ZERO, SUBJECT, CauseChain.root(acceptedId), new Delta(1), Optional.of(new EngineScheduleBinding(Revision.ZERO, current)));
        assertInstanceOf(CommandResult.Accepted.class, engine.submit(accepted));
        CommandId staleId = new CommandId("command:bound-stale");
        FrontierCommand stale = new FrontierCommand(FrontierCommand.SCHEMA_VERSION, staleId, WORLD, new Revision(1L),
                SimInstant.ZERO, SUBJECT, CauseChain.root(staleId), new Delta(1), Optional.of(new EngineScheduleBinding(Revision.ZERO, current)));
        assertRejected(engine.submit(stale), RejectionCode.STALE_SCHEDULE_BINDING);
        assertEquals(1, engine.projection(ProjectionQuery.summary()).value());
        assertEquals(List.of(current), engine.scheduledActions());
    }

    @Test
    void wrongThreadAndReducerFailureNeverPartiallyMutateState() throws InterruptedException {
        InMemoryFrontierEngine<Counter, CounterProjection> engine = engine(List.of(), true);
        AtomicReference<CommandResult> result = new AtomicReference<>();
        Thread thread = new Thread(() -> result.set(engine.submit(command("command:foreign", Revision.ZERO, 1))));
        thread.start();
        thread.join();

        assertRejected(result.get(), RejectionCode.WRONG_THREAD);
        assertRejected(engine.submit(command("command:explode", Revision.ZERO, 9)), RejectionCode.INVARIANT_FAILURE);
        assertEquals(0, engine.projection(ProjectionQuery.summary()).value());
        assertEquals(Revision.ZERO, engine.projection(ProjectionQuery.summary()).revision());
        assertEquals(0, engine.transactions().size());
        assertEquals("QUARANTINED", engine.status().kind().name());
    }

    @Test
    void failedWriteAheadCommitQuarantinesWithoutAcknowledgingOrMutatingCanonicalState() {
        InMemoryFrontierEngine<Counter, CounterProjection> engine = engine(List.of(), false,
                (transaction, durability) -> { throw new IllegalStateException("synthetic WAL failure"); });

        assertRejected(engine.submit(command("command:wal-failure", Revision.ZERO, 2)), RejectionCode.INVARIANT_FAILURE);

        CounterProjection projection = engine.projection(ProjectionQuery.summary());
        assertEquals(0, projection.value());
        assertEquals(Revision.ZERO, projection.revision());
        assertEquals(0, engine.transactions().size());
        assertEquals(0, engine.checkpoint().receipts().size());
        assertEquals("QUARANTINED", engine.status().kind().name());
    }

    @Test
    void completeStateAuditRejectsAReducerResultBeforeWalDurabilityOrCanonicalInstall() {
        List<TransactionRecord> durable = new ArrayList<>();
        InMemoryFrontierEngine<Counter, CounterProjection> engine = new InMemoryFrontierEngine<>(
                WORLD, new Counter(0), SimInstant.ZERO,
                (state, command) -> new CommandPlan.Accepted(List.of(new ProposedEvent(SUBJECT, command.payload()))),
                (state, action) -> List.of(new ProposedEvent(action.subject(), new Delta(action.weight()))),
                (state, event) -> reduce(state, event, false),
                state -> ByteBuffer.allocate(4).putInt(state.value()).array(),
                (state, world, revision, instant, query) -> new CounterProjection(world, revision, instant, state.value()),
                new EngineLimits(8, 100L, 8), List.of(),
                (transaction, durability) -> durable.add(transaction),
                state -> {
                    if (state.value() != 0) throw new IllegalArgumentException("synthetic complete-state invariant failure");
                });

        assertRejected(engine.submit(command("command:state-audit", Revision.ZERO, 1)), RejectionCode.INVARIANT_FAILURE);
        assertEquals(0, engine.projection(ProjectionQuery.summary()).value());
        assertEquals(Revision.ZERO, engine.projection(ProjectionQuery.summary()).revision());
        assertTrue(engine.transactions().isEmpty());
        assertTrue(durable.isEmpty(), "invalid reduced state must never reach the WAL committer");
        assertEquals("QUARANTINED", engine.status().kind().name());
    }

    @Test
    void writeAheadCommitReceivesTheCompleteTransactionBeforeAcknowledgement() {
        List<TransactionRecord> committed = new ArrayList<>();
        InMemoryFrontierEngine<Counter, CounterProjection> engine = engine(List.of(), false,
                (transaction, durability) -> {
                    assertEquals(io.farfrontier.palemirror.frontier.v3.persistence.Durability.BATCHABLE, durability);
                    committed.add(transaction);
                });

        assertInstanceOf(CommandResult.Accepted.class, engine.submit(command("command:durable", Revision.ZERO, 2)));

        assertEquals(engine.transactions(), committed);
        TransactionRecord transaction = committed.getFirst();
        assertEquals("transaction:revision-1", transaction.id().value());
        assertEquals("command:durable", transaction.acceptedCommandReceipt().orElseThrow().commandId().value());
        assertEquals(new Revision(1L), engine.projection(ProjectionQuery.summary()).revision());
    }

    @Test
    void dueActionsCommitBeforeAcknowledgementAndBudgetDeferralRetainsOrder() {
        List<ScheduledAction> schedules = List.of(
                scheduled("schedule:a", "settlement:a", 10L, 1),
                scheduled("schedule:b", "settlement:b", 10L, 2));
        InMemoryFrontierEngine<Counter, CounterProjection> engine = engine(schedules, false);

        AdvanceResult first = engine.advanceTo(new SimInstant(10L), new WorkBudget(1, 2));
        AdvanceResult second = engine.advanceTo(new SimInstant(11L), new WorkBudget(1, 2));

        assertEquals(1, first.transactions().size());
        assertEquals("schedule:b", first.deferredAction().orElseThrow().id().value());
        assertEquals(1, second.transactions().size());
        assertTrue(second.deferredAction().isEmpty());
        assertEquals(3, engine.projection(ProjectionQuery.summary()).value());
        assertEquals(List.of("event:revision-1-0", "event:revision-2-0"), engine.transactions().stream()
                .map(record -> record.events().getFirst().id().value()).toList());
        assertEquals(List.of("event:revision-1-1", "event:revision-2-1"), engine.transactions().stream()
                .map(record -> record.events().get(1).id().value()).toList());
    }

    @Test
    void failedDueActionRemainsScheduledAndQuarantinesTheEngine() {
        ScheduledAction action = scheduled("schedule:failure", "settlement:a", 10L, 9);
        InMemoryFrontierEngine<Counter, CounterProjection> engine = engine(List.of(action), true);

        AdvanceResult result = engine.advanceTo(new SimInstant(10L), new WorkBudget(1, 9));

        assertEquals("QUARANTINED", result.status().kind().name());
        assertEquals(List.of(action), engine.scheduledActions());
        assertEquals(0, engine.transactions().size());
        assertEquals(0, engine.projection(ProjectionQuery.summary()).value());
    }

    @Test
    void duePlannerMayDurablyCancelItsOwnObsoleteActionWithoutASecondAcknowledgement() {
        ScheduledAction action = scheduled("schedule:obsolete", "settlement:a", 10L, 1);
        InMemoryFrontierEngine<Counter, CounterProjection> engine = new InMemoryFrontierEngine<>(
                WORLD, new Counter(0), SimInstant.ZERO,
                (state, command) -> new CommandPlan.Accepted(List.of(new ProposedEvent(SUBJECT, command.payload()))),
                (state, due) -> List.of(new ProposedEvent(due.subject(), new ScheduleEffect.Cancelled(due.id()))),
                (state, event) -> reduce(state, event, false), state -> ByteBuffer.allocate(4).putInt(state.value()).array(),
                (state, world, revision, instant, query) -> new CounterProjection(world, revision, instant, state.value()),
                new EngineLimits(8, 100L, 8), List.of(action));

        AdvanceResult result = engine.advanceTo(new SimInstant(10L), new WorkBudget(1, 1));

        assertEquals("ACTIVE", result.status().kind().name());
        assertTrue(engine.scheduledActions().isEmpty());
        assertEquals(List.of("kernel.schedule_cancelled"), engine.transactions().getFirst().events().stream()
                .map(event -> event.payload().type()).toList());
    }

    @Test
    void heldDueContinuationNeverWritesHistoricalTimeAfterAnInterveningPhysicalCommand() {
        ScheduledAction held = scheduled("schedule:held", "settlement:held", 5L, 1);
        InMemoryFrontierEngine<Counter, CounterProjection> engine = new InMemoryFrontierEngine<>(
                WORLD, new Counter(0), SimInstant.ZERO,
                (state, command) -> new CommandPlan.Accepted(List.of(new ProposedEvent(SUBJECT, command.payload()))),
                (state, due) -> List.of(new ProposedEvent(due.subject(), new ScheduleEffect.Rescheduled(due.id(), due))),
                (state, event) -> reduce(state, event, false), state -> ByteBuffer.allocate(4).putInt(state.value()).array(),
                (state, world, revision, instant, query) -> new CounterProjection(world, revision, instant, state.value()),
                new EngineLimits(8, 100L, 8), List.of(held));

        engine.advanceTo(new SimInstant(10L), new WorkBudget(1, 1));
        CommandId commandId = new CommandId("command:physical-after-hold");
        assertInstanceOf(CommandResult.Accepted.class, engine.submit(new FrontierCommand(1, commandId, WORLD, new Revision(1L),
                new SimInstant(10L), SUBJECT, CauseChain.root(commandId), new Delta(1))));
        engine.advanceTo(new SimInstant(11L), new WorkBudget(1, 1));

        assertEquals(List.of(new SimInstant(5L), new SimInstant(10L), new SimInstant(10L)),
                engine.transactions().stream().map(TransactionRecord::instant).toList());
        assertEquals(3, new RecoveryImage(WORLD, Optional.empty(), engine.transactions()).walTail().size(),
                "a retained due action may not make a recoverable WAL move backwards");
    }

    @Test
    void scheduleOnlyTransactionDoesNotReauditTheSameImmutableStateSnapshot() {
        ScheduledAction action = scheduled("schedule:audit-free", "settlement:a", 10L, 1);
        AtomicInteger validations = new AtomicInteger();
        InMemoryFrontierEngine<Counter, CounterProjection> engine = new InMemoryFrontierEngine<>(
                WORLD, new Counter(0), SimInstant.ZERO,
                (state, command) -> new CommandPlan.Accepted(List.of(new ProposedEvent(SUBJECT, command.payload()))),
                (state, due) -> List.of(new ProposedEvent(due.subject(), new ScheduleEffect.Cancelled(due.id()))),
                (state, event) -> reduce(state, event, false), state -> ByteBuffer.allocate(4).putInt(state.value()).array(),
                (state, world, revision, instant, query) -> new CounterProjection(world, revision, instant, state.value()),
                new EngineLimits(8, 100L, 8), List.of(action), TransactionCommitter.noOp(), ignored -> validations.incrementAndGet());

        engine.advanceTo(new SimInstant(10L), new WorkBudget(1, 1));

        assertEquals(1, validations.get(), "only construction validates; a pure schedule transition has no new aggregate snapshot");
    }

    @Test
    void ownerHeldHeadDoesNotSpendIndependentBudgetAndCommittedReplayNeedsNoHistoricalPolicy() {
        ScheduledAction held = scheduled("schedule:held-first", "settlement:a", 5L, 1);
        ScheduledAction independent = scheduled("schedule:independent", "settlement:b", 6L, 1);
        ScheduledActionPlanner<Counter> planner = new ScheduledActionPlanner<>() {
            @Override public boolean held(Counter state, ScheduledAction action) {
                return state.value() == 0 && action.equals(held);
            }
            @Override public List<ProposedEvent> plan(Counter state, ScheduledAction action) {
                return List.of(new ProposedEvent(action.subject(), new Delta(1)));
            }
        };
        InMemoryFrontierEngine<Counter, CounterProjection> engine = new InMemoryFrontierEngine<>(
                WORLD, new Counter(0), SimInstant.ZERO,
                (state, command) -> new CommandPlan.Accepted(List.of(new ProposedEvent(SUBJECT, command.payload()))),
                planner, (state, event) -> reduce(state, event, false),
                state -> ByteBuffer.allocate(4).putInt(state.value()).array(),
                (state, world, revision, instant, query) -> new CounterProjection(world, revision, instant, state.value()),
                new EngineLimits(8, 100L, 8), List.of(held, independent));

        engine.advanceTo(new SimInstant(5L), new WorkBudget(1, 1));
        assertTrue(engine.transactions().isEmpty(), "an owner hold is not a repeated no-op WAL transaction");
        assertEquals(List.of(held, independent), engine.scheduledActions());
        engine.advanceTo(new SimInstant(10L), new WorkBudget(1, 1));
        assertEquals(List.of(held), engine.scheduledActions(), "held identity/deadline must stay byte-exact");
        assertEquals(1, engine.canonicalState().state().value(), "independent work receives the only budget slot");
        engine.advanceTo(new SimInstant(11L), new WorkBudget(1, 1));
        assertEquals(List.of(), engine.scheduledActions());
        assertEquals(List.of(new SimInstant(6L), new SimInstant(10L)),
                engine.transactions().stream().map(TransactionRecord::instant).toList());
        var replay = TransactionReplayer.replayFrom(WORLD, new Counter(0), Revision.ZERO, SimInstant.ZERO,
                List.of(held, independent), engine.transactions(), (state, event) -> reduce(state, event, false),
                state -> ByteBuffer.allocate(4).putInt(state.value()).array(), StateValidator.none());
        assertEquals(2, replay.state().value());
        assertTrue(replay.schedules().isEmpty());
        var firstOnly = TransactionReplayer.replayFrom(
                WORLD, new Counter(0), Revision.ZERO, SimInstant.ZERO, List.of(held, independent), List.of(engine.transactions().getFirst()),
                (state, event) -> reduce(state, event, false), state -> ByteBuffer.allocate(4).putInt(state.value()).array(),
                StateValidator.none());
        assertEquals(List.of(held), firstOnly.schedules(), "historical consumption must retain the other exact action");
        assertEquals(1, firstOnly.state().value());
        assertThrows(IllegalStateException.class, () -> TransactionReplayer.replayFrom(
                WORLD, new Counter(0), Revision.ZERO, SimInstant.ZERO, List.of(held), List.of(engine.transactions().getFirst()),
                (state, event) -> reduce(state, event, false), state -> ByteBuffer.allocate(4).putInt(state.value()).array(),
                StateValidator.none()), "missing committed schedule is still corrupt history");
    }

    @Test
    void committedConsumptionRejectsDuplicateAndPrematureFactsWithoutChangingTheQueue() {
        ScheduledAction action = scheduled("schedule:committed", "settlement:a", 10L, 1);
        ScheduledActionQueue queue = new ScheduledActionQueue();
        queue.schedule(action);
        var pending = queue.beginMutation();
        var consumption = new ScheduleEffect.Consumed(action.id());
        assertThrows(IllegalStateException.class, () -> ScheduleEffectApplier.applyCommitted(pending, consumption, new SimInstant(9L)));
        assertEquals(List.of(action), pending.snapshot());
        ScheduleEffectApplier.applyCommitted(pending, consumption, new SimInstant(10L));
        assertThrows(IllegalStateException.class, () -> ScheduleEffectApplier.applyCommitted(pending, consumption, new SimInstant(10L)));
        assertEquals(List.of(action), queue.snapshot(), "failed/uncommitted overlay cannot alter canonical state");
    }

    @Test
    void scheduleCreationRescheduleAndCancellationAreCommittedEvents() {
        InMemoryFrontierEngine<Counter, CounterProjection> engine = engine(List.of(), false);
        ScheduledAction original = scheduled("schedule:created", "settlement:a", 10L, 1);
        ScheduledAction replacement = scheduled("schedule:replacement", "settlement:b", 20L, 2);

        assertInstanceOf(CommandResult.Accepted.class,
                engine.submit(command("command:create", Revision.ZERO, new ScheduleEffect.Created(original))));
        assertEquals(List.of(original), engine.scheduledActions());
        assertInstanceOf(CommandResult.Accepted.class,
                engine.submit(command("command:reschedule", new Revision(1L),
                        new ScheduleEffect.Rescheduled(original.id(), replacement))));
        assertEquals(List.of(replacement), engine.scheduledActions());
        assertInstanceOf(CommandResult.Accepted.class,
                engine.submit(command("command:cancel", new Revision(2L), new ScheduleEffect.Cancelled(replacement.id()))));
        assertTrue(engine.scheduledActions().isEmpty());
        assertEquals(List.of("kernel.schedule_created", "kernel.schedule_rescheduled", "kernel.schedule_cancelled"),
                engine.transactions().stream().map(record -> record.events().getFirst().payload().type()).toList());
    }

    @Test
    void asynchronousClearanceCancelsItsExactContinuationWithoutConsumingAnEarlierRunnableAction() {
        ScheduledAction earlier = scheduled("schedule:other-resident", "resident:other", 5L, 1);
        ScheduledAction meal = scheduled("schedule:resident-meal", "resident:eating", 10L, 1);
        InMemoryFrontierEngine<Counter, CounterProjection> invalid = engine(List.of(earlier, meal), false);
        assertRejected(invalid.submit(command("command:wrong-hot-consume", Revision.ZERO,
                new ScheduleEffect.Consumed(meal.id()))), RejectionCode.INVARIANT_FAILURE);
        assertEquals(List.of(earlier, meal), invalid.scheduledActions(),
                "a rejected HOT effect must not partially mutate the durable queue");
        InMemoryFrontierEngine<Counter, CounterProjection> engine = engine(List.of(earlier, meal), false);
        assertInstanceOf(CommandResult.Accepted.class, engine.submit(command("command:hot-clearance", Revision.ZERO,
                new ScheduleEffect.Cancelled(meal.id()))));
        assertEquals(List.of(earlier), engine.scheduledActions(),
                "the unrelated earlier resident must retain its due action");
    }

    @Test
    void scheduleOnlyReferencesAreCheckedBeforeWalWithoutReauditingStateOrUnchangedQueue() {
        List<TransactionRecord> durable = new ArrayList<>();
        List<List<ScheduledAction>> checked = new ArrayList<>();
        StateValidator<Counter> validator = new StateValidator<>() {
            @Override public void validateInitial(Counter state) { }
            @Override public void validateTransition(Counter previous, Counter next) {
                throw new AssertionError("schedule-only work must not audit the aggregate");
            }
            @Override public void validateScheduleChanges(Counter state, List<ScheduledAction> changes) {
                checked.add(changes);
                if (changes.stream().anyMatch(action -> !action.subject().equals(SUBJECT))) {
                    throw new IllegalArgumentException("absent scheduled owner");
                }
            }
        };
        ScheduledAction retained = scheduled("schedule:retained", SUBJECT.value(), 10L, 1);
        ScheduledAction created = scheduled("schedule:new", SUBJECT.value(), 20L, 1);
        ScheduledAction invalid = scheduled("schedule:invalid", "subject:absent", 30L, 1);
        InMemoryFrontierEngine<Counter, CounterProjection> engine = new InMemoryFrontierEngine<>(
                WORLD, new Counter(0), SimInstant.ZERO,
                (state, command) -> new CommandPlan.Accepted(List.of(new ProposedEvent(SUBJECT, command.payload()))),
                (state, due) -> List.of(new ProposedEvent(due.subject(), new ScheduleEffect.Cancelled(due.id()))),
                (state, event) -> reduce(state, event, false), state -> ByteBuffer.allocate(4).putInt(state.value()).array(),
                (state, world, revision, instant, query) -> new CounterProjection(world, revision, instant, state.value()),
                new EngineLimits(8, 100L, 8), List.of(retained), (transaction, durability) -> durable.add(transaction),
                validator, FrontierExecutionMetrics.noOp(), KernelQuarantineReporter.disabled());

        assertInstanceOf(CommandResult.Accepted.class,
                engine.submit(command("command:valid-reference", Revision.ZERO, new ScheduleEffect.Created(created))));
        assertEquals(List.of(List.of(retained), List.of(created)), checked);
        assertRejected(engine.submit(command("command:invalid-reference", new Revision(1L),
                new ScheduleEffect.Rescheduled(created.id(), invalid))), RejectionCode.INVARIANT_FAILURE);
        assertEquals(List.of(retained, created), engine.scheduledActions());
        assertEquals(1, durable.size(), "invalid schedule reference must not reach the WAL");
        assertEquals(new Revision(1L), engine.canonicalState().revision());

        var replayed = TransactionReplayer.replayFrom(WORLD, new Counter(0), Revision.ZERO, SimInstant.ZERO,
                List.of(retained), durable, (state, event) -> reduce(state, event, false),
                state -> ByteBuffer.allocate(4).putInt(state.value()).array(), validator);
        assertEquals(List.of(retained, created), replayed.schedules());
        // Simulate a WAL written by the former bypass, not a fabricated successful outcome.
        var unchecked = engine(List.of(), false);
        assertInstanceOf(CommandResult.Accepted.class, unchecked.submit(
                command("command:legacy-invalid-reference", Revision.ZERO, new ScheduleEffect.Created(invalid))));
        assertThrows(IllegalArgumentException.class, () -> TransactionReplayer.replayFrom(
                WORLD, new Counter(0), Revision.ZERO, SimInstant.ZERO, List.of(), unchecked.transactions(),
                (state, event) -> reduce(state, event, false),
                state -> ByteBuffer.allocate(4).putInt(state.value()).array(), validator));
    }

    @Test
    void ownerRetirementMustCancelItsScheduleInTheSameDurableTransaction() {
        var continuation = scheduled("schedule:retiring-owner", SUBJECT.value(), 10L, 1);
        var durable = new ArrayList<TransactionRecord>();
        StateValidator<Counter> validator = new StateValidator<>() {
            @Override public void validateInitial(Counter state) { }
            @Override public void validateTransition(Counter previous, Counter next) { }
            @Override public void validateRecoveryInitial(Counter state, List<ScheduledAction> schedules) {
                validateScheduleChanges(state, schedules);
            }
            @Override public void validateScheduleChanges(Counter state, List<ScheduledAction> schedules) {
                if (state.value() != 0 && !schedules.isEmpty()) throw new IllegalArgumentException("retired owner");
            }
            @Override public void validateTransaction(Counter previous, Counter next, List<FrontierEvent> events,
                                                       List<ScheduledAction> before, List<ScheduledAction> after) {
                validateScheduleChanges(next, after);
            }
        };
        java.util.function.Supplier<InMemoryFrontierEngine<Counter, CounterProjection>> factory = () ->
                new InMemoryFrontierEngine<Counter, CounterProjection>(WORLD, new Counter(0), SimInstant.ZERO,
                (state, command) -> {
                    var events = new ArrayList<ProposedEvent>();
                    events.add(new ProposedEvent(SUBJECT, new Delta(1)));
                    if (command.payload().equals(new Delta(2))) {
                        events.add(new ProposedEvent(SUBJECT, new ScheduleEffect.Cancelled(continuation.id())));
                    }
                    return new CommandPlan.Accepted(events);
                }, (state, action) -> List.of(), (state, event) -> reduce(state, event, false),
                state -> ByteBuffer.allocate(4).putInt(state.value()).array(),
                (state, world, revision, instant, query) -> new CounterProjection(world, revision, instant, state.value()),
                new EngineLimits(8, 100L, 8), List.of(continuation),
                (transaction, durability) -> durable.add(transaction), validator,
                FrontierExecutionMetrics.noOp(), KernelQuarantineReporter.disabled());
        var engine = factory.get();
        assertRejected(engine.submit(command("command:incomplete-retirement", Revision.ZERO, new Delta(1))),
                RejectionCode.INVARIANT_FAILURE);
        assertTrue(durable.isEmpty());
        assertEquals(0, engine.canonicalState().state().value());
        assertEquals(List.of(continuation), engine.scheduledActions());

        // The injected invariant violation correctly quarantines that instance.
        // Exercise the valid transition in a fresh engine with the same intact initial state.
        engine = factory.get();
        assertInstanceOf(CommandResult.Accepted.class,
                engine.submit(command("command:complete-retirement", Revision.ZERO, new Delta(2))));
        assertEquals(1, durable.size());
        assertEquals(1, engine.canonicalState().state().value());
        assertTrue(engine.scheduledActions().isEmpty());
        var replayed = TransactionReplayer.replayFrom(WORLD, new Counter(0), Revision.ZERO, SimInstant.ZERO,
                List.of(continuation), durable, (state, event) -> reduce(state, event, false),
                state -> ByteBuffer.allocate(4).putInt(state.value()).array(), validator);
        assertTrue(replayed.schedules().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> TransactionReplayer.replayFrom(
                WORLD, new Counter(1), Revision.ZERO, SimInstant.ZERO, List.of(continuation), List.of(),
                (state, event) -> reduce(state, event, false),
                state -> ByteBuffer.allocate(4).putInt(state.value()).array(), validator));
    }

    @Test
    void overCapacityScheduledPlanQuarantinesBeforeWalOrCanonicalQueueMutation() {
        ScheduledAction trigger = scheduled("schedule:capacity-trigger", "settlement:capacity", 10L, 1);
        ScheduledAction first = scheduled("schedule:capacity-first", "settlement:capacity", 20L, 1);
        ScheduledAction second = scheduled("schedule:capacity-second", "settlement:capacity", 21L, 1);
        List<TransactionRecord> durable = new ArrayList<>();
        InMemoryFrontierEngine<Counter, CounterProjection> engine = new InMemoryFrontierEngine<>(
                WORLD, new Counter(0), SimInstant.ZERO,
                (state, command) -> new CommandPlan.Accepted(List.of(new ProposedEvent(SUBJECT, command.payload()))),
                (state, due) -> List.of(new ProposedEvent(due.subject(), new ScheduleEffect.Created(first)),
                        new ProposedEvent(due.subject(), new ScheduleEffect.Created(second))),
                (state, event) -> reduce(state, event, false), state -> ByteBuffer.allocate(4).putInt(state.value()).array(),
                (state, world, revision, instant, query) -> new CounterProjection(world, revision, instant, state.value()),
                new EngineLimits(8, 100L, 8, 1), List.of(trigger), (transaction, durability) -> durable.add(transaction));

        AdvanceResult result = engine.advanceTo(new SimInstant(10L), new WorkBudget(1, 1));

        assertEquals("QUARANTINED", result.status().kind().name());
        assertEquals(List.of(trigger), engine.scheduledActions(), "an over-cap overlay may not consume its triggering work");
        assertTrue(engine.transactions().isEmpty() && durable.isEmpty(), "an over-cap queue plan may not reach the WAL");
        assertEquals(0, engine.projection(ProjectionQuery.summary()).value());
    }

    @Test
    void oversizedRecoveredFutureWorkFailsBeforeCanonicalEngineInstallation() {
        ScheduledAction first = scheduled("schedule:recovery-capacity-first", "settlement:capacity", 10L, 1);
        ScheduledAction second = scheduled("schedule:recovery-capacity-second", "settlement:capacity", 11L, 1);
        FrontierEngineConfiguration<Counter, CounterProjection> base = configuration();
        FrontierEngineConfiguration<Counter, CounterProjection> constrained = new FrontierEngineConfiguration<>(
                WORLD, new Counter(0), SimInstant.ZERO,
                (state, command) -> new CommandPlan.Accepted(List.of(new ProposedEvent(SUBJECT, command.payload()))),
                (state, action) -> List.of(new ProposedEvent(action.subject(), new Delta(action.weight()))),
                (state, event) -> reduce(state, event, false), base.stateCodec(),
                (state, world, revision, instant, query) -> new CounterProjection(world, revision, instant, state.value()),
                new EngineLimits(8, 100L, 8, 1), List.of(), TransactionCommitter.noOp());

        assertThrows(IllegalArgumentException.class, () -> FrontierEngines.recover(constrained,
                new RecoveryImage(WORLD, Optional.of(new SnapshotRecord(new io.farfrontier.palemirror.frontier.v3.api.CheckpointImage(
                        WORLD, Revision.ZERO, SimInstant.ZERO, base.stateCodec().encode(base.initialState()), List.of(first, second), List.of()), 0L)), List.of())));
    }

    @Test
    void sameInputStreamProducesTheSameCheckpointAndEvents() {
        InMemoryFrontierEngine<Counter, CounterProjection> first = engine(List.of(), false);
        InMemoryFrontierEngine<Counter, CounterProjection> second = engine(List.of(), false);

        for (InMemoryFrontierEngine<Counter, CounterProjection> engine : List.of(first, second)) {
            assertInstanceOf(CommandResult.Accepted.class, engine.submit(command("command:one", Revision.ZERO, 1)));
            assertInstanceOf(CommandResult.Accepted.class, engine.submit(command("command:two", new Revision(1L), 2)));
        }

        assertArrayEquals(first.checkpoint().canonicalState(), second.checkpoint().canonicalState());
        assertEquals(first.transactions(), second.transactions());
        assertTrue(Arrays.equals(first.checkpoint().canonicalState(), ByteBuffer.allocate(4).putInt(3).array()));
    }

    @Test
    void retainedTransactionsReplayToTheSameStateAndRejectCorruptSequence() {
        List<ScheduledAction> schedules = List.of(scheduled("schedule:replay", "settlement:a", 4L, 2));
        InMemoryFrontierEngine<Counter, CounterProjection> engine = engine(schedules, false);
        assertInstanceOf(CommandResult.Accepted.class, engine.submit(command("command:replay", Revision.ZERO, 3)));
        engine.advanceTo(new SimInstant(4L), new WorkBudget(1, 2));

        TransactionReplayer.ReplayResult<Counter> replay = TransactionReplayer.replay(
                WORLD, new Counter(0), SimInstant.ZERO, schedules, engine.transactions(),
                (state, event) -> reduce(state, event, false),
                state -> ByteBuffer.allocate(4).putInt(state.value()).array());

        assertEquals(engine.projection(ProjectionQuery.summary()).value(), replay.state().value());
        assertEquals(new Revision(2L), replay.revision());
        assertTrue(replay.schedules().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new TransactionRecord(engine.transactions().getFirst().id(), WORLD,
                new Revision(2L), SimInstant.ZERO, engine.transactions().getFirst().events()));
        TransactionRecord skippedRevision = engine.transactions().get(1);
        assertThrows(IllegalArgumentException.class, () -> TransactionReplayer.replay(
                WORLD, new Counter(0), SimInstant.ZERO, List.of(), List.of(skippedRevision),
                (state, event) -> reduce(state, event, false), state -> new byte[] {0}));
    }

    @Test
    void delayedCommandCannotCreateAChronologicallyInvalidEvent() {
        InMemoryFrontierEngine<Counter, CounterProjection> engine = engine(List.of(), false);
        engine.advanceTo(new SimInstant(5L), new WorkBudget(1, 1));

        assertRejected(engine.submit(command("command:late", Revision.ZERO, 1)), RejectionCode.COMMAND_EXPIRED);
        assertEquals(0, engine.transactions().size());
        assertEquals(new SimInstant(5L), engine.projection(ProjectionQuery.summary()).instant());
    }

    @Test
    void checkpointIncludesBoundedScheduleAndCommandReceiptState() {
        ScheduledAction action = scheduled("schedule:checkpoint", "settlement:a", 8L, 1);
        InMemoryFrontierEngine<Counter, CounterProjection> engine = engine(List.of(action), false);
        assertInstanceOf(CommandResult.Accepted.class, engine.submit(command("command:checkpoint", Revision.ZERO, 2)));

        io.farfrontier.palemirror.frontier.v3.api.CheckpointImage checkpoint = engine.checkpoint();

        assertEquals(List.of(action), checkpoint.schedules());
        assertEquals(1, checkpoint.receipts().size());
        assertEquals("command:checkpoint", checkpoint.receipts().getFirst().commandId().value());
        assertEquals("transaction:revision-1", checkpoint.receipts().getFirst().transactionId().value());
    }

    @Test
    void compactionDropsOnlySnapshotCoveredTransactions() {
        InMemoryFrontierEngine<Counter, CounterProjection> engine = engine(List.of(), false);
        assertInstanceOf(CommandResult.Accepted.class, engine.submit(command("command:compact-one", Revision.ZERO, 1)));
        assertInstanceOf(CommandResult.Accepted.class, engine.submit(command("command:compact-two", new Revision(1L), 1)));

        engine.compact(new Revision(1L));

        assertEquals(1, engine.transactions().size());
        assertEquals(new Revision(2L), engine.transactions().getFirst().revision());
        assertThrows(IllegalArgumentException.class, () -> engine.compact(new Revision(3L)));
    }

    @Test
    void checkpointReusesTheEncodedStateUntilACommittedRevisionChangesIt() {
        AtomicInteger encodes = new AtomicInteger();
        StateCodec<Counter> codec = new StateCodec<>() {
            @Override public byte[] encode(Counter state) {
                encodes.incrementAndGet();
                return ByteBuffer.allocate(4).putInt(state.value()).array();
            }
            @Override public Counter decode(byte[] bytes) { return new Counter(ByteBuffer.wrap(bytes).getInt()); }
        };
        InMemoryFrontierEngine<Counter, CounterProjection> engine = new InMemoryFrontierEngine<>(WORLD, new Counter(0), SimInstant.ZERO,
                (state, command) -> new CommandPlan.Accepted(List.of(new ProposedEvent(SUBJECT, command.payload()))),
                (state, action) -> List.of(new ProposedEvent(action.subject(), new Delta(action.weight()))),
                (state, event) -> reduce(state, event, false), codec,
                (state, world, revision, instant, query) -> new CounterProjection(world, revision, instant, state.value()),
                new EngineLimits(8, 100L, 8), List.of());

        engine.checkpoint(); engine.checkpoint();
        assertEquals(1, encodes.get());
        ScheduledAction schedule = scheduled("schedule:cached-state", "settlement:cached", 10L, 1);
        assertInstanceOf(CommandResult.Accepted.class, engine.submit(command("command:schedule-only", Revision.ZERO,
                new ScheduleEffect.Created(schedule))));
        assertEquals(1, encodes.get(), "schedule-only transactions must reuse exact canonical-state bytes");
        assertEquals(List.of(schedule), engine.checkpoint().schedules());
        assertInstanceOf(CommandResult.Accepted.class, engine.submit(command("command:cache", new Revision(1L), 2)));
        assertEquals(1, encodes.get(), "a WAL-backed state change waits for the next explicit checkpoint");
        var execution = engine.executionView();
        assertEquals(new Revision(2L), execution.revision());
        assertEquals(List.of(schedule), execution.schedules());
        engine.advanceTo(new SimInstant(1L), new WorkBudget(8, 8));
        assertEquals(new SimInstant(1L), engine.executionView().instant());
        assertEquals(List.of(schedule), execution.schedules(), "retained execution views remain immutable");
        assertEquals(1, encodes.get(), "ordinary current execution reads must never encode the world");
        engine.checkpoint(); engine.checkpoint();
        assertEquals(2, encodes.get());
    }

    @Test
    void recoveryExpiresSnapshotReceiptsAtTheReplayedWalInstantBeforeCheckingCapacity() {
        var uninterrupted = engine(List.of(), false);
        for (int i = 0; i < 8; i++) assertInstanceOf(CommandResult.Accepted.class,
                uninterrupted.submit(command("command:retained-" + i, new Revision(i), 1)));
        var snapshot = uninterrupted.checkpoint();
        uninterrupted.compact(snapshot.revision());
        uninterrupted.advanceTo(new SimInstant(101), new WorkBudget(1, 1));
        var id = new CommandId("command:after-expiry");
        var command = new FrontierCommand(1, id, WORLD, uninterrupted.executionView().revision(),
                new SimInstant(101), SUBJECT, CauseChain.root(id), new Delta(1));
        assertInstanceOf(CommandResult.Accepted.class, uninterrupted.submit(command));
        var tail = uninterrupted.transactions();
        var recovered = FrontierEngines.recover(configuration(),
                new RecoveryImage(WORLD, Optional.of(new SnapshotRecord(snapshot, 1L)), tail));
        assertEquals(uninterrupted.checkpoint(), recovered.checkpoint());
        assertEquals(1, recovered.checkpoint().receipts().size());
        assertEquals(7, recovered.commandAdmissionCapacity().availableReceipts());
        assertRejected(recovered.submit(command), RejectionCode.DUPLICATE_COMMAND);
    }

    @Test
    void engineFactoryRecoversVerifiedCheckpointAndWalWithoutStartingFresh() {
        InMemoryFrontierEngine<Counter, CounterProjection> uninterrupted = engine(List.of(), false);
        assertInstanceOf(CommandResult.Accepted.class, uninterrupted.submit(command("command:checkpoint-one", Revision.ZERO, 2)));
        io.farfrontier.palemirror.frontier.v3.api.CheckpointImage checkpoint = uninterrupted.checkpoint();
        assertInstanceOf(CommandResult.Accepted.class, uninterrupted.submit(command("command:checkpoint-two", new Revision(1L), 3)));

        FrontierEngineConfiguration<Counter, CounterProjection> configuration = configuration();
        io.farfrontier.palemirror.frontier.v3.api.FrontierEngine<CounterProjection> recovered = FrontierEngines.recover(configuration,
                new RecoveryImage(WORLD, Optional.of(new SnapshotRecord(checkpoint, 1L)), List.of(uninterrupted.transactions().getLast())));

        assertEquals(uninterrupted.projection(ProjectionQuery.summary()), recovered.projection(ProjectionQuery.summary()));
        assertEquals(uninterrupted.checkpoint(), recovered.checkpoint());
        assertRejected(recovered.submit(command("command:checkpoint-two", new Revision(2L), 3)), RejectionCode.DUPLICATE_COMMAND);
        assertThrows(IllegalArgumentException.class, () -> FrontierEngines.recover(configuration,
                new RecoveryImage(new WorldId("frontier:other"), Optional.empty(), List.of())));
    }

    @Test
    void parkedDueActionWakesFromAcceptedOwnerChangeAndMatchesFreshRecovery() {
        ScheduledAction waiting = scheduled("schedule:addressed-wait", SUBJECT.value(), 1L, 1);
        StateCodec<Counter> codec = new StateCodec<>() {
            @Override public byte[] encode(Counter state) { return ByteBuffer.allocate(4).putInt(state.value()).array(); }
            @Override public Counter decode(byte[] bytes) { return new Counter(ByteBuffer.wrap(bytes).getInt()); }
        };
        ScheduledActionPlanner<Counter> planner = new ScheduledActionPlanner<>() {
            @Override public List<ProposedEvent> plan(Counter state, ScheduledAction action) {
                return List.of(new ProposedEvent(action.subject(), new Delta(10)));
            }
            @Override public boolean held(Counter state, ScheduledAction action) { return state.value() == 0; }
            @Override public Set<SubjectId> holdWakeKeys(Counter state, ScheduledAction action) { return Set.of(SUBJECT); }
            @Override public Set<SubjectId> wakeKeys(Counter previous, Counter next, FrontierEvent event) {
                return previous.value() == next.value() ? Set.of() : Set.of(SUBJECT);
            }
        };
        var configuration = new FrontierEngineConfiguration<>(WORLD, new Counter(0), SimInstant.ZERO,
                (state, command) -> new CommandPlan.Accepted(List.of(new ProposedEvent(SUBJECT, command.payload()))),
                planner, (state, event) -> reduce(state, event, false), codec,
                (state, world, revision, instant, query) -> new CounterProjection(world, revision, instant, state.value()),
                new EngineLimits(8, 100L, 8), List.of(waiting), TransactionCommitter.noOp());
        var uninterrupted = FrontierEngines.create(configuration);
        uninterrupted.advanceTo(new SimInstant(1L), new WorkBudget(1, 1));
        assertEquals(List.of(waiting), uninterrupted.checkpoint().schedules());
        assertEquals(Revision.ZERO, uninterrupted.checkpoint().revision());
        var recovered = FrontierEngines.recover(configuration, new RecoveryImage(WORLD,
                Optional.of(new SnapshotRecord(uninterrupted.checkpoint(), 1L)), List.of()));
        CommandId id = new CommandId("command:addressed-source-arrived");
        FrontierCommand sourceChange = new FrontierCommand(1, id, WORLD, Revision.ZERO,
                new SimInstant(1L), SUBJECT, CauseChain.root(id), new Delta(1));
        assertInstanceOf(CommandResult.Accepted.class, uninterrupted.submit(sourceChange));
        assertInstanceOf(CommandResult.Accepted.class, recovered.submit(sourceChange));
        uninterrupted.advanceTo(new SimInstant(2L), new WorkBudget(1, 1));
        recovered.advanceTo(new SimInstant(2L), new WorkBudget(1, 1));
        assertEquals(11, uninterrupted.projection(ProjectionQuery.summary()).value());
        assertEquals(uninterrupted.checkpoint(), recovered.checkpoint());
    }

    private static InMemoryFrontierEngine<Counter, CounterProjection> engine(
            List<ScheduledAction> schedules, boolean failOnNine
    ) {
        return engine(schedules, failOnNine, TransactionCommitter.noOp());
    }

    private static InMemoryFrontierEngine<Counter, CounterProjection> engine(
            List<ScheduledAction> schedules, boolean failOnNine, TransactionCommitter transactionCommitter
    ) {
        return new InMemoryFrontierEngine<>(
                WORLD,
                new Counter(0),
                SimInstant.ZERO,
                (state, command) -> new CommandPlan.Accepted(List.of(new ProposedEvent(SUBJECT, command.payload()))),
                (state, action) -> List.of(new ProposedEvent(action.subject(), new Delta(action.weight()))),
                (state, event) -> reduce(state, event, failOnNine),
                state -> ByteBuffer.allocate(4).putInt(state.value()).array(),
                (state, world, revision, instant, query) -> new CounterProjection(world, revision, instant, state.value()),
                new EngineLimits(8, 100L, 8),
                schedules,
                transactionCommitter);
    }

    private static FrontierEngineConfiguration<Counter, CounterProjection> configuration() {
        StateCodec<Counter> codec = new StateCodec<>() {
            @Override public byte[] encode(Counter state) { return ByteBuffer.allocate(4).putInt(state.value()).array(); }
            @Override public Counter decode(byte[] bytes) {
                if (bytes.length != 4) throw new IllegalArgumentException("counter checkpoint is malformed");
                return new Counter(ByteBuffer.wrap(bytes).getInt());
            }
        };
        return new FrontierEngineConfiguration<>(WORLD, new Counter(0), SimInstant.ZERO,
                (state, command) -> new CommandPlan.Accepted(List.of(new ProposedEvent(SUBJECT, command.payload()))),
                (state, action) -> List.of(new ProposedEvent(action.subject(), new Delta(action.weight()))),
                (state, event) -> reduce(state, event, false), codec,
                (state, world, revision, instant, query) -> new CounterProjection(world, revision, instant, state.value()),
                new EngineLimits(8, 100L, 8), List.of(), TransactionCommitter.noOp());
    }

    private static Counter reduce(Counter state, FrontierEvent event, boolean failOnNine) {
        Delta delta = (Delta) event.payload();
        if (failOnNine && delta.value() == 9) {
            throw new IllegalStateException("synthetic reducer failure");
        }
        return new Counter(Math.addExact(state.value(), delta.value()));
    }

    private static FrontierCommand command(String id, Revision revision, int delta) {
        return command(id, revision, new Delta(delta));
    }

    private static FrontierCommand command(String id, Revision revision, FrontierPayload payload) {
        CommandId commandId = new CommandId(id);
        return new FrontierCommand(1, commandId, WORLD, revision, SimInstant.ZERO, SUBJECT,
                CauseChain.root(commandId), payload);
    }

    private static ScheduledAction scheduled(String id, String subject, long dueAt, int weight) {
        return new ScheduledAction(new ScheduleId(id), new SimInstant(dueAt), 0,
                new SubjectId(subject), "process.test", weight);
    }

    private static void assertRejected(CommandResult result, RejectionCode code) {
        CommandResult.Rejected rejected = assertInstanceOf(CommandResult.Rejected.class, result);
        assertEquals(code, rejected.rejection().code());
    }

    private record Counter(int value) {
    }

    private record Delta(int value) implements FrontierPayload {
        @Override
        public String type() {
            return "test.delta";
        }
    }

    private record CounterProjection(WorldId worldId, Revision revision, SimInstant instant, int value)
            implements FrontierProjection {
    }
}
