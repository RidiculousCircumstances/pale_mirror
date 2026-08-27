package io.farfrontier.palemirror.frontier.v3.kernel;

import io.farfrontier.palemirror.frontier.v3.api.AdvanceResult;
import io.farfrontier.palemirror.frontier.v3.api.CauseChain;
import io.farfrontier.palemirror.frontier.v3.api.CommandId;
import io.farfrontier.palemirror.frontier.v3.api.CommandResult;
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
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryFrontierEngineTest {
    private static final WorldId WORLD = new WorldId("frontier:test-world");
    private static final SubjectId SUBJECT = new SubjectId("settlement:test");

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
        TransactionRecord corrupt = new TransactionRecord(engine.transactions().getFirst().id(), WORLD,
                new Revision(2L), SimInstant.ZERO, engine.transactions().getFirst().events());
        assertThrows(IllegalArgumentException.class, () -> TransactionReplayer.replay(
                WORLD, new Counter(0), SimInstant.ZERO, List.of(), List.of(corrupt),
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

    private static InMemoryFrontierEngine<Counter, CounterProjection> engine(
            List<ScheduledAction> schedules, boolean failOnNine
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
                schedules);
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
                new EngineLimits(8, 100L, 8), List.of());
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
