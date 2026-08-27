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
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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

    private static Counter reduce(Counter state, FrontierEvent event, boolean failOnNine) {
        Delta delta = (Delta) event.payload();
        if (failOnNine && delta.value() == 9) {
            throw new IllegalStateException("synthetic reducer failure");
        }
        return new Counter(Math.addExact(state.value(), delta.value()));
    }

    private static FrontierCommand command(String id, Revision revision, int delta) {
        CommandId commandId = new CommandId(id);
        return new FrontierCommand(1, commandId, WORLD, revision, SimInstant.ZERO, SUBJECT,
                CauseChain.root(commandId), new Delta(delta));
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
